package za.co.titandynamix.sasbo_online_backend.dto.payroll;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * A payroll discrepancy, with the rule of Stephne's it comes from, what to do about it, who it is about, the amounts,
 * its query, the outcomes that fit it, and its notes. A list carries {@code noteCount} and {@code lastNote}; one
 * discrepancy read alone, or written to, carries every note in {@code notes} (absent from list items).
 */
public record PayrollDiscrepancyDto(
        long id,
        long fileId,
        PayrollEmployerRef employer,
        String period,
        String type,
        String label,
        String rule,
        String whatToDo,
        String status,
        String detail,
        Member member,
        Row row,
        String amountOnFile,
        String expectedAmount,
        String difference,
        Integer months,
        String queryAction,
        QueryRef query,
        List<String> outcomes,
        Resolution resolution,
        int noteCount,
        Note lastNote,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<Note> notes,
        String createdAt,
        int version) {

    /** The member on record it is about, with the employee number on record (set beside the file's). */
    public record Member(long id, String membershipNumber, String name, String status, String paymentType,
                         String staffNumber) {
    }

    /** The line of the file it is about, while the file's lines are kept. The ID number is masked. */
    public record Row(int rowNumber, String staffNumber, String idNumber, String initials, String surname,
                      String amount) {
    }

    /** The query list it went on. */
    public record QueryRef(long id, String raisedAt) {
    }

    /** How it was resolved, by whom and when. */
    public record Resolution(String outcome, String label, String by, String at) {
    }

    /** A note, as written. */
    public record Note(long id, String text, String by, String at) {
    }
}
