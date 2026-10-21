package za.co.titandynamix.sasbo_online_backend.dto.monthend;

/**
 * A benefit provider's monthly list (October build; Stephne MEN-35). {@code provisional}: the layout is ours until the
 * provider's own reaches us. {@code delivery}: {@code DOWNLOAD}, or {@code SFTP} onto the provider's server. Dates are
 * ISO text (MD16).
 */
public record MonthEndProviderDto(String code, String name, String layout, boolean provisional, String delivery,
                                  boolean active, int version, String updatedBy, String updatedAt) {
}
