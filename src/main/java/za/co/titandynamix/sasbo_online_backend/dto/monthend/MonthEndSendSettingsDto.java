package za.co.titandynamix.sasbo_online_backend.dto.monthend;

import java.util.List;

/**
 * Who gets what at month end (October build): the recipients of the regions' packs within the caller's scope, each
 * bank's file and each provider's list (for a caller who sees every region), whether this server can send mail and
 * upload by SFTP, the layouts it knows and the ceilings it keeps.
 */
public record MonthEndSendSettingsDto(List<MonthEndPackSetDto> sets, List<MonthEndBankDto> banks,
                                      List<MonthEndProviderDto> providers, Mail mail, Sftp sftp, Layouts layouts,
                                      Ceilings ceilings) {

    /** Whether this server has mail set up: without it a pack is downloaded, never e-mailed. */
    public record Mail(boolean configured) {
    }

    /** Whether this server can upload a provider's list by SFTP: {@code CONFIGURED} or {@code NOT_CONFIGURED}. */
    public record Sftp(boolean configured, String state) {
    }

    /** The layouts a bank's file and a provider's list can take. */
    public record Layouts(List<String> banks, List<String> providers) {
    }

    /** How many recipients and banks this server keeps at most. */
    public record Ceilings(int sets, int banks) {
    }
}
