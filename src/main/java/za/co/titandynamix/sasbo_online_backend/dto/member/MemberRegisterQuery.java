package za.co.titandynamix.sasbo_online_backend.dto.member;

import java.util.ArrayList;
import java.util.List;

/**
 * What a page of the member register asks for (register row A10): the search box, the criteria, the page, its size and
 * its order. Every field may be left out -- an empty question is the register's first page, in surname order.
 *
 * <p>🔒 A POST BODY, NEVER A QUERY STRING. A search names a member -- a surname, an identity number's digits -- and this
 * server prints every request's query string into its log ({@code DispatcherServlet} at DEBUG writes the URI with its
 * query, whatever {@code log-request-details} says). A body is not printed. The Debits screen's search is a POST for
 * the same reason ({@code MemberSearchRequest}).
 *
 * @param q                 the search box: the start of a surname, a first name, a membership number (with or without
 *                          its {@code M-}) or an employee number, in any case -- or an identity number, whole or by its
 *                          last four
 * @param status            one of the statuses the record takes, exactly
 * @param bank              the bank where the member is employed, exactly as the picker offers it
 * @param branch            the branch, exactly
 * @param title             exactly
 * @param initials          the start of the initials, in any case
 * @param surname           the start of the surname, in any case
 * @param employeeNumber    the start of the employee number, in any case
 * @param membershipNumber  the start of the membership number, in any case
 * @param identityType      exactly
 * @param identityNumber    whole, or its last four
 * @param bankAccountNumber whole, or its last four
 * @param phone             the cellphone number, whole or by its last four
 * @param personalEmail     the start of the address, in any case
 * @param workEmail         the start of the address, in any case
 * @param race              exactly
 * @param page              from 0; a page past the last is answered as the last
 * @param size              1 to {@code MemberRegisterService.MAX_PAGE_SIZE}; 50 where none is named
 * @param sort              {@code key} or {@code key,asc|desc} -- one of {@code MemberRegisterService.SORTS}
 */
public record MemberRegisterQuery(
        String q,
        String status,
        String bank,
        String branch,
        String title,
        String initials,
        String surname,
        String employeeNumber,
        String membershipNumber,
        String identityType,
        String identityNumber,
        String bankAccountNumber,
        String phone,
        String personalEmail,
        String workEmail,
        String race,
        Integer page,
        Integer size,
        String sort) {

    /** The first page, in surname order, with nothing asked. */
    public static MemberRegisterQuery everyone() {
        return new MemberRegisterQuery(null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    /**
     * 🔒 Names the criteria that are set, never what they hold: a record's own {@code toString} prints every value, and
     * these are a member's name and numbers.
     */
    @Override
    public String toString() {
        List<String> asked = new ArrayList<>();
        String[] names = {"q", "status", "bank", "branch", "title", "initials", "surname", "employeeNumber",
                "membershipNumber", "identityType", "identityNumber", "bankAccountNumber", "phone", "personalEmail",
                "workEmail", "race"};
        String[] values = {q, status, bank, branch, title, initials, surname, employeeNumber, membershipNumber,
                identityType, identityNumber, bankAccountNumber, phone, personalEmail, workEmail, race};
        for (int i = 0; i < names.length; i++) {
            if (values[i] != null && !values[i].isBlank()) {
                asked.add(names[i]);
            }
        }
        return "MemberRegisterQuery[asked=" + asked + ", page=" + page + ", size=" + size + ", sort=" + sort + "]";
    }
}
