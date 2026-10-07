package za.co.titandynamix.sasbo_online_backend.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.acb.AcbActor;
import za.co.titandynamix.sasbo_online_backend.acb.AcbLogging;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndDeliveryDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndFileDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndFilesDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndProducedFileDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndSendSettingsDto;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.monthend.MonthEndFileLayouts;
import za.co.titandynamix.sasbo_online_backend.monthend.MonthEndFileLayouts.AbsaFile;
import za.co.titandynamix.sasbo_online_backend.monthend.MonthEndFileLayouts.BankLayout;
import za.co.titandynamix.sasbo_online_backend.monthend.MonthEndFileLayouts.Counts;
import za.co.titandynamix.sasbo_online_backend.monthend.MonthEndFileLayouts.CsvFile;
import za.co.titandynamix.sasbo_online_backend.monthend.MonthEndFileLayouts.ProviderLayout;
import za.co.titandynamix.sasbo_online_backend.monthend.MonthEndFileLayouts.SheetFile;
import za.co.titandynamix.sasbo_online_backend.monthend.MonthEndFileProtection;
import za.co.titandynamix.sasbo_online_backend.monthend.MonthEndSftp;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository.TakenMonth;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndListingsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndListingsRepository.MemberLine;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndListingsRepository.TableCodeRows;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSendSettingsRepository.Bank;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSendSettingsRepository.Provider;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSentRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSentRepository.Delivery;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSentRepository.NewDelivery;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSentRepository.NewFile;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSentRepository.SentFile;
import za.co.titandynamix.sasbo_online_backend.utils.MonthRange;
import za.co.titandynamix.sasbo_online_backend.utils.SeedMemberNumbers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The files that leave SASBO each month for the banks and the benefit providers (October build) -- every one
 * PASSWORD-PROTECTED (Stephne MEN-05: "a requirement for POPIA"), produced from a taken month, and every download and
 * upload recorded.
 *
 * <ul>
 *   <li>A BANK'S FILE carries the members it deducts for -- at its table codes, paying by payroll deduction (MEN-32:
 *       "Only payroll deduction instructions are send to the Bank") -- the full list or the month's new and deleted, as
 *       the bank's setting says (MEN-33), in its layout ({@link MonthEndFileLayouts});</li>
 *   <li>A PROVIDER'S LIST carries every member paying at the month's end, provisionally (MEN-35); Law Call's goes onto the
 *       provider's SFTP server where one is set up, and is downloaded until then;</li>
 *   <li>A FILE IS ENCRYPTED AS IT IS WRITTEN, with a password made for it ({@link MonthEndFileProtection}) and handed
 *       back once, in the answer that produced it -- never stored, never logged. The rows stream from the database into
 *       the encryption a member at a time; the clear file is never kept, even in memory whole;</li>
 *   <li>EVERY REGION'S MEMBERS are in these files, so producing, downloading and uploading one needs a caller who sees
 *       every region ({@code SCOPE_TOO_NARROW});</li>
 *   <li>where the production marker is set, a member a seed file lists is never written -- counted as left out, as A.C.B.
 *       never puts a seeded member in a bureau file.</li>
 * </ul>
 */
@Slf4j
@Service
public class MonthEndSentFiles {

    static final String ZIP = "ZIP";
    static final String XLSX = "XLSX";
    static final String AES_256_ZIP = "AES_256_ZIP";
    static final String EXCEL_AES_256 = "EXCEL_AES_256";

    /** SASBO's own file names end in the month's last day: NEDBANK_260831.XLSX for August 2026. */
    static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyMMdd");

    /** The files a recipient's row shows; the month's record of deliveries is paged. */
    static final int FILES_SHOWN = 5;

    static final String PASSWORD_NOTE = "This password is shown once and is not kept. Give it to the bank by another "
            + "channel -- telephone or SMS -- never in the e-mail that carries the file. Type it exactly as shown, "
            + "hyphens included. Lost, produce the file again: a new file, with a new password.";

    private final MonthEndRegionPacks packs;
    private final MonthEndSendSettings settings;
    private final MonthEndListingsRepository listings;
    private final MonthEndSentRepository sent;
    private final MonthEndSftp sftp;
    private final SasboEnvironment environment;
    private final SeedMemberNumbers seedNumbers;
    private final TransactionTemplate readOnly;
    private final Clock clock;

    public MonthEndSentFiles(MonthEndRegionPacks packs, MonthEndSendSettings settings,
                             MonthEndListingsRepository listings, MonthEndSentRepository sent, MonthEndSftp sftp,
                             SasboEnvironment environment, SeedMemberNumbers seedNumbers,
                             PlatformTransactionManager transactions, Clock clock) {
        this.packs = packs;
        this.settings = settings;
        this.listings = listings;
        this.sent = sent;
        this.sftp = sftp;
        this.environment = environment;
        this.seedNumbers = seedNumbers;
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.setReadOnly(true);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.readOnly = template;
        this.clock = clock;
    }

    // ------------------------------------------------------------------------------------------------ the month's

    /** The month's banks and providers: what a file made now would hold, and the files already made. */
    public MonthEndFilesDto list(String month) {
        MonthEndSendSettings.requireEveryRegion("A bank's or a provider's file");
        TakenMonth taken = packs.taken(month);
        MonthRange range = MonthRange.of(YearMonth.from(taken.monthStart()));
        Map<Integer, TableCodeRows> byCode = new HashMap<>();
        TableCodeRows none = new TableCodeRows(null, 0, 0, 0, 0);
        long paying = 0;
        for (TableCodeRows rows : listings.rowsByTableCode(range, seeded())) {
            paying += rows.paying();
            if (rows.tableCode() == null) {
                none = rows;
            } else {
                byCode.put(rows.tableCode(), rows);
            }
        }
        Map<String, List<SentFile>> files = new LinkedHashMap<>();
        for (SentFile file : sent.filesOf(taken.id())) {
            files.computeIfAbsent(file.kind() + ":" + file.recipientCode(), k -> new ArrayList<>()).add(file);
        }
        List<MonthEndFilesDto.BankRow> banks = new ArrayList<>();
        for (Bank bank : settings.banks()) {
            long full = 0;
            long added = 0;
            long deleted = 0;
            for (Integer code : bank.tableCodes()) {
                TableCodeRows rows = byCode.get(code);
                if (rows != null) {
                    full += rows.fullRows();
                    added += rows.newRows();
                    deleted += rows.deletedRows();
                }
            }
            MonthEndFilesDto.Preview preview = bank.fullList() ? new MonthEndFilesDto.Preview(full, 0, 0)
                    : new MonthEndFilesDto.Preview(added + deleted, added, deleted);
            banks.add(new MonthEndFilesDto.BankRow(MonthEndSendSettings.dto(bank), preview,
                    shown(files.get("BANK:" + bank.code()))));
        }
        List<MonthEndFilesDto.ProviderRow> providers = new ArrayList<>();
        for (Provider provider : settings.providers()) {
            providers.add(new MonthEndFilesDto.ProviderRow(MonthEndSendSettings.dto(provider),
                    new MonthEndFilesDto.Preview(paying, 0, 0), shown(files.get("PROVIDER:" + provider.code()))));
        }
        return new MonthEndFilesDto(YearMonth.from(taken.monthStart()).toString(), MonthEndRegionPacks.take(taken),
                new MonthEndSendSettingsDto.Sftp(sftp.configured(), sftp.state()), banks, providers,
                new MonthEndFilesDto.Preview(none.fullRows(), none.newRows(), none.deletedRows()));
    }

    private static List<MonthEndFileDto> shown(List<SentFile> files) {
        return files == null ? List.of() : files.stream().limit(FILES_SHOWN).map(MonthEndSentFiles::dto).toList();
    }

    // ------------------------------------------------------------------------------------------------ producing

    /** A bank's file of the month, encrypted, and its password -- in this answer only. */
    public MonthEndProducedFileDto produceBank(String month, String code) {
        return produceBank(month, code, AcbActor.current(), clock.instant());
    }

    /** As above, by {@code actor}, made {@code at} -- the seed dates its files by the take they are made from. */
    MonthEndProducedFileDto produceBank(String month, String code, String actor, Instant at) {
        MonthEndSendSettings.requireEveryRegion("A bank's file");
        Bank bank = settings.bank(code).orElseThrow(() -> new MonthEndRefusal(MonthEndRefusalCode.BANK_NOT_FOUND,
                "There is no such bank's file here.", MonthEndRefusal.details("bank", MonthEndRefusal.echo(code))));
        if (!bank.active()) {
            throw inactive("bank", bank.code());
        }
        TakenMonth taken = packs.taken(month);
        YearMonth asked = YearMonth.from(taken.monthStart());
        MonthRange range = MonthRange.of(asked);
        String stem = bank.code().replace("_", "") + (bank.fullList() ? "FULL" : "") + "_" + stamp(range);
        BankLayout layout = BankLayout.valueOf(bank.layout());
        String password = MonthEndFileProtection.newPassword();
        Instant now = at;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Counts[] counts = new Counts[1];
        readOnly.executeWithoutResult(status -> {
            switch (layout) {
                case ABSA_FIXED_WIDTH -> MonthEndFileProtection.aesZip(out, password, stem + ".txt", now.toEpochMilli(),
                        zip -> {
                            AbsaFile absa = new AbsaFile(zip, leaveOut());
                            listings.fullList(range, bank.tableCodes(), seeded(), absa);
                            counts[0] = absa.finish();
                        });
                case SHEET -> {
                    try (SheetFile sheet = new SheetFile(bank.fullList() ? "Full list" : "New and deleted",
                            bank.fullList() ? MonthEndFileLayouts.fullListTitle(range.firstDayOfNext().minusDays(1))
                                    : MonthEndFileLayouts.newAndDeletedTitle(bank.name(),
                                    range.firstDayOfNext().minusDays(1)),
                            bank.fullList() ? MonthEndFileLayouts.FULL_LIST_COLUMNS
                                    : MonthEndFileLayouts.NEW_AND_DELETED_COLUMNS)) {
                        counts[0] = bankRows(bank, range, sheet::row);
                        MonthEndFileProtection.excelEncrypted(out, password, sheet::writeTo);
                    }
                }
                case CSV -> MonthEndFileProtection.aesZip(out, password, stem + ".csv", now.toEpochMilli(), zip -> {
                    CsvFile csv = new CsvFile(zip, bank.fullList() ? MonthEndFileLayouts.FULL_LIST_COLUMNS
                            : MonthEndFileLayouts.NEW_AND_DELETED_COLUMNS);
                    counts[0] = bankRows(bank, range, csv::row);
                    csv.finish();
                });
            }
        });
        boolean zipped = layout != BankLayout.SHEET;
        return stored(taken, "BANK", bank.code(), bank.name(), bank.layout(), bank.fullList(),
                stem + (zipped ? ".zip" : ".xlsx"), zipped, counts[0], out.toByteArray(), password, actor, now);
    }

    /** A bank's rows: its full list, or its new and then its deleted members, a row each through {@code row}. */
    private Counts bankRows(Bank bank, MonthRange range, java.util.function.Consumer<List<String>> row) {
        Set<String> leaveOut = leaveOut();
        int[] n = new int[4];
        if (bank.fullList()) {
            listings.fullList(range, bank.tableCodes(), seeded(), m -> {
                if (leaveOut.contains(m.membershipNumber())) {
                    n[3]++;
                    return;
                }
                row.accept(MonthEndFileLayouts.fullListRow(m));
                n[0]++;
            });
        } else {
            listings.newRows(range, bank.tableCodes(), m -> {
                if (leaveOut.contains(m.membershipNumber())) {
                    n[3]++;
                    return;
                }
                row.accept(MonthEndFileLayouts.newAndDeletedRow(m));
                n[0]++;
                n[1]++;
            });
            listings.deletedRows(range, bank.tableCodes(), m -> {
                if (leaveOut.contains(m.membershipNumber())) {
                    n[3]++;
                    return;
                }
                row.accept(MonthEndFileLayouts.newAndDeletedRow(m));
                n[0]++;
                n[2]++;
            });
        }
        return new Counts(n[0], n[1], n[2], n[3]);
    }

    /** A provider's list of the month, encrypted, and its password -- in this answer only. */
    public MonthEndProducedFileDto produceProvider(String month, String code) {
        return produceProvider(month, code, AcbActor.current(), clock.instant());
    }

    /** As above, by {@code actor}, made {@code at}. */
    MonthEndProducedFileDto produceProvider(String month, String code, String actor, Instant at) {
        MonthEndSendSettings.requireEveryRegion("A provider's list");
        Provider provider = settings.provider(code).orElseThrow(() -> new MonthEndRefusal(
                MonthEndRefusalCode.PROVIDER_NOT_FOUND, "There is no such provider here.",
                MonthEndRefusal.details("provider", MonthEndRefusal.echo(code))));
        if (!provider.active()) {
            throw inactive("provider", provider.code());
        }
        TakenMonth taken = packs.taken(month);
        MonthRange range = MonthRange.of(YearMonth.from(taken.monthStart()));
        String stem = provider.code().replace("_", "") + "_" + stamp(range);
        ProviderLayout layout = ProviderLayout.valueOf(provider.layout());
        String password = MonthEndFileProtection.newPassword();
        Instant now = at;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Set<String> leaveOut = leaveOut();
        int[] n = new int[2];
        readOnly.executeWithoutResult(status -> {
            switch (layout) {
                case OLD_MUTUAL_FUNERAL -> {
                    try (SheetFile sheet = new SheetFile("Sheet1", null, MonthEndFileLayouts.OLD_MUTUAL_COLUMNS)) {
                        listings.paying(range, seeded(), m -> provide(m, leaveOut, n,
                                () -> sheet.row(MonthEndFileLayouts.oldMutualRow(m))));
                        MonthEndFileProtection.excelEncrypted(out, password, sheet::writeTo);
                    }
                }
                case PROVISIONAL_MEMBER_LIST -> MonthEndFileProtection.aesZip(out, password, stem + ".csv",
                        now.toEpochMilli(), zip -> {
                            CsvFile csv = new CsvFile(zip, MonthEndFileLayouts.PROVISIONAL_COLUMNS);
                            listings.paying(range, seeded(), m -> provide(m, leaveOut, n,
                                    () -> csv.row(MonthEndFileLayouts.provisionalRow(m))));
                            csv.finish();
                        });
            }
        });
        boolean zipped = layout != ProviderLayout.OLD_MUTUAL_FUNERAL;
        return stored(taken, "PROVIDER", provider.code(), provider.name(), provider.layout(), true,
                stem + (zipped ? ".zip" : ".xlsx"), zipped, new Counts(n[0], 0, 0, n[1]), out.toByteArray(), password,
                actor, now);
    }

    private static void provide(MemberLine m, Set<String> leaveOut, int[] n, Runnable write) {
        if (leaveOut.contains(m.membershipNumber())) {
            n[1]++;
            return;
        }
        write.run();
        n[0]++;
    }

    private MonthEndProducedFileDto stored(TakenMonth taken, String kind, String code, String name, String layout,
                                           boolean fullList, String fileName, boolean zipped, Counts counts,
                                           byte[] content, String password, String actor, Instant now) {
        long id = sent.insertFile(new NewFile(taken.id(), taken.monthStart(), kind, code, name, layout, fullList,
                fileName, zipped ? ZIP : XLSX, zipped ? AES_256_ZIP : EXCEL_AES_256, counts.rows(), counts.newRows(),
                counts.deletedRows(), counts.leftOut(), content, MonthEndRegionPacks.sha256(content), actor, now));
        // Counts, never the password, never a member: the file is encrypted and its password is in the answer alone.
        log.info("Month end: file {} produced ({} rows, {} left out)", id, counts.rows(), counts.leftOut());
        return new MonthEndProducedFileDto(dto(sent.file(id).orElseThrow()), password, PASSWORD_NOTE);
    }

    // ------------------------------------------------------------------------------------------------ handing over

    /** The encrypted file, handed over -- and the download recorded, by whom and when. */
    public MonthEndRegionPacks.Download download(long id) {
        MonthEndSendSettings.requireEveryRegion("A bank's or a provider's file");
        SentFile file = file(id);
        record(file, "DOWNLOAD", null, "SENT", null);
        return new MonthEndRegionPacks.Download(file.fileName(), ZIP.equals(file.format()) ? "application/zip"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", file.bytes(), file.sha256(),
                out -> sent.streamFile(id, out));
    }

    /** A provider's file put onto its SFTP server -- refused, recorded, where none is set up. */
    public MonthEndDeliveryDto upload(long id) {
        MonthEndSendSettings.requireEveryRegion("A provider's list");
        SentFile file = file(id);
        Provider provider = "PROVIDER".equals(file.kind()) ? settings.provider(file.recipientCode()).orElse(null) : null;
        if (provider == null || !"SFTP".equals(provider.delivery())) {
            throw new MonthEndRefusal(MonthEndRefusalCode.SFTP_NOT_FOR_THIS_RECIPIENT, "This file is not sent by SFTP: "
                    + "download it instead.", MonthEndRefusal.details("file", String.valueOf(id)));
        }
        if (!sftp.configured()) {
            record(file, "SFTP", null, "REFUSED", MonthEndRefusalCode.SFTP_NOT_CONFIGURED.name());
            throw new MonthEndRefusal(MonthEndRefusalCode.SFTP_NOT_CONFIGURED, "No SFTP server is set up on this "
                    + "server, so nothing was sent. Download the file and send it the way it went before.",
                    MonthEndRefusal.details("file", String.valueOf(id)));
        }
        try {
            sftp.upload(file.fileName(), sent.fileBytes(id).orElseThrow());
        } catch (IOException | RuntimeException failed) {
            log.warn("Month end: file {} could not be uploaded", id, AcbLogging.scrubbed(failed));
            record(file, "SFTP", sftp.destination(), "FAILED", MonthEndRefusalCode.SFTP_FAILED.name());
            throw new MonthEndRefusal(MonthEndRefusalCode.SFTP_FAILED, "The SFTP server did not take the file, so it "
                    + "was not sent. Try again later, or download it.", MonthEndRefusal.details("file",
                    String.valueOf(id)));
        }
        return MonthEndRegionPacks.dto(record(file, "SFTP", sftp.destination(), "SENT", null));
    }

    private SentFile file(long id) {
        return sent.file(id).orElseThrow(() -> new MonthEndRefusal(MonthEndRefusalCode.FILE_NOT_FOUND,
                "There is no such file here.", MonthEndRefusal.details("file", String.valueOf(id))));
    }

    private Delivery record(SentFile file, String channel, String address, String outcome, String code) {
        long id = sent.insertDelivery(new NewDelivery(null, file.id(), file.monthStart(),
                "BANK".equals(file.kind()) ? "BANK_FILE" : "PROVIDER_FILE", file.recipientCode(), file.recipientName(),
                channel, List.of(file.format()), address, outcome, code, file.fileName(), AcbActor.current(),
                clock.instant()));
        return sent.delivery(id).orElseThrow();
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static String stamp(MonthRange range) {
        LocalDate last = range.firstDayOfNext().minusDays(1);
        return last.format(STAMP);
    }

    /** Where the production marker is set, the members a seed file lists: never written to a file that leaves SASBO. */
    private Set<String> leaveOut() {
        return environment.isProduction() ? seedNumbers.all() : Set.of();
    }

    private Collection<String> seeded() {
        return environment.isProduction() ? List.of() : seedNumbers.all();
    }

    private static MonthEndRefusal inactive(String what, String code) {
        return new MonthEndRefusal(MonthEndRefusalCode.RECIPIENT_INACTIVE, "That " + what + "'s file is switched off. "
                + "Switch it on in the settings to make it.", MonthEndRefusal.details(what, code));
    }

    static MonthEndFileDto dto(SentFile f) {
        return new MonthEndFileDto(f.id(), YearMonth.from(f.monthStart()).toString(), f.takeNumber(), f.kind(),
                f.recipientCode(), f.recipientName(), f.layout(), f.fullList(), f.fileName(), f.format(), f.protection(),
                f.rows(), f.newRows(), f.deletedRows(), f.leftOut(), f.bytes(), f.sha256(), f.producedAt().toString(),
                f.producedBy(), f.downloads(), f.lastDeliveredAt() == null ? null : f.lastDeliveredAt().toString());
    }
}
