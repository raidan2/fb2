package ru.raidan.books.ui.main;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.control.cell.TreeItemPropertyValueFactory;
import lombok.extern.slf4j.Slf4j;
import ru.raidan.books.db.BooksFilter;
import ru.raidan.books.db.model.library.tables.pojos.Author;
import ru.raidan.books.db.model.library.tables.pojos.Book;
import ru.raidan.books.db.model.library.tables.pojos.Language;
import ru.raidan.books.ui.state.AppExecutor;
import ru.raidan.books.ui.state.LibraryContainer;
import ru.raidan.books.ui.state.LibraryListener;
import ru.raidan.books.util.PojoUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Slf4j
public class BooksController implements LibraryListener {
    private final AppExecutor appExecutor;
    private final Settings settings;
    private final StatusListener statusListener;
    private final TreeTableView<Book> treeBooks;
    private final PreviewController previewController;

    private final SimpleObjectProperty<Author> currentAuthor = new SimpleObjectProperty<>();
    private final SimpleObjectProperty<Book> currentBook = new SimpleObjectProperty<>();
    private final SimpleObjectProperty<TreeItem<Book>> currentBookItem = new SimpleObjectProperty<>();
    private final SimpleObjectProperty<List<Book>> currentBookList = new SimpleObjectProperty<>();

    private final SimpleObjectProperty<Map<Integer, TreeItem<Book>>> currentBookMap = new SimpleObjectProperty<>();
    private final SimpleObjectProperty<BooksFilter> lastFilter = new SimpleObjectProperty<>();

    private LibraryContainer library;

    @SuppressWarnings("unchecked")
    BooksController(Controller controller) {
        this.appExecutor = controller.getAppExecutor();
        this.settings = controller.getSettings();
        this.statusListener = controller.getStatusListener();

        this.treeBooks = Objects.requireNonNull(controller.treeBooks);

        getColumn(treeBooks, "columnBookId")
                .setCellValueFactory(new TreeItemPropertyValueFactory<>("title"));

        getColumn(treeBooks, "columnBookSeries")
                .setCellValueFactory(new TreeItemPropertyValueFactory<>("seriesNumber"));

        ((TreeTableColumn<Book, String>) getColumn(treeBooks, "columnBookSize"))
                .setCellValueFactory(param -> new SimpleStringProperty(PojoUtils.toSize(param.getValue().getValue())));
        getColumn(treeBooks, "columnBookLang")
                .setCellValueFactory(new TreeItemPropertyValueFactory<>("lang"));
        getColumn(treeBooks, "columnBookDate")
                .setCellValueFactory(new TreeItemPropertyValueFactory<>("date"));

        this.previewController = controller.getPreviewController();

        var selectionDelay = new PauseTransition(settings.getBookSelectionDelay());
        treeBooks.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        treeBooks.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            selectionDelay.setOnFinished(event -> onBookSelected(newValue));
            selectionDelay.playFromStart();
        });

        var contextBooksMenuItemOpenBook = Objects.requireNonNull(controller.contextBooksMenuItemOpenBook);
        contextBooksMenuItemOpenBook.setOnAction(event -> openBook());

        treeBooks.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                contextBooksMenuItemOpenBook.fire();
            }
        });

        var contextBooksMenuItemExportAllBooks = Objects.requireNonNull(controller.contextBooksMenuItemExportAllBooks);
        contextBooksMenuItemExportAllBooks.setOnAction(event -> exportAllBooks());

        var contextBooksMenuItemExportSelectedBooks = Objects.requireNonNull(controller.contextBooksMenuItemExportSelectedBooks);
        contextBooksMenuItemExportSelectedBooks.setOnAction(event -> exportSelectedBooks());

        var contextAuthorsMenuItemExportAllBooks = Objects.requireNonNull(controller.contextAuthorsMenuItemExportAllBooks);
        contextAuthorsMenuItemExportAllBooks.setOnAction(event -> exportAllBooks());

        contextBooksMenuItemOpenBook.setDisable(true);
        contextBooksMenuItemExportSelectedBooks.setDisable(true);
        contextBooksMenuItemExportAllBooks.setDisable(true);
        contextAuthorsMenuItemExportAllBooks.setDisable(true);
        currentBook.addListener((observable, oldValue, newValue) ->
                contextBooksMenuItemOpenBook.setDisable(newValue == null)
        );
        currentBookList.addListener((observable, oldValue, newValue) -> {
            contextBooksMenuItemExportAllBooks.setDisable(isEmpty(newValue));
            contextAuthorsMenuItemExportAllBooks.setDisable(isEmpty(newValue));
        });
        currentBookItem.addListener((observable, oldValue, newValue) ->
                contextBooksMenuItemExportSelectedBooks.setDisable(
                        newValue == null || (isGroup(newValue.getValue()) && isEmpty(newValue.getChildren()))
                ));

        this.clearBooks();
    }

    @Override
    public void onLibraryLoaded(LibraryContainer library) {
        previewController.onLibraryLoaded(library);

        this.library = library;
        clearBooks();
    }

    private void onBookSelected(@Nullable TreeItem<Book> item) {
        currentBookItem.setValue(item);
        if (item != null) {
            var book = item.getValue();
            if (book != null && !isGroup(book)) {

                var author = currentAuthor.getValue();
                currentBook.setValue(book);

                log.info("Book selected: {}", PojoUtils.toFullName(book));
                previewController.showPreview(book);

                if (author != null) {
                    appExecutor.runAsync(() -> library.getCache().setDefaultBook(author.getAuthorId(), book.getBookId()));
                }
                return;
            }
        }
        currentBook.setValue(null);
        previewController.clearPreview();
    }

    public void clearBooks() {
        treeBooks.setRoot(null);
        currentAuthor.setValue(null);
        currentBook.setValue(null);
        currentBookItem.setValue(null);
        currentBookList.setValue(null);
        currentBookMap.setValue(null);
        lastFilter.setValue(null);
    }

    public void loadBooks(Author author, @Nullable Language language) {
        var filterBuilder = BooksFilter.builder()
                .authorId(author.getAuthorId());

        if (language != null) {
            filterBuilder.lang(language.getLang());
        }

        var filter = filterBuilder.build();
        if (!Objects.equals(lastFilter.getValue(), filter)) {
            lastFilter.setValue(filter);
            currentAuthor.setValue(author);

            log.info("Loading books, filter={}", filter);

            appExecutor.loadAsync(
                    () -> library.getQueries().searchBooks(filter),
                    () -> statusListener.setStatus("Loading books"),
                    books -> {
                        log.info("Loaded {} books", books.size());

                        currentBookList.setValue(books);

                        var transformedBooks = transformBooks(books, language);
                        currentBookMap.setValue(transformedBooks.bookMap());

                        treeBooks.setRoot(transformedBooks.root());
                        statusListener.setReady();

                        selectDefaultBook(author);
                    }
            );
        }
    }

    public void selectDefaultBook(Author author) {
        log.info("Try to select default book for authorId={}", author.getAuthorId());

        appExecutor.loadAsync(
                () -> library.getCache().getDefaultBook(author.getAuthorId()),
                () -> {
                },
                bookId -> {
                    var map = currentBookMap.getValue();
                    if (map != null) {
                        var bookItem = map.get(bookId);
                        if (bookItem != null) {
                            log.info("Select default bookId={}", bookId);

                            bookItem.getParent().setExpanded(true);

                            treeBooks.getSelectionModel().select(bookItem);
                            treeBooks.scrollTo(treeBooks.getSelectionModel().getSelectedIndex());
                        } else if (bookId != null) {
                            log.warn("Not found bookId={}", bookId);
                        }
                    }
                }
        );
    }

    private void openBook() {
        var author = currentAuthor.getValue();
        if (author == null) {
            log.warn("No author selected, nothing to open");
            return;
        }

        var book = currentBook.getValue();
        if (book == null) {
            log.warn("No book selected, nothing to open");
            return;
        }

        log.info("Open book, author={}, book={}", author, book);

        appExecutor.loadAsync(
                () -> {
                    try {
                        return library.getExportService().exportBooks(
                                settings.getExportBooksPath(),
                                author,
                                List.of(book),
                                ignore -> {
                                }
                        );
                    } catch (IOException io) {
                        throw new RuntimeException("Unable to export book", io);
                    }
                },
                () -> statusListener.setStatus("Exporting book " + PojoUtils.toFullName(book)),
                exportMap -> {
                    statusListener.setReady();
                    var path = exportMap.get(book.getBookId());
                    if (path == null) {
                        throw new IllegalStateException("Internal error. Unable to find export book " + book.getBookId());
                    }

                    var defaultArgs = settings.getBookReaderArgs();
                    var args = fillArgs(defaultArgs, path.toString());

                    appExecutor.loadAsync(
                            () -> {
                                try {
                                    Runtime.getRuntime().exec(args);
                                    return null;
                                } catch (IOException e) {
                                    throw new RuntimeException("Unable to run book reader", e);
                                }
                            },
                            () -> statusListener.setStatus("Starting " + Arrays.asList(defaultArgs)),
                            any -> statusListener.setReady()
                    );

                    appExecutor.runAsync(() -> library.getCache().addToHistory(book.getBookId()));
                }
        );
    }

    private void exportAllBooks() {
        var author = currentAuthor.getValue();
        if (author == null) {
            log.warn("No author selected, nothing to open");
            return;
        }

        var bookList = currentBookList.getValue();
        if (bookList == null) {
            log.warn("No books selected, nothing to open");
            return;
        }

        log.info("Export all books, author={}, total books={}", author, bookList.size());

        var expectCount = bookList.size();
        appExecutor.loadAsync(
                () -> {
                    try {
                        return library.getExportService().exportBooks(
                                settings.getExportBooksPath(),
                                author,
                                bookList,
                                actualCount -> Platform.runLater(() ->
                                        statusListener.setStatus(
                                                "Exporting " + actualCount + " out of " + expectCount + " books...")
                                )
                        );
                    } catch (IOException io) {
                        throw new RuntimeException("Unable to export books", io);
                    }
                },
                () -> statusListener.setStatus("Exporting " + expectCount + " books..."),
                exportMap -> statusListener.setReady()
        );
    }

    private void exportSelectedBooks() {
        var author = currentAuthor.getValue();
        if (author == null) {
            log.warn("No author selected, nothing to open");
            return;
        }

        var bookItem = currentBookItem.getValue();
        if (bookItem == null) {
            log.warn("No book selected, nothing to open");
            return;
        }

        var bookList = flatTree(bookItem);

        var expectCount = bookList.size();

        log.info("Export selected books, author={}, total books={}", author, bookList.size());
        appExecutor.loadAsync(
                () -> {
                    try {
                        return library.getExportService().exportBooks(
                                settings.getExportBooksPath(),
                                author,
                                bookList,
                                actualCount -> Platform.runLater(() ->
                                        statusListener.setStatus(
                                                "Exporting " + actualCount + " out of " + expectCount + " books...")
                                )
                        );
                    } catch (IOException io) {
                        throw new RuntimeException("Unable to export selected books", io);
                    }
                },
                () -> statusListener.setStatus("Exporting selected " + expectCount + " books..."),
                exportMap -> statusListener.setReady()
        );
    }

    private static List<Book> flatTree(TreeItem<Book> item) {
        if (!isGroup(item.getValue())) {
            return List.of(item.getValue());
        }
        var result = new ArrayList<Book>(item.getChildren().size());
        for (var child : item.getChildren()) {
            if (isGroup(child.getValue())) {
                result.addAll(flatTree(child));
            } else {
                result.add(child.getValue());
            }
        }
        return result;
    }

    private static TransformedBooks transformBooks(List<Book> books, @Nullable Language language) {
        var rootBook = new Book();

        var langSuffix = language != null
                ? " (" + language.getLang() + ")"
                : "";
        rootBook.setTitle("[" + books.size() + "] Books" + langSuffix);

        var allLangs = new LinkedHashSet<String>();
        var totalSize = 0L;

        var rootItem = new TreeItem<>(rootBook);
        rootItem.setExpanded(true);

        var listWithoutSeriesItem = new TreeItem<Book>();

        Map<Integer, TreeItem<Book>> bookItemMap = new HashMap<>(books.size());

        var map = books.stream().collect(Collectors.groupingBy(Book::getSeries));
        for (var series : new TreeSet<>(map.keySet())) {

            var bookList = Objects.requireNonNull(map.get(series),
                    "Internal error, unable to find books by series");

            var size = bookList.stream()
                    .map(Book::getSize)
                    .filter(Objects::nonNull)
                    .mapToLong(i -> i)
                    .sum();
            totalSize += size;

            TreeItem<Book> seriesItem;
            if (series.isEmpty()) {
                seriesItem = listWithoutSeriesItem;
            } else {
                var seriesBook = new Book();
                seriesBook.setTitle("[" + bookList.size() + "] " + series);

                var lang = bookList.stream()
                        .map(Book::getLang)
                        .distinct()
                        .sorted()
                        .peek(allLangs::add)
                        .collect(Collectors.joining(", "));
                seriesBook.setLang(lang);
                seriesBook.setSize(size);

                seriesItem = new TreeItem<>(seriesBook);
                rootItem.getChildren().add(seriesItem);
            }
            for (var book : bookList) {
                var item = new TreeItem<>(book);
                bookItemMap.put(book.getBookId(), item);
                seriesItem.getChildren().add(item);
            }
        }
        rootBook.setLang(String.join(", ", allLangs));
        rootBook.setSize(totalSize);
        rootItem.getChildren().addAll(listWithoutSeriesItem.getChildren());

        return new TransformedBooks(
                rootItem,
                bookItemMap
        );
    }

    private static TreeTableColumn<Book, ?> getColumn(TreeTableView<Book> treeBooks, String id) {
        return treeBooks.getColumns().stream()
                .filter(column -> Objects.equals(column.getId(), id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Internal error. Unable to find column " + id));
    }

    private static boolean isGroup(Book book) {
        return book.getBookId() == null;
    }

    private static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    private static String[] fillArgs(String[] defaultArgs, String path) {
        var args = new String[defaultArgs.length + 1];
        System.arraycopy(defaultArgs, 0, args, 0, defaultArgs.length);
        args[args.length - 1] = path;
        return args;
    }

    private record TransformedBooks(
            TreeItem<Book> root,
            Map<Integer, TreeItem<Book>> bookMap
    ) {
    }
}