package ru.raidan.books.operations;

import ru.raidan.books.db.Utils;
import ru.raidan.books.db.model.library.tables.pojos.Book;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

// inp/inpx are binary formats, can't really prepare it without much hustle
public class InpxTestData {

    public static Book book1() {
        return new Book(
                1,
                "Название 1",
                "Серия 1",
                1,
                3081L,
                "2002-01-01",
                "ru",
                "file-10.fb2",
                "",
                "",
                2,
                1
        );
    }

    public static Book book2() {
        return new Book(
                2,
                "Название 2",
                "Серия 1",
                2,
                676L,
                "2001-01-02",
                "ru",
                "file-2.fb2",
                "",
                "",
                1,
                1
        );
    }

    public static Book book3() {
        return new Book(
                3,
                "Название 3",
                "",
                null,
                676L,
                "2001-01-03",
                "ru",
                "file-3.fb2",
                "",
                "",
                1,
                1
        );
    }

    public static Book book4() {
        return new Book(
                4,
                "Название 2",
                "",
                null,
                832L,
                "2001-01-04",
                "ru",
                "file-4.fb2",
                "",
                "",
                1,
                1
        );
    }

    public static Book book5() {
        return new Book(
                5,
                "Название 3",
                "",
                null,
                3129L,
                "2001-01-05",
                "en",
                "file-5.fb2",
                "",
                "",
                1,
                1
        );
    }

    public static Book book11() {
        return new Book(
                11,
                "Название 3",
                "",
                null,
                832L,
                "2002-01-02",
                "en",
                "file-11.fb2",
                "",
                "",
                2,
                1
        );
    }

    public static Book book12() {
        return new Book(
                12,
                "Название 4",
                "",
                null,
                12345L,
                "2002-01-03",
                "ru",
                "file-12.fb2",
                "",
                "",
                2,
                1
        );
    }

    public static void prepare(Path targetInpxFile) throws IOException {
        prepareInpx(targetInpxFile, "f1.inp", "f2.inp");
        prepareInpDir(targetInpxFile.getParent(), "f1",
                "file-1.fb2", "file-2.fb2", "file-3.fb2", "file-4.fb2", "file-5.fb2");
        prepareInpDir(targetInpxFile.getParent(), "f2",
                "file-10.fb2", "file-11.fb2", "file-12.fb2");
    }

    private static void prepareInpx(Path targetInpxFile, String... files) throws IOException {
        Files.createDirectories(targetInpxFile.getParent());
        try (var zip = new ZipOutputStream(Files.newOutputStream(targetInpxFile))) {
            for (var file : files) {
                var text = Utils.readResource("inpx/" + file)
                        .replace(';', (char) 0x04)
                        .replace("\n", "" + (char) 0x0D + (char) 0x0A);

                zip.putNextEntry(new ZipEntry(file));
                zip.write(text.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    private static void prepareInpDir(Path targetDir, String inpDir, String... files) throws IOException {
        Files.createDirectories(targetDir);

        var zipArchive = targetDir.resolve(inpDir + ".zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(zipArchive))) {
            for (var file : files) {
                var text = Utils.readResource("inpx/" + inpDir + "/" + file);

                zip.putNextEntry(new ZipEntry(file));
                zip.write(text.getBytes(StandardCharsets.UTF_8));
            }
        }
    }

}
