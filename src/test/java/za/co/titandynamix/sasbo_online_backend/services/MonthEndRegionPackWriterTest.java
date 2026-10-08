package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndStatisticDto;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A region's pack as its PDF prints a statistic's row (October build): the words a hidden row reads are the workbook's,
 * so the pack's two formats never disagree -- and never "withheld", which SASBO read as "has to be approved" at the
 * 5 Oct Show &amp; Tell (OB28).
 */
@DisplayName("A region's pack, as the PDF prints a row")
class MonthEndRegionPackWriterTest {

    private static final MonthEndStatisticDto.Sheet SHEET = new MonthEndStatisticDto.Sheet("by-group",
            "Membership by group", null, List.of(new MonthEndStatisticDto.Column("members", "Members",
            MonthEndStatisticDto.Column.COUNT)), List.of(), null);

    @Test
    @DisplayName("OB28: a row rule C hides reads \"fewer than 5\", or \"not shown until you sign in\" -- never \"withheld\"")
    void aHiddenRowSaysWhyInWords() {
        MonthEndStatisticDto.Row fewer = new MonthEndStatisticDto.Row("g1", "INVENTED SMALL GROUP", "GROUP", true,
                false, null);
        MonthEndStatisticDto.Row beside = new MonthEndStatisticDto.Row("g2", "INVENTED GROUP", "GROUP", false, true,
                null);

        assertThat(MonthEndRegionPackWriter.statisticRow(fewer, SHEET))
                .containsExactly("INVENTED SMALL GROUP", "fewer than 5");
        assertThat(MonthEndRegionPackWriter.statisticRow(beside, SHEET))
                .containsExactly("INVENTED GROUP", "not shown until you sign in");
    }

    @Test
    @DisplayName("a row shown prints its figure, grouped by thousands")
    void aShownRowPrintsItsFigure() {
        MonthEndStatisticDto.Row shown = new MonthEndStatisticDto.Row("g3", "INVENTED BIG GROUP", "GROUP", false, false,
                List.of(new BigDecimal("12345")));

        assertThat(MonthEndRegionPackWriter.statisticRow(shown, SHEET)).containsExactly("INVENTED BIG GROUP", "12,345");
    }
}
