package za.co.titandynamix.sasbo_online_backend.dto.monthend;

import java.util.List;

/**
 * The month's files that leave SASBO (October build): each active bank and every provider, what a file made now would
 * hold, and the month's files already made, newest first. {@code noTableCode}: members paying by payroll deduction who
 * are in no bank's file -- no workplace, or a workplace with no table code. Dates are ISO text (MD16).
 */
public record MonthEndFilesDto(String month, MonthEndRegionPacksDto.Take take, MonthEndSendSettingsDto.Sftp sftp,
                               List<BankRow> banks, List<ProviderRow> providers, Preview noTableCode) {

    /** How many rows a file made now would hold. */
    public record Preview(long rows, long newRows, long deletedRows) {
    }

    /** A bank, its preview, and its files of the month. */
    public record BankRow(MonthEndBankDto bank, Preview preview, List<MonthEndFileDto> files) {
    }

    /** A provider, its preview, and its files of the month. */
    public record ProviderRow(MonthEndProviderDto provider, Preview preview, List<MonthEndFileDto> files) {
    }
}
