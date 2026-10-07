package za.co.titandynamix.sasbo_online_backend.dto.monthend;

import java.util.List;

/**
 * What was sent to whom, and when (October build): a download, an e-mail or an SFTP upload of a region's pack or a
 * bank's or provider's file. {@code address}: the e-mail address for an e-mail, the server for an upload, none for a
 * download (the person is {@code by}). Dates are ISO text (MD16).
 *
 * @param kind    {@code REGION_PACK}, {@code BANK_FILE} or {@code PROVIDER_FILE}
 * @param channel {@code DOWNLOAD}, {@code EMAIL} or {@code SFTP}
 * @param outcome {@code SENT}, {@code REFUSED} or {@code FAILED}; {@code code} says why where it is not SENT
 */
public record MonthEndDeliveryDto(long id, String at, String by, String kind, String recipientCode,
                                  String recipientName, String channel, List<String> formats, String address,
                                  String outcome, String code, String fileName, Long packId, Long fileId) {
}
