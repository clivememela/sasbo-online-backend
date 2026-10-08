package za.co.titandynamix.sasbo_online_backend.services;

import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.acb.AcbActor;
import za.co.titandynamix.sasbo_online_backend.acb.AcbLogging;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndBulkEmailDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndDeliveriesPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndDeliveryDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndRegionPackDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndRegionPacksDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndStatisticDto;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.monthend.MonthEndMailer;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository.Fact;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository.TakenMonth;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndListingsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndListingsRepository.MemberLine;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndListingsRepository.PackScope;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSendSettingsRepository.PackSet;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSentRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSentRepository.Delivery;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSentRepository.NewDelivery;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSentRepository.NewRegionPack;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSentRepository.RegionPack;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.utils.MonthRange;
import za.co.titandynamix.sasbo_online_backend.utils.SeedMemberNumbers;

import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * The regions' packs (October build; Stephne MEN-02, MEN-04, MEN-05, MEN-08): each Regional Secretary's pack of a taken
 * month -- the region's own information only, Johannesburg in two sets -- produced as a PDF and a workbook, then
 * downloaded or e-mailed, chosen each month, and every download and e-mail recorded: who, when, to which address.
 *
 * <ul>
 *   <li>MADE FROM A TAKEN MONTH, its newest take: a pack is the month as it was frozen, its listings dated by the
 *       month's own history. The open month has no pack;</li>
 *   <li>ONCE A TAKE AND A SET, unless asked again: a second ask -- a double click -- is the pack already made, held by
 *       the database ({@code month_end_region_packs_one_a_generation}), never by looking first;</li>
 *   <li>A REGION'S USER SEES THEIR REGION'S PACKS ONLY ({@code CallerScope}): another region's is answered as if it were
 *       not there;</li>
 *   <li>MAIL MAY BE OFF on a host: an e-mail is then refused {@code MAIL_NOT_CONFIGURED} -- recorded, said in a sentence
 *       -- and downloading still works. One recipient's refusal never stops the others';</li>
 *   <li>never posted on WhatsApp (SASBO, 5 Oct): the channels are download and e-mail.</li>
 * </ul>
 */
@Slf4j
@Service
public class MonthEndRegionPacks {

    public static final String PDF = "PDF";
    public static final String XLSX = "XLSX";

    /** The deliveries a pack's row shows; the full record is paged. */
    static final int DELIVERIES_SHOWN = 10;

    static final int PAGE_CEILING = 100;

    private final MonthEndTakes takes;
    private final MonthEndStatistics statistics;
    private final MonthEndFactsRepository facts;
    private final MonthEndListingsRepository listings;
    private final MonthEndSendSettings settings;
    private final MonthEndSentRepository sent;
    private final MonthEndRegionPackWriter writer;
    private final MonthEndMailer mailer;
    private final SasboEnvironment environment;
    private final SeedMemberNumbers seedNumbers;
    private final Clock clock;

    public MonthEndRegionPacks(MonthEndTakes takes, MonthEndStatistics statistics, MonthEndFactsRepository facts,
                               MonthEndListingsRepository listings, MonthEndSendSettings settings,
                               MonthEndSentRepository sent, MonthEndRegionPackWriter writer, MonthEndMailer mailer,
                               SasboEnvironment environment, SeedMemberNumbers seedNumbers, Clock clock) {
        this.takes = takes;
        this.statistics = statistics;
        this.facts = facts;
        this.listings = listings;
        this.settings = settings;
        this.sent = sent;
        this.writer = writer;
        this.mailer = mailer;
        this.environment = environment;
        this.seedNumbers = seedNumbers;
        this.clock = clock;
    }

    /** What producing gave: the pack, and whether this ask made it. */
    public record Produced(MonthEndRegionPackDto pack, boolean created) {
    }

    /** A file to hand over: its name, type, size and hash, and how to write it. */
    public record Download(String fileName, String mediaType, long bytes, String sha256, Consumer<OutputStream> body) {
    }

    // ------------------------------------------------------------------------------------------------ the month's

    /** The month's packs, a row a recipient in the caller's scope. */
    public MonthEndRegionPacksDto list(String month) {
        TakenMonth taken = taken(month);
        CallerScope scope = CallerScope.current();
        List<PackSet> sets = settings.visibleSets().stream().filter(PackSet::active).toList();
        Map<String, RegionPack> newest = new LinkedHashMap<>();
        for (RegionPack pack : sent.regionPacksOf(taken.id())) {
            newest.putIfAbsent(pack.setCode(), pack);
        }
        Map<Long, List<Delivery>> deliveries = new LinkedHashMap<>();
        for (Delivery d : sent.deliveriesOfPacks(newest.values().stream().map(RegionPack::id).toList(),
                DELIVERIES_SHOWN)) {
            deliveries.computeIfAbsent(d.packId(), k -> new ArrayList<>()).add(d);
        }
        List<MonthEndRegionPacksDto.SetRow> rows = new ArrayList<>();
        for (PackSet set : sets) {
            RegionPack pack = newest.get(set.code());
            List<MonthEndDeliveryDto> shown = pack == null ? List.of() : deliveries.getOrDefault(pack.id(), List.of())
                    .stream().limit(DELIVERIES_SHOWN).map(MonthEndRegionPacks::dto).toList();
            rows.add(new MonthEndRegionPacksDto.SetRow(MonthEndSendSettings.dto(set), pack == null ? null : dto(pack),
                    shown));
        }
        long unplaced = 0;
        MonthEndRegionPacksDto.Uncovered uncovered = new MonthEndRegionPacksDto.Uncovered(List.of(), 0);
        if (scope.isAll()) {
            List<Fact> rowsOfTheMonth = facts.facts(taken.id());
            unplaced = rowsOfTheMonth.stream().filter(f -> f.workplaceKey() == 0).mapToLong(Fact::onRecord).sum();
            uncovered = uncovered(rowsOfTheMonth, settings.allSets());
        }
        return new MonthEndRegionPacksDto(YearMonth.from(taken.monthStart()).toString(), take(taken),
                new MonthEndRegionPacksDto.Mail(mailer.configured()), statistics.grouped(), unplaced, uncovered, rows);
    }

    /** Areas with members on record at the month's end that no active recipient covers -- in no region's pack. */
    static MonthEndRegionPacksDto.Uncovered uncovered(List<Fact> rows, List<PackSet> sets) {
        Set<String> wholeRegions = new LinkedHashSet<>();
        Set<String> coveredAreas = new LinkedHashSet<>();
        for (PackSet set : sets) {
            if (!set.active()) {
                continue;
            }
            if (set.wholeRegion()) {
                wholeRegions.add(set.regionCode());
            } else {
                coveredAreas.addAll(set.areaLetters());
            }
        }
        Set<String> areas = new TreeSet<>();
        long members = 0;
        for (Fact f : rows) {
            if (f.workplaceKey() == 0 || f.onRecord() == 0) {
                continue;
            }
            boolean covered = f.regionCode() != null && wholeRegions.contains(f.regionCode())
                    || f.areaLetter() != null && coveredAreas.contains(f.areaLetter());
            if (!covered) {
                areas.add(f.areaLetter() == null ? "?" : f.areaLetter());
                members += f.onRecord();
            }
        }
        return new MonthEndRegionPacksDto.Uncovered(List.copyOf(areas), members);
    }

    // ------------------------------------------------------------------------------------------------ producing

    /** A recipient's pack of the month: the one made already, or -- the first time, or {@code again} -- a new one. */
    public Produced produce(String month, String setCode, boolean again) {
        return produce(month, setCode, again, false, AcbActor.current(), clock.instant());
    }

    /**
     * As above, by {@code actor}, made {@code at}; {@code waitForAPlace}: the seed waits for the figures' gate, a screen
     * does not. The seed dates its packs by the take they are made from, as a person would make them that morning.
     */
    Produced produce(String month, String setCode, boolean again, boolean waitForAPlace, String actor, Instant at) {
        PackSet set = settings.visibleSet(setCode);
        if (!set.active()) {
            throw new MonthEndRefusal(MonthEndRefusalCode.RECIPIENT_INACTIVE, "That recipient is switched off. Switch "
                    + "it on in the settings to make its pack.", MonthEndRefusal.details("set", set.code()));
        }
        if (!statistics.grouped()) {
            throw new MonthEndRefusal(MonthEndRefusalCode.REGION_PACKS_UNAVAILABLE, "This server does not hold SASBO's "
                    + "workplaces in full, so no member can be placed in a region and no region's pack can be made.",
                    Map.of());
        }
        TakenMonth taken = taken(month);
        Optional<RegionPack> made = sent.newestRegionPack(taken.id(), set.code());
        if (made.isPresent() && !again) {
            return new Produced(dto(made.get()), false);
        }
        YearMonth asked = YearMonth.from(taken.monthStart());
        Map<String, List<MonthEndStatisticDto>> places = statistics.forPack(asked.toString(), set.regionCode(),
                set.areaLetters(), waitForAPlace);
        MonthRange range = MonthRange.of(asked);
        PackScope scope = new PackScope(set.regionCode(), set.areaLetters());
        List<Fact> rows = facts.facts(taken.id()).stream().filter(f -> f.workplaceKey() != 0 && (set.wholeRegion()
                ? set.regionCode().equals(f.regionCode()) : set.areaLetters().contains(f.areaLetter()))).toList();
        List<MemberLine> joined = listings.joined(range, scope);
        List<MemberLine> left = listings.left(range, scope);
        List<MemberLine> suspended = listings.suspended(range, scope, seeded());
        Instant now = at;
        MonthEndRegionPackWriter.Content content = new MonthEndRegionPackWriter.Content(asked, taken.takeNumber(),
                taken.takenAt(), taken.takenBy(), set.code(), set.label(), set.regionCode(), set.areaLetters(),
                set.position(), places, rows, joined, left, suspended, actor, now);
        byte[] pdf = writer.pdf(content);
        byte[] xlsx = writer.xlsx(content);
        String stem = "sasbo-month-end-" + asked + "-" + set.code();
        NewRegionPack pack = new NewRegionPack(taken.id(), taken.monthStart(), set.code(), set.label(), set.regionCode(),
                set.areaLetters(), set.position(), joined.size(), left.size(), suspended.size(), stem + ".pdf", pdf,
                sha256(pdf), stem + ".xlsx", xlsx, sha256(xlsx), actor, now);
        long id;
        try {
            id = sent.insertRegionPack(pack, made.map(RegionPack::generation).orElse(0) + 1);
        } catch (DataIntegrityViolationException another) {
            // Two asks at once -- a double click: the generation's unique index decides, and the one made is the answer.
            return new Produced(dto(sent.newestRegionPack(taken.id(), set.code()).orElseThrow(() -> another)), false);
        }
        log.info("Month end: region pack {} produced ({} joined, {} left, {} suspended)", id, joined.size(),
                left.size(), suspended.size());
        return new Produced(dto(sent.regionPack(id).orElseThrow()), true);
    }

    // ------------------------------------------------------------------------------------------------ downloading

    /** One of a pack's two files, handed over -- and the download recorded, by whom and when. */
    public Download download(long id, String format) {
        RegionPack pack = visiblePack(id);
        boolean pdf = PDF.equalsIgnoreCase(format);
        if (!pdf && !XLSX.equalsIgnoreCase(format)) {
            throw packNotFound(id);
        }
        record(pack, "DOWNLOAD", List.of(pdf ? PDF : XLSX), null, "SENT", null);
        return new Download(pdf ? pack.pdfName() : pack.xlsxName(), pdf ? "application/pdf"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", pdf ? pack.pdfBytes()
                : pack.xlsxBytes(), pdf ? pack.pdfSha256() : pack.xlsxSha256(),
                out -> sent.streamRegionPack(id, pdf, out));
    }

    // ------------------------------------------------------------------------------------------------ e-mailing

    /** A pack e-mailed to its recipient, in the formats chosen -- every attempt recorded, sent or not. */
    public MonthEndDeliveryDto email(long id, List<String> formats) {
        return email(visiblePack(id), formats(formats));
    }

    private MonthEndDeliveryDto email(RegionPack pack, List<String> formats) {
        PackSet set = settings.visibleSet(pack.setCode());
        if (!mailer.configured()) {
            record(pack, "EMAIL", formats, set.email(), "REFUSED", MonthEndRefusalCode.MAIL_NOT_CONFIGURED.name());
            throw new MonthEndRefusal(MonthEndRefusalCode.MAIL_NOT_CONFIGURED, "This server has no mail set up, so "
                    + "nothing was sent. Download the pack and e-mail it yourself.", MonthEndRefusal.details("set",
                    set.code()));
        }
        if (set.email() == null) {
            record(pack, "EMAIL", formats, null, "REFUSED", MonthEndRefusalCode.RECIPIENT_HAS_NO_ADDRESS.name());
            throw new MonthEndRefusal(MonthEndRefusalCode.RECIPIENT_HAS_NO_ADDRESS, "This recipient has no e-mail "
                    + "address, so nothing was sent. Add one in the settings, or download the pack.",
                    MonthEndRefusal.details("set", set.code()));
        }
        List<MonthEndMailer.Attachment> attachments = new ArrayList<>();
        if (formats.contains(PDF)) {
            attachments.add(new MonthEndMailer.Attachment(pack.pdfName(), "application/pdf",
                    sent.regionPackBytes(pack.id(), true).orElseThrow()));
        }
        if (formats.contains(XLSX)) {
            attachments.add(new MonthEndMailer.Attachment(pack.xlsxName(),
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    sent.regionPackBytes(pack.id(), false).orElseThrow()));
        }
        YearMonth month = YearMonth.from(pack.monthStart());
        try {
            mailer.send(set.email(), "SASBO month-end pack -- " + MonthEndRegionPackWriter.monthName(month) + " -- "
                    + pack.setLabel(), body(pack, set, month), attachments);
        } catch (MailException | MessagingException failed) {
            // The class alone: a mail server's refusal quotes the address back (B8).
            log.warn("Month end: region pack {} could not be e-mailed", pack.id(), AcbLogging.scrubbed(failed));
            record(pack, "EMAIL", formats, set.email(), "FAILED", MonthEndRefusalCode.MAIL_FAILED.name());
            throw new MonthEndRefusal(MonthEndRefusalCode.MAIL_FAILED, "The mail server did not take the message, so "
                    + "it was not sent. Try again later, or download the pack.", MonthEndRefusal.details("set",
                    set.code()));
        }
        return dto(record(pack, "EMAIL", formats, set.email(), "SENT", null));
    }

    private static String body(RegionPack pack, PackSet set, YearMonth month) {
        return "Dear " + set.position() + ",\n\n"
                + "Please find attached SASBO's month-end pack for " + MonthEndRegionPackWriter.monthName(month) + ": "
                + pack.setLabel() + (pack.areaLetters().isEmpty() ? "" : ", areas "
                + String.join(", ", pack.areaLetters())) + ".\n\n"
                + "It holds your region's information only. It names members: keep it within SASBO, and do not "
                + "forward it or post it on WhatsApp.\n\n"
                + "SASBO Online\n";
    }

    /** The month's packs e-mailed, a recipient at a time -- one refusal never stops the others. */
    public MonthEndBulkEmailDto emailAll(String month, List<String> setCodes, List<String> formats) {
        List<String> chosen = formats(formats);
        TakenMonth taken = taken(month);
        List<PackSet> targets = new ArrayList<>();
        if (setCodes == null || setCodes.isEmpty()) {
            settings.visibleSets().stream().filter(PackSet::active).forEach(targets::add);
        } else {
            if (setCodes.size() > MonthEndSendSettings.SETS_CEILING) {
                throw new MonthEndRefusal(MonthEndRefusalCode.REQUEST_MALFORMED, "Name at most "
                        + MonthEndSendSettings.SETS_CEILING + " recipients.", Map.of("field", "sets"));
            }
            for (String code : new LinkedHashSet<>(setCodes)) {
                targets.add(settings.visibleSet(code));
            }
        }
        Map<String, Optional<RegionPack>> made = new LinkedHashMap<>();
        for (PackSet set : targets) {
            made.put(set.code(), sent.newestRegionPack(taken.id(), set.code()));
        }
        if (made.values().stream().noneMatch(Optional::isPresent)) {
            throw new MonthEndRefusal(MonthEndRefusalCode.PACK_NOT_PRODUCED, "None of these recipients' packs has been "
                    + "made yet, so nothing was sent. Make the packs first.", MonthEndRefusal.details("month",
                    YearMonth.from(taken.monthStart()).toString()));
        }
        List<MonthEndBulkEmailDto.Result> results = new ArrayList<>();
        for (PackSet set : targets) {
            Optional<RegionPack> pack = made.get(set.code());
            if (pack.isEmpty()) {
                results.add(new MonthEndBulkEmailDto.Result(set.code(), "REFUSED",
                        MonthEndRefusalCode.PACK_NOT_PRODUCED.name(), "This recipient's pack has not been made yet. "
                        + "Make it first.", null));
                continue;
            }
            try {
                results.add(new MonthEndBulkEmailDto.Result(set.code(), "SENT", null, "Sent.",
                        email(pack.get(), chosen)));
            } catch (MonthEndRefusal refused) {
                String outcome = refused.code() == MonthEndRefusalCode.MAIL_FAILED ? "FAILED" : "REFUSED";
                results.add(new MonthEndBulkEmailDto.Result(set.code(), outcome, refused.code().name(),
                        refused.getMessage(), sent.newestDeliveryOfPack(pack.get().id()).map(MonthEndRegionPacks::dto)
                        .orElse(null)));
            }
        }
        return new MonthEndBulkEmailDto(YearMonth.from(taken.monthStart()).toString(), results);
    }

    private static List<String> formats(List<String> asked) {
        if (asked == null || asked.isEmpty() || asked.size() > 2) {
            throw new MonthEndRefusal(MonthEndRefusalCode.REQUEST_MALFORMED, "Choose PDF, Excel or both.",
                    Map.of("field", "formats"));
        }
        Set<String> chosen = new LinkedHashSet<>();
        for (String format : asked) {
            String f = format == null ? "" : format.trim().toUpperCase(Locale.ROOT);
            if (!PDF.equals(f) && !XLSX.equals(f)) {
                throw new MonthEndRefusal(MonthEndRefusalCode.REQUEST_MALFORMED, "Choose PDF, Excel or both.",
                        Map.of("field", "formats"));
            }
            chosen.add(f);
        }
        return List.copyOf(chosen);
    }

    // ------------------------------------------------------------------------------------------------ the record

    /** A page of the month's deliveries, the newest first -- a region's user sees their own packs' alone. */
    public MonthEndDeliveriesPageDto deliveries(String month, int page, int size) {
        if (page < 0 || size < 1 || size > PAGE_CEILING) {
            throw new MonthEndRefusal(MonthEndRefusalCode.REQUEST_MALFORMED, "Ask for a page from 0, of 1 to "
                    + PAGE_CEILING + " rows.", Map.of("field", page < 0 ? "page" : "size"));
        }
        YearMonth asked = takes.month(month);
        Collection<String> scoped = CallerScope.current().isAll() ? null
                : settings.visibleSets().stream().map(PackSet::code).toList();
        List<MonthEndDeliveryDto> items = sent.deliveries(asked.atDay(1), scoped, page, size).stream()
                .map(MonthEndRegionPacks::dto).toList();
        return new MonthEndDeliveriesPageDto(asked.toString(), page, size, sent.countDeliveries(asked.atDay(1), scoped),
                items);
    }

    private Delivery record(RegionPack pack, String channel, List<String> formats, String address, String outcome,
                            String code) {
        String files = formats.stream().map(f -> PDF.equals(f) ? pack.pdfName() : pack.xlsxName())
                .reduce((a, b) -> a + ", " + b).orElse("");
        long id = sent.insertDelivery(new NewDelivery(pack.id(), null, pack.monthStart(), "REGION_PACK", pack.setCode(),
                pack.setLabel(), channel, formats, address, outcome, code, files, AcbActor.current(), clock.instant()));
        return sent.delivery(id).orElseThrow();
    }

    // ------------------------------------------------------------------------------------------------ helpers

    /** The month's newest take: a pack is made from what was frozen, and the open month has none. */
    TakenMonth taken(String month) {
        YearMonth asked = takes.month(month);
        return takes.taken(asked).orElseThrow(() -> new MonthEndRefusal(MonthEndRefusalCode.MONTH_NOT_TAKEN,
                "That month has not been taken, and a pack is made only from a month taken.",
                MonthEndRefusal.details("month", asked.toString(), "open", takes.openMonth().toString())));
    }

    private RegionPack visiblePack(long id) {
        RegionPack pack = sent.regionPack(id).orElseThrow(() -> packNotFound(id));
        if (!MonthEndSendSettings.visible(pack.regionCode(), pack.areaLetters(), CallerScope.current())) {
            throw packNotFound(id);
        }
        return pack;
    }

    private static MonthEndRefusal packNotFound(long id) {
        return new MonthEndRefusal(MonthEndRefusalCode.REGION_PACK_NOT_FOUND, "There is no such region's pack here.",
                MonthEndRefusal.details("pack", String.valueOf(id)));
    }

    private Collection<String> seeded() {
        return environment.isProduction() ? List.of() : seedNumbers.all();
    }

    static MonthEndRegionPacksDto.Take take(TakenMonth taken) {
        return new MonthEndRegionPacksDto.Take(taken.takeNumber(), taken.takenAt().toString(), taken.takenBy());
    }

    static MonthEndRegionPackDto dto(RegionPack p) {
        return new MonthEndRegionPackDto(p.id(), YearMonth.from(p.monthStart()).toString(), p.takeNumber(), p.setCode(),
                p.setLabel(), p.regionCode(), p.areaLetters(), p.position(), p.producedAt().toString(), p.producedBy(),
                new MonthEndRegionPackDto.Counts(p.joined(), p.left(), p.suspended()),
                List.of(new MonthEndRegionPackDto.PackFile(PDF, p.pdfName(), p.pdfBytes(), p.pdfSha256()),
                        new MonthEndRegionPackDto.PackFile(XLSX, p.xlsxName(), p.xlsxBytes(), p.xlsxSha256())));
    }

    static MonthEndDeliveryDto dto(Delivery d) {
        return new MonthEndDeliveryDto(d.id(), d.deliveredAt().toString(), d.deliveredBy(), d.kind(), d.recipientCode(),
                d.recipientName(), d.channel(), d.formats(), d.address(), d.outcome(), d.code(), d.fileName(),
                d.packId(), d.fileId());
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException(unavailable);
        }
    }
}
