package org.example;



import com.cybozu.labs.langdetect.Detector;
import com.cybozu.labs.langdetect.DetectorFactory;
import com.cybozu.labs.langdetect.LangDetectException;
import com.cybozu.labs.langdetect.Language;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static java.lang.System.out;


/**
 * Hello world!
 *
 */
public class App 
{
    private static final Lock lock = new ReentrantLock();

    public static void main( String[] args ) throws IOException, LangDetectException {

        String profileDirectory = "src\\main\\resources\\profiles";
        DetectorFactory.loadProfile(profileDirectory);

        File file = new File("Disk:\\file/directory"); //what the program will read
        if(file.getName().toLowerCase().endsWith(".txt")) {

            out.println(txtWork(file));

        }
        else if (file.getName().toLowerCase().endsWith(".docx")){
            out.println( wordWork(file));
        }
        else if (file.getName().toLowerCase().endsWith(".log")){
            out.println( txtWork(file));
        }
        else {
            out.println(directoryWork(file));
        }
    }


    public static String directoryWork(File file) throws IOException, LangDetectException {
        ExecutorService executor = Executors.newFixedThreadPool(5);

        StringBuilder output = new StringBuilder();

        List<File> filesToProcess = new ArrayList<>();
        Map<String, String> fileResults = new ConcurrentHashMap<>();
        filesToProcess = Files.walk(Paths.get(file.getAbsolutePath()))
                .filter(Files::isRegularFile)
                .map(Path::toFile)
                .collect(Collectors.toList());
        for (File f: filesToProcess) {
            executor.submit(() -> {
                try {
                    String result;
                    if (f.getName().toLowerCase().endsWith(".txt") || f.getName().toLowerCase().endsWith(".log")) {
                        result = txtWork(f);
                        fileResults.put(f.getName(), result);

                    } else {
                        result = wordWork(f);
                        fileResults.put(f.getName(), result);

                    }
                } catch (Exception e) {
                    fileResults.put(f.getName(), "Error: " + e.getMessage());
                }
            });
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }


        for(Map.Entry<String, String> entry : fileResults.entrySet()) {
            output.append("\n==============================\n");
            output.append("File: ").append(entry.getKey());
            output.append(entry.getValue());
        }
        createDocument(fileResults);
        return output.toString();
    }

    public static void createDocument(Map<String, String > fileResult) throws IOException {
        XWPFDocument document = new XWPFDocument();
        FileOutputStream out = new FileOutputStream( new File("D:/statsFile.docx"));
        lock.lock();
        try {
            for (Map.Entry<String, String> entry : fileResult.entrySet()) {
                XWPFParagraph fileNameParagraph = document.createParagraph();
                XWPFRun fileNameRun = fileNameParagraph.createRun();
                fileNameRun.setText("File: " + entry.getKey());

                XWPFParagraph contentParagraph = document.createParagraph();
                XWPFRun contentRun = contentParagraph.createRun();
                String[] lines = entry.getValue().split("\n");
                for (String line : lines) {
                    contentRun.setText(line.trim());
                    contentRun.addBreak();
                }
                contentRun.addBreak();
            }

            document.write(out);
        } finally {
            out.close();
            lock.unlock();
        }
    }

    public static String[] findFrequencyFile(String[] arrayWords) {
        return Arrays.stream(arrayWords)
                .map(word -> word.replaceAll("[^A-Za-zА-Яа-яЁё]+", "").toLowerCase())
                .filter(word -> word.length() > 3)
                .collect(Collectors.toMap(
                        key -> key,
                        val -> 1,
                        Integer::sum
                ))
                .entrySet().stream()
                .filter(entry -> entry.getValue() > 2)
                .sorted((e1, e2) -> {
                    int val = e1.getValue().compareTo(e2.getValue()) * -1;
                    if (val == 0) {
                        val = e1.getKey().compareTo(e2.getKey());

                        if (e1.getKey().charAt(0) <= 'z'
                                && e2.getKey().charAt(0) > 'z'
                                || e1.getKey().charAt(0) > 'z'
                                && e2.getKey().charAt(0) <= 'z') {
                            val *= -1;
                        }
                    }
                    return val;
                })
                .map(e -> e.getKey() + " - " + e.getValue())
                .toArray(String[]::new);

    }


    public static String txtWork(File file) {
        int wordCount = 0;
        int totalLength = 0;
        StringBuilder output = new StringBuilder();
        List<String> allWords = new ArrayList<>();
        lock.lock();

        try {


            String text = Files.readString(Paths.get(file.getAbsolutePath()), StandardCharsets.UTF_8);

            text = text.replaceAll("[\\p{Punct}]", "");
            String[] words = text.trim().split("\\s+");
            allWords.addAll(Arrays.asList(words));
            String[] frequentWords = findFrequencyFile(allWords.toArray(new String[0]));
            wordCount += words.length;
            for (String word : words) {

                totalLength += word.length();
            }
            output.append(languageAnalyzer(words, wordCount));
            double averageLength = (double) totalLength / words.length;
            String min = allWords.stream().filter(value -> value.length() > 2).min(Comparator.comparingInt(String::length)).orElse("There are no words.");
            String max = allWords.stream().max(Comparator.comparingInt(String::length)).orElse("There are no words.");
            output.append("\nMinimum word length in a file: ").append(min).append(" - ").append(min.length());
            output.append("\nMaximum word length in a file: ").append(max).append(" - ").append(max.length());

            output.append("\nThe number of words in the file: ").append(wordCount).append(".");
            output.append("\nThe most common words:\n");
            for (String wordInfo : frequentWords) {
                output.append(wordInfo).append(" раза\n");
            }

            output.append("Average word length: ").append(String.format("%.3f", averageLength)).append("\n");
            return output.toString();
        } catch (IOException | LangDetectException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    public static String wordWork(File file) {
        int wordCount = 0;
        int totalLength = 0;
        StringBuilder output = new StringBuilder();
        List<String> allWords = new ArrayList<>();
        lock.lock();

        try {


            FileInputStream fis = new FileInputStream(file.getAbsolutePath());

            XWPFDocument document = new XWPFDocument(fis);
            XWPFWordExtractor extractor = new XWPFWordExtractor(document);
            String text = extractor.getText();

            text = text.replaceAll("[\\p{Punct}]", "");
            String[] words = text.trim().split("\\s+");
            allWords.addAll(Arrays.asList(words));
            String[] frequentWords = findFrequencyFile(allWords.toArray(new String[0]));
            wordCount += words.length;
            for (String word : words) {
                totalLength += word.length();
            }

            double averageLength = (double) totalLength / words.length;

            output.append(languageAnalyzer(words, wordCount));
            String max = allWords.stream().max(Comparator.comparingInt(String::length)).orElse("There are no words.");
            String min = allWords.stream().filter(value -> value.length() > 2).min(Comparator.comparingInt(String::length)).orElse("There are no words.");
            output.append("\nMinimum word length in a file: ").append(min).append(" - ").append(min.length());
            output.append("\nMaximum word length in a file: ").append(max).append(" - ").append(max.length());

            output.append("\nThe number of words in the file: ").append(wordCount);
            output.append("\nThe most common words:\n");
            for (String wordInfo : frequentWords) {
                output.append(wordInfo).append(" раза\n");
            }

            output.append("Average word length: ").append(String.format("%.3f", averageLength)).append("\n");
            return output.toString();
        } catch (IOException | LangDetectException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    public static String languageAnalyzer(String[] words, int wordCount) throws LangDetectException {
        StringBuilder output = new StringBuilder();

        Map<String, Integer> languageCount = new HashMap<>();

        double threshold = 0.1;
        for (String word : words) {
            ArrayList<Language> listLanguages = detectLangs(word);

            for (Language lang : listLanguages) {
                if (lang.prob >= threshold) {
                    languageCount.put(lang.lang, languageCount.getOrDefault(lang.lang, 0) + 1);
                }
            }
        }
        output.append("\nText analysis:\n");
        for (Map.Entry<String, Integer> entry : languageCount.entrySet()) {
            String lang = entry.getKey();
            int count = entry.getValue();
            double languagePercentage = (double) count / wordCount * 100;

            if (languagePercentage >= 10) {
                output.append("Language: ").append(lang)
                        .append(", Number of words: ").append(count)
                        .append(", Percent: ").append(String.format("%.2f", languagePercentage))
                        .append("%\n");
            }
        }

        output.append("\n");
        return output.toString();
    }
    public static ArrayList<Language> detectLangs(String text) throws LangDetectException {
        Detector detector = DetectorFactory.create();
        detector.append(text);
        return detector.getProbabilities();
    }
}

