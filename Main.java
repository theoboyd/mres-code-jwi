import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import edu.mit.jwi.*;
import edu.mit.jwi.data.*;
import edu.mit.jwi.item.*;
import edu.mit.jwi.morph.WordnetStemmer;

public class Main {
    private static final int MAX_HYPERNYMY_DEPTH = 100;
    private ISynsetID ENTITY_WORD_SYNSET = null;

    public void getHypernymyDepths() {
        /**
         * Creates a mapping from "words1k" (see mres-code project) to MIT JWI
         * WordNet hypernymy depths (see http://projects.csail.mit.edu/jwi).
         */

        IDictionary dict = createDictionary();
        try {
            dict.open();
        } catch (IOException e) {
            System.err.println("Failed to open dictionary.");
        }

        LinkedList<String> words1k = getWords1k();

        // Find and set top-level word ("entity") ID
        IIndexWord idxWord = dict.getIndexWord("entity", POS.NOUN);
        ENTITY_WORD_SYNSET = idxWord.getWordIDs().get(0).getSynsetID();
        
        String terminator = "}, ";
        int index = 1;
        // Save results to file
        try (FileWriter writer = new FileWriter(System.getProperty("user.dir") +
                                 File.separator + "src\\words1k.jwt.hypernymy.depths.txt")) {
            writer.write("{");
            for (String word : words1k) {
                writer.write("{" + Integer.toString(index) + ", " +
                                   Integer.toString(getHypernymyDepth(word, dict)) + 
                                   ((index == 1000) ? "}" : terminator));
                index += 1;
            }
            writer.write("}");
        } catch (IOException e) {
            System.err.println("Could not write results to file.");
        }
    }

    private LinkedList<String> getWords1k() {
        // Find the words1k file in the mres-code project.
        File words1kFile = new File(System.getProperty("user.dir")
                + File.separator
                + "..\\..\\Research\\MRes\\IntermediateWordLists\\words1k.txt");
        LinkedList<String> words1k = new LinkedList<String>();

        // Read the file and convert it to a list of strings
        try (BufferedReader br = new BufferedReader(new FileReader(words1kFile))) {
            for (String line; (line = br.readLine()) != null;) {
                words1k.add(line);
            }
        } catch (Exception e) {
            System.err.println("Could not load words1k file.");
        }

        return words1k;
    }

    public IDictionary createDictionary() {
        String topProjectDir = System.getProperty("user.dir");
        String path = topProjectDir + File.separator + "dict";
        URL url = null;
        try {
            url = new URL("file:///" + path);
        } catch (MalformedURLException e) {
            System.err.println("Failed to create URL to dictionary.");
        }

        return new RAMDictionary(url, ILoadPolicy.BACKGROUND_LOAD);
    }

    public int getHypernymyDepth(String word, IDictionary dict) {
        WordnetStemmer wnStemmer = new WordnetStemmer(dict);
        List<String> wordStems = wnStemmer.findStems(word, null);
        IIndexWord idxWord = null;
        
        if (wordStems.size() < 1) {
            System.err.println("Could not find stemmed form of the word " + word + ".");
            idxWord = dict.getIndexWord(word, POS.NOUN);
        } else {
            // TODO: Guess best part of speech
            idxWord = dict.getIndexWord(wordStems.get(0), POS.NOUN);
        }
        
        if (idxWord == null) {
            return MAX_HYPERNYMY_DEPTH;
        }
        
        IWordID wordID = idxWord.getWordIDs().get(0);
        IWord wordnetWord = dict.getWord(wordID);
        ISynset wordSynset = wordnetWord.getSynset();
        
        return getMinHypernymyDepth(wordSynset, dict);
    }

    private int getMinHypernymyDepth(ISynset wordSynset, IDictionary dict) {
        
        List<ISynsetID> hypernyms = wordSynset.getRelatedSynsets(Pointer.HYPERNYM);
        
        if (hypernyms.contains(ENTITY_WORD_SYNSET) || (hypernyms.size() == 0)) {
            // This word's hypernyms contain the top-level synset "entity"
            // or it has no hypernyms (therefore is top-level itself)
            return 1;
        } else {
            int minHypernymyDepth = MAX_HYPERNYMY_DEPTH;
            
            // Get most likely meaning
            ISynset hypernym = dict.getSynset(hypernyms.get(0));
            
            int hypernymyDepth = getMinHypernymyDepth(hypernym, dict);
            if (hypernymyDepth < minHypernymyDepth) {
                minHypernymyDepth = hypernymyDepth;
            }
            
            return 1 + minHypernymyDepth;
        }
    }

    public static void main(String[] args) {
        Main m = new Main();

        m.getHypernymyDepths();
    }
}