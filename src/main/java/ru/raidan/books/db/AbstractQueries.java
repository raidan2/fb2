package ru.raidan.books.db;

import org.jooq.DSLContext;
import org.jooq.TransactionalCallable;
import org.jooq.TransactionalRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AbstractQueries {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final DSLContext dsl;

    public AbstractQueries(DSLContext dsl) {
        this.dsl = dsl;
    }

    protected void execute(String title, TransactionalRunnable action) {
        long time = System.currentTimeMillis();
        dsl.transaction(action);
        log.debug(" >> {} execute in {} msec", title, System.currentTimeMillis() - time);
    }

    protected <T> T executeResult(String title, TransactionalCallable<T> action) {
        long time = System.currentTimeMillis();
        var ret = dsl.transactionResult(action);
        log.debug(" >> {} load in {} msec", title, System.currentTimeMillis() - time);
        return ret;
    }
}
