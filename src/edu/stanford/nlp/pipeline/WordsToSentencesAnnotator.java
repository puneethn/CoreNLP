package edu.stanford.nlp.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.WordToSentenceProcessor;
import edu.stanford.nlp.util.ArrayUtils;
import edu.stanford.nlp.util.CoreMap;


/**
 * This class assumes that there is a {@code List<? extends CoreLabel>}
 * under the {@code TokensAnnotation} field, and runs it
 * through {@link edu.stanford.nlp.process.WordToSentenceProcessor}
 * and puts the new {@code List<List<? extends CoreLabel>>}
 * under the {@code SentencesAnnotation} field.
 *
 * @author Jenny Finkel
 * @author Christopher Manning
 */
public class WordsToSentencesAnnotator implements Annotator {

  private final WordToSentenceProcessor<CoreLabel> wts;

  private final boolean VERBOSE;

  private final boolean countLineNumbers;

  public WordsToSentencesAnnotator() {
    this(false);
  }

  public WordsToSentencesAnnotator(boolean verbose) {
    this(verbose, false, new WordToSentenceProcessor<CoreLabel>());
  }

  public WordsToSentencesAnnotator(boolean verbose, String boundaryTokenRegex,
                                   Set<String> boundaryToDiscard, Set<String> htmlElementsToDiscard,
                                   String newlineIsSentenceBreak) {
    this(verbose, false,
         new WordToSentenceProcessor<CoreLabel>(boundaryTokenRegex,
                 boundaryToDiscard, htmlElementsToDiscard,
                 WordToSentenceProcessor.stringToNewlineIsSentenceBreak(newlineIsSentenceBreak)));
  }

  private WordsToSentencesAnnotator(boolean verbose, boolean countLineNumbers,
                                    WordToSentenceProcessor<CoreLabel> wts) {
    VERBOSE = verbose;
    this.countLineNumbers = countLineNumbers;
    this.wts = wts;
  }


  /** Return a WordsToSentencesAnnotator that splits on newlines (only), which are then deleted.
   *  This constructor counts the lines by putting in empty token lists for empty lines.
   *  It tells the underlying splitter to return empty lists of tokens
   *  and then treats those empty lists as empty lines.  We don't
   *  actually include empty sentences in the annotation, though. But they
   *  are used in numbering the sentence. Only this constructor leads to
   *  empty sentences.
   *
   *  @param verbose Whether it is verbose.
   *  @param  nlToken Zero or more new line tokens, which might be a {@literal \n} or the fake
   *                 newline tokens returned from the tokenizer.
   *  @return A WordsToSentenceAnnotator.
   */
  public static WordsToSentencesAnnotator newlineSplitter(boolean verbose, String ... nlToken) {
    // this constructor will keep empty lines as empty sentences
    WordToSentenceProcessor<CoreLabel> wts =
            new WordToSentenceProcessor<CoreLabel>(ArrayUtils.asImmutableSet(nlToken));
    return new WordsToSentencesAnnotator(verbose, true, wts);
  }


  /** Return a WordsToSentencesAnnotator that never splits the token stream. You just get one sentence.
   *
   *  @param verbose Whether it is verbose.
   *  @return A WordsToSentenceAnnotator.
   */
  public static WordsToSentencesAnnotator nonSplitter(boolean verbose) {
    WordToSentenceProcessor<CoreLabel> wts = new WordToSentenceProcessor<CoreLabel>(true);
    return new WordsToSentencesAnnotator(verbose, false, wts);
  }


  @Override
  public void annotate(Annotation annotation) {
    if (VERBOSE) {
      System.err.print("Sentence splitting ...");
    }
    if ( ! annotation.has(CoreAnnotations.TokensAnnotation.class)) {
      throw new IllegalArgumentException("WordsToSentencesAnnotator: unable to find words/tokens in: " + annotation);
    }

    // get text and tokens from the document
    String text = annotation.get(CoreAnnotations.TextAnnotation.class);
    List<CoreLabel> tokens = annotation.get(CoreAnnotations.TokensAnnotation.class);
    // System.err.println("Tokens are: " + tokens);

    // assemble the sentence annotations
    int tokenOffset = 0;
    int lineNumber = 0;
    List<CoreMap> sentences = new ArrayList<CoreMap>();
    for (List<CoreLabel> sentenceTokens: this.wts.process(tokens)) {
      if (countLineNumbers) {
        ++lineNumber;
      }
      if (sentenceTokens.isEmpty()) {
        if (!countLineNumbers) {
          throw new IllegalStateException("unexpected empty sentence: " + sentenceTokens);
        } else {
          continue;
        }
      }

      // get the sentence text from the first and last character offsets
      int begin = sentenceTokens.get(0).get(CoreAnnotations.CharacterOffsetBeginAnnotation.class);
      int last = sentenceTokens.size() - 1;
      int end = sentenceTokens.get(last).get(CoreAnnotations.CharacterOffsetEndAnnotation.class);
      String sentenceText = text.substring(begin, end);

      // create a sentence annotation with text and token offsets
      Annotation sentence = new Annotation(sentenceText);
      sentence.set(CoreAnnotations.CharacterOffsetBeginAnnotation.class, begin);
      sentence.set(CoreAnnotations.CharacterOffsetEndAnnotation.class, end);
      sentence.set(CoreAnnotations.TokensAnnotation.class, sentenceTokens);
      sentence.set(CoreAnnotations.TokenBeginAnnotation.class, tokenOffset);
      tokenOffset += sentenceTokens.size();
      sentence.set(CoreAnnotations.TokenEndAnnotation.class, tokenOffset);
      sentence.set(CoreAnnotations.SentenceIndexAnnotation.class, sentences.size());

      if (countLineNumbers) {
        sentence.set(CoreAnnotations.LineNumberAnnotation.class, lineNumber);
      }

      // add the sentence to the list
      sentences.add(sentence);
    }
    // the condition below is possible if sentenceBoundaryToDiscard is initialized!
      /*
      if (tokenOffset != tokens.size()) {
        throw new RuntimeException(String.format(
            "expected %d tokens, found %d", tokens.size(), tokenOffset));
      }
      */

    // add the sentences annotations to the document
    annotation.set(CoreAnnotations.SentencesAnnotation.class, sentences);
  }


  @Override
  public Set<Requirement> requires() {
    return Collections.singleton(TOKENIZE_REQUIREMENT);
  }

  @Override
  public Set<Requirement> requirementsSatisfied() {
    return Collections.singleton(SSPLIT_REQUIREMENT);
  }

}
