package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.exception.MigrationRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MigrationRefusalCode;

/** A page of the migration's lists: nought or more, of one to the list's ceiling -- or PAGE_INVALID, saying the ceiling. */
final class MigrationPages {

    /** No list of the migration is read past this page: 10,000 pages of 100 is a million rows, the row ceiling. */
    static final int MAX_PAGE = 10_000;

    private MigrationPages() {
    }

    static void check(int page, int size, int maxSize) {
        if (page < 0 || page > MAX_PAGE || size < 1 || size > maxSize) {
            throw new MigrationRefusal(MigrationRefusalCode.PAGE_INVALID, "That page does not exist: pages start at "
                    + "nought, and hold from 1 to " + maxSize + " rows.",
                    MigrationRefusal.details("page", String.valueOf(page), "size", String.valueOf(size),
                            "maxSize", String.valueOf(maxSize)));
        }
    }
}
