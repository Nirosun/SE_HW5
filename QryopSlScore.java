/**
 *  This class implements the SCORE operator for all retrieval models.
 *  The single argument to a score operator is a query operator that
 *  produces an inverted list.  The SCORE operator uses this
 *  information to produce a score list that contains document ids and
 *  scores.
 *
 *  Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.*;

public class QryopSlScore extends QryopSl {
  
  public int ctf;		// Indri needs this
  public String field;	// Indri needs this
  private static DocLengthStore dls;
  
  static {
    try {
      //x = new MyFileWriter("foo.txt"); 
      dls= new DocLengthStore(QryEval.READER);
    } catch (Exception e) {
      //logging_and _stuff_you_might_want_to_terminate_the_app_here_blah();
    	System.err.println("Error: Can't set up DocLengthStore in QryopScore.");
        System.exit(1);
    } 
  }

  /**
   *  Construct a new SCORE operator.  The SCORE operator accepts just
   *  one argument.
   *  @param q The query operator argument.
   *  @return @link{QryopSlScore}
   */
  public QryopSlScore(Qryop q) {
    this.args.add(q);
  }

  /**
   *  Construct a new SCORE operator.  Allow a SCORE operator to be
   *  created with no arguments.  This simplifies the design of some
   *  query parsing architectures.
   *  @return @link{QryopSlScore}
   */
  public QryopSlScore() {
  }

  /**
   *  Appends an argument to the list of query operator arguments.  This
   *  simplifies the design of some query parsing architectures.
   *  @param q The query argument to append.
   */
  public void add (Qryop a) {
    this.args.add(a);
  }

  /**
   *  Evaluate the query operator.
   *  @param r A retrieval model that controls how the operator behaves.
   *  @return The result of evaluating the query.
   *  @throws IOException
   */
  public QryResult evaluate(RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelUnrankedBoolean || r instanceof RetrievalModelRankedBoolean)
      return (evaluateBoolean (r));
    if (r instanceof RetrievalModelBM25)
      return (evaluateBM25 ((RetrievalModelBM25)r));
    if (r instanceof RetrievalModelIndri)
        return (evaluateIndri ((RetrievalModelIndri)r));

    return null;
  }

 /**
   *  Evaluate the query operator for boolean retrieval models.
   *  @param r A retrieval model that controls how the operator behaves.
   *  @return The result of evaluating the query.
   *  @throws IOException
   */
  public QryResult evaluateBoolean(RetrievalModel r) throws IOException {

    // Evaluate the query argument.

    QryResult result = args.get(0).evaluate(r);

    // Each pass of the loop computes a score for one document. Note:
    // If the evaluate operation above returned a score list (which is
    // very possible), this loop gets skipped.

    for (int i = 0; i < result.invertedList.df; i++) {

      // DIFFERENT RETRIEVAL MODELS IMPLEMENT THIS DIFFERENTLY. 
      // Unranked Boolean. All matching documents get a score of 1.0.

      if (r instanceof RetrievalModelUnrankedBoolean) {
    	result.docScores.add(result.invertedList.postings.get(i).docid,
			   (float) 1.0);
      }      
      // Ranked Boolean. Matching documents get a score equal to tf.
      else {
        result.docScores.add(result.invertedList.postings.get(i).docid,
			   (float) result.invertedList.postings.get(i).tf);
      }
      //System.out.println((float) result.invertedList.postings.get(i).tf);
    }

    // The SCORE operator should not return a populated inverted list.
    // If there is one, replace it with an empty inverted list.

    if (result.invertedList.df > 0)
	  result.invertedList = new InvList();

    return result;
  }
  

  /**
   *  Evaluate the query operator for BM25 retrieval model.
   *  @param r A retrieval model that controls how the operator behaves.
   *  @return The result of evaluating the query.
   *  @throws IOException
   */
  public QryResult evaluateBM25(RetrievalModelBM25 r) throws IOException {

    // Evaluate the query argument.
    QryResult result = args.get(0).evaluate(r);
    
    int N = QryEval.READER.getDocCount(result.invertedList.field);
    //int N = QryEval.READER.numDocs();
    double avg_doclen = QryEval.READER.getSumTotalTermFreq(result.invertedList.field) / (double)N;
    //DocLengthStore dls = new DocLengthStore(QryEval.READER);
    int df = result.invertedList.df;
    double idf = Math.log((N - df + 0.5) / (df + 0.5));
    

    // Each pass of the loop computes a score for one document. Note:
    // If the evaluate operation above returned a score list (which is
    // very possible), this loop gets skipped.

    for (int i = 0; i < result.invertedList.df; i++) {

      // DIFFERENT RETRIEVAL MODELS IMPLEMENT THIS DIFFERENTLY. 
      long doclen = dls.getDocLength(result.invertedList.field, 
    		  result.invertedList.postings.get(i).docid);
      int tf = result.invertedList.postings.get(i).tf;
      double tfWeight = tf / 
    		  (tf + r.k_1 * (1 - r.b + r.b * doclen / avg_doclen));
      
      result.docScores.add(result.invertedList.postings.get(i).docid, idf * tfWeight);
    }

    // The SCORE operator should not return a populated inverted list.
    // If there is one, replace it with an empty inverted list.
    if (result.invertedList.df > 0)
	  result.invertedList = new InvList();

    return result;
  }  
  

  /**
   *  Evaluate the query operator for BM25 retrieval model.
   *  @param r A retrieval model that controls how the operator behaves.
   *  @return The result of evaluating the query.
   *  @throws IOException
   */
  public QryResult evaluateIndri(RetrievalModelIndri r) throws IOException {

    // Evaluate the query argument.
    QryResult result = args.get(0).evaluate(r);
    
    long lengthC = QryEval.READER.getSumTotalTermFreq(result.invertedList.field);
    int ctf = result.invertedList.ctf;
    double p_qi_C = ctf / (double) lengthC;
    //DocLengthStore dls = new DocLengthStore(QryEval.READER);
    
    this.ctf = result.invertedList.ctf;
    this.field = result.invertedList.field;

    // Each pass of the loop computes a score for one document. Note:
    // If the evaluate operation above returned a score list (which is
    // very possible), this loop gets skipped.

    for (int i = 0; i < result.invertedList.df; i++) {

      // DIFFERENT RETRIEVAL MODELS IMPLEMENT THIS DIFFERENTLY. 
      long length_d = dls.getDocLength(result.invertedList.field, 
    		  result.invertedList.postings.get(i).docid);
      int tf = result.invertedList.postings.get(i).tf;
      double p_qi_d = (tf + r.mu * p_qi_C) / (double)(length_d + r.mu);
      double p_lambda_qi_d = r.lambda * p_qi_d + (1 - r.lambda) * p_qi_C;
      
      result.docScores.add(result.invertedList.postings.get(i).docid, p_lambda_qi_d);
    }

    // The SCORE operator should not return a populated inverted list.
    // If there is one, replace it with an empty inverted list.
    if (result.invertedList.df > 0)
	  result.invertedList = new InvList();

    return result;
  }  
  

  /*
   *  Calculate the default score for a document that does not match
   *  the query argument.  This score is 0 for many retrieval models,
   *  but not all retrieval models.
   *  @param r A retrieval model that controls how the operator behaves.
   *  @param docid The internal id of the document that needs a default score.
   *  @return The default score.
   */
  public double getDefaultScore (RetrievalModel r, long docid) throws IOException {

    if (r instanceof RetrievalModelUnrankedBoolean || r instanceof RetrievalModelRankedBoolean
    		|| r instanceof RetrievalModelBM25)
      return (0.0);
    
    if (r instanceof RetrievalModelIndri) {
      long lengthC = QryEval.READER.getSumTotalTermFreq(this.field);
      double p_qi_C = this.ctf / (double) lengthC;
    	
      //DocLengthStore dls = new DocLengthStore(QryEval.READER);
      long length_d = dls.getDocLength(this.field, (int)docid);
      int tf = 0;
      double p_qi_d = (tf + ((RetrievalModelIndri)r).mu * p_qi_C) /
    		  (double)(length_d + ((RetrievalModelIndri)r).mu);
      double p_lambda_qi_d = ((RetrievalModelIndri)r).lambda * p_qi_d +
    		  (1 - ((RetrievalModelIndri)r).lambda) * p_qi_C;
      return p_lambda_qi_d;      
    }

    return 0.0;
  }

  /**
   *  Return a string version of this query operator.  
   *  @return The string version of this query operator.
   */
  public String toString(){
    
    String result = new String ();

    for (Iterator<Qryop> i = this.args.iterator(); i.hasNext(); )
      result += (i.next().toString() + " ");

    return ("#SCORE( " + result + ")");
  }
}
