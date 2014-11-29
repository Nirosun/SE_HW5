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
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Version;

public class QryEval {	// NOW HW5

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
      System.err.println("Error: General parameters were missing.");
      System.exit(1);
    }

    // open the index
    READER = DirectoryReader.open(FSDirectory.open(new File(params.get("indexPath"))));

    if (READER == null) {
      System.err.println(usage);
      System.exit(1);
    }

    DocLengthStore s = new DocLengthStore(READER);

    //RetrievalModel model = null;    
    if (params.get("retrievalAlgorithm").equals("letor")) {
      if (!params.containsKey("letor:trainingQueryFile") || !params.containsKey("letor:trainingQrelsFile") ||
    		  !params.containsKey("letor:trainingFeatureVectorsFile") || !params.containsKey("letor:pageRankFile") ||
    		  !params.containsKey("letor:svmRankLearnPath") ||
    		  !params.containsKey("letor:svmRankClassifyPath") || !params.containsKey("letor:svmRankParamC") ||
    		  !params.containsKey("letor:svmRankModelFile") || !params.containsKey("letor:testingFeatureVectorsFile") ||
    		  !params.containsKey("letor:testingDocumentScores")) {
		System.err.println("Error: LeToR parameters were missing.");
		System.exit(1);
      }
      if (!params.containsKey("Indri:mu") || !params.containsKey("Indri:lambda")) {
  		System.err.println("Error: Indri parameters were missing.");
  		System.exit(1);        
      }
      if (!params.containsKey("BM25:k_1") || !params.containsKey("BM25:b") || !params.containsKey("BM25:k_3")) {
  		System.err.println("Error: BM25 parameters were missing.");
  		System.exit(1);        
      }
    }
    else {
      System.err.println("Error: Retrieval algorithm not implemented.");
      System.exit(1);
    }       
    
    double k_1 = Double.parseDouble(params.get("BM25:k_1"));
    double k_3 = Double.parseDouble(params.get("BM25:k_3"));
    double b = Double.parseDouble(params.get("BM25:b"));
    double mu = Double.parseDouble(params.get("Indri:mu"));
    double lambda = Double.parseDouble(params.get("Indri:lambda"));
    
    ArrayList<Integer> disableIDs = new ArrayList<Integer>();
    if (params.containsKey("letor:featureDisable")) {
      String[] disableRaw = params.get("letor:featureDisable").split(",");    
      for (int i = 0; i < disableRaw.length; i ++) {
        disableIDs.add(Integer.parseInt(disableRaw[i]));
      }
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
    
    //System.out.println(getInternalDocid("clueweb09-en0005-66-26526"));
    File trainQueryFile = new File(params.get("letor:trainingQueryFile"));
    File trainQrelsFile = new File(params.get("letor:trainingQrelsFile"));
    File pageRankFile = new File(params.get("letor:pageRankFile"));
    File featureFile = new File(params.get("letor:trainingFeatureVectorsFile"));
    
    BufferedReader brTrainQuery = new BufferedReader(new FileReader(trainQueryFile)); 
    BufferedReader brTrainQrels = new BufferedReader(new FileReader(trainQrelsFile));
    BufferedReader brPageRank = new BufferedReader(new FileReader(pageRankFile));
    BufferedWriter bwFeature = new BufferedWriter(new FileWriter(featureFile));
    
    HashMap<String, String> queriesTrain = new HashMap<String, String>();
    HashMap<String, ArrayList<String>> qidToDocs = new HashMap<String, ArrayList<String>>();
    HashMap<String, Double> pageRanksWhole = new HashMap<String, Double>(); 
    ArrayList<String> queryIDs = new ArrayList<String>();
    
    
    // store pagerank into hashmap
    String linePageRank = null;
    while ((linePageRank = brPageRank.readLine()) != null) {
      pageRanksWhole.put(linePageRank.split("\\t")[0].trim(), Double.parseDouble(linePageRank.split("\\t")[1].trim()));
    }
    brPageRank.close();
    
    
    // get training queries
    String lineQuery = null;   
    while ((lineQuery = brTrainQuery.readLine()) != null) {
      String id = lineQuery.split(":")[0];
      queryIDs.add(id);
      queriesTrain.put(id, lineQuery.split(":")[1]);
    }
    brTrainQuery.close();
    
    // analyze each query and its relevance judgement
    String fields[] = {"body", "title", "url", "inlink"};
    HashMap<String, ArrayList<String>> qidToDocIDs = new HashMap<String, ArrayList<String>>();
    HashMap<String, ArrayList<Integer>> qidToRels = new HashMap<String, ArrayList<Integer>>();    
    
    String lineQrels = null;
    String idLast = null;
    while ((lineQrels = brTrainQrels.readLine()) != null) {
      String[] qrels = lineQrels.split(" ");
      String qid = qrels[0];
      String exDocID = qrels[2];
      int rel = Integer.parseInt(qrels[3]);      
      //int docID = QryEval.getInternalDocid(exDocID);
      
      if (idLast == null || !idLast.equals(qid)) {
        qidToDocIDs.put(qid, new ArrayList<String>());
        qidToRels.put(qid, new ArrayList<Integer>());
        idLast = qid;
      }
      
      qidToDocIDs.get(qid).add(exDocID);
      qidToRels.get(qid).add(rel);
    }
    
    System.out.println("Training: ");
    
    // for each query
    for (String qid : queryIDs) {
      System.out.println(qid + ":" + queriesTrain.get(qid));
    	
      String[] stems = QryEval.tokenizeQuery(queriesTrain.get(qid));
      ArrayList<String> queryStems = new ArrayList<String>();
      for (int i = 0; i < stems.length; i ++) {
        queryStems.add(stems[i]);
      }
    	
      ArrayList<String> exDocIDs = qidToDocIDs.get(qid);
      ArrayList<Integer> rels = qidToRels.get(qid);     
      ArrayList<Integer> docIDs = new ArrayList<Integer>();
      
      for (String exID : exDocIDs) {
    	docIDs.add(QryEval.getInternalDocid(exID));
      }
      
      // define features for each specific query
      ArrayList<Double> spamScores = new ArrayList<Double>();
      ArrayList<Double> urlDepths = new ArrayList<Double>();
      ArrayList<Double> fromWikis = new ArrayList<Double>();
      ArrayList<Double> pageRanks = new ArrayList<Double>();
      ArrayList<HashMap<String, Double>> BM25Scores = new ArrayList<HashMap<String, Double>>();
      ArrayList<HashMap<String, Double>> IndriScores = new ArrayList<HashMap<String, Double>>();
      ArrayList<HashMap<String, Double>> overlapScores = new ArrayList<HashMap<String, Double>>();
      
      double maxSpamScore = 0;
      double minSpamScore = 99;
      double maxUrlDepth = 0;
      double minUrlDepth = Double.MAX_VALUE;
      double maxPageRank = Double.MIN_VALUE;
      double minPageRank = Double.MAX_VALUE;
      double maxFromWiki = 0;
      double minFromWiki = 1;
      HashMap<String, Double> maxBM25Score = new HashMap<String, Double>();
      HashMap<String, Double> minBM25Score = new HashMap<String, Double>();
      HashMap<String, Double> maxIndriScore = new HashMap<String, Double>();
      HashMap<String, Double> minIndriScore = new HashMap<String, Double>();
      HashMap<String, Double> maxOverlapScore = new HashMap<String, Double>();
      HashMap<String, Double> minOverlapScore = new HashMap<String, Double>();
      
      for (int i = 0; i < fields.length; i ++) {
        maxBM25Score.put(fields[i], 0.0);
        minBM25Score.put(fields[i], Double.MAX_VALUE);
        maxIndriScore.put(fields[i], 0.0);
        minIndriScore.put(fields[i], Double.MAX_VALUE);
        maxOverlapScore.put(fields[i], 0.0);
        minOverlapScore.put(fields[i], 1.0);
      }    
      
      // for each document for this query
      for (Integer docID : docIDs) {
        //double pageRank = 0;     
      
        String exDocID = QryEval.getExternalDocid(docID);
      
        // get page rank
        if (pageRanksWhole.containsKey(exDocID)) {
          double rankTmp = pageRanksWhole.get(exDocID);
          pageRanks.add(rankTmp);
          if (rankTmp > maxPageRank) {
            maxPageRank = rankTmp;
          }
          else if (rankTmp < minPageRank) {
            minPageRank = rankTmp;
          }
        }
        else {
          pageRanks.add(Double.MAX_VALUE);
        }
      
        // get spam score
        Document d = QryEval.READER.document(docID);
        double spamScore = (double)Integer.parseInt(d.get("score"));
        spamScores.add(spamScore);
        if (spamScore > maxSpamScore) {
          maxSpamScore = spamScore;
        }
        else if (spamScore < minSpamScore) {
          minSpamScore = spamScore;
        }
      
        // get url depth and FromWikipedia score
        String rawUrl = d.get("rawUrl");
        double urlDepth = 0;
        //int fromWikipedia = 0;
      
        int idTmp = 0;      
        while ((idTmp = rawUrl.indexOf("/", idTmp)) < rawUrl.length() && idTmp != -1) {
    	  idTmp ++;
    	  urlDepth++;
        }
        urlDepths.add(urlDepth);
        if (urlDepth > maxUrlDepth) {
          maxUrlDepth = urlDepth;
        }
        else if (urlDepth < minUrlDepth) {
          minUrlDepth = urlDepth;
        }
        
        double fromWiki = rawUrl.contains("wikipedia.org") ? 1 : 0;
        fromWikis.add(fromWiki);
        if (fromWiki > maxFromWiki) {
          maxFromWiki = fromWiki;
        }
        else if (fromWiki < minFromWiki) {
          minFromWiki = fromWiki;
        }
      
        // get BM25, Indri and term overlap scores
        BM25Scores.add(new HashMap<String, Double>());
        IndriScores.add(new HashMap<String, Double>());
        overlapScores.add(new HashMap<String, Double>());
        
        for (int i = 0; i < fields.length; i ++) {
          int idForList = BM25Scores.size() - 1;
          
          Terms terms = QryEval.READER.getTermVector(docID, fields[i]);
          if (terms == null) {
            // field doesn't exist!
            //System.out.println("Doc missing field: " + docID + " " + fields[i]);
        	BM25Scores.get(idForList).put(fields[i], Double.MAX_VALUE);
            IndriScores.get(idForList).put(fields[i], Double.MAX_VALUE);
            overlapScores.get(idForList).put(fields[i], Double.MAX_VALUE);
          }  
          else {
            TermVector tv = new TermVector(docID, fields[i]);
          
            // get BM25 score
            int N = QryEval.READER.getDocCount(fields[i]);
            long lengthC = QryEval.READER.getSumTotalTermFreq(fields[i]);
            double avg_doclen = lengthC / (double)N;
            long doclen = s.getDocLength(fields[i], docID);
            
            double totalBM25Score = 0.0;
            for (int j = 0; j < tv.stemsLength(); j ++) {
              if (queryStems.contains(tv.stemString(j))) {
                int tf = tv.stemFreq(j);
                int df = tv.stemDf(j);
                double idf = Math.log((N - df + 0.5) / (df + 0.5));
                double tfWeight = tf / (tf + k_1 * (1 - b + b * (doclen / (double) avg_doclen)));
                totalBM25Score += idf * tfWeight * (k_3 + 1) / (double)(k_3 + 1);
              }
            }
            BM25Scores.get(idForList).put(fields[i], totalBM25Score);
            if (totalBM25Score > maxBM25Score.get(fields[i])) {
              maxBM25Score.put(fields[i], totalBM25Score);
            }
            else if (totalBM25Score < minBM25Score.get(fields[i])) {
              minBM25Score.put(fields[i], totalBM25Score);              
            }
          
            // get Indri score
            double indriScore = 1.0;
            boolean matchFlag = false;
            for (int j = 0; j < tv.stemsLength(); j ++) {
              if (queryStems.contains(tv.stemString(j))) {
            	matchFlag = true;
            	long ctf = tv.totalStemFreq(j);
                double p_qi_C = ctf / (double) lengthC;
                int tf = tv.stemFreq(j);
                double p_qi_d = (tf + mu * p_qi_C) / (double)(doclen + mu);
                indriScore *= lambda * p_qi_d + (1 - lambda) * p_qi_C;                               
              }
            }
            if (matchFlag) {
              indriScore = Math.pow(indriScore, 1/(double)queryStems.size());
            }
            else {
              indriScore = 0.0;
            }
            IndriScores.get(idForList).put(fields[i], indriScore);
            if (indriScore > maxIndriScore.get(fields[i])) {
              maxIndriScore.put(fields[i], indriScore);
            }
            else if (indriScore < minIndriScore.get(fields[i])) {
              minIndriScore.put(fields[i], indriScore);              
            }
          
            // get term overlap score
            int matchCount = 0;
            for (int j = 0; j < tv.stemsLength(); j ++) {
              if (queryStems.contains(tv.stemString(j))) {
                matchCount ++;                             
              }
            }
            double overScore = matchCount / (double)queryStems.size();
            overlapScores.get(idForList).put(fields[i], overScore);
            if (overScore > maxOverlapScore.get(fields[i])) {
              maxOverlapScore.put(fields[i], overScore);
            }
            else if (overScore < minOverlapScore.get(fields[i])) {
              minOverlapScore.put(fields[i], overScore);              
            }            
          }
        }
      }
      
      // normalize feature values
      for (int i = 0; i < docIDs.size(); i ++) {
        // normalize spam score
        if (maxSpamScore != minSpamScore) {
          spamScores.set(i, (spamScores.get(i) - minSpamScore) / (maxSpamScore - minSpamScore));
        }
        else {
          spamScores.set(i, 0.0);
        }        
        // normalize page rank
        if (maxPageRank != minPageRank && pageRanks.get(i) != Double.MAX_VALUE) {
          pageRanks.set(i, (pageRanks.get(i) - minPageRank) / (maxPageRank - minPageRank));
        }
        else {
          pageRanks.set(i, 0.0);
        }        
        // normalize url depth
        if (maxUrlDepth != minUrlDepth) {
          urlDepths.set(i, (urlDepths.get(i) - minUrlDepth) / (maxUrlDepth - minUrlDepth));
        }
        else {
          urlDepths.set(i, 0.0);
        }
        // normalize fromwikipedia score
        if (maxFromWiki != minFromWiki) {
          fromWikis.set(i, (fromWikis.get(i) - minFromWiki) / (maxFromWiki - minFromWiki));
        }
        else {
          fromWikis.set(i, 0.0);
        }
        // normalize BM25, Indri, overlap scores
        for (int j = 0; j < fields.length; j ++) {
          double bm25Tmp = BM25Scores.get(i).get(fields[j]);
          double bm25Min = minBM25Score.get(fields[j]);
          double bm25Max = maxBM25Score.get(fields[j]);
          if (bm25Max != bm25Min && bm25Tmp != Double.MAX_VALUE) {
            BM25Scores.get(i).put(fields[j], (bm25Tmp - bm25Min) / (bm25Max - bm25Min));
          }
          else {
        	BM25Scores.get(i).put(fields[j], 0.0);
          }
          double indriTmp = IndriScores.get(i).get(fields[j]);
          double indriMin = minIndriScore.get(fields[j]);
          double indriMax = maxIndriScore.get(fields[j]);
          if (indriMax != indriMin && indriTmp != Double.MAX_VALUE) {
            IndriScores.get(i).put(fields[j], (indriTmp - indriMin) / (indriMax - indriMin));
          }
          else {
        	IndriScores.get(i).put(fields[j], 0.0);
          }
          double overlapTmp = overlapScores.get(i).get(fields[j]);
          double overlapMin = minOverlapScore.get(fields[j]);
          double overlapMax = maxOverlapScore.get(fields[j]);
          if (overlapMax != overlapMin && overlapTmp != Double.MAX_VALUE) {
            overlapScores.get(i).put(fields[j], (overlapTmp - overlapMin) / (overlapMax - overlapMin));
          }
          else {
        	overlapScores.get(i).put(fields[j], 0.0);
          }          
        }
        
      }
      
      // write the feature vectors to file
      for (int i = 0; i < docIDs.size(); i ++) {
        bwFeature.write(rels.get(i) + " qid:" + qid + " ");
        
        for (int j = 1; j <= 16; j ++) {
          if (!disableIDs.contains(j)) {
        	bwFeature.write(j + ":" + "");
            switch (j) {
            case 1: 
              bwFeature.write(spamScores.get(i).toString());
              break;
            case 2:
              bwFeature.write(urlDepths.get(i).toString());
              break;
            case 3:
              bwFeature.write(fromWikis.get(i).toString());
              break;
            case 4:
              bwFeature.write(pageRanks.get(i).toString());
              break;
            case 5:
              bwFeature.write(BM25Scores.get(i).get("body").toString());
              break;
            case 6:
              bwFeature.write(IndriScores.get(i).get("body").toString());
              break;
            case 7:
              bwFeature.write(overlapScores.get(i).get("body").toString());
              break;
            case 8:
              bwFeature.write(BM25Scores.get(i).get("title").toString());
              break;
            case 9:
              bwFeature.write(IndriScores.get(i).get("title").toString());
              break;
            case 10:
              bwFeature.write(overlapScores.get(i).get("title").toString());
              break;
            case 11:
              bwFeature.write(BM25Scores.get(i).get("url").toString());
              break;
            case 12:
              bwFeature.write(IndriScores.get(i).get("url").toString());
              break;
            case 13:
              bwFeature.write(overlapScores.get(i).get("url").toString());
              break;
            case 14:
              bwFeature.write(BM25Scores.get(i).get("inlink").toString());
              break;
            case 15:
              bwFeature.write(IndriScores.get(i).get("inlink").toString());
              break;
            case 16:
              bwFeature.write(overlapScores.get(i).get("inlink").toString());
              break;
            }
            bwFeature.write(" ");
          }
        }
        
        bwFeature.write("# " + exDocIDs.get(i));
        bwFeature.newLine();
      }     
    }    
    brTrainQrels.close();
    bwFeature.close();
    
    
    // train
    String learnPath = params.get("letor:svmRankLearnPath");
    String paramC = params.get("letor:svmRankParamC");
    String featureFileName = params.get("letor:trainingFeatureVectorsFile");
    String modelFileName = params.get("letor:svmRankModelFile");
    
    Process cmdProc = Runtime.getRuntime().exec(
    		new String[] {learnPath, "-c", paramC, featureFileName, modelFileName});
    BufferedReader stdoutReader = new BufferedReader(
    		new InputStreamReader(cmdProc.getInputStream()));
    while ((line = stdoutReader.readLine()) != null) {
      System.out.println(line);
    }
    BufferedReader stderrReader = new BufferedReader(
    		new InputStreamReader(cmdProc.getErrorStream()));
    while ((line = stderrReader.readLine()) != null) {
      System.out.println(line);
    }    
    
    int retValue = cmdProc.waitFor();
    if (retValue != 0) {
      throw new Exception("SVM Rank crashed.");
    }
    
    

    // Later HW assignments will use more RAM, so you want to be aware
    // of how much memory your program uses.

    printMemoryUsage(false);

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


    if (qString.charAt(0) != '#' || !qString.endsWith(")") ||
    		qString.startsWith("#NEAR/") || qString.startsWith("#near/") || 
    		qString.startsWith("#WINDOW/") || qString.startsWith("#window/") || 
    		qString.startsWith("#SYN") || qString.startsWith("#syn")) {
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
      } else if (token.equalsIgnoreCase("#wand")) {
        currentOp = new QryopSlWAnd();
        stack.push(currentOp);
      } else if (token.equalsIgnoreCase("#or")) {
        currentOp = new QryopSlOr();
        stack.push(currentOp);
      } else if (token.equalsIgnoreCase("#sum")) {
        currentOp = new QryopSlSum();
        stack.push(currentOp);
      } else if (token.equalsIgnoreCase("#wsum")) {
        currentOp = new QryopSlWSum();
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
    	currentOp = new QryopIlNear(num);
    	stack.push(currentOp);   	
      } else if (token.startsWith("#WINDOW/") || token.startsWith("#window/")) {
      	int num = 0;
      	String[] strs = token.split("/");
          num = Integer.parseInt(strs[1]);
      	currentOp = new QryopIlWindow(num);
      	stack.push(currentOp);   
      	
      } else if (token.startsWith(")")) { // Finish current query operator.
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
        
        // discard null #WSum
        if (!(arg instanceof QryopSlWSum && arg.args.isEmpty())) {
            currentOp.add(arg);
        }      
        
      } else {
    	//System.out.println("before tokenize: " + token);
    	  
        // NOTE: You should do lexical processing of the token before
        // creating the query term, and you should check to see whether
        // the token specifies a particular field (e.g., apple.title).    	
    	
    	if (tokenizeQuery(token).length != 0) {
    	  
    	  if (currentOp instanceof QryopSlWAnd) {
    	    QryopSlWAnd tmpOp = (QryopSlWAnd) currentOp;
    	    if (tmpOp.isNextWeight()) {
    	      tmpOp.addWeight(new QryopIlTerm(token));
    	      continue;
    	    }
    	  }
    	  else if (currentOp instanceof QryopSlWSum) {
      	    QryopSlWSum tmpOp = (QryopSlWSum) currentOp;
      	    if (tmpOp.isNextWeight()) {
      	      tmpOp.addWeight(new QryopIlTerm(token));
      	      continue;
      	    }
      	  }
          
    	  if (token.contains(".")) {
    		String[] termStrs = token.split("\\.");
    		if (tokenizeQuery(termStrs[0]).length != 0) {
    		  token = tokenizeQuery(termStrs[0])[0];
    		  currentOp.add(new QryopIlTerm(token, termStrs[1]));
    		}
    		else {
	    	  if (currentOp instanceof QryopSlWAnd) {
	      	    ((QryopSlWAnd) currentOp).deleteWeight();
	      	  }
	      	  else if (currentOp instanceof QryopSlWSum) {
	      	    ((QryopSlWSum) currentOp).deleteWeight();
	      	  }
    		}
        	//System.out.println(termStrs[0] + " " + termStrs[1]);
          } else {
        	token = tokenizeQuery(token)[0];
            currentOp.add(new QryopIlTerm(token));
          }
    	}
    	else {
    	  if (currentOp instanceof QryopSlWAnd) {
    	    ((QryopSlWAnd) currentOp).deleteWeight();
    	  }
    	  else if (currentOp instanceof QryopSlWSum) {
    	    ((QryopSlWSum) currentOp).deleteWeight();
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
    
    if(result == null) {
    	writer.write(queryID + " Q0 dummy 1 0 run-1");
        writer.newLine();
        return;
    }
	  
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


class TermMapComparator implements Comparator<String> {

    Map<String, Double> base;
    public TermMapComparator(Map<String, Double> base) {
        this.base = base;
    }

    // Note: this comparator imposes orderings that are inconsistent with equals.    
    public int compare(String a, String b) {
        if (base.get(a) >= base.get(b)) {
            return -1;
        } else {
            return 1;
        } // returning 0 would merge keys
    }
}
