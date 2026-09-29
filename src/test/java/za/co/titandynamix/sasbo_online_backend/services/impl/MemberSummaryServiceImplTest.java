package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.QueryTimeoutException;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;
import za.co.titandynamix.sasbo_online_backend.dto.summary.MemberSummaryDto;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.MemberSummaryRepository;
import za.co.titandynamix.sasbo_online_backend.services.SummaryGate;
import za.co.titandynamix.sasbo_online_backend.utils.SeedMemberNumbers;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * What the figures' service decides before and after its one statement (02.2): the gate, the switch, the seed's count,
 * and the refusals that give nothing back.
 */
@DisplayName("MemberSummaryServiceImpl")
class MemberSummaryServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-15T08:00:00Z"), ZoneOffset.UTC);

    private final MemberSummaryRepository repository = mock(MemberSummaryRepository.class);
    private final SummaryGate gate = new SummaryGate();

    private MemberSummaryServiceImpl service(String workplaces, String environment) {
        return new MemberSummaryServiceImpl(repository, new WorkplaceSwitch(workplaces), new SasboEnvironment(environment),
                new SeedMemberNumbers(), gate, CLOCK);
    }

    private static MemberSummaryRepository.Line check() {
        return new MemberSummaryRepository.Line(MemberSummaryRepository.CHECK, null, null, false, null, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, null, 0, null, 1, null);
    }

    @Test
    @DisplayName("🔒 MD63, MV-56: two figure queries at once -- the third is SUMMARY_UNAVAILABLE at once, and does not queue")
    void theThirdIsRefusedAtOnce() throws Exception {
        CountDownLatch inside = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        when(repository.figures(any())).thenAnswer(invocation -> {
            inside.countDown();
            release.await(30, TimeUnit.SECONDS);
            return List.of(check());
        });
        MemberSummaryServiceImpl service = service("all", null);
        List<Thread> running = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Thread query = new Thread(() -> service.summary("2026-09", "group", null, null), "figures-" + i);
            query.start();
            running.add(query);
        }
        assertThat(inside.await(10, TimeUnit.SECONDS)).as("two are working the figures out").isTrue();
        try {
            assertThatThrownBy(() -> service.summary("2026-09", "group", null, null))
                    .isInstanceOfSatisfying(MonthEndRefusal.class,
                            refusal -> assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.SUMMARY_UNAVAILABLE));
        } finally {
            release.countDown();
            for (Thread query : running) {
                query.join(10_000);
            }
        }
        assertThat(service.summary("2026-09", "group", null, null).month()).as("and then the gate is open").isEqualTo("2026-09");
    }

    @Test
    @DisplayName("🔒 MD47: a query that outlives its timeout is SUMMARY_UNAVAILABLE -- and leaves the gate open behind it")
    void aTimeoutIsUnavailable() {
        when(repository.figures(any())).thenThrow(new QueryTimeoutException("cancelled at its timeout"));
        MemberSummaryServiceImpl service = service("all", null);

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.summary("2026-09", "group", null, null))
                    .isInstanceOfSatisfying(MonthEndRefusal.class,
                            refusal -> assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.SUMMARY_UNAVAILABLE));
        }
        assertThat(gate.tryEnter() && gate.tryEnter()).as("both places free again").isTrue();
        gate.leave();
        gate.leave();
    }

    @Test
    @DisplayName("🔒 MD62, MV-55: grouped through the workplace only where the switch says all -- elsewhere one row, no complement")
    void groupedOnlyAtAll() {
        when(repository.figures(any())).thenReturn(List.of(check()));
        ArgumentCaptor<MemberSummaryRepository.Ask> asked = ArgumentCaptor.forClass(MemberSummaryRepository.Ask.class);

        MemberSummaryDto none = service("", null).summary("2026-09", "group", null, null);
        MemberSummaryDto names = service("names", null).summary("2026-09", "group", null, null);
        service("all", null).summary("2026-09", "group", null, null);

        verify(repository, org.mockito.Mockito.times(3)).figures(asked.capture());
        assertThat(asked.getAllValues()).extracting(MemberSummaryRepository.Ask::grouped).containsExactly(false, false, true);
        assertThat(asked.getAllValues()).extracting(MemberSummaryRepository.Ask::complementServed)
                .containsExactly(false, false, true);
        assertThat(none.workplacesServed()).isEqualTo("NONE");
        assertThat(names.workplacesServed()).isEqualTo("NAMES");
        assertThat(none.totals().complement()).isNull();
        assertThat(none.totals().penetration()).isNull();
        assertThat(none.unplaced().noWorkplace()).as("nothing to say of workplaces where none is served").isNull();
    }

    @Test
    @DisplayName("seededMembers: the seed's numbers are asked for in a demo, and nought where the production marker is set")
    void theSeedsCount() {
        when(repository.figures(any())).thenReturn(List.of(check()));
        ArgumentCaptor<MemberSummaryRepository.Ask> asked = ArgumentCaptor.forClass(MemberSummaryRepository.Ask.class);

        service("all", null).summary("2026-09", "group", null, null);
        MemberSummaryDto production = service("all", "production").summary("2026-09", "group", null, null);

        verify(repository, org.mockito.Mockito.times(2)).figures(asked.capture());
        assertThat(asked.getAllValues().get(0).seeded()).isNotEmpty();
        assertThat(asked.getAllValues().get(1).seeded()).isEmpty();
        assertThat(production.seededMembers()).isZero();
    }

    @Test
    @DisplayName("ME-14: a grouping nobody takes is SUMMARY_GROUPING_UNKNOWN naming the six -- and never the one sent")
    void aGroupingNobodyTakes() {
        assertThatThrownBy(() -> service("all", null).summary("2026-09", "PLANTEDGROUPING", null, null))
                .isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.SUMMARY_GROUPING_UNKNOWN);
                    assertThat(refusal.details()).containsEntry("known", "group,employer,tableCode,area,region,committee");
                    assertThat(refusal.getMessage() + refusal.details()).doesNotContain("PLANTEDGROUPING");
                });
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("an area and a region together, or a code over its ceiling, is REQUEST_MALFORMED naming the field")
    void anAreaAndARegion() {
        assertThatThrownBy(() -> service("all", null).summary("2026-09", "group", "A", "NORTH"))
                .isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.REQUEST_MALFORMED);
                    assertThat(refusal.details()).containsEntry("field", "region");
                });
        assertThatThrownBy(() -> service("all", null).summary("2026-09", "group", "PLANTED", null))
                .isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
                    assertThat(refusal.details()).containsEntry("field", "area");
                    assertThat(refusal.getMessage()).doesNotContain("PLANTED");
                });
        verifyNoInteractions(repository);
    }
}
