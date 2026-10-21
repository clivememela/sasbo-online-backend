package za.co.titandynamix.sasbo_online_backend.dto.monthend;

import java.util.List;

/**
 * What one bank is sent every month (October build; Stephne MEN-32, MEN-33, MEN-34): the table codes its file is made
 * from, the full list or the month's new and deleted members, and the layout. Dates are ISO text (MD16).
 *
 * @param fullList true: every member on record at the bank's table codes who pays by payroll deduction; false: the
 *                 month's new and deleted members only
 * @param layout   {@code ABSA_FIXED_WIDTH}, {@code SHEET} or {@code CSV}
 */
public record MonthEndBankDto(String code, String name, List<Integer> tableCodes, boolean fullList, String layout,
                              boolean active, int version, String updatedBy, String updatedAt) {
}
