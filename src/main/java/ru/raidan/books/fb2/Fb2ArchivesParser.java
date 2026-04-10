package ru.raidan.books.fb2;

import ru.raidan.books.fb2.model.FictionBook;
import ru.raidan.books.util.BOMHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Fb2ArchivesParser {

    public static final String ZIP = ".zip";
    public static final String FB2 = ".fb2";
    static final int MIN_FILE_SIZE = 16;

    public void exportFictionBook(Path libraryLocation, String archiveName, Map<String, Path> filesToExport)
            throws IOException {
        loadFiles(libraryLocation, archiveName, filesToExport.keySet(), (zipEntry, stream) -> {
            var fileName = zipEntry.getName();
            var targetFile = Objects.requireNonNull(filesToExport.get(fileName),
                    "Internal error, unable to find " + fileName);
            try {
                Files.deleteIfExists(targetFile);
                Files.createDirectories(targetFile.getParent());
                Files.copy(stream, targetFile);
            } catch (IOException e) {
                throw new RuntimeException("Unable to copy " + fileName + " into " + targetFile, e);
            }
        });
    }

    public FictionBook loadFictionBook(Path libraryLocation, String archiveName, String fileName)
            throws IOException {
        var document = new AtomicReference<FictionBook>();

        loadFiles(libraryLocation, archiveName, Set.of(fileName),
                (zipEntry, stream) -> document.set(Fb2ArchivesParser.parseDocument(stream)));

        return Objects.requireNonNull(document.get(), "Internal error, document is not loaded: " + fileName);
    }

    private void loadFiles(Path libraryLocation, String archiveName, Set<String> fileNames,
                           BiConsumer<ZipEntry, InputStream> parser) throws IOException {
        var uncheckedFiles = new HashSet<>(fileNames);
        var archivePath = libraryLocation.resolve(archiveName);
        try (var zip = new ZipFile(archivePath.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                var name = entry.getName();
                if (!fileNames.contains(name)) {
                    continue;
                }

                if (entry.isDirectory()) {
                    throw new RuntimeException("Invalid file, %s is a directory".formatted(name));
                }

                var size = entry.getSize();
                if (size <= MIN_FILE_SIZE) {
                    throw new RuntimeException("Invalid file, %s is too small (%s bytes)".formatted(fileNames, size));
                }

                try (var stream = zip.getInputStream(entry)) {
                    parser.accept(entry, stream);
                }

                uncheckedFiles.remove(name);
                if (uncheckedFiles.isEmpty()) {
                    return;
                }
            }
        }

        throw new RuntimeException("Unable to find files in archive %s: %s".formatted(archivePath, uncheckedFiles));
    }

    static FictionBook parseDocument(InputStream in) {
        try {
            return Fb2Parser.parse(BOMHandler.xmlFromInputStream(in));
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

}