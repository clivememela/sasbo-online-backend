package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;
import za.co.titandynamix.sasbo_online_backend.dto.reference.ReferenceListAnswer;
import za.co.titandynamix.sasbo_online_backend.dto.reference.ReferenceRows;
import za.co.titandynamix.sasbo_online_backend.dto.reference.ReferenceStatusDto;
import za.co.titandynamix.sasbo_online_backend.enums.ListEmptyReason;
import za.co.titandynamix.sasbo_online_backend.repository.SasboReferenceRepository;
import za.co.titandynamix.sasbo_online_backend.repository.SasboReferenceRepository.ListRecord;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SASBO's lists, and why each is empty where it is (month end 01.1, ME-18, ME-19): one of four words, a failure said
 * before anything else, and the workplaces answered as the switch allows (MD62).
 */
@DisplayName("ReferenceListServiceImpl")
class ReferenceListServiceImplTest {

    private static final LocalDate PRINTED = LocalDate.of(2026, 8, 31);

    private final SasboReferenceRepository repository = mock(SasboReferenceRepository.class);

    private ReferenceListServiceImpl service(String environment, String switchWord) {
        return new ReferenceListServiceImpl(repository, new SasboEnvironment(environment), new WorkplaceSwitch(switchWord));
    }

    private static ListRecord seeded(int rows) {
        return new ListRecord(PRINTED, null, "SEEDED", null, rows, Instant.parse("2026-09-29T08:00:00Z"));
    }

    @Test
    @DisplayName("a list answers its rows with the day its report was printed, and no confirmation until SASBO gives one (MD28)")
    void aListAnswersWhereItStands() {
        when(repository.regions()).thenReturn(List.of(new ReferenceRows.Region("NORTH", "NORTH")));
        when(repository.recorded()).thenReturn(Map.of("regions", seeded(1)));

        ReferenceListAnswer<ReferenceRows.Region> answer = service(null, null).regions();

        assertThat(answer.list()).isEqualTo("regions");
        assertThat(answer.printedOn()).isEqualTo("2026-08-31");
        assertThat(answer.confirmedOn()).isNull();
        assertThat(answer.reason()).isNull();
        assertThat(answer.rows()).hasSize(1);
    }

    @Test
    @DisplayName("ME-19: the four reasons, in their order -- a failure first, then an unconfirmed list in production, then empty, else not loaded")
    void theFourReasons() {
        ListRecord failed = new ListRecord(PRINTED, null, "FAILED", "UNREADABLE", null, Instant.EPOCH);
        assertThat(ListEmptyReason.of(0, failed.outcome(), false, true)).isEqualTo(ListEmptyReason.FAILED);
        assertThat(ListEmptyReason.of(0, null, false, true)).isEqualTo(ListEmptyReason.NOT_CONFIRMED);
        assertThat(ListEmptyReason.of(0, null, true, true))
                .as("a confirmed list in production that was never loaded is not loaded -- it is not unconfirmed")
                .isEqualTo(ListEmptyReason.NOT_LOADED);
        assertThat(ListEmptyReason.of(0, "SEEDED", false, false)).isEqualTo(ListEmptyReason.EMPTY);
        assertThat(ListEmptyReason.of(0, null, false, false)).isEqualTo(ListEmptyReason.NOT_LOADED);
        assertThat(ListEmptyReason.of(3, "FAILED", false, true)).as("a list with rows says nothing").isNull();
    }

    @Test
    @DisplayName("the status read answers every list, says a failure in one of four words, and never an exception's name (MR-SEC-21)")
    void theStatusRead() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String key : SasboReferenceRepository.countedLists()) {
            counts.put(key, 5L);
        }
        counts.put("committees", 0L);
        when(repository.counts()).thenReturn(counts);
        when(repository.recorded()).thenReturn(Map.of(
                "regions", seeded(5),
                "committees", new ListRecord(PRINTED, null, "FAILED", "REFUSED", null, Instant.EPOCH),
                "workplaces", seeded(5)));

        ReferenceStatusDto status = service(null, "all").status();

        assertThat(status.lists()).extracting(ReferenceStatusDto.ListStatus::list)
                .containsExactly("regions", "areas", "employerGroups", "employers", "tableCodes", "committeeProvinces",
                        "committees", "suspensionCodes", "deletionCodes");
        ReferenceStatusDto.ListStatus committees = status.lists().stream()
                .filter(s -> s.list().equals("committees")).findFirst().orElseThrow();
        assertThat(committees.reason()).isEqualTo("FAILED");
        assertThat(committees.failure()).isEqualTo("REFUSED");
        assertThat(status.lists().get(0).seededAt()).isEqualTo("2026-09-29T08:00:00Z");
        assertThat(status.workplaces().rows()).isEqualTo(5);
        assertThat(status.workplacesServed()).isEqualTo("ALL");
    }

    @Test
    @DisplayName("🔒 MD62: switched off, the workplaces at rest are not answered -- nought, and not loaded here")
    void theWorkplacesFollowTheSwitch() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String key : SasboReferenceRepository.countedLists()) {
            counts.put(key, 5507L);
        }
        when(repository.counts()).thenReturn(counts);
        when(repository.recorded()).thenReturn(Map.of("workplaces", seeded(5507)));

        ReferenceStatusDto status = service(null, null).status();

        assertThat(status.workplaces().rows()).isZero();
        assertThat(status.workplaces().reason()).isEqualTo("NOT_LOADED");
        assertThat(status.workplacesServed()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("🔒 MD28: where the production marker is set, a list SASBO has not confirmed says so")
    void inProductionAnUnconfirmedListSaysSo() {
        when(repository.suspensionCodes()).thenReturn(List.of());
        when(repository.recorded()).thenReturn(Map.of());

        assertThat(service("production", null).suspensionCodes().reason()).isEqualTo("NOT_CONFIRMED");
    }
}
