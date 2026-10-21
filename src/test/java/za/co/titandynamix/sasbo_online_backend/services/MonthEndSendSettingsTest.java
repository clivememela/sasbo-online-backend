package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndBankDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndBankRequest;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndPackSetDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndPackSetRequest;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndProviderDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndProviderRequest;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndSendSettingsDto;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.support.SendFixture;
import za.co.titandynamix.sasbo_online_backend.utils.DataSeeder;

import java.time.Clock;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Who gets what at month end (October build), on H2 from {@link SendFixture}: recipients, banks and providers kept as
 * data and edited -- each field checked and named where refused, a member in one region's pack only (no two recipients of
 * a region on one area), a member in one bank's file only (no table code in two), every save naming the version it read,
 * and nothing changed by a caller who does not see every region.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "sasbo.workplaces=all",
        "spring.datasource.url=jdbc:h2:mem:month-end-send-settings;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@Import(MonthEndSendSettingsTest.AtTheFixturesClock.class)
@DisplayName("Who gets what at month end")
class MonthEndSendSettingsTest {

    @TestConfiguration
    static class AtTheFixturesClock {
        @Bean
        @Primary
        Clock theFixturesClock() {
            return Clock.fixed(SendFixture.NOW, AcbTime.ZONE);
        }
    }

    /** Nothing of the seed's: only the fixture is on this database. */
    @MockitoBean
    private DataSeeder noSeed;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MemberRepository members;

    @Autowired
    private MonthEndSendSettings settings;

    @BeforeEach
    void thePopulation() {
        SendFixture.clear(jdbc);
        SendFixture.insert(jdbc, members);
        SendFixture.settings(jdbc);
    }

    private static MonthEndPackSetRequest recipient(String code, String region, List<String> areas, Boolean active,
                                                    Integer version) {
        return new MonthEndPackSetRequest(code, "Invented recipient", region, areas, "Regional Secretary",
                "someone@example.org", active, version);
    }

    // ------------------------------------------------------------------------------------------------ recipients

    @Test
    @DisplayName("a recipient is created, its code in capitals, its areas sorted, by whom and when -- then changed by the version read")
    void aRecipientCreatedAndChanged() {
        jdbc.update("UPDATE month_end_pack_sets SET active = FALSE WHERE code = 'NORTH1'");

        MonthEndPackSetDto created = settings.createSet(recipient("north3", "north", List.of("a"), true, null));
        assertThat(created.code()).isEqualTo("NORTH3");
        assertThat(created.areaLetters()).containsExactly("A");
        assertThat(created.version()).isZero();
        assertThat(created.updatedBy()).isEqualTo("system");
        assertThat(created.updatedAt()).isEqualTo(SendFixture.NOW.toString());

        MonthEndPackSetDto changed = settings.updateSet("NORTH3", new MonthEndPackSetRequest(null, "Renamed", "NORTH",
                List.of("A"), "Regional Secretary", null, true, 0));
        assertThat(changed.label()).isEqualTo("Renamed");
        assertThat(changed.email()).isNull();
        assertThat(changed.version()).isEqualTo(1);
    }

    @Test
    @DisplayName("🔒 MEN-08: a member is in one pack -- two active recipients of a region on one area, or a whole region beside another, is PACK_SET_OVERLAP")
    void oneAreaOneRecipient() {
        assertThat(code(() -> settings.createSet(recipient("NORTH3", "NORTH", List.of("A"), true, null))))
                .isEqualTo(MonthEndRefusalCode.PACK_SET_OVERLAP);
        assertThat(code(() -> settings.createSet(recipient("NORTH3", "NORTH", List.of(), true, null))))
                .isEqualTo(MonthEndRefusalCode.PACK_SET_OVERLAP);
        assertThat(code(() -> settings.createSet(recipient("SOUTH2", "SOUTH", List.of("C"), true, null))))
                .as("beside a whole-region recipient").isEqualTo(MonthEndRefusalCode.PACK_SET_OVERLAP);
        assertThat(settings.createSet(recipient("NORTH3", "NORTH", List.of("A"), false, null)).active())
                .as("must-pass: a recipient switched off overlaps nobody").isFalse();
    }

    @Test
    @DisplayName("every field is checked and the one refused is named: code, label, region, an area not the region's, position, address")
    void everyFieldIsChecked() {
        assertThat(field(() -> settings.createSet(recipient("JHB 2!", "NORTH", List.of(), false, null)))).isEqualTo("code");
        assertThat(field(() -> settings.createSet(new MonthEndPackSetRequest("N9", "x".repeat(61), "NORTH", List.of(),
                "Regional Secretary", null, false, null)))).isEqualTo("label");
        assertThat(field(() -> settings.createSet(recipient("N9", "QQ", List.of(), false, null)))).isEqualTo("regionCode");
        assertThat(field(() -> settings.createSet(recipient("N9", "NORTH", List.of("C"), false, null))))
                .isEqualTo("areaLetters");
        assertThat(field(() -> settings.createSet(new MonthEndPackSetRequest("N9", "Invented", "NORTH", List.of(), " ",
                null, false, null)))).isEqualTo("position");
        assertThat(field(() -> settings.createSet(new MonthEndPackSetRequest("N9", "Invented", "NORTH", List.of(),
                "Regional Secretary", "not-an-address", false, null)))).isEqualTo("email");
        assertThat(field(() -> settings.updateSet("NORTH1", recipient("NORTH1", "NORTH", List.of("A"), true, null))))
                .isEqualTo("version");
    }

    @Test
    @DisplayName("a code another recipient has is PACK_SET_TAKEN; past twenty recipients none is added")
    void takenAndCeiling() {
        assertThat(code(() -> settings.createSet(recipient("SOUTH", "SOUTH", List.of(), false, null))))
                .isEqualTo(MonthEndRefusalCode.PACK_SET_TAKEN);
        for (int i = 0; i < MonthEndSendSettings.SETS_CEILING - 3; i++) {
            settings.createSet(recipient("X" + i, "NORTH", List.of(), false, null));
        }
        assertThat(code(() -> settings.createSet(recipient("ONEMORE", "NORTH", List.of(), false, null))))
                .isEqualTo(MonthEndRefusalCode.PACK_SET_INVALID);
    }

    @Test
    @DisplayName("🔒 a save on a stale view is SETTING_CHANGED_SINCE, saying the version now and who saved it -- and nothing is changed")
    void staleView() {
        settings.updateSet("NORTH1", recipient("NORTH1", "NORTH", List.of("A"), true, 0));

        MonthEndRefusal refused = refusal(() -> settings.updateSet("NORTH1", new MonthEndPackSetRequest(null, "Lost",
                "NORTH", List.of("A"), "Regional Secretary", null, true, 0)));

        assertThat(refused.code()).isEqualTo(MonthEndRefusalCode.SETTING_CHANGED_SINCE);
        assertThat(refused.details()).containsEntry("version", "1").containsEntry("updatedBy", "system");
        assertThat(jdbc.queryForObject("SELECT label FROM month_end_pack_sets WHERE code = 'NORTH1'", String.class))
                .isEqualTo("Invented recipient");
    }

    // ------------------------------------------------------------------------------------------------ banks

    @Test
    @DisplayName("🔒 a member is sent to one bank: a table code in another bank's file is TABLE_CODE_TAKEN, naming both")
    void oneTableCodeOneBank() {
        MonthEndRefusal refused = refusal(() -> settings.createBank(new MonthEndBankRequest("OTHER", "Other", List.of(901),
                false, "SHEET", true, null)));

        assertThat(refused.code()).isEqualTo(MonthEndRefusalCode.TABLE_CODE_TAKEN);
        assertThat(refused.details()).containsEntry("tableCode", "901").containsEntry("bank", "INVENTED_BANK");
        MonthEndBankDto created = settings.createBank(new MonthEndBankRequest("OTHER", "Other", List.of(903), false,
                "CSV", true, null));
        assertThat(created.tableCodes()).containsExactly(903);
    }

    @Test
    @DisplayName("a bank's file is checked: an unknown table code, Absa's layout on new and deleted, an unknown layout -- each named")
    void aBankIsChecked() {
        MonthEndRefusal unknown = refusal(() -> settings.createBank(new MonthEndBankRequest("OTHER", "Other",
                List.of(999), false, "SHEET", true, null)));
        assertThat(unknown.code()).isEqualTo(MonthEndRefusalCode.BANK_SETTING_INVALID);
        assertThat(unknown.details()).containsEntry("tableCode", "999");
        assertThat(field(() -> settings.createBank(new MonthEndBankRequest("OTHER", "Other", List.of(903), false,
                "ABSA_FIXED_WIDTH", true, null)))).isEqualTo("fullList");
        assertThat(field(() -> settings.createBank(new MonthEndBankRequest("OTHER", "Other", List.of(903), false,
                "WORD", true, null)))).isEqualTo("layout");
        assertThat(field(() -> settings.createBank(new MonthEndBankRequest("OTHER", "Other", List.of(), false, "SHEET",
                true, null)))).isEqualTo("tableCodes");
        assertThat(code(() -> settings.createBank(new MonthEndBankRequest("INVENTED_BANK", "Again", List.of(903),
                false, "SHEET", true, null)))).isEqualTo(MonthEndRefusalCode.BANK_TAKEN);
    }

    @Test
    @DisplayName("MEN-33's setting is data: a bank moved to the full list, its table codes replaced -- and a stale save refused")
    void aBankChanged() {
        MonthEndBankDto changed = settings.updateBank("INVENTED_INSURER", new MonthEndBankRequest(null, "Invented Insurer",
                List.of(902, 903), true, "SHEET", true, 0));

        assertThat(changed.fullList()).isTrue();
        assertThat(changed.tableCodes()).containsExactly(902, 903);
        assertThat(changed.version()).isEqualTo(1);
        assertThat(code(() -> settings.updateBank("INVENTED_INSURER", new MonthEndBankRequest(null, "Lost",
                List.of(902), false, "SHEET", true, 0)))).isEqualTo(MonthEndRefusalCode.SETTING_CHANGED_SINCE);
        assertThat(jdbc.queryForList("SELECT table_code FROM month_end_bank_table_codes c JOIN month_end_banks b "
                + "ON b.id = c.bank_id WHERE b.code = 'INVENTED_INSURER' ORDER BY table_code", Integer.class))
                .containsExactly(902, 903);
        assertThat(code(() -> settings.updateBank("NOWHERE", new MonthEndBankRequest(null, "x", List.of(903), false,
                "SHEET", true, 0)))).isEqualTo(MonthEndRefusalCode.BANK_NOT_FOUND);
    }

    // ------------------------------------------------------------------------------------------------ providers

    @Test
    @DisplayName("MEN-35: a suspended benefit switched back on by the version read; stale is refused; unknown is PROVIDER_NOT_FOUND")
    void aProviderSwitched() {
        MonthEndProviderDto on = settings.updateProvider("CRITICARE", new MonthEndProviderRequest(true, 0));

        assertThat(on.active()).isTrue();
        assertThat(on.provisional()).isTrue();
        assertThat(code(() -> settings.updateProvider("CRITICARE", new MonthEndProviderRequest(false, 0))))
                .isEqualTo(MonthEndRefusalCode.SETTING_CHANGED_SINCE);
        assertThat(code(() -> settings.updateProvider("NOWHERE", new MonthEndProviderRequest(false, 0))))
                .isEqualTo(MonthEndRefusalCode.PROVIDER_NOT_FOUND);
        assertThat(code(() -> settings.updateProvider("CRITICARE", new MonthEndProviderRequest(null, 1))))
                .isEqualTo(MonthEndRefusalCode.REQUEST_MALFORMED);
    }

    // ------------------------------------------------------------------------------------------------ scope

    @Test
    @DisplayName("🔒 a north user reads the north's recipients alone, no bank and no provider -- and changes nothing: SCOPE_TOO_NARROW")
    void aRegionsUser() {
        CallerScope north = CallerScope.regions(Set.of("NORTH"));

        MonthEndSendSettingsDto seen = SendFixture.as(north, () -> settings.settings());
        assertThat(seen.sets()).extracting(MonthEndPackSetDto::code).containsExactly("NORTH1", "NORTH2");
        assertThat(seen.banks()).isEmpty();
        assertThat(seen.providers()).isEmpty();
        assertThat(code(() -> SendFixture.as(north, () -> settings.updateSet("NORTH1", recipient("NORTH1", "NORTH",
                List.of("A"), true, 0))))).isEqualTo(MonthEndRefusalCode.SCOPE_TOO_NARROW);
        MonthEndSendSettingsDto all = settings.settings();
        assertThat(all.sets()).hasSize(3);
        assertThat(all.banks()).hasSize(2);
        assertThat(all.mail().configured()).isFalse();
        assertThat(all.sftp().state()).isEqualTo("NOT_CONFIGURED");
        assertThat(all.layouts().banks()).containsExactly("ABSA_FIXED_WIDTH", "SHEET", "CSV");
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static MonthEndRefusal refusal(Runnable call) {
        try {
            call.run();
        } catch (MonthEndRefusal refused) {
            return refused;
        }
        throw new AssertionError("expected a refusal, and the call was answered");
    }

    private static MonthEndRefusalCode code(Runnable call) {
        return refusal(call).code();
    }

    private static String field(Runnable call) {
        MonthEndRefusal refused = refusal(call);
        assertThat(refused.code()).isIn(MonthEndRefusalCode.PACK_SET_INVALID, MonthEndRefusalCode.BANK_SETTING_INVALID);
        return refused.details().get("field");
    }
}
