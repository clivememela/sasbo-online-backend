package za.co.titandynamix.sasbo_online_backend.dto.payroll;

/**
 * A protected file produced from a query list: what it is and who produced it, never its password (kept nowhere).
 * {@code protection} is {@code EXCEL_AES_256} or {@code PDF_AES_256}; {@code purged} once the content went with the
 * payroll file's, a year after loading (PAY-39).
 */
public record PayrollQueryFileDto(
        long id,
        long queryId,
        String format,
        String protection,
        String fileName,
        int bytes,
        String sha256,
        String producedAt,
        String producedBy,
        int downloads,
        String lastDownloadedAt,
        boolean purged) {
}
