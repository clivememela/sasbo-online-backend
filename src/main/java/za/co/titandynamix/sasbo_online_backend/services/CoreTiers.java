package za.co.titandynamix.sasbo_online_backend.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.titandynamix.sasbo_online_backend.acb.AcbActor;
import za.co.titandynamix.sasbo_online_backend.dto.reference.CoreTiersDto;
import za.co.titandynamix.sasbo_online_backend.dto.reference.TableCodeTierDto;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.SasboReferenceRepository;
import za.co.titandynamix.sasbo_online_backend.repository.SasboReferenceRepository.TableCodeTier;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;

/**
 * SASBO's core tiers (Stephne, MEN-31): "Decisions are made by Senior Secretariat. | Core A - Absa, FNB, Nedbank,
 * Standard, Capitec, oldmutual. | Core B - African, Landbank, SBV, SA Reserve, Santam, Ubank. | Core C - Access,
 * Bankserve, National Housing | All other are captured under individual institutions".
 *
 * <p>The tiers are V79's three rows; which table code stands in which is kept on the code, set here by an administrator
 * who maintains SASBO's lists -- or, provisionally, by the seeder from her Core List -- with who set it and when.
 */
@Service
public class CoreTiers {

    /** What a table code in no tier is: one of the individual institutions. */
    public static final String ALL_OTHER = "All other institutions";

    private final SasboReferenceRepository codes;
    private final Clock clock;

    public CoreTiers(SasboReferenceRepository codes, Clock clock) {
        this.codes = codes;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CoreTiersDto list() {
        return new CoreTiersDto(codes.coreTiers(), ALL_OTHER);
    }

    /**
     * Puts table code {@code code} in tier {@code tier} -- or in none, for all other institutions.
     *
     * @throws MonthEndRefusal {@code CORE_TIER_UNKNOWN}, {@code TABLE_CODE_UNKNOWN} -- nothing changed
     */
    @Transactional
    public TableCodeTierDto setTier(int code, String tier) {
        String letter = tier == null || tier.isBlank() ? null : tier.trim().toUpperCase(Locale.ROOT);
        Long tierId = letter == null ? null : codes.coreTierId(letter).orElseThrow(() -> new MonthEndRefusal(
                MonthEndRefusalCode.CORE_TIER_UNKNOWN, "A table code stands in Core A, B or C, or in none. Nothing "
                + "was changed.", MonthEndRefusal.details("tier", MonthEndRefusal.echo(letter), "known", "A,B,C")));
        if (codes.tableCodeTier(code).isEmpty()) {
            throw new MonthEndRefusal(MonthEndRefusalCode.TABLE_CODE_UNKNOWN, "No table code has that number here.",
                    Map.of("code", String.valueOf(code)));
        }
        codes.setTableCodeTier(code, tierId, AcbActor.current(), clock.instant());
        TableCodeTier set = codes.tableCodeTier(code).orElseThrow(() -> new IllegalStateException(
                "the table code was changed and cannot be read back in its own transaction"));
        return new TableCodeTierDto(set.code(), set.name(), set.coreTier(), set.setBy(),
                set.setAt() == null ? null : set.setAt().toString());
    }
}
