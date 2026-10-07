package za.co.titandynamix.sasbo_online_backend.dto.migration;

/**
 * A batch as a screen polls it: its file, the mapping it is read by, its counts, its status, who moved it on and when,
 * and its job's progress. Every instant and date is ISO text.
 *
 * @param alreadyRegistered the file was registered before -- this is that batch, and nothing was read again
 */
public record MigrationBatchDto(long id, String fileName, String sha256, long bytes, String format, String mappingKey,
                                String asAt, String status, int rowsRead, int rowsValid, int rowsWarned, int rowsInvalid,
                                int rowsCommitted, String registeredBy, String registeredAt, String validatedBy,
                                String validatedAt, String committedBy, String committedAt, String rolledBackBy,
                                String rolledBackAt, String rollbackReason, Job job, boolean alreadyRegistered) {

    /** The batch's one job -- a check, a move or an undo -- and how far it has got. {@code null} where it never had one. */
    public record Job(String kind, String state, int done, int total, String startedAt, String heartbeatAt,
                      String finishedAt, String failure) {
    }
}
