/**
 *  This class implements the SYN operator for all retrieval models.
 *  The synonym operator creates a new inverted list that is the union
 *  of its constituents.  Typically it is used for morphological or
 *  conceptual variants, e.g., #SYN (cat cats) or #SYN (cat kitty) or
 *  #SYN (astronaut cosmonaut).
 *
 *  Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.*;


public class QryopIlNear extends QryopIl {

  private int distance;
  /**
   *  It is convenient for the constructor to accept a variable number
   *  of arguments. Thus new QryopIlSyn (arg1, arg2, arg3, ...).
   */
  
  public QryopIlNear(int n, Qryop... q) {
    for (int i = 0; i < q.length; i++)
      this.args.add(q[i]);
    this.distance = n;
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

    //  Initialization

    allocDaaTPtrs (r);
    QryResult result = new QryResult ();
    result.invertedList.field = new String (this.daatPtrs.get(0).invList.field);

    //  Exact-match NEAR/n requires that ALL invLists contain a
    //  document id.  Use the first (shortest) list to control the
    //  search for matches.

    //  Named loops are a little ugly.  However, they make it easy
    //  to terminate an outer loop from within an inner loop.
    //  Otherwise it is necessary to use flags, which is also ugly.

    DaaTPtr ptr0 = this.daatPtrs.get(0);

    EVALUATEDOCUMENTS:
    for ( ; ptr0.nextDoc < ptr0.invList.postings.size(); ptr0.nextDoc ++) {

      int ptr0Docid = ptr0.invList.getDocid (ptr0.nextDoc);
      //List<Double> ptrsScores = new ArrayList<Double>();
      //ptrsScores.add(ptr0.scoreList.getDocidScore(ptr0.nextDoc));

      //  Do the other query arguments have the ptr0Docid?

      for (int j=1; j<this.daatPtrs.size(); j++) {

		DaaTPtr ptrj = this.daatPtrs.get(j);
	
		while (true) {
		  if (ptrj.nextDoc >= ptrj.invList.postings.size())
		    break EVALUATEDOCUMENTS;		// No more docs can match
		  else
		    if (ptrj.invList.getDocid (ptrj.nextDoc) > ptr0Docid)
		      continue EVALUATEDOCUMENTS;	// The ptr0docid can't match.
		  else
		    if (ptrj.invList.getDocid (ptrj.nextDoc) < ptr0Docid)
		      ptrj.nextDoc ++;			// Not yet at the right doc.
		  else {
			  break;				// ptrj matches ptr0Docid
		  }
		}
      }
      
      
      //  Dig into the specific document.
      List<Integer> positions = new ArrayList<Integer>();
      List<Integer> ptrs = new ArrayList<Integer>();
      List<Integer> locs = new ArrayList<Integer>();
      List<Integer> posSizes = new ArrayList<Integer>();
      //List<DaatPtr> daats = new ArrayList<DaatPtr>();
      int daatPtrsSize = this.daatPtrs.size();
      
      //System.out.println("DocID: " + ptr0Docid);
      //System.out.println("daatPtrsSize: " + daatPtrsSize);
      
      for (int i = 0; i < daatPtrsSize; i ++)
      {
        ptrs.add(0);
        int nextD = this.daatPtrs.get(i).nextDoc;
        locs.add(this.daatPtrs.get(i).invList.postings.get(nextD).positions.get(0));
        posSizes.add(this.daatPtrs.get(i).invList.postings.get(nextD).positions.size());
      }
      
      EVALUATELOCS:
      while (true)
      {
    	for (int i = 0; i < daatPtrsSize; i ++)
    	{
          if (ptrs.get(i) >= posSizes.get(i))
          {
        	break EVALUATELOCS;
          }
          int nextD = this.daatPtrs.get(i).nextDoc;
          locs.set(i, this.daatPtrs.get(i).invList.postings.get(nextD).positions.get(ptrs.get(i)));
    	}
    	EVALUATECOMBO:
    	for (int i = 0; i < daatPtrsSize - 1; i ++)
    	{
          if (locs.get(i + 1) - locs.get(i) > this.distance 
        		  || locs.get(i + 1) - locs.get(i) <= 0)
          {
        	int minLoc = min(locs);
        	for (int j = 0; j < daatPtrsSize; j ++)
        	{        	  
        	  if (locs.get(j) == minLoc)
        	  {
        		ptrs.set(j, ptrs.get(j) + 1);
        		//locs.set(j, this.daatPtrs.get(j).invList.postings.get(ptr0Docid).positions.get(ptrs.get(j)));
        		break EVALUATECOMBO;
        	  }
        	}
          }
          if (i == daatPtrsSize - 2)
          {
        	for (int j = 0; j < daatPtrsSize; j ++)
        	{
        	  ptrs.set(j, ptrs.get(j) + 1);        	  
        	  //System.out.println(locs.get());
        	}
        	positions.add(locs.get(0));
          }
    	}  	
      }
    	  
      if (!positions.isEmpty()) {
        result.invertedList.appendPosting (ptr0Docid, positions);
        //System.out.println("Find: " + ptr0Docid);        
      }
    }

    freeDaaTPtrs ();

    return result;
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
   *  Return a string version of this query operator.  
   *  @return The string version of this query operator.
   */
  public String toString(){
    
    String result = new String ();

    for (Iterator<Qryop> i = this.args.iterator(); i.hasNext(); )
      result += (i.next().toString() + " ");

    return ("#NEAR\\" + distance + "( " + result + ")");
  }
  
  /**
   * Find min.
   * @param l
   * @return
   */
  public int min(List<Integer> l) {
	int tmp = 10000;
	for (int i = 0; i < l.size(); i ++) {
      if (l.get(i) < tmp) tmp = l.get(i);
	}
	return tmp;
  }
}
