package ru.raidan.books.db;

import lombok.extern.slf4j.Slf4j;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import ru.raidan.books.db.model.cache.tables.pojos.BookCache;
import ru.raidan.books.db.model.cache.tables.pojos.BookHistory;
import ru.raidan.books.db.model.cache.tables.records.BookCacheRecord;
import ru.raidan.books.db.model.cache.tables.records.BookHistoryRecord;
import ru.raidan.books.db.model.cache.tables.records.DefaultBooksRecord;
import ru.raidan.books.db.model.cache.tables.records.WorkspaceSettingsRecord;
import ru.raidan.books.db.model.library.tables.pojos.Author;
import ru.raidan.books.fb2.BookLoader;
import ru.raidan.books.fb2.BookParser;
import ru.raidan.books.fb2.model.Binary;
import ru.raidan.books.fb2.model.Cover;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static ru.raidan.books.db.model.cache.tables.BookCache.BOOK_CACHE;
import static ru.raidan.books.db.model.cache.tables.BookHistory.BOOK_HISTORY;
import static ru.raidan.books.db.model.cache.tables.DefaultBooks.DEFAULT_BOOKS;
import static ru.raidan.books.db.model.cache.tables.WorkspaceSettings.WORKSPACE_SETTINGS;

@Slf4j
public class CacheQueries extends AbstractQueries {
    private static final String SETTINGS_LANG = "LANG";
    private static final String SETTINGS_SEARCH_QUERY = "SEARCH_QUERY";
    private static final String SETTINGS_AUTHOR = "AUTHOR_ID";
    private static final String SETTINGS_LAST_CLEANUP_INSTANT = "CLEANUP_INSTANT";

    public CacheQueries(DSLContext dsl) {
        super(dsl);
    }

    public List<BookHistory> getHistory(int lastNRecords) {
        return executeResult("getHistory", cfg ->
                cfg.dsl()
                        .select(BOOK_HISTORY.asterisk())
                        .from(BOOK_HISTORY)
                        .orderBy(BOOK_HISTORY.CREATED.desc())
                        .limit(lastNRecords)
                        .fetchInto(BookHistory.class));
    }

    public void addToHistory(int bookId) {
        execute("addToHistory", cfg -> {
            var now = System.currentTimeMillis();

            var historyRecord = new BookHistory();
            historyRecord.setCreated(now);
            historyRecord.setBookId(bookId);

            cfg.dsl().insertInto(BOOK_HISTORY)
                    .set(new BookHistoryRecord(historyRecord))
                    .execute();

            log.debug("Add {}", historyRecord);
        });
    }

    public void setAuthor(@Nullable Author author) {
        execute("setAuthor", cfg ->
                updateSettingsImpl(cfg, SETTINGS_AUTHOR,
                        author != null
                                ? String.valueOf(author.getAuthorId())
                                : null));
    }

    @Nullable
    public Integer getAuthor() {
        return executeResult("getAuthor", cfg ->
                getWorkspaceSettingsImpl(cfg, SETTINGS_AUTHOR)
                        .map(Integer::parseInt)
                        .orElse(null)
        );
    }

    public void setDefaultBook(int authorId, int bookId) {
        execute("setDefaultBook", cfg -> {
            var result = cfg.dsl()
                    .update(DEFAULT_BOOKS)
                    .set(DEFAULT_BOOKS.BOOK_ID, bookId)
                    .where(DEFAULT_BOOKS.AUTHOR_ID.eq(authorId))
                    .execute();

            if (result <= 0) {
                cfg.dsl()
                        .insertInto(DEFAULT_BOOKS)
                        .set(new DefaultBooksRecord(authorId, bookId))
                        .execute();
            }

            log.debug("Set default book, authorId={}, bookId={}", authorId, bookId);
        });
    }

    @Nullable
    public Integer getDefaultBook(int authorId) {
        return executeResult("getDefaultBook", cfg -> {
            var result = cfg.dsl()
                    .select(DEFAULT_BOOKS.BOOK_ID)
                    .from(DEFAULT_BOOKS)
                    .where(DEFAULT_BOOKS.AUTHOR_ID.eq(authorId))
                    .fetchOptionalInto(Integer.class)
                    .orElse(null);

            log.debug("Load default book, authorId={}, bookId={}", authorId, result);
            return result;

        });
    }

    public void setLanguage(@Nullable String language) {
        execute("setLanguage", cfg -> updateSettingsImpl(cfg, SETTINGS_LANG, language));
    }

    @Nullable
    public String getLanguage() {
        return executeResult("getLanguage", cfg -> getWorkspaceSettingsImpl(cfg, SETTINGS_LANG).orElse(null));
    }

    public void setSearchQuery(String searchQuery) {
        execute("setSearchQuery", cfg -> updateSettingsImpl(cfg, SETTINGS_SEARCH_QUERY, searchQuery));
    }

    public String getSearchQuery() {
        return executeResult("getSearchQuery", cfg -> getWorkspaceSettingsImpl(cfg, SETTINGS_SEARCH_QUERY).orElse(""));
    }

    public BookCache getBookDetails(int bookId, BookLoader bookLoader, BookParser bookParser) {
        return executeResult("getBookDetails", cfg -> {
            var now = System.currentTimeMillis();

            var rec = cfg.dsl()
                    .select(BOOK_CACHE.asterisk())
                    .from(BOOK_CACHE)
                    .where(BOOK_CACHE.BOOK_ID.eq(bookId))
                    .fetchOptionalInto(BookCache.class);

            if (rec.isPresent()) {
                log.debug("Get cached bookId={}", bookId);
                cfg.dsl()
                        .update(BOOK_CACHE)
                        .set(BOOK_CACHE.UPDATED, now)
                        .where(BOOK_CACHE.BOOK_ID.eq(bookId))
                        .execute(); // TODO: update returning, but it does not work for some reason
                return rec.get();
            }

            var newRec = createRecord(bookId, bookLoader, bookParser, now);
            cfg.dsl().insertInto(BOOK_CACHE)
                    .set(new BookCacheRecord(newRec))
                    .execute();

            log.debug("Add bookId={} to cache", bookId);
            return newRec;
        });
    }

    public boolean cleanupCacheIfRequired(Duration olderThan) {
        return executeResult("cleanupCacheIfRequired", cfg -> {
            var now = System.currentTimeMillis();
            var deleteOlderThan = now - olderThan.toMillis();

            var lastCleanup = getWorkspaceSettingsImpl(cfg, SETTINGS_LAST_CLEANUP_INSTANT)
                    .map(Long::parseLong)
                    .orElse(null);

            if (lastCleanup != null && lastCleanup > deleteOlderThan) {
                log.debug("Skip cleanup, last={}, wait={}",
                        Instant.ofEpochMilli(lastCleanup), Instant.ofEpochMilli(lastCleanup + olderThan.toMillis()));
                return false;
            }


            var total = cfg.dsl()
                    .delete(BOOK_CACHE)
                    .where(BOOK_CACHE.UPDATED.lessOrEqual(deleteOlderThan))
                    .execute();

            log.debug("Cleaned total={} records from cache, older than={}",
                    total, Instant.ofEpochMilli(deleteOlderThan));

            updateSettingsImpl(cfg, SETTINGS_LAST_CLEANUP_INSTANT, String.valueOf(now));
            return true;
        });
    }

    private void updateSettingsImpl(Configuration cfg, String key, String value) {
        var result = cfg.dsl()
                .update(WORKSPACE_SETTINGS)
                .set(WORKSPACE_SETTINGS.VALUE, value)
                .where(WORKSPACE_SETTINGS.KEY.eq(key))
                .execute();

        if (result <= 0) {
            cfg.dsl()
                    .insertInto(WORKSPACE_SETTINGS)
                    .set(new WorkspaceSettingsRecord(key, value))
                    .execute();
        }

        log.debug("Set workspace settings, {}={}", key, value);
    }

    private Optional<String> getWorkspaceSettingsImpl(Configuration cfg, String key) {
        var result = cfg.dsl()
                .select(WORKSPACE_SETTINGS.VALUE)
                .from(WORKSPACE_SETTINGS)
                .where(WORKSPACE_SETTINGS.KEY.eq(key))
                .fetchOptionalInto(String.class);

        log.debug("Load workspace settings, {}={}", key, result);

        return result;
    }

    private static BookCache createRecord(int bookId, BookLoader bookLoader, BookParser bookParser, long now) {
        log.debug("Loading bookId={}", bookId);

        var book = bookLoader.loadBook(bookId);

        var annotation = book.getDescription().getTitleInfo().getAnnotation();
        var annotationPlain = annotation != null && !annotation.isBlank()
                ? bookParser.convertToText(annotation)
                : null;

        var cacheRecord = new BookCache();
        cacheRecord.setBookId(bookId);
        cacheRecord.setAnnotation(annotation);
        cacheRecord.setAnnotationPlain(annotationPlain);
        cacheRecord.setCreated(now);
        cacheRecord.setUpdated(now);

        book.getDescription().getTitleInfo().getCovers().values().stream()
                .findAny()
                .map(Cover::getBinary)
                .map(Binary::toBytes)
                .ifPresent(cacheRecord::setPicture);

        return cacheRecord;
    }

}
