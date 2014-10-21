/**
 *  This class implements the AND operator for all retrieval models.
 *
 *  Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.*;

//import Qryop.DaaTPtr;

public class QryopSlAnd extends QryopSl {

  /**
   *  It is convenient for the constructor to accept a variable number
   *  of arguments. Thus new qryopAnd (arg1, arg2, arg3, ...).
   *  @param q A query argument (a query operator).
   */
  public QryopSlAnd(Qryop... q) {
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

    if (r instanceof RetrievalModelUnrankedBoolean || r instanceof RetrievalModelRankedBoolean)
      return (evaluateBoolean (r));
    if (r instanceof RetrievalModelIndri) {
      return (evaluateIndri ((RetrievalModelIndri)r));
    }

    return null;
  }

  /**
   *  Evaluates the query operator for boolean retrieval models,
   *  including any child operators and returns the result.
   *  @param r A retrieval model that controls how the operator behaves.
   *  @return The result of evaluating the query.
   *  @throws IOException
   */
  public QryResult evaluateBoolean (RetrievalModel r) throws IOException {

    //  Initialization

    allocDaaTPtrs (r);
    QryResult result = new QryResult ();

    //this.daatPtrs.get(0).invList.print();
    
    
    //  Sort the arguments so that the shortest lists are first.  This
    //  improves the efficiency of exact-match AND without changing
    //  the result.

    for (int i=0; i<(this.daatPtrs.size()-1); i++) {
      for (int j=i+1; j<this.daatPtrs.size(); j++) {
	if (this.daatPtrs.get(i).scoreList.scores.size() >
	    this.daatPtrs.get(j).scoreList.scores.size()) {
	    ScoreList tmpScoreList = this.daatPtrs.get(i).scoreList;
	    this.daatPtrs.get(i).scoreList = this.daatPtrs.get(j).scoreList;
	    this.daatPtrs.get(j).scoreList = tmpScoreList;
	}
      }
    }

    //  Exact-match AND requires that ALL scoreLists contain a
    //  document id.  Use the first (shortest) list to control the
    //  search for matches.

    //  Named loops are a little ugly.  However, they make it easy
    //  to terminate an outer loop from within an inner loop.
    //  Otherwise it is necessary to use flags, which is also ugly.

    DaaTPtr ptr0 = this.daatPtrs.get(0);

    EVALUATEDOCUMENTS:
    for ( ; ptr0.nextDoc < ptr0.scoreList.scores.size(); ptr0.nextDoc ++) {

      int ptr0Docid = ptr0.scoreList.getDocid (ptr0.nextDoc);
      double docScore = 1.0;
      List<Double> ptrsScores = new ArrayList<Double>();
      ptrsScores.add(ptr0.scoreList.getDocidScore(ptr0.nextDoc));

      //  Do the other query arguments have the ptr0Docid?

      for (int j=1; j<this.daatPtrs.size(); j++) {

		DaaTPtr ptrj = this.daatPtrs.get(j);
	
		while (true) {
		  if (ptrj.nextDoc >= ptrj.scoreList.scores.size())
		    break EVALUATEDOCUMENTS;		// No more docs can match
		  else
		    if (ptrj.scoreList.getDocid (ptrj.nextDoc) > ptr0Docid)
		      continue EVALUATEDOCUMENTS;	// The ptr0docid can't match.
		  else
		    if (ptrj.scoreList.getDocid (ptrj.nextDoc) < ptr0Docid)
		      ptrj.nextDoc ++;			// Not yet at the right doc.
		  else {
			  ptrsScores.add(ptrj.scoreList.getDocidScore(ptrj.nextDoc));
			  break;				// ptrj matches ptr0Docid
		  }
		}
      }

      //  The ptr0Docid matched all query arguments, so save it.
      if (r instanceof RetrievalModelRankedBoolean) {
    	/*for (int i = 0; i < ptrsScores.size(); i ++) {
    		System.out.println(ptrsScores.get(i));
    	}
	    System.out.println("size: " + ptrsScores.size());*/
    	docScore = min(ptrsScores);
      }
      //System.out.println("DocID: " + ptr0Docid);
      
      /*if (ptr0Docid == 250747) {
    	System.out.println("Oh!");
      }*/
      
      result.docScores.add (ptr0Docid, docScore);
    }

    freeDaaTPtrs ();

    return result;
  }
  
  
  /**
   *  Evaluates the query operator for Indri retrieval model,
   *  including any child operators and returns the result.
   *  @param r A retrieval model that controls how the operator behaves.
   *  @return The result of evaluating the query.
   *  @throws IOException
   */
  public QryResult evaluateIndri(RetrievalModelIndri r) throws IOException {

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
    }

    
    int ptrsCount = this.daatPtrs.size();	// count the number of active ptrs 

    QryResult result = new QryResult ();

    //  Each pass of the loop adds 1 document to result until all of
    //  the score lists are depleted.  When a list is depleted, it
    //  is removed from daatPtrs, so this loop runs until daatPtrs is empty.

    //  This implementation is intended to be clear.  A more efficient
    //  implementation would combine loops and use merge-sort.

    //System.out.println("Before while");
    
    while (ptrsCount > 0) {

      int nextDocid = getSmallestCurrentDocid ();
      double docScore = 1.0;
      List<Double> ptrsScores = new ArrayList<Double>();  // scores of the ptri's with nextDocid 

      for (int i=0; i<this.daatPtrs.size(); i++) {
		DaaTPtr ptri = this.daatPtrs.get(i);
		//int ptrID = ptrsIDs.get(i);
		
		if (!ptri.scoreList.scores.isEmpty()) {
		  
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
    	  docScore *= Math.pow(ptrsScores.get(i), 1/(double)this.daatPtrs.size());
    	}
    	result.docScores.add (nextDocid, docScore);
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
      double product = 1.0;
      
      for (int i = 0; i < this.args.size(); i ++) {
		if (this.args.get(i) instanceof QryopSlScore) {
		  product *= Math.pow(((QryopSlScore)(this.args.get(i))).getDefaultScore(
				  r, (int)docid), 1/(double)this.args.size());
		}
  		else if (this.args.get(i) instanceof QryopSlAnd) {
		  product *= Math.pow(((QryopSlAnd)(this.args.get(i))).getDefaultScore(
				  r, (int)docid), 1/(double)this.args.size());
		}
  		else if (this.args.get(i) instanceof QryopSlOr) {
		  product *= Math.pow(((QryopSlOr)(this.args.get(i))).getDefaultScore(
				  r, (int)docid), 1/(double)this.args.size());
		}
		else if (this.args.get(i) instanceof QryopSlWSum) {
		  product *= Math.pow(((QryopSlWSum)(this.args.get(i))).getDefaultScore(
					  r, (int)docid), 1/(double)this.args.size());
		}
		else if (this.args.get(i) instanceof QryopSlWAnd) {
		  product *= Math.pow(((QryopSlWAnd)(this.args.get(i))).getDefaultScore(
					  r, (int)docid), 1/(double)this.args.size());
		}
		else {
		  System.out.println("Error: default score not implemented in this operator");
		  break;
		}
      }
      return product;
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

    for (int i=0; i<this.args.size(); i++)
      result += this.args.get(i).toString() + " ";

    return ("#AND( " + result + ")");
  }
  
  /*  Return the max value.
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
