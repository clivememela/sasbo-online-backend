package za.co.titandynamix.sasbo_online_backend.services;

import java.math.BigDecimal;

/**
 * The bureau file a run produces: rendered once, kept as the evidence of what was sent, and served
 * again byte for byte.
 */
public interface AcbFileService {

    /**
     * The run's bureau file. The first call for an approved run renders and stores it; later calls
     * return the stored bytes if a fresh render reproduces them, and refuse with
     * {@code FILE_CHANGED_SINCE} if it does not. A run that is not approved is refused with
     * {@code FILE_NOT_APPROVED}; a sent run's file past its purge date with {@code FILE_PURGED}.
     *
     * <p>BUILT, and proven -- but no route serves it while the API is anonymous: the file carries
     * every debited member's account number.
     */
    RenderedFile render(long runId, String actor);

    /** Whether the run has a stored file -- recording its submission requires one. */
    boolean hasRendered(long runId);

    /**
     * Drops the bytes of every file whose purge date has passed; the hash, counts and value stay.
     *
     * @return how many files were purged
     */
    int purgeExpired();

    /** A rendered file. 🔒 Its bytes are member data, so {@code toString} leaves them out. */
    record RenderedFile(String fileName, String sha256, byte[] bytes, int records, int debitCount,
                        BigDecimal debitValue) {

        @Override
        public String toString() {
            return "RenderedFile[" + fileName + ", sha256=" + sha256 + ", records=" + records + "]";
        }
    }
}
