package ru.raidan.books.util;

import ru.raidan.books.db.model.library.tables.pojos.Author;
import ru.raidan.books.db.model.library.tables.pojos.Book;
import ru.raidan.books.fb2.Fb2ArchivesParser;

import java.text.DecimalFormat;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PojoUtils {
    private static final DecimalFormat SIZE_FORMATTER = new DecimalFormat("#0.0");
    private static final long KIB = 1024;
    private static final long MIB = KIB * KIB;
    private static final int MAX_FILENAME_LEN = 255;

    public static String toName(Author author) {
        return Stream.of(author.getName(), author.getMiddleName(), author.getSurname())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
    }

    public static String toFullName(Book book) {
        return toFullName(book, false);
    }

    public static String toFullNameOnFilesystem(Book book) {
        return toFullName(book, true);
    }

    private static String toFullName(Book book, boolean stripForFilesystem) {
        var title = book.getTitle();
        var bookId = book.getBookId();
        var seriesNumber = book.getSeriesNumber();

        while (true) {
            String fileName = title + " [" + bookId + "]" + Fb2ArchivesParser.FB2;
            if (seriesNumber != null && seriesNumber != 0) {
                fileName = seriesNumber + ". " + fileName;
            }

            if (!stripForFilesystem || fileName.getBytes().length < MAX_FILENAME_LEN) {
                return fileName;
            }

            title = title.substring(0, title.length() - 8);
            if (!title.endsWith("...")) {
                title += "...";
            }
        }
    }

    public static String toSize(Book book) {
        var size = book.getSize();
        if (size == null || size == 0) {
            return "";
        }

        if (size < KIB) {
            return "1 KB";
        }
        if (size < MIB) {
            return (long) (size / (double) KIB) + " KB";
        }

        return SIZE_FORMATTER.format((double) size / (double) MIB) + " MB";
    }
}
