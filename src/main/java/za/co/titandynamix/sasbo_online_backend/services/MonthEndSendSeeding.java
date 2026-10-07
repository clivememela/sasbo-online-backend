package za.co.titandynamix.sasbo_online_backend.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndFileDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndRegionPackDto;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSendSettingsRepository.Bank;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSendSettingsRepository.PackSet;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSendSettingsRepository.Provider;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSentRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSentRepository.NewDelivery;
import za.co.titandynamix.sasbo_online_backend.utils.DemonstrationMemberSeeder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

/**
 * The demonstration's packs and files of a taken month, made through the real services by
 * {@value DemonstrationMemberSeeder#SEED_ACTOR} (October build) -- called by {@code MonthEndSendSeeder}, never by a route.
 * Each pack and file is made as a person would make it; each failure is caught and said by its code, so one recipient
 * never stops the rest. The deliveries it records -- downloads, e-mails sent, one refused for want of mail -- are the
 * demonstration's record: nothing was sent, and the actor says it was the seed.
 *
 * <p>DATED BY THE TAKE, as a morning's work after it (SASBO starts at 06:30-07:00, MEN-40): the packs twenty minutes after
 * the month was taken, downloaded five minutes on and e-mailed ten; the banks' and providers' files forty minutes after,
 * downloaded five minutes on -- and never later than the clock: a month the seed takes at this start is dated now, and
 * its packs with it, never in the future.
 */
@Slf4j
@Component
public class MonthEndSendSeeding {

    private final MonthEndRegionPacks packs;
    private final MonthEndSentFiles files;
    private final MonthEndSendSettings settings;
    private final MonthEndSentRepository sent;
    private final Clock clock;

    public MonthEndSendSeeding(MonthEndRegionPacks packs, MonthEndSentFiles files, MonthEndSendSettings settings,
                               MonthEndSentRepository sent, Clock clock) {
        this.packs = packs;
        this.files = files;
        this.settings = settings;
        this.sent = sent;
        this.clock = clock;
    }

    /** {@code delay} after the take -- or now, where that would be later than the clock. */
    public Instant after(Instant takenAt, Duration delay) {
        Instant then = takenAt.plus(delay);
        Instant now = clock.instant();
        return then.isAfter(now) ? now : then;
    }

    static final Duration PACKS_MADE = Duration.ofMinutes(20);
    static final Duration PACKS_DOWNLOADED = Duration.ofMinutes(25);
    static final Duration PACKS_EMAILED = Duration.ofMinutes(30);
    static final Duration FILES_MADE = Duration.ofMinutes(40);
    static final Duration FILES_DELIVERED = Duration.ofMinutes(45);

    /**
     * Every active recipient's pack of {@code month}, taken {@code takenAt}, downloaded in both formats and e-mailed --
     * one refused for want of mail.
     */
    public int packs(String month, Instant takenAt, String mailRefusedFor) {
        int made = 0;
        for (PackSet set : settings.allSets()) {
            if (!set.active()) {
                continue;
            }
            try {
                MonthEndRegionPacks.Produced produced = packs.produce(month, set.code(), false, true,
                        DemonstrationMemberSeeder.SEED_ACTOR, after(takenAt, PACKS_MADE));
                if (!produced.created()) {
                    continue;
                }
                made++;
                MonthEndRegionPackDto pack = produced.pack();
                LocalDate monthStart = YearMonth.parse(pack.month()).atDay(1);
                for (MonthEndRegionPackDto.PackFile file : pack.files()) {
                    deliver(pack.id(), null, monthStart, "REGION_PACK", set, "DOWNLOAD", List.of(file.format()), null,
                            "SENT", null, file.name(), after(takenAt, PACKS_DOWNLOADED));
                }
                List<String> both = List.of(MonthEndRegionPacks.PDF, MonthEndRegionPacks.XLSX);
                String names = pack.files().get(0).name() + ", " + pack.files().get(1).name();
                if (set.code().equals(mailRefusedFor)) {
                    deliver(pack.id(), null, monthStart, "REGION_PACK", set, "EMAIL", both, set.email(), "REFUSED",
                            MonthEndRefusalCode.MAIL_NOT_CONFIGURED.name(), names, after(takenAt, PACKS_EMAILED));
                } else if (set.email() != null) {
                    deliver(pack.id(), null, monthStart, "REGION_PACK", set, "EMAIL", both, set.email(), "SENT", null,
                            names, after(takenAt, PACKS_EMAILED));
                }
            } catch (MonthEndRefusal refused) {
                log.info("Month end: a seeded region pack was not made ({})", refused.code());
            }
        }
        return made;
    }

    /** The files of {@code month}, taken {@code takenAt}, for the banks named and every active provider, each downloaded. */
    public int files(String month, Instant takenAt, Set<String> banks) {
        int made = 0;
        for (Bank bank : settings.banks()) {
            if (!bank.active() || !banks.contains(bank.code())) {
                continue;
            }
            try {
                MonthEndFileDto file = files.produceBank(month, bank.code(), DemonstrationMemberSeeder.SEED_ACTOR,
                        after(takenAt, FILES_MADE)).file();
                made++;
                deliver(null, file.id(), YearMonth.parse(file.month()).atDay(1), "BANK_FILE", bank.code(), bank.name(),
                        "DOWNLOAD", List.of(file.format()), null, "SENT", null, file.fileName(),
                        after(takenAt, FILES_DELIVERED));
            } catch (MonthEndRefusal refused) {
                log.info("Month end: a seeded bank file was not made ({})", refused.code());
            }
        }
        for (Provider provider : settings.providers()) {
            if (!provider.active()) {
                continue;
            }
            try {
                MonthEndFileDto file = files.produceProvider(month, provider.code(),
                        DemonstrationMemberSeeder.SEED_ACTOR, after(takenAt, FILES_MADE)).file();
                made++;
                LocalDate monthStart = YearMonth.parse(file.month()).atDay(1);
                if ("SFTP".equals(provider.delivery())) {
                    deliver(null, file.id(), monthStart, "PROVIDER_FILE", provider.code(), provider.name(), "SFTP",
                            List.of(file.format()), null, "REFUSED", MonthEndRefusalCode.SFTP_NOT_CONFIGURED.name(),
                            file.fileName(), after(takenAt, FILES_DELIVERED.minusMinutes(1)));
                }
                deliver(null, file.id(), monthStart, "PROVIDER_FILE", provider.code(), provider.name(), "DOWNLOAD",
                        List.of(file.format()), null, "SENT", null, file.fileName(), after(takenAt, FILES_DELIVERED));
            } catch (MonthEndRefusal refused) {
                log.info("Month end: a seeded provider file was not made ({})", refused.code());
            }
        }
        return made;
    }

    private void deliver(Long packId, Long fileId, LocalDate monthStart, String kind, PackSet set, String channel,
                         List<String> formats, String address, String outcome, String code, String fileName,
                         Instant at) {
        deliver(packId, fileId, monthStart, kind, set.code(), set.label(), channel, formats, address, outcome, code,
                fileName, at);
    }

    private void deliver(Long packId, Long fileId, LocalDate monthStart, String kind, String recipient, String name,
                         String channel, List<String> formats, String address, String outcome, String code,
                         String fileName, Instant at) {
        sent.insertDelivery(new NewDelivery(packId, fileId, monthStart, kind, recipient, name, channel, formats, address,
                outcome, code, fileName, DemonstrationMemberSeeder.SEED_ACTOR, at));
    }
}
