package za.co.titandynamix.sasbo_online_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import za.co.titandynamix.sasbo_online_backend.acb.file.BureauFileSettings;

/**
 * The bureau file's settings, from {@code acb.file.*}.
 *
 * <p>The four identifiers have NO default -- they come from the environment
 * ({@code ACB_FILE_USER_CODE}, {@code ACB_FILE_USER_BRANCH}, {@code ACB_FILE_NOMINATED_ACCOUNT},
 * {@code ACB_FILE_BANK_IDENTIFIERS}), and until they are set a file refuses to render and names what
 * is missing. The constants default to what the July 2026 file carries.
 *
 * <p>🔒 {@link #toString()} is written by hand, so the union's account never reaches a log line
 * through a bound-properties dump.
 */
@ConfigurationProperties(prefix = "acb.file")
public record AcbFileProperties(
        @DefaultValue("SASBO") String userName,
        String userCode,
        String userBranch,
        String nominatedAccount,
        String bankIdentifiers,
        @DefaultValue("1001") String setNumber,
        @DefaultValue("000018") String headerCounter,
        @DefaultValue("SASBO") String userReference,
        @DefaultValue("TWO DAY") String serviceType,
        @DefaultValue("BEFT") String fileLabel) {

    public BureauFileSettings settings() {
        return new BureauFileSettings(userName, userCode, userBranch, nominatedAccount, bankIdentifiers,
                setNumber, headerCounter, userReference, serviceType, fileLabel);
    }

    @Override
    public String toString() {
        return "AcbFileProperties[" + settings() + "]";
    }
}
