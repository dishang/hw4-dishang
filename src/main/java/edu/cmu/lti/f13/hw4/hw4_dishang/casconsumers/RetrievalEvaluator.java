package edu.cmu.lti.f13.hw4.hw4_dishang.casconsumers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.collection.CasConsumer_ImplBase;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSList;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceProcessException;
import org.apache.uima.util.ProcessTrace;

import edu.cmu.lti.f13.hw4.hw4_dishang.typesystems.Document;
import edu.cmu.lti.f13.hw4.hw4_dishang.typesystems.Token;
import edu.cmu.lti.f13.hw4.hw4_dishang.utils.Utils;


public class RetrievalEvaluator extends CasConsumer_ImplBase {

  /** query id number **/
  public ArrayList<Integer> qIdList;

  /** query and text relevant values **/
  public ArrayList<Integer> relList;
  
  /** list of document vectors **/
  
  public ArrayList<Map<String, Integer>> docVectorListU;  //Case sensitive
  
  public ArrayList<Map<String, Integer>> docVectorListL;  //Case insensitive
  
  /** document strings **/
  
  public ArrayList<String> docTextList;
    
  public void initialize() throws ResourceInitializationException {

    qIdList = new ArrayList<Integer>();

    relList = new ArrayList<Integer>();
    
    docVectorListU = new ArrayList<Map<String, Integer>>();
    
    docVectorListL = new ArrayList<Map<String, Integer>>();

    docTextList = new ArrayList<String>();
  }

  /**
   * TODO :: 1. construct the global word dictionary 2. keep the word
   * frequency for each sentence
   */
  @Override
  public void processCas(CAS aCas) throws ResourceProcessException {

    JCas jcas;
    try {
      jcas = aCas.getJCas();
    } catch (CASException e) {
      throw new ResourceProcessException(e);
    }

    FSIterator it = jcas.getAnnotationIndex(Document.type).iterator();
    Map<String, Integer> tokenFrequencyMapU;
    Map<String, Integer> tokenFrequencyMapL;
    
    if (it.hasNext()) {
      Document doc = (Document) it.next();

      //Make sure that your previous annotators have populated this in CAS
      FSList fsTokenList = doc.getTokenList();
      ArrayList<Token> tokenList = Utils.fromFSListToCollection(fsTokenList, Token.class);
      tokenFrequencyMapU = convertToMap(tokenList, "upper");  
      tokenFrequencyMapL = convertToMap(tokenList, "lower"); 
      qIdList.add(doc.getQueryID());
      relList.add(doc.getRelevanceValue());
      docVectorListU.add(tokenFrequencyMapU);
      docVectorListL.add(tokenFrequencyMapL);
      docTextList.add(doc.getText());
      
    }

  }
  
  /** Convert tokenList to a token frequency HashMap **/
  
  public Map<String, Integer> convertToMap(ArrayList<Token> tokenList, String type){
    
    Map<String, Integer> tokenFrequencyMap = new HashMap<String, Integer>();
    int numTokens = tokenList.size();
    
    for(int i = 0; i < numTokens; i++){
      Token token = tokenList.get(i);
      
      //upper case
      if(type.matches("upper"))
        tokenFrequencyMap.put(token.getText(), token.getFrequency());
      
      //lower case
      else{
        String lower = token.getText().toLowerCase();
        if(tokenFrequencyMap.containsKey(lower))
          tokenFrequencyMap.put(lower, tokenFrequencyMap.get(lower) + token.getFrequency());
        else  
          tokenFrequencyMap.put(lower, token.getFrequency());
      }
            
    }
    
    return tokenFrequencyMap;
    
  }

  /**
   * TODO 1. Compute Cosine Similarity and rank the retrieved sentences 2.
   * Compute the MRR metric
   */
  @Override
  public void collectionProcessComplete(ProcessTrace arg0)
      throws ResourceProcessException, IOException {

    super.collectionProcessComplete(arg0);

    //Compute the cosine similarity measure
    
    Map<String, Integer> queryVectorU;
    Map<String, Integer> docVectorU;
    Map<String, Integer> queryVectorL;
    Map<String, Integer> docVectorL;
    List<RetrievedDoc> retDocsList = new ArrayList<RetrievedDoc>();
    List<Integer> ranks = new ArrayList<Integer>();
    
        
    int rank;
    int numDocs = qIdList.size();
    double uW = 0.4; //Weight assigned to upper case
    
    for(int i = 0; i < numDocs; i++){
      
      if(relList.get(i) != 99)
        continue;
      
      retDocsList.clear();
      queryVectorU = docVectorListU.get(i); 
      queryVectorL = docVectorListL.get(i);
      
      for(int j = 0; j < numDocs; j++){
        
        if(relList.get(j) == 99 || (qIdList.get(i) != qIdList.get(j)))
          continue;
        
        docVectorU = docVectorListU.get(j);
        docVectorL = docVectorListL.get(j);
        
        RetrievedDoc retDoc = new RetrievedDoc();
        retDoc.score = uW*computeCosineSimilarity(queryVectorU, docVectorU) + 
                       (1-uW)*computeCosineSimilarity(queryVectorL, docVectorL);
        //retDoc.score = uW*computeJaccardIndex(queryVectorU, docVectorU) + 
        //               (1-uW)*computeJaccardIndex(queryVectorL, docVectorL);
        //retDoc.score = uW*computeDiceCoeff(queryVectorU, docVectorU) + 
        //               (1-uW)*computeDiceCoeff(queryVectorL, docVectorL);
        retDoc.relevance = relList.get(j);
        retDoc.qid = qIdList.get(j);
        retDoc.text = docTextList.get(j);
        retDocsList.add(retDoc);
        
      }
      
    //Compute the rank of retrieved sentences
    rank = computeRank(retDocsList);
    ranks.add(rank);
    
    }
      
    //Compute the metric:: mean reciprocal rank
    double metric_mrr = compute_mrr(ranks);
    System.out.println(" (MRR) Mean Reciprocal Rank ::" + metric_mrr);
  }
  
  
  private int computeRank(List<RetrievedDoc> relScoreList){
    int rank = 0;
    Collections.sort(relScoreList, new ScoreComparator());
    
    for(RetrievedDoc relScore: relScoreList){
      rank++;
      if(relScore.relevance == 1){
        System.out.println("Score: " + relScore.score + "\trel=" + relScore.relevance
                + "\trank=" + rank + "\tqid="
                + relScore.qid + " " + relScore.text);
        break;
      }
    }
    
    return rank;
  }

  /**
   * 
   * @return cosine_similarity
   */
  private double computeCosineSimilarity(Map<String, Integer> queryVector,
      Map<String, Integer> docVector) {
    double cosine_similarity = 0.0;

    //Construct global token set for query and doc
    Set<String> queryDoc = new HashSet<String>();  
    queryDoc.addAll(queryVector.keySet());
    queryDoc.addAll(docVector.keySet());
        
    int dotProd = 0, queryNorm = 0, docNorm = 0; 
    int queryFreq = 0, docFreq = 0;
    
    //Compute cosine similarity between two sentences
    for(String s: queryDoc){
      
      queryFreq = 0;
      docFreq = 0;
      if(queryVector.containsKey(s) && docVector.containsKey(s)){
        queryFreq = queryVector.get(s);
        docFreq = docVector.get(s);
      }
      
      else if(queryVector.containsKey(s))
        queryFreq = queryVector.get(s);
      
      else 
        docFreq = docVector.get(s);
                
      dotProd = dotProd + queryFreq*docFreq;
      queryNorm = queryNorm + (int) Math.pow(queryFreq, 2); 
      docNorm = docNorm + (int) Math.pow(docFreq, 2);
      
    }
    
    cosine_similarity = (double) dotProd/(Math.sqrt(queryNorm)*Math.sqrt(docNorm));

    return cosine_similarity;
  }

  /**
   * 
   * @return jaccard_index
   */  
  private double computeJaccardIndex(Map<String, Integer> queryVector,
          Map<String, Integer> docVector) {
        double jaccard_index = 0.0;
        int union, intersection;

        //Construct global token set for query and doc
        Set<String> global = new HashSet<String>();  
        global.addAll(queryVector.keySet());
        global.addAll(docVector.keySet());
            
        union = global.size();
        
        global.retainAll(queryVector.keySet());
        global.retainAll(docVector.keySet());
        
        intersection = global.size();
        
        if(union != 0)
          jaccard_index = (double) intersection/union;
        else
          jaccard_index = 0.0;
        
        return jaccard_index;
      }

  
  /**
   * 
   * @return dice_coeff
   */  
  private double computeDiceCoeff(Map<String, Integer> queryVector,
          Map<String, Integer> docVector) {
        double dice_coeff = 0.0;
        int a, b, ab;
        
        Set<String> intersection = new HashSet<String>();  
        intersection.addAll(queryVector.keySet());
        intersection.retainAll(docVector.keySet());    
        
        ab = intersection.size();
        a = queryVector.size();
        b = docVector.size();
                
        if((a+b) != 0)
          dice_coeff = (double) 2.0*ab/(a+b);
        else
          dice_coeff = 0.0;
        
        return dice_coeff;
      }

  /**
   * 
   * @return mrr
   */
  private double compute_mrr(List<Integer> ranks) {
    double metric_mrr = 0.0;
    
    //Compute Mean Reciprocal Rank (MRR) of the text collection
    for(int i = 0; i < ranks.size(); i++){
      metric_mrr = metric_mrr + (double) 1/ranks.get(i); 
    }
     
    metric_mrr = (double) metric_mrr/ranks.size();
    
    return metric_mrr;
  }
  
  
  //Inner class to store relevance and cosine similarity scores for a document
  private class RetrievedDoc{
    
     public int relevance;
     public double score;
     public int qid;
     public String text;
  }
  
  //Comparator class to sort queries (with same Id) based on score
  private class ScoreComparator implements Comparator<RetrievedDoc> {
    public int compare(RetrievedDoc o1, RetrievedDoc o2) {
            if (o1.score <= o2.score)
                    return 1;
            else
                    return -1;

    }
}


}