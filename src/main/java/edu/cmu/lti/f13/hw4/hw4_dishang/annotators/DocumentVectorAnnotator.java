package edu.cmu.lti.f13.hw4.hw4_dishang.annotators;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSList;
import org.apache.uima.jcas.cas.IntegerArray;
import org.apache.uima.jcas.cas.StringArray;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;

import edu.cmu.lti.f13.hw4.hw4_dishang.typesystems.Document;
import edu.cmu.lti.f13.hw4.hw4_dishang.typesystems.Token;
import edu.cmu.lti.f13.hw4.hw4_dishang.utils.Utils;

import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.process.PTBTokenizer.PTBTokenizerFactory;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.TokenizerFactory;

public class DocumentVectorAnnotator extends JCasAnnotator_ImplBase {

  StanfordCoreNLP pipeline;

  public void initialize(UimaContext aContext)
                  throws ResourceInitializationException {
          super.initialize(aContext);
          Properties props = new Properties();
          props.put("annotators", "tokenize, ssplit,pos,lemma");
          pipeline = new StanfordCoreNLP(props);

  }

  
  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {

    FSIterator<Annotation> iter = jcas.getAnnotationIndex().iterator();
    if (iter.isValid()) {
      iter.moveToNext();
      Document doc = (Document) iter.get();
      createTermFreqVector(jcas, doc);
    }

  }
  /**
   * 
   * @param jcas
   * @param doc
   */

  private void createTermFreqVector(JCas jcas, Document doc) {

    String docText = doc.getText();
    
    //Construct a vector of tokens and update the tokenList in CAS
    
    Map<String, Integer> tokenFrequencyMap = new HashMap<String, Integer>();
    
    //Use Stanford CoreNLP to tokenize each document
    
    TokenizerFactory<Word> factory = PTBTokenizerFactory.newTokenizerFactory();
    Tokenizer<Word> tokenizer = factory.getTokenizer(new StringReader(docText));
       
    List<Word> wordList = tokenizer.tokenize(); 
    
    String[] tokens = new String[wordList.size()];
    int i = 0;
    for(Word word: wordList){
      tokens[i] = word.toString();
      i++;
    }
      
    //Populate token frequency map
    
    for(String s: tokens){
     if(tokenFrequencyMap.containsKey(s))
        tokenFrequencyMap.put(s, tokenFrequencyMap.get(s) + 1);
     else
       tokenFrequencyMap.put(s, 1);
    }
       
    //Populate token annotations & create a list of tokens
    ArrayList<Token> tokenList = new ArrayList<Token>();
    for(String s: tokenFrequencyMap.keySet()){
      Token annotation = new Token(jcas);
      annotation.setText(s);
      annotation.setFrequency(tokenFrequencyMap.get(s));
      annotation.addToIndexes();
      tokenList.add(annotation);
    }
    
    //Convert tokenList to FSList & insert it in document annotation
    FSList tokenFSList;
    tokenFSList = Utils.fromCollectionToFSList(jcas, tokenList);
    doc.setTokenList(tokenFSList);
    
  
  }

}
