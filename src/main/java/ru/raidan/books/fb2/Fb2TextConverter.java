package ru.raidan.books.fb2;

import org.jsoup.Jsoup;

import java.util.Arrays;
import java.util.stream.Collectors;

public class Fb2TextConverter implements BookParser {

    @Override
    public String convertToText(String fb2Html) {
        var phase1 = Jsoup.parse(fb2Html).wholeText();
        var phase2 = Jsoup.parse(phase1).wholeText();
        var linesNormalized = Arrays.stream(phase2.split("\n"))
                .map(String::trim)
                .collect(Collectors.joining("\n"));
        return linesNormalized.replace("\n\n", "\n").trim();
    }
}
