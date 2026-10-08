package za.co.titandynamix.sasbo_online_backend.dto.payroll;

/**
 * The answer to producing a protected query list: the file, and the password the system generated for it -- in this
 * answer only (sent {@code Cache-Control: no-store}), never stored, never logged. Whoever produced it gives the password
 * to the employer by another channel; a lost password is a file produced again. 🔒 {@code toString()} withholds it.
 */
public record PayrollProducedFileDto(PayrollQueryFileDto file, String password, String passwordNote) {

    @Override
    public String toString() {
        return "PayrollProducedFileDto[file=" + (file == null ? null : file.id()) + ", password withheld]";
    }
}
