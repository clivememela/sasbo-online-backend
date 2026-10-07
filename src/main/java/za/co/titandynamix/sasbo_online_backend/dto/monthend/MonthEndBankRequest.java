package za.co.titandynamix.sasbo_online_backend.dto.monthend;

import java.util.List;

/** A bank's monthly file, as saved: {@code code} on a create; {@code version} on an update. Checked by the service. */
public record MonthEndBankRequest(String code, String name, List<Integer> tableCodes, Boolean fullList, String layout,
                                  Boolean active, Integer version) {
}
