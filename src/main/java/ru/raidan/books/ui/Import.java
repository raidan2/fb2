package ru.raidan.books.ui;

import ru.raidan.books.db.model.library.tables.pojos.Library;
import ru.raidan.books.operations.ImportService;

import java.nio.file.Path;

public class Import {

    private Import() {

    }

    public static void importWithoutUI(Path inpxLocation, Path databasePath) {
        var library = new Library();
        library.setInpxLocation(inpxLocation.toString());
        library.setLibraryLocation(inpxLocation.getParent().toString());

        // "flibusta_fb2_local.inpx"?
        library.setTitle(inpxLocation.getFileName().toString());

        try {
            ImportService.importLibrary(databasePath, library, false, false);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
