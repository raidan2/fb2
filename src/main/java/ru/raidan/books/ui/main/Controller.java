package ru.raidan.books.ui.main;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeTableView;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.exception.NoDataFoundException;
import ru.raidan.books.db.model.library.tables.pojos.Author;
import ru.raidan.books.db.model.library.tables.pojos.Book;
import ru.raidan.books.ui.ErrorMessages;
import ru.raidan.books.ui.state.AppExecutor;
import ru.raidan.books.ui.state.LibraryContainer;
import ru.raidan.books.ui.state.LibraryListener;

import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
public class Controller implements LibraryListener {
    @Getter
    private final Stage aboutStage;
    @Getter
    private final AppExecutor appExecutor;
    @Getter
    private final Settings settings;

    // Menu

    @FXML
    MenuItem menuCreateLibrary;

    @FXML
    MenuItem menuOpenLibrary;

    @FXML
    Menu menuRemoveRecent;

    @FXML
    MenuItem menuCloseLibrary;

    @FXML
    MenuItem menuPreferences;

    @FXML
    MenuItem menuQuit;

    @FXML
    MenuItem menuAbout;


    // Books, first pane
    @FXML
    TextField fieldSearch;

    @FXML
    ComboBox<String> comboLang;

    @FXML
    ListView<Author> listAuthors;

    @FXML
    MenuItem contextAuthorsMenuItemExportAllBooks;

    // Books, second pane

    @FXML
    TreeTableView<Book> treeBooks;

    @FXML
    MenuItem contextBooksMenuItemOpenBook;

    @FXML
    MenuItem contextBooksMenuItemExportSelectedBooks;

    @FXML
    MenuItem contextBooksMenuItemExportAllBooks;

    // Books, third pane

    @FXML
    ImageView imageBook;

    @FXML
    TextArea viewDescription;

    // Status

    @FXML
    Label labelDatabase;

    @FXML
    Label labelStatus;

    private StatusController statusController;
    private BooksController booksController;
    private AuthorsController authorsController;
    private PreviewController previewController;
    private LibraryContainer library;

    public void initialize() {
        new MenuController(this);
        this.statusController = new StatusController(this);
        this.previewController = new PreviewController(this);
        this.booksController = new BooksController(this);
        this.authorsController = new AuthorsController(this);
    }

    @Override
    public void onLibraryLoaded(LibraryContainer library) {
        this.library = library;
        log.info("Library is loaded: {}", library);

        getAuthorsController().onLibraryLoaded(library);

        appExecutor.runAsync(() -> {
            try {
                library.getQueries().getLibrary();
            } catch (NoDataFoundException e) {
                ErrorMessages.warning("Unable to find library in database, please re-import the library");
                return;
            }

            library.getQueries().printStats();
            if (library.getCache().cleanupCacheIfRequired(settings.getKeepCacheDuration())) {
                library.getCacheDatabase().compact();
            }
        });
    }

    public StatusListener getStatusListener() {
        return Objects.requireNonNull(statusController, "Internal error, unable to access statusController before loading app");
    }

    public PreviewController getPreviewController() {
        return Objects.requireNonNull(previewController, "Internal error, unable to access previewController before loading app");
    }

    public BooksController getBooksController() {
        return Objects.requireNonNull(booksController, "Internal error, unable to access booksController before loading app");
    }

    public AuthorsController getAuthorsController() {
        return Objects.requireNonNull(authorsController, "Internal error, unable to access authorsController before loading app");
    }

    public LibraryContainer getLibrary() {
        return Objects.requireNonNull(library, "Internal error, unable to access library before loading app");
    }

}
