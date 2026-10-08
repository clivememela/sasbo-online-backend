package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndProducedFileDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndRegionPackDto;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndListingsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndListingsRepository.PackScope;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSendSettingsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSentRepository;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSwitchedOnIT;
import za.co.titandynamix.sasbo_online_backend.support.SendFixture;
import za.co.titandynamix.sasbo_online_backend.utils.MonthRange;

import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The files that leave SASBO on PostgreSQL (October build): the database's own rules, by name -- a file kept only
 * encrypted, a table code in one bank's file, a pack once a take and a set, the record of what was sent append-only --
 * the regions cut in SQL, never another region's member in a pack; and the time a region's pack of TWENTY THOUSAND
 * members takes, with its bank's full list (both stated, both printed to the test's output).
 */
@DisplayName("The files that leave SASBO, on PostgreSQL")
class MonthEndSendIT extends AbstractSwitchedOnIT {

    /** The bound a region's pack of twenty thousand members, and its bank's full list, are held to. */
    static final Duration BOUND = Duration.ofSeconds(60);

    static final int GENERATED = 20_000;

    @Autowired
    private MemberRepository members;

    @Autowired
    private MonthEndTakes takes;

    @Autowired
    private MonthEndRegionPacks packs;

    @Autowired
    private MonthEndSentFiles files;

    @Autowired
    private MonthEndListingsRepository listings;

    @Autowired
    private MonthEndSendSettingsRepository settings;

    @Autowired
    private MonthEndSentRepository sent;

    @BeforeEach
    void thePopulation() {
        SendFixture.insert(jdbc, members);
        SendFixture.settings(jdbc);
    }

    // ------------------------------------------------------------------------------------------------ the rules

    @Test
    @DisplayName("🔒 MEN-05: a file that is not protected is refused by month_end_files_protected -- a workbook must be Excel's, a ZIP AES-256")
    void filesAreKeptOnlyEncrypted() {
        long take = takes.take(SendFixture.TAKEN, "seed").id();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO month_end_files (month_id, month_start, kind, recipient_code, recipient_name, layout,
                  full_list, file_name, format, protection, rows_written, new_rows, deleted_rows, left_out, content,
                  sha256, produced_by, produced_at)
                VALUES (?, DATE '2026-08-01', 'BANK', 'INVENTED_BANK', 'Invented Bank', 'SHEET', TRUE, 'x.xlsx', 'XLSX',
                  'AES_256_ZIP', 0, 0, 0, 0, '\\x00', 'none', 'system', now())
                """, take)).hasMessageContaining("month_end_files_protected");
    }

    @Test
    @DisplayName("🔒 a table code is in one bank's file -- month_end_bank_table_codes_one_bank; Absa's layout is a full list -- month_end_banks_absa_is_a_full_list")
    void oneTableCodeOneBankInTheDatabase() {
        long other = settings.insertBank("OTHER", new MonthEndSendSettingsRepository.BankValues("Other", List.of(903),
                false, "SHEET", true), "system", SendFixture.NOW);

        assertThatThrownBy(() -> settings.replaceTableCodes(other, List.of(901)))
                .hasMessageContaining("month_end_bank_table_codes_one_bank");
        assertThatThrownBy(() -> jdbc.update("UPDATE month_end_banks SET full_list = FALSE WHERE code = 'INVENTED_BANK'"))
                .hasMessageContaining("month_end_banks_absa_is_a_full_list");
        assertThatThrownBy(() -> jdbc.update("UPDATE month_end_pack_sets SET area_letters = 'a,b' WHERE code = 'NORTH1'"))
                .hasMessageContaining("month_end_pack_sets_areas_shape");
    }

    @Test
    @DisplayName("🔒 a pack once a take and a set -- month_end_region_packs_one_a_generation refuses a second of the same generation")
    void oncePerGeneration() {
        takes.take(SendFixture.TAKEN, "seed");
        packs.produce(SendFixture.TAKEN.toString(), "NORTH1", false);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO month_end_region_packs (month_id, month_start, set_code, generation, set_label, region_code,
                  area_letters, position, joined, left_count, suspended, pdf_name, pdf_file, pdf_sha256, xlsx_name,
                  xlsx_file, xlsx_sha256, produced_by, produced_at)
                SELECT month_id, month_start, set_code, generation, set_label, region_code, area_letters, position,
                  joined, left_count, suspended, pdf_name, pdf_file, pdf_sha256, xlsx_name, xlsx_file, xlsx_sha256,
                  produced_by, produced_at FROM month_end_region_packs
                """)).hasMessageContaining("month_end_region_packs_one_a_generation");
    }

    @Test
    @DisplayName("🔒 what was sent is append-only -- an UPDATE or a DELETE of a delivery is refused -- and a delivery not SENT says why")
    void theRecordIsAppendOnly() {
        takes.take(SendFixture.TAKEN, "seed");
        MonthEndRegionPackDto pack = packs.produce(SendFixture.TAKEN.toString(), "NORTH1", false).pack();
        packs.download(pack.id(), "pdf");

        assertThatThrownBy(() -> jdbc.update("UPDATE month_end_deliveries SET outcome = 'FAILED', code = 'MAIL_FAILED'"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM month_end_deliveries")).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO month_end_deliveries (pack_id, month_start, kind, recipient_code, recipient_name, channel,
                  formats, outcome, code, file_name, delivered_by, delivered_at)
                VALUES (?, DATE '2026-08-01', 'REGION_PACK', 'NORTH1', 'x', 'EMAIL', 'PDF', 'SENT', 'MAIL_FAILED', 'x',
                  'system', now())
                """, pack.id())).hasMessageContaining("month_end_deliveries_outcome_says_why");
    }

    // ------------------------------------------------------------------------------------------------ MEN-08

    @Test
    @DisplayName("🔒 MEN-08 on PostgreSQL: the regions are cut in SQL -- area A's listings hold area A's members alone, the south's the south's")
    void theRegionsAreCutInSql() {
        MonthRange august = MonthRange.of(SendFixture.TAKEN);

        assertThat(listings.joined(august, new PackScope("NORTH", List.of("A"))))
                .extracting(MonthEndListingsRepository.MemberLine::membershipNumber)
                .containsExactly(SendFixture.number("a1"));
        assertThat(listings.joined(august, new PackScope("NORTH", List.of())))
                .extracting(MonthEndListingsRepository.MemberLine::membershipNumber)
                .containsExactlyInAnyOrder(SendFixture.number("a1"), SendFixture.number("b1"));
        assertThat(listings.left(august, new PackScope("SOUTH", List.of()))).isEmpty();
        assertThat(listings.suspended(august, new PackScope("SOUTH", List.of()), List.of()))
                .singleElement().satisfies(m -> {
                    assertThat(m.membershipNumber()).isEqualTo(SendFixture.number("c2"));
                    assertThat(m.code()).as("a returned debit, read as SASBO's code 13 (MO23)").isEqualTo(13);
                });
    }

    // ------------------------------------------------------------------------------------------------ the bound

    @Test
    @DisplayName("⏱ a region's pack of twenty thousand members -- every one joined in the month -- and its bank's full list, each within the bound")
    void twentyThousand() {
        generate();
        takes.take(SendFixture.TAKEN, "seed");

        long started = System.nanoTime();
        MonthEndRegionPackDto pack = packs.produce(SendFixture.TAKEN.toString(), "NORTH1", false).pack();
        Duration packTook = Duration.ofNanos(System.nanoTime() - started);

        started = System.nanoTime();
        MonthEndProducedFileDto absa = files.produceBank(SendFixture.TAKEN.toString(), "INVENTED_BANK");
        Duration fileTook = Duration.ofNanos(System.nanoTime() - started);

        System.out.println("MONTH-END-PACKS PERFORMANCE: region pack of " + GENERATED + " generated members in "
                + packTook.toMillis() + " ms (PDF " + pack.files().get(0).bytes() + " bytes, XLSX "
                + pack.files().get(1).bytes() + " bytes); bank full list of " + absa.file().rows() + " rows in "
                + fileTook.toMillis() + " ms (" + absa.file().bytes() + " bytes encrypted)");
        assertThat(pack.counts().joined()).isEqualTo(GENERATED + 1);
        assertThat(pack.counts().left()).isEqualTo(GENERATED / 20 + 1);
        assertThat(pack.counts().suspended()).isEqualTo(GENERATED / 40 + 1);
        assertThat(absa.file().rows()).as("the payroll members on record at 901: the generated less the deleted, and "
                + "the fixture's six").isEqualTo(GENERATED - GENERATED / 20 + 6);
        assertThat(packTook).isLessThan(BOUND);
        assertThat(fileTook).isLessThan(BOUND);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        packs.download(pack.id(), "pdf").body().accept(out);
        assertThat(out.size()).isEqualTo(pack.files().get(0).bytes());
        assertThat(sent.regionPacksOf(takes.taken(SendFixture.TAKEN).orElseThrow().id())).hasSize(1);
    }

    /**
     * Twenty thousand invented members at area A's workplace, all joined in August, in SQL and never row by row: one in
     * twenty deleted in August (code 2), one in forty suspended (code 11), the rest on record, all paying by payroll.
     */
    private void generate() {
        jdbc.update("""
                INSERT INTO members (membership_number, employee_number, title, initials, first_names, surname,
                  identity_type, status, join_date, payment_type, subscription_frequency, monthly_subscription_numeric,
                  is_representative, workplace_id, created_at, updated_at)
                SELECT 'M-6' || lpad(n::text, 6, '0'), '5' || lpad(n::text, 7, '0'), 'Mx', 'I', 'INVENTED',
                  'INVENTED' || chr(65 + n % 26) || chr(65 + (n / 26) % 26), 'OTHER',
                  CASE WHEN n % 20 = 0 THEN 'Resigned' WHEN n % 40 = 1 THEN 'Suspended' ELSE 'Active' END,
                  DATE '2026-08-01' + (n % 28), 'Payroll Deduction', 'Monthly', 174.00, FALSE,
                  (SELECT id FROM workplaces WHERE ra_number = 990001), TIMESTAMP '2026-08-01 00:00:00',
                  TIMESTAMP '2026-08-01 00:00:00'
                FROM generate_series(1, ?) n
                """, GENERATED);
        jdbc.update("""
                INSERT INTO member_status_changes (member_id, from_status, to_status, reason, changed_by, changed_at,
                  deletion_code_id)
                SELECT m.id, 'Active', 'Resigned', 'DELETION_CODE', 'system', TIMESTAMPTZ '2026-08-29 08:00:00+00',
                  (SELECT id FROM deletion_codes WHERE sasbo_code = 2)
                FROM members m WHERE m.membership_number LIKE 'M-6%' AND m.status = 'Resigned'
                """);
        jdbc.update("""
                INSERT INTO member_status_changes (member_id, from_status, to_status, reason, changed_by, changed_at,
                  suspension_code_id)
                SELECT m.id, 'Active', 'Suspended', 'SUSPENSION_CODE', 'system', TIMESTAMPTZ '2026-08-29 08:00:00+00',
                  (SELECT id FROM suspension_codes WHERE code = 11)
                FROM members m WHERE m.membership_number LIKE 'M-6%' AND m.status = 'Suspended'
                """);
        jdbc.execute("ANALYZE members");
        jdbc.execute("ANALYZE member_status_changes");
    }
}
