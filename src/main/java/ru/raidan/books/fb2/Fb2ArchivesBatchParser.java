package ru.raidan.books.fb2;

import lombok.extern.slf4j.Slf4j;
import ru.raidan.books.fb2.model.FictionBook;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipFile;

@Slf4j
public class Fb2ArchivesBatchParser implements Closeable {
    private final ExecutorService executor;

    public Fb2ArchivesBatchParser(int threadCount) {
        log.info("Processing archives with {} threads", threadCount);
        this.executor = Executors.newFixedThreadPool(
                threadCount,
                runnable -> {
                    var thread = new Thread(runnable);
                    thread.setName("Archive Parser #" + thread.threadId());
                    thread.setDaemon(true);
                    return thread;
                }
        );
    }

    @Override
    public void close() {
        this.executor.shutdown();
    }

    public void loadArchives(DocumentFilter filter, DocumentConsumer consumer, Path libraryLocation, boolean fullyParse)
            throws IOException, ExecutionException, InterruptedException {
        List<Path> files;
        try (var stream = Files.list(libraryLocation)) {
            files = stream
                    .filter(path -> !Files.isDirectory(path))
                    .filter(path -> path.getFileName().toString().endsWith(Fb2ArchivesParser.ZIP))
                    .toList();
        }

        if (files.isEmpty()) {
            throw new IllegalArgumentException("Invalid books location, unable to find books in " + libraryLocation);
        }

        log.info("Found total {} files in libraryLocation={}", files.size(), libraryLocation);

        var time = System.currentTimeMillis();
        var stats = new ArchiveStats();
        for (var path : files) {
            if (!path.getFileName().toString().endsWith(Fb2ArchivesParser.ZIP)) {
                log.info("Not a zip archive: {}", path);
                continue;
            }
            var fileStats = loadArchiveImpl(filter, consumer, path, fullyParse);
            stats.addAll(fileStats);
        }

        log.info("Parsed total {} files, {}, took {} msec",
                stats.addCount, stats, System.currentTimeMillis() - time);
    }

    private ArchiveStats loadArchiveImpl(
            DocumentFilter filter,
            DocumentConsumer consumer,
            Path archivePath,
            boolean fullyParse
    ) throws IOException, ExecutionException, InterruptedException {

        log.info("Loading archivePath={}", archivePath);

        var archiveFile = archivePath.getFileName().toString();

        var time = System.currentTimeMillis();

        var futures = new ArrayList<CompletableFuture<?>>();
        var stats = new ArchiveStats();
        try (var zip = new ZipFile(archivePath.toFile())) {

            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }

                var fileName = entry.getName();
                if (!fileName.endsWith(Fb2ArchivesParser.FB2)) {
                    stats.unsupported++;
                    continue;
                }
                if (entry.getSize() <= Fb2ArchivesParser.MIN_FILE_SIZE) {
                    stats.invalid++;
                    continue;
                }
                if (!filter.canParse(archiveFile, entry)) {
                    stats.skipped++;
                    continue;
                }

                if (fullyParse) {
                    var future = CompletableFuture.runAsync(() -> {
                        try {
                            FictionBook document;
                            try (var stream = zip.getInputStream(entry)) {
                                document = Fb2ArchivesParser.parseDocument(stream);
                            }
                            consumer.onDocument(entry, document);
                            synchronized (stats) {
                                stats.parsed++;
                            }
                        } catch (Exception e) {
                            synchronized (stats) {
                                stats.invalid++;
                            }
                            log.error("Unable to parse FB2 fileName={}", fileName, e);
                        }
                    }, executor);
                    futures.add(future);
                } else {
                    stats.parsed++;
                }
            }

            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get();
        }

        log.info("Archive {} loaded, {}, took {} msec",
                archivePath.getFileName(), stats, System.currentTimeMillis() - time);

        return stats;
    }

    private static class ArchiveStats {
        private int addCount;
        private int parsed;
        private int unsupported;
        private int skipped;
        private int invalid;

        void addAll(ArchiveStats otherStats) {
            addCount++;
            parsed += otherStats.parsed;
            unsupported += otherStats.unsupported;
            skipped += otherStats.skipped;
            invalid += otherStats.invalid;
        }

        @Override
        public String toString() {
            return "%s books (%s unsupported, %s skipped, %s invalid)".formatted(
                    parsed, unsupported, skipped, invalid);
        }
    }
}
