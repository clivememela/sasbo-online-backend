package za.co.titandynamix.sasbo_online_backend.services;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.acb.AcbActor;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndBankDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndBankRequest;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndPackSetDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndPackSetRequest;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndProviderDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndProviderRequest;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndSendSettingsDto;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.monthend.MonthEndFileLayouts.BankLayout;
import za.co.titandynamix.sasbo_online_backend.monthend.MonthEndFileLayouts.ProviderLayout;
import za.co.titandynamix.sasbo_online_backend.monthend.MonthEndMailer;
import za.co.titandynamix.sasbo_online_backend.monthend.MonthEndSftp;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSendSettingsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSendSettingsRepository.Bank;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSendSettingsRepository.BankValues;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSendSettingsRepository.PackSet;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSendSettingsRepository.PackSetValues;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSendSettingsRepository.Provider;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;

import java.time.Clock;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Who gets what at month end (October build) -- kept as data and edited on screen by a holder of MONTH_END_SEND:
 * <ul>
 *   <li>THE RECIPIENTS OF THE REGIONS' PACKS (Stephne MEN-02, MEN-08): a region, and -- where it is split, as
 *       Johannesburg is ("2x files") -- the areas each set covers. Two active sets of a region never cover one area, and
 *       a whole-region set is its region's only active set: a member is in one pack, never two;</li>
 *   <li>EACH BANK'S FILE (MEN-32, MEN-33): its table codes -- a table code in one bank's file at most, by the database's
 *       own constraint -- the full list or the month's new and deleted, and its layout. Who gets a full list is a
 *       SETTING, not code: her list leaves Nedbank out against its August file, and MEN-32 says a full list goes only
 *       when a bank verifies, so the values are provisional and edited, never redeployed;</li>
 *   <li>EACH PROVIDER (MEN-35): switched on or off -- "currently suspended but it can be used again going forward".</li>
 * </ul>
 * Every save names the version the screen read; a stale one is refused SETTING_CHANGED_SINCE, naming who saved since.
 * A caller limited to a region or an area sees that region's recipients only, and changes nothing: a setting reaches
 * every region's files, so changing one needs a caller who sees every region (SCOPE_TOO_NARROW).
 */
@Service
public class MonthEndSendSettings {

    /** How many recipients and banks a server keeps at most -- SASBO has seven pack sets and about twenty banks. */
    public static final int SETS_CEILING = 20;
    public static final int BANKS_CEILING = 60;

    static final int AREAS_CEILING = 30;
    static final int TABLE_CODES_CEILING = 30;
    static final int LABEL_CEILING = 60;
    static final int EMAIL_CEILING = 254;

    private static final Pattern SET_CODE = Pattern.compile("[A-Z0-9]{1,8}");
    private static final Pattern BANK_CODE = Pattern.compile("[A-Z0-9_]{1,20}");
    private static final Pattern AREA = Pattern.compile("[A-Z]{1,4}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    /** The provider layouts that are ours until the provider's own arrives. */
    static final Set<String> PROVISIONAL_LAYOUTS = Set.of(ProviderLayout.PROVISIONAL_MEMBER_LIST.name(),
            ProviderLayout.OLD_MUTUAL_FUNERAL.name());

    private final MonthEndSendSettingsRepository settings;
    private final MonthEndMailer mailer;
    private final MonthEndSftp sftp;
    private final TransactionTemplate tx;
    private final Clock clock;

    public MonthEndSendSettings(MonthEndSendSettingsRepository settings, MonthEndMailer mailer, MonthEndSftp sftp,
                                PlatformTransactionManager transactions, Clock clock) {
        this.settings = settings;
        this.mailer = mailer;
        this.sftp = sftp;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clock;
    }

    // ------------------------------------------------------------------------------------------------ reads

    /** Everything the settings screen shows, cut to the caller's scope. */
    public MonthEndSendSettingsDto settings() {
        CallerScope scope = CallerScope.current();
        List<MonthEndPackSetDto> sets = settings.packSets().stream().filter(s -> visible(s, scope))
                .map(MonthEndSendSettings::dto).toList();
        List<MonthEndBankDto> banks = scope.isAll() ? settings.banks().stream().map(MonthEndSendSettings::dto).toList()
                : List.of();
        List<MonthEndProviderDto> providers = scope.isAll()
                ? settings.providers().stream().map(MonthEndSendSettings::dto).toList() : List.of();
        return new MonthEndSendSettingsDto(sets, banks, providers, new MonthEndSendSettingsDto.Mail(mailer.configured()),
                new MonthEndSendSettingsDto.Sftp(sftp.configured(), sftp.state()),
                new MonthEndSendSettingsDto.Layouts(Arrays.stream(BankLayout.values()).map(Enum::name).toList(),
                        Arrays.stream(ProviderLayout.values()).map(Enum::name).toList()),
                new MonthEndSendSettingsDto.Ceilings(SETS_CEILING, BANKS_CEILING));
    }

    /** The recipients the caller may see: every one for ALL, a region's for REGION, those within an area for AREA. */
    public List<PackSet> visibleSets() {
        CallerScope scope = CallerScope.current();
        return settings.packSets().stream().filter(s -> visible(s, scope)).toList();
    }

    /** One recipient the caller may see -- one outside their scope is answered as if it were not there. */
    public PackSet visibleSet(String code) {
        String wanted = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        return settings.packSet(wanted).filter(s -> visible(s, CallerScope.current()))
                .orElseThrow(() -> new MonthEndRefusal(MonthEndRefusalCode.PACK_SET_NOT_FOUND,
                        "There is no such recipient of a region's pack here.",
                        MonthEndRefusal.details("set", MonthEndRefusal.echo(code))));
    }

    /** Whether a pack made for {@code regionCode} and {@code areas} is the caller's to see. */
    public static boolean visible(String regionCode, List<String> areas, CallerScope scope) {
        return switch (scope.type()) {
            case ALL -> true;
            case REGION -> scope.allows(null, regionCode);
            case AREA -> !areas.isEmpty() && areas.stream().allMatch(a -> scope.allows(a, null));
        };
    }

    static boolean visible(PackSet set, CallerScope scope) {
        return visible(set.regionCode(), set.areaLetters(), scope);
    }

    /** Refuses a caller who does not see every region: a bank's or provider's file, and every setting, reach them all. */
    public static void requireEveryRegion(String what) {
        if (!CallerScope.current().isAll()) {
            throw new MonthEndRefusal(MonthEndRefusalCode.SCOPE_TOO_NARROW, what + " reaches members of every region, "
                    + "so it needs a user who sees every region. Ask head office.");
        }
    }

    // ------------------------------------------------------------------------------------------------ recipients

    /** A new recipient of a region's pack. */
    public MonthEndPackSetDto createSet(MonthEndPackSetRequest request) {
        requireEveryRegion("Changing who receives a pack");
        if (request == null) {
            throw invalidSet("code", "Send the recipient's code, label, region, position and address.");
        }
        String code = request.code() == null ? "" : request.code().trim().toUpperCase(Locale.ROOT);
        if (!SET_CODE.matcher(code).matches()) {
            throw invalidSet("code", "A recipient's code is one to eight capital letters or digits -- JHB2, say.");
        }
        PackSetValues values = setValues(request, true, null);
        if (settings.packSets().size() >= SETS_CEILING) {
            throw invalidSet("code", "This server keeps at most " + SETS_CEILING + " recipients of a region's pack.");
        }
        if (settings.packSet(code).isPresent()) {
            throw setTaken(code);
        }
        refuseOverlap(code, values);
        try {
            settings.insertPackSet(code, values, AcbActor.current(), clock.instant());
        } catch (DataIntegrityViolationException another) {
            // Two saves of one code at once: the unique code decides, and says so.
            throw setTaken(code);
        }
        return dto(settings.packSet(code).orElseThrow());
    }

    /** A recipient changed, naming the version the screen read. */
    public MonthEndPackSetDto updateSet(String code, MonthEndPackSetRequest request) {
        requireEveryRegion("Changing who receives a pack");
        PackSet current = visibleSet(code);
        if (request == null || request.version() == null) {
            throw invalidSet("version", "Send the version of the recipient the screen read.");
        }
        PackSetValues values = setValues(request, current.active(), current);
        refuseOverlap(current.code(), values);
        int changed = settings.updatePackSet(current.code(), request.version(), values, AcbActor.current(),
                clock.instant());
        PackSet now = settings.packSet(current.code()).orElseThrow();
        if (changed == 0) {
            throw changedSince(now.version(), now.updatedBy(), now.updatedAt().toString());
        }
        return dto(now);
    }

    private PackSetValues setValues(MonthEndPackSetRequest request, boolean activeByDefault, PackSet current) {
        String label = trimmed(request.label());
        if (label.isEmpty() || label.length() > LABEL_CEILING) {
            throw invalidSet("label", "A recipient's label is one to " + LABEL_CEILING + " characters.");
        }
        String region = trimmed(request.regionCode()).toUpperCase(Locale.ROOT);
        if (region.isEmpty() || region.length() > 8 || !settings.regionOnRecord(region)) {
            throw invalidSet("regionCode", "That region is not on SASBO's list here.");
        }
        List<String> areas = areas(request.areaLetters(), region);
        String position = trimmed(request.position());
        if (position.isEmpty() || position.length() > LABEL_CEILING) {
            throw invalidSet("position", "Name the position the pack goes to, in one to " + LABEL_CEILING
                    + " characters -- a position, such as Regional Secretary, never a person's name.");
        }
        String email = trimmed(request.email());
        if (!email.isEmpty() && (email.length() > EMAIL_CEILING || !EMAIL.matcher(email).matches())) {
            throw invalidSet("email", "That is not an e-mail address. Leave it empty to download the pack instead.");
        }
        boolean active = request.active() == null ? (current == null ? activeByDefault : current.active())
                : request.active();
        return new PackSetValues(label, region, areas, position, email.isEmpty() ? null : email, active);
    }

    private List<String> areas(List<String> given, String region) {
        if (given == null || given.isEmpty()) {
            return List.of();
        }
        if (given.size() > AREAS_CEILING) {
            throw invalidSet("areaLetters", "A recipient covers at most " + AREAS_CEILING + " areas.");
        }
        List<String> inRegion = settings.areasOf(region);
        Set<String> areas = new LinkedHashSet<>();
        for (String area : given) {
            String letter = trimmed(area).toUpperCase(Locale.ROOT);
            if (!AREA.matcher(letter).matches() || !inRegion.contains(letter)) {
                throw invalidSet("areaLetters", "Each area must be one of the region's own: "
                        + String.join(", ", inRegion) + ".");
            }
            areas.add(letter);
        }
        return areas.stream().sorted().toList();
    }

    /**
     * Two active sets of a region never cover one area, and a whole-region set is its region's only active set -- or a
     * member would be in two packs (MEN-08).
     */
    private void refuseOverlap(String code, PackSetValues values) {
        if (!values.active()) {
            return;
        }
        for (PackSet other : settings.packSets()) {
            if (other.code().equals(code) || !other.active() || !other.regionCode().equals(values.regionCode())) {
                continue;
            }
            if (other.wholeRegion() || values.areaLetters().isEmpty()) {
                throw overlap(values.areaLetters().isEmpty() ? "ALL" : values.areaLetters().get(0), other.code());
            }
            for (String area : values.areaLetters()) {
                if (other.areaLetters().contains(area)) {
                    throw overlap(area, other.code());
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ banks

    /** A new bank's monthly file. */
    public MonthEndBankDto createBank(MonthEndBankRequest request) {
        requireEveryRegion("Changing a bank's file");
        if (request == null) {
            throw invalidBank("code", "Send the bank's code, name, table codes, list and layout.");
        }
        String code = trimmed(request.code()).toUpperCase(Locale.ROOT);
        if (!BANK_CODE.matcher(code).matches()) {
            throw invalidBank("code", "A bank's code is one to twenty capital letters, digits or underscores -- "
                    + "STANDARD_BANK, say.");
        }
        BankValues values = bankValues(request, null);
        if (settings.banks().size() >= BANKS_CEILING) {
            throw invalidBank("code", "This server keeps at most " + BANKS_CEILING + " banks' files.");
        }
        if (settings.bank(code).isPresent()) {
            throw new MonthEndRefusal(MonthEndRefusalCode.BANK_TAKEN, "Another bank's file has that code. Nothing was "
                    + "saved.", MonthEndRefusal.details("code", code));
        }
        refuseHeldTableCodes(values.tableCodes(), code);
        saveBank(() -> {
            long id = settings.insertBank(code, values, AcbActor.current(), clock.instant());
            settings.replaceTableCodes(id, values.tableCodes());
        }, values.tableCodes(), code);
        return dto(settings.bank(code).orElseThrow());
    }

    /** A bank's file changed, naming the version the screen read. */
    public MonthEndBankDto updateBank(String code, MonthEndBankRequest request) {
        requireEveryRegion("Changing a bank's file");
        String wanted = trimmed(code).toUpperCase(Locale.ROOT);
        Bank current = settings.bank(wanted).orElseThrow(() -> new MonthEndRefusal(MonthEndRefusalCode.BANK_NOT_FOUND,
                "There is no such bank's file here.", MonthEndRefusal.details("bank", MonthEndRefusal.echo(code))));
        if (request == null || request.version() == null) {
            throw invalidBank("version", "Send the version of the bank's file the screen read.");
        }
        BankValues values = bankValues(request, current);
        refuseHeldTableCodes(values.tableCodes(), current.code());
        boolean[] saved = {false};
        saveBank(() -> {
            if (settings.updateBank(current.code(), request.version(), values, AcbActor.current(), clock.instant()) == 1) {
                settings.replaceTableCodes(current.id(), values.tableCodes());
                saved[0] = true;
            }
        }, values.tableCodes(), current.code());
        Bank now = settings.bank(current.code()).orElseThrow();
        if (!saved[0]) {
            throw changedSince(now.version(), now.updatedBy(), now.updatedAt().toString());
        }
        return dto(now);
    }

    private BankValues bankValues(MonthEndBankRequest request, Bank current) {
        String name = trimmed(request.name());
        if (name.isEmpty() || name.length() > LABEL_CEILING) {
            throw invalidBank("name", "A bank's name is one to " + LABEL_CEILING + " characters.");
        }
        List<Integer> codes = request.tableCodes() == null ? List.of()
                : request.tableCodes().stream().filter(c -> c != null).distinct().sorted().toList();
        if (codes.isEmpty() || codes.size() > TABLE_CODES_CEILING || codes.stream().anyMatch(c -> c <= 0)) {
            throw invalidBank("tableCodes", "A bank's file is made from one to " + TABLE_CODES_CEILING
                    + " of SASBO's table codes.");
        }
        Set<Integer> known = settings.tableCodesOnRecord(codes);
        List<Integer> unknown = codes.stream().filter(c -> !known.contains(c)).toList();
        if (!unknown.isEmpty()) {
            throw new MonthEndRefusal(MonthEndRefusalCode.BANK_SETTING_INVALID, "Table code " + unknown.get(0)
                    + " is not on SASBO's list here.", MonthEndRefusal.details("field", "tableCodes", "tableCode",
                    String.valueOf(unknown.get(0))));
        }
        String layout = trimmed(request.layout()).toUpperCase(Locale.ROOT);
        if (Arrays.stream(BankLayout.values()).noneMatch(l -> l.name().equals(layout))) {
            throw invalidBank("layout", "A bank's layout is one of " + Arrays.toString(BankLayout.values()) + ".");
        }
        boolean fullList = request.fullList() == null ? (current != null && current.fullList()) : request.fullList();
        if (BankLayout.ABSA_FIXED_WIDTH.name().equals(layout) && !fullList) {
            throw invalidBank("fullList", "Absa's record has no column that says new or deleted, so Absa's layout "
                    + "carries the full list only.");
        }
        boolean active = request.active() == null ? current == null || current.active() : request.active();
        return new BankValues(name, codes, fullList, layout, active);
    }

    private void refuseHeldTableCodes(List<Integer> codes, String bank) {
        Map<Integer, String> held = settings.heldElsewhere(codes, bank);
        if (!held.isEmpty()) {
            Map.Entry<Integer, String> first = held.entrySet().iterator().next();
            throw tableCodeTaken(first.getKey(), first.getValue());
        }
    }

    /** A bank and its table codes in one transaction; a table code another save took meanwhile is said so. */
    private void saveBank(Runnable save, List<Integer> codes, String bank) {
        try {
            tx.executeWithoutResult(status -> save.run());
        } catch (DataIntegrityViolationException another) {
            Map<Integer, String> held = settings.heldElsewhere(codes, bank);
            if (!held.isEmpty()) {
                Map.Entry<Integer, String> first = held.entrySet().iterator().next();
                throw tableCodeTaken(first.getKey(), first.getValue());
            }
            throw new MonthEndRefusal(MonthEndRefusalCode.BANK_TAKEN, "Another bank's file has that code. Nothing was "
                    + "saved.", MonthEndRefusal.details("code", bank));
        }
    }

    // ------------------------------------------------------------------------------------------------ providers

    /** A provider switched on or off, naming the version the screen read. */
    public MonthEndProviderDto updateProvider(String code, MonthEndProviderRequest request) {
        requireEveryRegion("Changing a provider's list");
        String wanted = trimmed(code).toUpperCase(Locale.ROOT);
        Provider current = settings.provider(wanted).orElseThrow(() -> new MonthEndRefusal(
                MonthEndRefusalCode.PROVIDER_NOT_FOUND, "There is no such provider here.",
                MonthEndRefusal.details("provider", MonthEndRefusal.echo(code))));
        if (request == null || request.version() == null || request.active() == null) {
            throw new MonthEndRefusal(MonthEndRefusalCode.REQUEST_MALFORMED, "Send whether the provider's list is "
                    + "switched on, and the version the screen read.", Map.of("field", request == null
                    || request.active() == null ? "active" : "version"));
        }
        int changed = settings.updateProviderActive(current.code(), request.version(), request.active(),
                AcbActor.current(), clock.instant());
        Provider now = settings.provider(current.code()).orElseThrow();
        if (changed == 0) {
            throw changedSince(now.version(), now.updatedBy(), now.updatedAt().toString());
        }
        return dto(now);
    }

    // ------------------------------------------------------------------------------------------------ for the others

    public Optional<Bank> bank(String code) {
        return settings.bank(trimmed(code).toUpperCase(Locale.ROOT));
    }

    public List<Bank> banks() {
        return settings.banks();
    }

    public Optional<Provider> provider(String code) {
        return settings.provider(trimmed(code).toUpperCase(Locale.ROOT));
    }

    public List<Provider> providers() {
        return settings.providers();
    }

    public List<PackSet> allSets() {
        return settings.packSets();
    }

    // ------------------------------------------------------------------------------------------------ the DTOs

    public static MonthEndPackSetDto dto(PackSet s) {
        return new MonthEndPackSetDto(s.code(), s.label(), s.regionCode(), s.regionName(), s.areaLetters(),
                s.position(), s.email(), s.active(), s.version(), s.updatedBy(), s.updatedAt().toString());
    }

    public static MonthEndBankDto dto(Bank b) {
        return new MonthEndBankDto(b.code(), b.name(), b.tableCodes(), b.fullList(), b.layout(), b.active(),
                b.version(), b.updatedBy(), b.updatedAt().toString());
    }

    public static MonthEndProviderDto dto(Provider p) {
        return new MonthEndProviderDto(p.code(), p.name(), p.layout(), PROVISIONAL_LAYOUTS.contains(p.layout()),
                p.delivery(), p.active(), p.version(), p.updatedBy(), p.updatedAt().toString());
    }

    // ------------------------------------------------------------------------------------------------ refusals

    private static MonthEndRefusal invalidSet(String field, String message) {
        return new MonthEndRefusal(MonthEndRefusalCode.PACK_SET_INVALID, message + " Nothing was saved.",
                Map.of("field", field));
    }

    private static MonthEndRefusal invalidBank(String field, String message) {
        return new MonthEndRefusal(MonthEndRefusalCode.BANK_SETTING_INVALID, message + " Nothing was saved.",
                Map.of("field", field));
    }

    private static MonthEndRefusal setTaken(String code) {
        return new MonthEndRefusal(MonthEndRefusalCode.PACK_SET_TAKEN, "Another recipient has that code. Nothing was "
                + "saved.", MonthEndRefusal.details("code", code));
    }

    private static MonthEndRefusal overlap(String area, String other) {
        return new MonthEndRefusal(MonthEndRefusalCode.PACK_SET_OVERLAP, "Recipient " + other + " already receives "
                + ("ALL".equals(area) ? "part of that region" : "area " + area) + ", and a member is in one pack only. "
                + "Change that recipient first, or switch it off. Nothing was saved.",
                MonthEndRefusal.details("area", area, "set", other));
    }

    private static MonthEndRefusal tableCodeTaken(int tableCode, String bank) {
        return new MonthEndRefusal(MonthEndRefusalCode.TABLE_CODE_TAKEN, "Table code " + tableCode + " is in "
                + bank + "'s file already, and a member is sent to one bank only. Nothing was saved.",
                MonthEndRefusal.details("tableCode", String.valueOf(tableCode), "bank", bank));
    }

    private static MonthEndRefusal changedSince(int version, String by, String at) {
        return new MonthEndRefusal(MonthEndRefusalCode.SETTING_CHANGED_SINCE, "Someone saved this since your screen "
                + "read it. Nothing was changed: look again, then save.", MonthEndRefusal.details("version",
                String.valueOf(version), "updatedBy", by, "updatedAt", at));
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
