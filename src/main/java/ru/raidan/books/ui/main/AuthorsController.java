package ru.raidan.books.ui.main;

import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import lombok.extern.slf4j.Slf4j;
import ru.raidan.books.db.model.library.tables.pojos.Author;
import ru.raidan.books.db.model.library.tables.pojos.Language;
import ru.raidan.books.ui.state.AppExecutor;
import ru.raidan.books.ui.state.LibraryContainer;
import ru.raidan.books.ui.state.LibraryListener;
import ru.raidan.books.util.PojoUtils;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class AuthorsController implements LibraryListener {

    private final AppExecutor appExecutor;
    private final BooksController booksController;
    private final StatusListener statusListener;
    private final Settings settings;

    private final TextField fieldSearch;
    private final ListView<Author> listAuthors;
    private final ComboBox<String> comboLang;

    private final SimpleObjectProperty<Map<Integer, Author>> authorMap = new SimpleObjectProperty<>();
    private final SimpleObjectProperty<String> lastSearchQuery = new SimpleObjectProperty<>();

    private LibraryContainer library;

    AuthorsController(Controller controller) {
        appExecutor = controller.getAppExecutor();
        booksController = controller.getBooksController();
        statusListener = controller.getStatusListener();
        settings = controller.getSettings();

        var searchDelay = new PauseTransition(settings.getKeyboardTypeDelay());

        fieldSearch = Objects.requireNonNull(controller.fieldSearch);
        fieldSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            searchDelay.setOnFinished(event -> loadAuthors(newValue));
            searchDelay.playFromStart();
        });

        listAuthors = Objects.requireNonNull(controller.listAuthors);
        comboLang = Objects.requireNonNull(controller.comboLang);
        listAuthors.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Author item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(PojoUtils.toName(item));
                    setGraphic(new Label("[" + item.getTotalBooks() + "]"));
                }
            }
        });

        var selectionDelay = new PauseTransition(settings.getAuthorSelectionDelay());
        listAuthors.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        listAuthors.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            selectionDelay.setOnFinished(event -> onAuthorSelected(newValue));
            selectionDelay.playFromStart();
        });

        comboLang.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> onLanguageSelected(newValue));
    }

    @Override
    public void onLibraryLoaded(LibraryContainer library) {
        booksController.onLibraryLoaded(library);

        this.library = library;

        clearAuthors();

        appExecutor.loadAsync(
                () -> library.getCache().getSearchQuery(),
                () -> {
                },
                value -> {
                    fieldSearch.setText(value);
                    if (value.isEmpty()) {
                        loadAuthors(value);
                    }
                }
        );
        loadLanguages();
    }

    private void clearAuthors() {
        authorMap.setValue(null);
        lastSearchQuery.setValue(null);

        fieldSearch.setText("");
        listAuthors.getItems().clear();
        comboLang.getItems().clear();
    }

    private void onAuthorSelected(@Nullable Author author) {
        log.info("Author selected: {}", author);
        onFilterChanged(author, comboLang.getValue());
        appExecutor.runAsync(() -> library.getCache().setAuthor(author));
    }

    private void onLanguageSelected(@Nullable String language) {
        log.info("Language selected: {}", language);
        onFilterChanged(listAuthors.getSelectionModel().getSelectedItem(), language);
        appExecutor.runAsync(() -> library.getCache().setLanguage(language));
    }

    private void onFilterChanged(@Nullable Author author, @Nullable String language) {
        if (author == null) {
            booksController.clearBooks();
        } else {
            booksController.loadBooks(author, getLanguages().get(language));
        }
    }

    private void loadAuthors(String text) {
        if (!Objects.equals(lastSearchQuery.getValue(), text)) {
            lastSearchQuery.setValue(text);

            var filter = "%" + text + "%";
            var topAuthors = settings.getTopAuthors();
            log.info("Looking topAuthors={}, filter={}", topAuthors, filter);

            appExecutor.loadAsync(
                    () -> library.getQueries().searchAuthors(filter, topAuthors),
                    () -> statusListener.setStatus("Loading authors"),
                    authors -> {
                        log.info("Loaded {} authors", authors.size());

                        authorMap.setValue(authors.stream()
                                .collect(Collectors.toMap(Author::getAuthorId, Function.identity())));

                        var items = listAuthors.getItems();
                        items.clear();
                        items.addAll(authors);

                        statusListener.setReady();

                        selectDefaultAuthor();
                    }
            );

            appExecutor.runAsync(() -> library.getCache().setSearchQuery(text));
        }
    }

    private void selectDefaultAuthor() {
        appExecutor.loadAsync(
                () -> library.getCache().getAuthor(),
                () -> {
                },
                authorId -> {
                    var map = authorMap.get();
                    if (map != null) {
                        var author = map.get(authorId);
                        if (author != null) {
                            log.info("Select default authorId={}", authorId);

                            listAuthors.getSelectionModel().select(author);
                            listAuthors.scrollTo(author);
                        } else {
                            log.info("Default author not found...");
                        }
                    }
                }
        );
    }

    private void selectDefaultLanguage() {
        appExecutor.loadAsync(
                () -> library.getCache().getLanguage(),
                () -> {
                },
                language -> {
                    log.info("Select default language={}", language);
                    comboLang.setValue(language);
                }
        );
    }


    private void loadLanguages() {
        var items = comboLang.getItems();
        appExecutor.loadAsync(
                this::getLanguages,
                () -> statusListener.setStatus("Loading languages"),
                languages -> {
                    items.clear();
                    items.add(null);
                    items.addAll(languages.keySet());

                    selectDefaultLanguage();
                }
        );
    }

    private Map<String, Language> getLanguages() {
        return library.getQueries().getLanguages();
    }

}
