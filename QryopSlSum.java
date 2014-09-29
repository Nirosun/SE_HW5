/**
 *  This class implements the SUM operator for BM25.
 *
 *  Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.*;

//import Qryop.DaaTPtr;

public class QryopSlSum extends QryopSl {

  /**
   *  It is convenient for the constructor to accept a variable number
   *  of arguments. Thus new qryopAnd (arg1, arg2, arg3, ...).
   *  @param q A query argument (a query operator).
   */
  public QryopSlSum(Qryop... q) {
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

    if (r instanceof RetrievalModelBM25)
      return (evaluateBM25 ((RetrievalModelBM25)r));

    return null;
  }

  
  /**
   *  Evaluates the query operator for BM25 retrieval model,
   *  including any child operators and returns the result.
   *  @param r A retrieval model that controls how the operator behaves.
   *  @return The result of evaluating the query.
   *  @throws IOException
   */
  public QryResult evaluateBM25 (RetrievalModelBM25 r) throws IOException {
    //  Initialization

    allocDaaTPtrs (r);
    int qtf = 1;
    //syntaxCheckArgResults (this.daatPtrs);
    //int numPtrs = this.daatPtrs.size();
    //List<Integer> ptrsIDs = new ArrayList<Integer>();
    /*for (int i = 0; i < this.daatPtrs.size(); i ++) {
      ptrsIDs.add(i);
    }*/
    int ptrsCount = this.daatPtrs.size();

    QryResult result = new QryResult ();

    //  Each pass of the loop adds 1 document to result until all of
    //  the score lists are depleted.  When a list is depleted, it
    //  is removed from daatPtrs, so this loop runs until daatPtrs is empty.

    //  This implementation is intended to be clear.  A more efficient
    //  implementation would combine loops and use merge-sort.

    //System.out.println("Before while");
    
    while (ptrsCount > 0) {

      int nextDocid = getSmallestCurrentDocid ();
      double docScore = 0.0;
      //List<Double> ptrsScores = new ArrayList<Double>();  // scores of the ptri's with nextDocid 

      for (int i=0; i<this.daatPtrs.size(); i++) {
		DaaTPtr ptri = this.daatPtrs.get(i);
		//int ptrID = ptrsIDs.get(i);
		
		if (!ptri.scoreList.scores.isEmpty()) {
		  
		  if (ptri.nextDoc != Integer.MAX_VALUE && ptri.scoreList.getDocid (ptri.nextDoc) == nextDocid) {
		    docScore += ptri.scoreList.getDocidScore(ptri.nextDoc) 
		    		* (r.k_3 + 1) * qtf / (double)(r.k_3 + qtf);
		    		//docScore += ptrsScores.get(i) * (r.k_3 + 1) * qtf / (double)(r.k_3 + qtf);
			ptri.nextDoc ++;
		  }
		}
      }      
      
      // add score to result 
      if (docScore != 0) {
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
  
}
