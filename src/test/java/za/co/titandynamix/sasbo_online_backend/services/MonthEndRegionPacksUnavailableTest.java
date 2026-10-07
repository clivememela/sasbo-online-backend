package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.monthend.MonthEndMailer;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndListingsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSendSettingsRepository.PackSet;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndSentRepository;
import za.co.titandynamix.sasbo_online_backend.utils.SeedMemberNumbers;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Below the workplaces switch's {@code all} nothing is placed in a region (MD62), so no region's pack can be made: asked,
 * the answer is REGION_PACKS_UNAVAILABLE -- and nothing is read or written.
 */
@DisplayName("A region's pack, where the switch is below all")
class MonthEndRegionPacksUnavailableTest {

    @Test
    @DisplayName("🔒 MD62: REGION_PACKS_UNAVAILABLE, and the month is never read")
    void refused() {
        MonthEndStatistics statistics = mock(MonthEndStatistics.class);
        MonthEndSendSettings settings = mock(MonthEndSendSettings.class);
        MonthEndTakes takes = mock(MonthEndTakes.class);
        MonthEndListingsRepository listings = mock(MonthEndListingsRepository.class);
        MonthEndSentRepository sent = mock(MonthEndSentRepository.class);
        when(statistics.grouped()).thenReturn(false);
        when(settings.visibleSet("JHB")).thenReturn(new PackSet(1, "JHB", "Johannesburg (first set)", "JHB", "JHB",
                List.of("A"), "Regional Secretary", null, true, 0, "seed", Instant.EPOCH));
        MonthEndRegionPacks packs = new MonthEndRegionPacks(takes, statistics, mock(MonthEndFactsRepository.class),
                listings, settings, sent, mock(MonthEndRegionPackWriter.class), mock(MonthEndMailer.class),
                new SasboEnvironment(""), mock(SeedMemberNumbers.class), Clock.systemUTC());

        assertThatThrownBy(() -> packs.produce("2026-09", "JHB", false))
                .satisfies(e -> assertThat(((MonthEndRefusal) e).code())
                        .isEqualTo(MonthEndRefusalCode.REGION_PACKS_UNAVAILABLE));
        verifyNoInteractions(takes, listings, sent);
    }
}
