/**
 *  This class implements the WSUM operator for all retrieval models.
 *
 *  Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.*;

//import Qryop.DaaTPtr;

public class QryopSlWSum extends QryopSl {
	
  private List<Double> weights = new ArrayList<Double>();

  /**
   *  It is convenient for the constructor to accept a variable number
   *  of arguments. Thus new qryopAnd (arg1, arg2, arg3, ...).
   *  @param q A query argument (a query operator).
   */
  public QryopSlWSum(Qryop... q) {
    for (int i = 0; i < q.length; i++)
      this.args.add(q[i]);
  }

  /**
   *  Appends an argument to the list of query operator arguments.  This
   *  simplifies the design of some query parsing architectures.
   *  @param {q} q The query argument (query operator) to append.
   *  @return void
   *  @throws IOException
   */
  public void add (Qryop a) {
	
	if (a instanceof QryopIlTerm) {
		String strOp = a.toString();
		String[] parts = strOp.split("\\.");
		if (parts.length == 3 && parts[1].charAt(0) >= '0' && parts[1].charAt(0) <= '9') {
			if (args.size() != weights.size()) {
			  weights.remove(weights.size() - 1);
			}
			this.weights.add(Double.valueOf(parts[0] + "." + parts[1]));
			return;
		}
	}
	
    this.args.add(a);
  }

  /**
   *  Evaluates the query operator, including any child operators and
   *  returns the result.
   *  @param r A retrieval model that controls how the operator behaves.
   *  @return The result of evaluating the query.
   *  @throws IOException
   */
  public QryResult evaluate(RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelIndri) {
      return (evaluateIndri ((RetrievalModelIndri)r));
    }
    else {
    	System.out.println("Error: #WAND not supported in this model.");
    }

    return null;
  }
  
  
  /**
   *  Evaluates the query operator for Indri retrieval model,
   *  including any child operators and returns the result.
   *  @param r A retrieval model that controls how the operator behaves.
   *  @return The result of evaluating the query.
   *  @throws IOException
   */
  public QryResult evaluateIndri(RetrievalModelIndri r) throws IOException {

	if (weights.size() != args.size()) {
		//System.err.println("Error: weights do not match args.");
		weights.remove(weights.size() - 1);
	}
	
	// weights normalization
	double totalWeight = 0;
	for (Double wt : this.weights) {
	  totalWeight += wt;
	}
	for (int i = 0; i < this.weights.size(); i ++) {
	  double wt = this.weights.get(i);
	  this.weights.set(i, wt / totalWeight);
	}

    //  Initialization
    allocDaaTPtrs (r);
    //syntaxCheckArgResults (this.daatPtrs);
    //int numPtrs = this.daatPtrs.size();
    //List<Integer> ptrsIDs = new ArrayList<Integer>();
    /*for (int i = 0; i < this.daatPtrs.size(); i ++) {
      ptrsIDs.add(i);
    }*/
    
    for (int i = 0 ; i < this.daatPtrs.size(); i ++) {
        if (this.args.get(i) instanceof QryopSlWSum && this.args.get(i).args.isEmpty()) {
          this.args.remove(i);
          this.daatPtrs.remove(i);
        }
        else if (this.daatPtrs.get(i).scoreList.scores.isEmpty()) { // get rid of empty inverted lists
          this.args.remove(i);
          this.daatPtrs.remove(i);
        }
    }
    
    int ptrsCount = this.daatPtrs.size();	// count the number of active ptrs 

    QryResult result = new QryResult ();
    
    if (ptrsCount == 0) {
        freeDaaTPtrs();
        return result;
    }

    //  Each pass of the loop adds 1 document to result until all of
    //  the score lists are depleted.  When a list is depleted, it
    //  is removed from daatPtrs, so this loop runs until daatPtrs is empty.

    //  This implementation is intended to be clear.  A more efficient
    //  implementation would combine loops and use merge-sort.

    //System.out.println("Before while");
    
    while (ptrsCount > 0) {

      int nextDocid = getSmallestCurrentDocid ();
      double docScore = 0.0;
      List<Double> ptrsScores = new ArrayList<Double>();  // scores of the ptri's with nextDocid 

      for (int i=0; i<this.daatPtrs.size(); i++) {
		DaaTPtr ptri = this.daatPtrs.get(i);
		//int ptrID = ptrsIDs.get(i);
		
		if (true/*!ptri.scoreList.scores.isEmpty()*/) {
		  
		  if (ptri.nextDoc != Integer.MAX_VALUE && ptri.scoreList.getDocid (ptri.nextDoc) == nextDocid) {
		    ptrsScores.add(ptri.scoreList.getDocidScore (ptri.nextDoc));
			ptri.nextDoc ++;
		  }
		  else {	// get default score
			if (this.args.get(i) instanceof QryopSlScore) {
			  ptrsScores.add(((QryopSlScore)(this.args.get(i))).getDefaultScore(
					  r, nextDocid));
			}
			else if (this.args.get(i) instanceof QryopSlAnd) {
				  ptrsScores.add(((QryopSlAnd)(this.args.get(i))).getDefaultScore(
						  r, nextDocid));
			}
			else if (this.args.get(i) instanceof QryopSlOr){
				  ptrsScores.add(((QryopSlOr)(this.args.get(i))).getDefaultScore(
						  r, nextDocid));
			}
			else if (this.args.get(i) instanceof QryopSlWAnd){
				  ptrsScores.add(((QryopSlWAnd)(this.args.get(i))).getDefaultScore(
						  r, nextDocid));
			}
			else if (this.args.get(i) instanceof QryopSlWSum){
				  ptrsScores.add(((QryopSlWSum)(this.args.get(i))).getDefaultScore(
						  r, nextDocid));
			}
			else {
				System.out.println("Error: default score not implemented in this operator");
				break;
			}
		  }
		}
      }
      
      
      // add score to result     
      if (ptrsScores.size() != this.daatPtrs.size()) {
    	System.err.println("Not enough ptrsScores");
      }
      else {
    	for (int i = 0; i < ptrsScores.size(); i ++) {
    	  docScore += ptrsScores.get(i) * weights.get(i);
    	}
    	if (docScore > 0) {
    	  result.docScores.add (nextDocid, docScore);
    	}
      }

      //  If a DaatPtr has reached the end of its list, decrease the ptrsCount.
      //  The loop is backwards so that removing an arg does not
      //  interfere with iteration.

      for (int i=this.daatPtrs.size()-1; i>=0; i--) {
		DaaTPtr ptri = this.daatPtrs.get(i);
	
		if (ptri.nextDoc >= ptri.scoreList.scores.size()) {
	      ptri.nextDoc = Integer.MAX_VALUE;
	      ptrsCount --;
		  //this.daatPtrs.remove (i);
		  //ptrsIDs.remove(i);
		}
      }
    }
    
    //System.out.println("After while");

    freeDaaTPtrs();

    return result;
  }


  /*
   *  Calculate the default score for the specified document if it
   *  does not match the query operator.  This score is 0 for many
   *  retrieval models, but not all retrieval models.
   *  @param r A retrieval model that controls how the operator behaves.
   *  @param docid The internal id of the document that needs a default score.
   *  @return The default score.
   */
  public double getDefaultScore (RetrievalModel r, long docid) throws IOException {

    if (r instanceof RetrievalModelUnrankedBoolean || r instanceof RetrievalModelRankedBoolean)
      return (0.0);
    if (r instanceof RetrievalModelIndri) {
      double sum = 0.0;
      
      for (int i = 0; i < this.args.size(); i ++) {
  		if (this.args.get(i) instanceof QryopSlScore) {
  		  sum += ((QryopSlScore)(this.args.get(i))).getDefaultScore(
  				  r, (int)docid) * this.weights.get(i);
  		}
  		else if (this.args.get(i) instanceof QryopSlAnd) {
		  sum += ((QryopSlAnd)(this.args.get(i))).getDefaultScore(
				  r, (int)docid) * this.weights.get(i);
		}
  		else if (this.args.get(i) instanceof QryopSlOr) {
		  sum += ((QryopSlOr)(this.args.get(i))).getDefaultScore(
				  r, (int)docid) * this.weights.get(i);
		}
  		else if (this.args.get(i) instanceof QryopSlWSum) {
  		  sum += ((QryopSlWSum)(this.args.get(i))).getDefaultScore(
  					  r, (int)docid) * this.weights.get(i);
  		}
  		else if (this.args.get(i) instanceof QryopSlWAnd) {
  		  sum += ((QryopSlWAnd)(this.args.get(i))).getDefaultScore(
  					  r, (int)docid) * this.weights.get(i);
  		}
  		else {
  		  System.out.println("Error: default score not implemented in this operator");
  		  break;
  		}
      }
      return sum;
    }

    return 0.0;
  }
  
  
  /**
   *  Return the smallest unexamined docid from the DaaTPtrs.
   *  @return The smallest internal document id.
   */
  public int getSmallestCurrentDocid () {

    int nextDocid = Integer.MAX_VALUE;

    for (int i=0; i<this.daatPtrs.size(); i++) {
      DaaTPtr ptri = this.daatPtrs.get(i);
      if (!ptri.scoreList.scores.isEmpty() && 
    		  ptri.nextDoc < Integer.MAX_VALUE && nextDocid > ptri.scoreList.getDocid (ptri.nextDoc))
	    nextDocid = ptri.scoreList.getDocid (ptri.nextDoc);
      }

    return (nextDocid);
  }
  
  

  /*
   *  Return a string version of this query operator.  
   *  @return The string version of this query operator.
   */
  public String toString(){
    
    String result = new String ();

    for (int i=0; i<this.args.size(); i++) {
      result += this.weights.get(i).toString() + " ";
      result += this.args.get(i).toString() + " ";
    }

    return ("#WSUM( " + result + ")");
  }
  
  /*  Return the min value.
   * 
   * 
   */
  public double min(List<Double> l) {
    double res;
    res = Integer.MAX_VALUE;
    for (int i = 0; i < l.size(); i ++) {
      if (l.get(i) < res) {
        res = l.get(i);
      }
    }
    return res;
  }
  
}
