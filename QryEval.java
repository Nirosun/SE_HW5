/**
 *  QryEval illustrates the architecture for the portion of a search
 *  engine that evaluates queries.  It is a template for class
 *  homework assignments, so it emphasizes simplicity over efficiency.
 *  It implements an unranked Boolean retrieval model, however it is
 *  easily extended to other retrieval models.  For more information,
 *  see the ReadMe.txt file.
 *
 *  Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.util.*;

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

public class QryEval {

  static String usage = "Usage:  java " + System.getProperty("sun.java.command")
      + " paramFile\n\n";

  //  The index file reader is accessible via a global variable. This
  //  isn't great programming style, but the alternative is for every
  //  query operator to store or pass this value, which creates its
  //  own headaches.

  public static IndexReader READER;

  //  Create and configure an English analyzer that will be used for
  //  query parsing.

  public static EnglishAnalyzerConfigurable analyzer =
      new EnglishAnalyzerConfigurable (Version.LUCENE_43);
  static {
    analyzer.setLowercase(true);
    analyzer.setStopwordRemoval(true);
    analyzer.setStemmer(EnglishAnalyzerConfigurable.StemmerType.KSTEM);
  }

  /**
   *  @param args The only argument is the path to the parameter file.
   *  @throws Exception
   */
  public static void main(String[] args) throws Exception {
    
    // set the beginning timer
	//long beginTime = System.currentTimeMillis();
	  
	// must supply parameter file
    if (args.length < 1) {
      System.err.println(usage);
      System.exit(1);
    }

    // read in the parameter file; one parameter per line in format of key=value
    Map<String, String> params = new HashMap<String, String>();
    Scanner scan = new Scanner(new File(args[0]));
    String line = null;
    do {
      line = scan.nextLine();
      String[] pair = line.split("=");
      params.put(pair[0].trim(), pair[1].trim());
    } while (scan.hasNext());
    scan.close();
    
    // parameters required for this example to run
    if (!params.containsKey("indexPath") || !params.containsKey("queryFilePath")
    		|| !params.containsKey("retrievalAlgorithm") 
    				|| !params.containsKey("trecEvalOutputPath")) {
      System.err.println("Error: Parameters were missing.");
      System.exit(1);
    }

    // open the index
    READER = DirectoryReader.open(FSDirectory.open(new File(params.get("indexPath"))));

    if (READER == null) {
      System.err.println(usage);
      System.exit(1);
    }

    DocLengthStore s = new DocLengthStore(READER);

    RetrievalModel model = null;
    if (params.get("retrievalAlgorithm").equals("UnrankedBoolean")) {
      model = new RetrievalModelUnrankedBoolean();
    }
    else if (params.get("retrievalAlgorithm").equals("RankedBoolean")) {
      model = new RetrievalModelRankedBoolean();
    }
    else if (params.get("retrievalAlgorithm").equals("BM25")) {
      model = new RetrievalModelBM25();
      if (!params.containsKey("BM25:k_1") || !params.containsKey("BM25:b") ||
    		  !params.containsKey("BM25:k_3")) {
        System.err.println("Error: Parameters were missing.");
        System.exit(1);
      }
      model.setParameter("k_1", params.get("BM25:k_1"));
      model.setParameter("b", params.get("BM25:b"));
      model.setParameter("k_3", params.get("BM25:k_3"));
    }
    else if (params.get("retrievalAlgorithm").equals("Indri")) {
      model = new RetrievalModelIndri();
      if (!params.containsKey("Indri:mu") || !params.containsKey("Indri:lambda")) {
        System.err.println("Error: Parameters were missing.");
        System.exit(1);
      }
      model.setParameter("mu", params.get("Indri:mu"));
      model.setParameter("lambda", params.get("Indri:lambda"));
    }
    else {
      System.err.println("Error: Retrieval algorithm not implemented.");
      System.exit(1);
    }

    /*
     *  The code below is an unorganized set of examples that show
     *  you different ways of accessing the index.  Some of these
     *  are only useful in HW2 or HW3.
     */

    // Lookup the document length of the body field of doc 0.
    //System.out.println(s.getDocLength("body", 0));

    // How to use the term vector.
    //TermVector tv = new TermVector(1, "body");
    //System.out.println(tv.stemString(10)); // get the string for the 10th stem
    //System.out.println(tv.stemDf(10)); // get its df
    //System.out.println(tv.totalStemFreq(10)); // get its ctf
    
    /**
     *  The index is open. Start evaluating queries. The examples
     *  below show query trees for two simple queries.  These are
     *  meant to illustrate how query nodes are created and connected.
     *  However your software will not create queries like this.  Your
     *  software will use a query parser.  See parseQuery.
     *
     *  The general pattern is to tokenize the  query term (so that it
     *  gets converted to lowercase, stopped, stemmed, etc), create a
     *  Term node to fetch the inverted list, create a Score node to
     *  convert an inverted list to a score list, evaluate the query,
     *  and print results.
     * 
     *  Modify the software so that you read a query from a file,
     *  parse it, and form the query tree automatically.
     */
    
    //System.out.println(getInternalDocid("clueweb09-en0000-99-20299"));
    
    Qryop qTree;
    String[] query = new String[2];
    String tmp = null;
    int nDoc = 100;
    File queryFile = new File(params.get("queryFilePath"));
    BufferedReader br = new BufferedReader(new FileReader(queryFile));
    BufferedWriter bw = null;
    bw = new BufferedWriter(new FileWriter(new File(params.get("trecEvalOutputPath"))));
    
    while((tmp = br.readLine()) != null) {
      query = tmp.split(":");
      qTree = parseQuery (query[1], model);
      System.out.println(query[0] + " " + query[1]);
      QryResult result = qTree.evaluate (model);
      //printResults (query[1], result);
      //System.out.println("Before sort");
      outputResults(bw, query[0], result, nDoc, model);
    }
    br.close();
    bw.close();
    

    // Later HW assignments will use more RAM, so you want to be aware
    // of how much memory your program uses.

    printMemoryUsage(false);
    
    //long endTime = System.currentTimeMillis();
    //long timeUsed = (endTime - beginTime) / 100;
    //System.out.println("Time used: " + timeUsed + "s");

  }

  /**
   *  Write an error message and exit.  This can be done in other
   *  ways, but I wanted something that takes just one statement so
   *  that it is easy to insert checks without cluttering the code.
   *  @param message The error message to write before exiting.
   *  @return void
   */
  static void fatalError (String message) {
    System.err.println (message);
    System.exit(1);
  }

  /**
   *  Get the external document id for a document specified by an
   *  internal document id. If the internal id doesn't exists, returns null.
   *  
   * @param iid The internal document id of the document.
   * @throws IOException 
   */
  static String getExternalDocid (int iid) throws IOException {
    Document d = QryEval.READER.document (iid);
    String eid = d.get ("externalId");
    return eid;
  }

  /**
   *  Finds the internal document id for a document specified by its
   *  external id, e.g. clueweb09-enwp00-88-09710.  If no such
   *  document exists, it throws an exception. 
   * 
   * @param externalId The external document id of a document.s
   * @return An internal doc id suitable for finding document vectors etc.
   * @throws Exception
   */
  static int getInternalDocid (String externalId) throws Exception {
    Query q = new TermQuery(new Term("externalId", externalId));
    
    IndexSearcher searcher = new IndexSearcher(QryEval.READER);
    TopScoreDocCollector collector = TopScoreDocCollector.create(1,false);
    searcher.search(q, collector);
    ScoreDoc[] hits = collector.topDocs().scoreDocs;
    
    if (hits.length < 1) {
      throw new Exception("External id not found.");
    } else {
      return hits[0].doc;
    }
  }

  /**
   * parseQuery converts a query string into a query tree.
   * 
   * @param qString
   *          A string containing a query.
   * @param qTree
   *          A query tree
   * @throws IOException
   */
  static Qryop parseQuery(String qString, RetrievalModel r) throws IOException {

    Qryop currentOp = null;
    Stack<Qryop> stack = new Stack<Qryop>();

    // Add a default query operator to an unstructured query. This
    // is a tiny bit easier if unnecessary whitespace is removed.

    qString = qString.trim();


    if (qString.charAt(0) != '#' || !qString.endsWith(")") 
    		|| qString.startsWith("#NEAR/") || qString.startsWith("#near/")) {
      if (r instanceof RetrievalModelUnrankedBoolean || r instanceof RetrievalModelRankedBoolean) {
        qString = "#or(" + qString + ")";
      }
      else if (r instanceof RetrievalModelBM25) {
    	qString = "#sum(" + qString + ")";
      }
      else if (r instanceof RetrievalModelIndri) {
    	qString = "#and(" + qString + ")";
      }
    }
    
    //System.out.println(qString);

    // Tokenize the query.

    StringTokenizer tokens = new StringTokenizer(qString, "\t\n\r ,()", true);
    String token = null;

    // Each pass of the loop processes one token. To improve
    // efficiency and clarity, the query operator on the top of the
    // stack is also stored in currentOp.

    while (tokens.hasMoreTokens()) {

      token = tokens.nextToken();
      //System.out.println(token);

      if (token.matches("[ ,(\t\n\r]")) {
        // Ignore most delimiters.
      } else if (token.equalsIgnoreCase("#and")) {
        currentOp = new QryopSlAnd();
        stack.push(currentOp);
      } else if (token.equalsIgnoreCase("#or")) {
        currentOp = new QryopSlOr();
        stack.push(currentOp);
      } else if (token.equalsIgnoreCase("#sum")) {
        currentOp = new QryopSlSum();
        stack.push(currentOp);
      } else if (token.equalsIgnoreCase("#syn")) {
        //currentOp = new QryopSlScore();  //wrap by score operator
        //stack.push(currentOp);
    	currentOp = new QryopIlSyn();
        stack.push(currentOp);
      } else if (token.startsWith("#NEAR/") || token.startsWith("#near/")) {
    	int num = 0;
    	String[] strs = token.split("/");
        num = Integer.parseInt(strs[1]);
        //currentOp = new QryopSlScore();  //wrap by score operator
        //stack.push(currentOp);
    	currentOp = new QryopIlNear(num);
    	stack.push(currentOp);
    	
      }
    	else if (token.startsWith(")")) { // Finish current query operator.
        // If the current query operator is not an argument to
        // another query operator (i.e., the stack is empty when it
        // is removed), we're done (assuming correct syntax - see
        // below). Otherwise, add the current operator as an
        // argument to the higher-level operator, and shift
        // processing back to the higher-level operator.

        stack.pop();
        if (stack.empty())
          break;
        Qryop arg = currentOp;
        currentOp = stack.peek();
        currentOp.add(arg);
        
        /*if (currentOp instanceof QryopSlScore) {
          stack.pop();
          if (stack.empty())
        	  break;
          Qryop tmp = currentOp;
          currentOp = stack.peek();
          currentOp.add(tmp);
        }*/
        
      } else {

        // NOTE: You should do lexical processing of the token before
        // creating the query term, and you should check to see whether
        // the token specifies a particular field (e.g., apple.title).
    	if (tokenizeQuery(token).length != 0) {
          token = tokenizeQuery(token)[0];
          if (token.contains(".")) {    // get the field
        	String[] termStrs = token.split("\\.");
        	token = tokenizeQuery(termStrs[0])[0];
        	currentOp.add(new QryopIlTerm(token, termStrs[1]));
          } else {
            currentOp.add(new QryopIlTerm(token));
          }
    	}
      }
    }

    // A broken structured query can leave unprocessed tokens on the
    // stack, so check for that.

    if (tokens.hasMoreTokens()) {
      System.err.println("Error:  Query syntax is incorrect.  " + qString);
      return null;
    }

    return currentOp;
  }

  /**
   *  Print a message indicating the amount of memory used.  The
   *  caller can indicate whether garbage collection should be
   *  performed, which slows the program but reduces memory usage.
   *  @param gc If true, run the garbage collector before reporting.
   *  @return void
   */
  public static void printMemoryUsage (boolean gc) {

    Runtime runtime = Runtime.getRuntime();

    if (gc) {
      runtime.gc();
    }

    System.out.println ("Memory used:  " +
			((runtime.totalMemory() - runtime.freeMemory()) /
			 (1024L * 1024L)) + " MB");
  }
  
  /**
   * Print the query results. 
   * 
   * I don't use this function to output results into trec file. Instead I use 
   * outputResults to do this. So this function is just for debugging now. 
   * Hence, I do not change the output format, because I think the print results
   * for now are easier to understand.
   * 
   * @param queryName Original query.
   * @param result Result object generated by {@link Qryop#evaluate()}.
   * @throws IOException 
   */
  static void printResults(String queryName, QryResult result) throws IOException {

    System.out.println(queryName + ":  ");
    if (result.docScores.scores.size() < 1) {
      System.out.println("\tNo results.");
    } else {
      for (int i = 0; i < result.docScores.scores.size(); i++) {
        System.out.println("\t" + i + ":  "
			   + getExternalDocid (result.docScores.getDocid(i))
			   + ", "
			   + result.docScores.getDocidScore(i));
      }
    }
  }

  /**
   *  Given a query string, returns the terms one at a time with stopwords
   *  removed and the terms stemmed using the Krovetz stemmer. 
   * 
   *  Use this method to process raw query terms. 
   * 
   *  @param query String containing query
   *  @return Array of query tokens
   *  @throws IOException
   */
  static String[] tokenizeQuery(String query) throws IOException {

    TokenStreamComponents comp = analyzer.createComponents("dummy", new StringReader(query));
    TokenStream tokenStream = comp.getTokenStream();

    CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);
    tokenStream.reset();

    List<String> tokens = new ArrayList<String>();
    while (tokenStream.incrementToken()) {
      String term = charTermAttribute.toString();
      tokens.add(term);
    }
    return tokens.toArray(new String[tokens.size()]);
  }
  
  
  
  /**
   * Output the results into trec file in the format according to the homework writeup.
   * 
   * @param writer the buffered writer for trec file
   * @param queryID id of query
   * @param result the result of query
   * @param nDoc number of documents showed in results for each query
   * @param r the retrieval model
   * @throws IOException
   */
  static void outputResults(BufferedWriter writer, String queryID, QryResult result, int nDoc, RetrievalModel r) throws IOException {
    
    int s = result.docScores.scores.size();

    if (s < 1) {
      writer.write(queryID + " Q0 dummy 1 0 run-1");
      writer.newLine();
    } else {
      List resultList = new ArrayList();  // list of query results     
      for (int i = 0; i < s; i ++) {
    	// add doc id and score into the resultList
    	resultList.add(new ResultElement(getExternalDocid (result.docScores.getDocid(i)), result.docScores.getDocidScore(i)));
      }
      if (r instanceof RetrievalModelUnrankedBoolean) {  	  
    	Collections.sort(resultList, new ResultComparatorUnranked());
      } else {
    	Collections.sort(resultList, new ResultComparatorRanked());
      }
      
      for (int i = 0; i < s && i < nDoc; i++) {
    	ResultElement elemTmp = (ResultElement)resultList.get(i); 
    	writer.write(queryID + " Q0 " + elemTmp.getId()
    			+ " " + (i+1) + " " + elemTmp.getScore()
    			+ " run-1");
    	writer.newLine();
      }
    }
  }
  
}


/**
 * The element of a result list for output. Includes the doc id and score.
 *
 */
class ResultElement {
  public String id;
  public double score;
  
  public ResultElement(String id, double score) {
	this.id = id;
	this.score = score;
  }
  
  public String getId() {
	return id;
  }
  
  public double getScore() {
	return score;
  }
}


/**
 * Two specified comparator for Collections.sort(). 
 */
class ResultComparatorRanked implements Comparator {
  public int compare(Object o1, Object o2) {
  	
    ResultElement res1 = (ResultElement)o1;
    ResultElement res2 = (ResultElement)o2;

    if (res1.score < res2.score)  return 1;
    else if (res1.score > res2.score)  return -1;
    else  return res1.id.compareTo(res2.id);
  }
}

class ResultComparatorUnranked implements Comparator {
  public int compare(Object o1, Object o2) {
  	
    ResultElement res1 = (ResultElement)o1;
    ResultElement res2 = (ResultElement)o2;

    return res1.id.compareTo(res2.id);
  }
}
