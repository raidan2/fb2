package ru.raidan.books.inpx;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.raidan.books.db.model.library.tables.pojos.Library;
import ru.raidan.books.fb2.Fb2ArchivesBatchParser;
import ru.raidan.books.fb2.Fb2ArchivesParser;
import ru.raidan.books.util.BOMHandler;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.zip.ZipFile;

/**
 * Структура записи файла .inp такая:
 * AUTHOR;GENRE;TITLE;SERIES;SERNO;FILE;SIZE;LIBID;DEL;EXT;DATE;LANG;INDEX?;KEYWORDS;<CR><LF>
 * Разделитель полей записи (вместо ';') - <0x04>
 * Завершают запись символы <CR><LF> - <0x0D,0x0A>
 */
@Slf4j
@RequiredArgsConstructor
public class InpxParser {

    private static final String INP_SUFFIX = ".inp";

    private final InpxListener listener;

    private final boolean firstFileOnly;

    public void parseLibrary(Library library) throws IOException {
        var time = System.currentTimeMillis();

        var booksFromLibrary = collectExistsBooks(Path.of(library.getLibraryLocation()));

        var inpxLocation = Path.of(library.getInpxLocation());
        log.info("Loading inpx files archive, inpxLocation={}", inpxLocation);

        listener.onBegin(library);

        var stats = new InpxStats();
        try (var zip = new ZipFile(inpxLocation.toFile())) {

            var entries = zip.entries();

            // TODO: read entries in sorted reverse order
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }

                var inpFile = entry.getName();
                if (!inpFile.endsWith(INP_SUFFIX)) {
                    continue;
                }

                var archiveFile = inpFile.substring(0, inpFile.length() - INP_SUFFIX.length()) +
                        Fb2ArchivesParser.ZIP;

                var booksFromArchive = booksFromLibrary.get(archiveFile);
                if (booksFromArchive == null) {
                    log.error("Unable to find archiveFile={} matched with inpFile={}", archiveFile, inpFile);
                    continue;
                }

                try (var inputStream = zip.getInputStream(entry)) {
                    var fileStats = loadInpFile(inpFile, archiveFile, inputStream, booksFromArchive);
                    stats.addAll(fileStats);
                }

                if (firstFileOnly) {
                    break;
                }

            }
        }

        listener.onComplete();
        log.info("Archive {} loaded, total {} files, {}, took {} msec",
                inpxLocation, stats.addCount, stats, System.currentTimeMillis() - time);
    }

    private Map<String, Map<String, ActualBook>> collectExistsBooks(Path libraryLocation) {
        log.info("Loading books from library: {}", libraryLocation);

        var booksFromLibrary = new HashMap<String, Map<String, ActualBook>>();
        try (var books = new Fb2ArchivesBatchParser(1)) {
            books.loadArchives(
                    (archive, entry) -> {
                        var name = entry.getName();
                        var size = entry.getSize();
                        var booksFromArchive = booksFromLibrary.computeIfAbsent(archive, arc -> new HashMap<>());
                        if (booksFromArchive.put(name, new ActualBook(archive, size)) != null) {
                            log.info("Duplicate book in archive: {}", name);
                        }
                        return true;
                    },
                    (entry, document) -> {
                        throw new RuntimeException("No book should be parsed"); // fullyParse = false
                    },
                    libraryLocation,
                    false
            );
        } catch (Exception e) {
            throw new RuntimeException("Unable to parse library " + libraryLocation, e);
        }

        if (booksFromLibrary.isEmpty()) {
            throw new IllegalStateException("Invalid library location, no books were loaded: " + libraryLocation);
        }

        int totalBooksCount = booksFromLibrary.values().stream()
                .mapToInt(Map::size)
                .sum();

        log.info("Loaded total {} books from library location of total {} archive files",
                totalBooksCount, booksFromLibrary.size());

        return booksFromLibrary;
    }


    private InpxStats loadInpFile(
            String inpFile,
            String archiveFile,
            InputStream stream,
            Map<String, ActualBook> booksFromArchive
    ) throws IOException {
        var time = System.currentTimeMillis();

        log.info("Loading file: {} (archive {})", inpFile, archiveFile);

        // TODO: должнен быть общий set на все книги
        var testedBooks = new HashSet<>(booksFromArchive.keySet());
        var stats = new InpxStats();
        try (var reader = new BufferedReader(BOMHandler.rawFromInputStream(stream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                var items = line.split("\\x04", 14);
                var record = parse(archiveFile, items, stats);
                if (record != null) {
                    if (record.isDeleted()) {
                        stats.deletedBooks++;
                    } else {
                        var fileName = record.getFileName() + "." + record.getFileExt();
                        var actualBook = booksFromArchive.get(fileName);
                        if (actualBook != null) {
                            if (actualBook.size != record.getSize()) {
                                stats.wrongSize++;
                                log.info("Book has wrong size: expected {}, got {}, {}",
                                        record.getSize(), actualBook.size, record);
                            }
                            if (!testedBooks.remove(fileName)) { // TODO: check bookId, not name
                                stats.duplicateReferences++;
                                log.info("Book with name {} found more than once: {}", fileName, record);
                            }
                            record = record.withArchiveMatched(true);
                        } else {
                            stats.notInLibrary++;
                            log.info("Book is not in library: {}", record);
                        }

                        stats.validBooks++;
                        listener.onRecordParsed(record);
                    }
                } else {
                    stats.invalidBooks++;
                }
            }
        }

        listener.onFileComplete();

        log.info("File {} loaded, {}, took {} msec", inpFile, stats, System.currentTimeMillis() - time);

        return stats;
    }

    @Nullable
    private InpxRawBook parse(String archiveFile, String[] items, InpxStats stats) {
        if (items.length < 11) {
            log.warn("Unable to parse book record: parsed {} items, {}", items.length, String.join(";", items));
            return null;
        }
        var bookId = Integer.parseInt(items[7].trim());

        var book = InpxRawBook.builder();
        book.author(items[0].trim());
        book.genres(items[1].trim());
        book.title(items[2].trim());
        book.series(items[3].trim());

        var seriesNumber = items[4].trim();
        try {
            book.seriesNumber(parseSeriesNumber(seriesNumber));
        } catch (NumberFormatException e) {
            stats.unparseableSeries++;
        }
        book.fileName(items[5].trim());
        book.size(Long.parseLong(items[6].trim()));
        book.bookId(bookId);
        book.deleted(items[8].trim().equals("1"));
        book.fileExt(items[9].trim());
        book.date(items[10].trim());
        book.lang(items.length > 11 ? items[11].trim() : "");
        book.index(items.length > 12 ? items[12].trim() : "");
        book.keywords(items.length > 13 ? items[13].trim() : "");
        book.archive(archiveFile);
        return book.build();
    }

    @Nullable
    private Integer parseSeriesNumber(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return Integer.parseInt(value);

    }

    private record ActualBook(String archiveFile, long size) {
    }

    private static class InpxStats {
        int addCount;
        int validBooks;
        int invalidBooks;
        int deletedBooks;
        int notInLibrary;
        int wrongSize;
        int duplicateReferences;
        int unparseableSeries;

        void addAll(InpxStats otherStats) {
            addCount++;
            validBooks += otherStats.validBooks;
            invalidBooks += otherStats.invalidBooks;
            deletedBooks += otherStats.deletedBooks;
            notInLibrary += otherStats.notInLibrary;
            wrongSize += otherStats.wrongSize;
            duplicateReferences += otherStats.duplicateReferences;
            unparseableSeries += otherStats.unparseableSeries;
        }

        @Override
        public String toString() {
            return "%s books (%s deleted, %s invalid, %s not in library, %s wrong size, %s duplicates, %s series cannot parse)".formatted(
                    validBooks, deletedBooks, invalidBooks, notInLibrary, wrongSize, duplicateReferences, unparseableSeries);
        }
    }

}
