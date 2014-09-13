/**
 *  This class implements the AND operator for all retrieval models.
 *
 *  Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.*;

//import Qryop.DaaTPtr;

public class QryopSlOr extends QryopSl {

  /**
   *  It is convenient for the constructor to accept a variable number
   *  of arguments. Thus new qryopAnd (arg1, arg2, arg3, ...).
   *  @param q A query argument (a query operator).
   */
  public QryopSlOr(Qryop... q) {
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

    return null;
  }
  
  
  /**
   *  Evaluates the query operator for boolean retrieval models,
   *  including any child operators and returns the result.
   *  @param r A retrieval model that controls how the operator behaves.
   *  @return The result of evaluating the query.
   *  @throws IOException
   */
  public QryResult evaluateBoolean(RetrievalModel r) throws IOException {

    //  Initialization

    allocDaaTPtrs (r);
    //syntaxCheckArgResults (this.daatPtrs);

    QryResult result = new QryResult ();
    //result.invertedList.field = new String (this.daatPtrs.get(0).invList.field);

    //  Each pass of the loop adds 1 document to result until all of
    //  the inverted lists are depleted.  When a list is depleted, it
    //  is removed from daatPtrs, so this loop runs until daatPtrs is empty.

    //  This implementation is intended to be clear.  A more efficient
    //  implementation would combine loops and use merge-sort.

    while (this.daatPtrs.size() > 0) {

      int nextDocid = getSmallestCurrentDocid ();
      double docScore = 1.0;
      List<Double> ptrsScores = new ArrayList<Double>();

      //  Create a new posting that is the union of the posting lists
      //  that match the nextDocid.

      //List<Integer> positions = new ArrayList<Integer>();

      for (int i=0; i<this.daatPtrs.size(); i++) {
		DaaTPtr ptri = this.daatPtrs.get(i);
	
		if (!ptri.scoreList.scores.isEmpty() && ptri.scoreList.getDocid (ptri.nextDoc) == nextDocid) {
		  //positions.addAll (ptri.invList.postings.get(ptri.nextDoc).positions);
		  ptrsScores.add(ptri.scoreList.getDocidScore (ptri.nextDoc));
		  ptri.nextDoc ++;
		}
      }
      
      if (ptrsScores.size() != 0) {
    	if (r instanceof RetrievalModelRankedBoolean) {
    	  docScore = max(ptrsScores);
    	}
    	result.docScores.add (nextDocid, docScore);
      }

      //  If a DaatPtr has reached the end of its list, remove it.
      //  The loop is backwards so that removing an arg does not
      //  interfere with iteration.

      for (int i=this.daatPtrs.size()-1; i>=0; i--) {
		DaaTPtr ptri = this.daatPtrs.get(i);
	
		if (ptri.nextDoc >= ptri.scoreList.scores.size()) {
		  this.daatPtrs.remove (i);
		}
      }
    }

    freeDaaTPtrs();

    return result;
  }

  /**
   *  Return the smallest unexamined docid from the DaaTPtrs.
   *  @return The smallest internal document id.
   */
  public int getSmallestCurrentDocid () {

    int nextDocid = Integer.MAX_VALUE;

    for (int i=0; i<this.daatPtrs.size(); i++) {
      DaaTPtr ptri = this.daatPtrs.get(i);
      if (!ptri.scoreList.scores.isEmpty() && nextDocid > ptri.scoreList.getDocid (ptri.nextDoc))
	    nextDocid = ptri.scoreList.getDocid (ptri.nextDoc);
      }

    return (nextDocid);
  }

  /**
   *  syntaxCheckArgResults does syntax checking that can only be done
   *  after query arguments are evaluated.
   *  @param ptrs A list of DaaTPtrs for this query operator.
   *  @return True if the syntax is valid, false otherwise.
   */
  public Boolean syntaxCheckArgResults (List<DaaTPtr> ptrs) {

    for (int i=0; i<this.args.size(); i++) {

      if (! (this.args.get(i) instanceof QryopIl)) 
	QryEval.fatalError ("Error:  Invalid argument in " +
			    this.toString());
      else
	if ((i>0) &&
	    (! ptrs.get(i).invList.field.equals (ptrs.get(0).invList.field)))
	  QryEval.fatalError ("Error:  Arguments must be in the same field:  " +
			      this.toString());
    }

    return true;
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

  /*
   *  Return a string version of this query operator.  
   *  @return The string version of this query operator.
   */
  public String toString(){
    
    String result = new String ();

    for (Iterator<Qryop> i = this.args.iterator(); i.hasNext(); )
      result += (i.next().toString() + " ");

    return ("#OR( " + result + ")");
  }
  
  /*  Return the max value.
   * 
   * 
   */
  public double max(List<Double> l) {
    double res;
    res = -1;
    for (int i = 0; i < l.size(); i ++) {
      if (l.get(i) > res) {
        res = l.get(i);
      }
    }
    return res;
  }
  
}
