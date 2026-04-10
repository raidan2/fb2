package ru.raidan.books.ui.main;

import javafx.util.Duration;
import lombok.Builder;
import lombok.Value;

import java.nio.file.Path;

@Value
@Builder
public class Settings {

    Duration keyboardTypeDelay;
    Duration authorSelectionDelay;
    Duration bookSelectionDelay;
    java.time.Duration keepCacheDuration;
    int topAuthors;
    Path lastDatabasePath;
    Path exportBooksPath;
    String[] bookReaderArgs;

    public static class SettingsBuilder {
        {
            keyboardTypeDelay = Duration.millis(250);
            authorSelectionDelay = Duration.millis(100);
            bookSelectionDelay = Duration.millis(100);
            keepCacheDuration = java.time.Duration.ofDays(7);
            topAuthors = 1000;
        }
    }
}

