package za.co.titandynamix.sasbo_online_backend.dto.monthend;

/**
 * A bank's or a provider's file, produced from a taken month and kept only encrypted (October build; Stephne MEN-05).
 * Fetched at {@code /api/month-end/files/{id}}. Dates are ISO text (MD16).
 *
 * @param kind       {@code BANK} or {@code PROVIDER}
 * @param format     {@code ZIP} or {@code XLSX}
 * @param protection {@code AES_256_ZIP} (7-Zip, WinZip or WinRAR open it) or {@code EXCEL_AES_256} (Excel asks for the
 *                   password)
 * @param leftOut    members the layout could not carry -- counted, never named
 */
public record MonthEndFileDto(long id, String month, int takeNumber, String kind, String recipientCode,
                              String recipientName, String layout, boolean fullList, String fileName, String format,
                              String protection, int rows, int newRows, int deletedRows, int leftOut, long bytes,
                              String sha256, String producedAt, String producedBy, long downloads,
                              String lastDeliveredAt) {
}
