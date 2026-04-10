create table if not exists BOOK_CACHE
(
    BOOK_ID INT NOT NULL primary key,
    ANNOTATION TEXT NULL,
    ANNOTATION_PLAIN TEXT NULL,
    PICTURE BLOB NULL,
    CREATED INT NOT NULL,
    UPDATED INT NOT NULL
) STRICT;
create index if not exists IDX_BOOK_CACHE_UPDATED on BOOK_CACHE (UPDATED);

create table if not exists BOOK_HISTORY
(
    CREATED INT NOT NULL primary key,
    BOOK_ID INT NOT NULL
) STRICT;

create table if not exists DEFAULT_BOOKS
(
    AUTHOR_ID INT NOT NULL,
    BOOK_ID INT NULL
) STRICT;