package za.co.titandynamix.sasbo_online_backend.legacy.reconcile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.legacy.reconcile.Reconciliation.Counts;
import za.co.titandynamix.sasbo_online_backend.legacy.reconcile.Reconciliation.Figure;
import za.co.titandynamix.sasbo_online_backend.legacy.reconcile.Reconciliation.Line;
import za.co.titandynamix.sasbo_online_backend.legacy.reconcile.Reconciliation.Status;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A batch set beside the old system's figures: SASBO's own shape -- on record, suspended and members excluding the
 * suspended, by the whole membership, area, table code and suspension code -- each line MATCH, DIFFERENT, LEGACY_ONLY or
 * NO_LEGACY_FIGURE, with the rows held back beside it. The figures are SASBO's August 2026 totals, as their reports print
 * them; nothing here names an employer's membership below five.
 */
@DisplayName("Reconciliation")
class ReconciliationTest {

    private static Map<Dimension, Map<String, Counts>> batch() {
        Map<Dimension, Map<String, Counts>> batch = new EnumMap<>(Dimension.class);
        batch.put(Dimension.TOTAL, Map.of("", new Counts(65_714, 145, 65_569, 591, 400, 3)));
        Map<String, Counts> areas = new LinkedHashMap<>();
        areas.put("D", new Counts(11_000, 10, 10_990, 113, 68, 1));
        areas.put("L", new Counts(580, 0, 580, 3, 3, 0));
        areas.put("", new Counts(12, 0, 12, 0, 0, 0));
        batch.put(Dimension.AREA, areas);
        batch.put(Dimension.SUSPENSION_CODE, Map.of("11", new Counts(139, 139, 0, 0, 0, 0),
                "13", new Counts(2, 2, 0, 0, 0, 0), "19", new Counts(4, 4, 0, 0, 0, 0)));
        return batch;
    }

    @Test
    @DisplayName("SASBO's own headline figures, given exactly: on record 65,714, suspended 145, members 65,569 -- MATCH, proved")
    void theHeadlineMatches() {
        List<Line> lines = Reconciliation.compare(List.of(
                new Figure(Dimension.TOTAL, "", Measure.ON_RECORD, 65_714),
                new Figure(Dimension.TOTAL, null, Measure.SUSPENDED, 145),
                new Figure(Dimension.TOTAL, "", Measure.MEMBERS, 65_569),
                new Figure(Dimension.SUSPENSION_CODE, "11", Measure.SUSPENDED, 139),
                new Figure(Dimension.SUSPENSION_CODE, "013", Measure.SUSPENDED, 2),
                new Figure(Dimension.SUSPENSION_CODE, "19", Measure.SUSPENDED, 4)), batch());

        assertThat(lines).allSatisfy(line -> assertThat(line.status()).isEqualTo(Status.MATCH));
        assertThat(lines).filteredOn(line -> line.dimension() == Dimension.TOTAL).first()
                .satisfies(line -> assertThat(line.heldBack()).isEqualTo(3));
        assertThat(Reconciliation.proved(lines)).isTrue();
    }

    @Test
    @DisplayName("a figure the batch gives otherwise is DIFFERENT, with the difference and the rows held back beside it")
    void different() {
        List<Line> lines = Reconciliation.compare(List.of(new Figure(Dimension.AREA, "d", Measure.MEMBERS, 11_052)),
                batch());

        Line area = lines.stream().filter(line -> line.key().equals("D")).findFirst().orElseThrow();
        assertThat(area.status()).isEqualTo(Status.DIFFERENT);
        assertThat(area.batch()).isEqualTo(10_990);
        assertThat(area.difference()).isEqualTo(-62);
        assertThat(area.heldBack()).isEqualTo(1);
        assertThat(Reconciliation.proved(lines)).isFalse();
    }

    @Test
    @DisplayName("LEGACY_ONLY: the old system counts members under a key the batch has no row for at all")
    void legacyOnly() {
        List<Line> lines = Reconciliation.compare(List.of(new Figure(Dimension.AREA, "X", Measure.MEMBERS, 4_726)), batch());

        assertThat(lines).filteredOn(line -> line.key().equals("X")).singleElement()
                .satisfies(line -> {
                    assertThat(line.status()).isEqualTo(Status.LEGACY_ONLY);
                    assertThat(line.batch()).isZero();
                });
    }

    @Test
    @DisplayName("NO_LEGACY_FIGURE: where a dimension has figures entered, a key the batch counts and the pack does not is listed too")
    void noLegacyFigure() {
        List<Line> lines = Reconciliation.compare(List.of(new Figure(Dimension.AREA, "D", Measure.MEMBERS, 10_990)), batch());

        assertThat(lines).extracting(Line::key, Line::status).containsExactly(
                org.assertj.core.groups.Tuple.tuple("", Status.NO_LEGACY_FIGURE),
                org.assertj.core.groups.Tuple.tuple("D", Status.MATCH),
                org.assertj.core.groups.Tuple.tuple("L", Status.NO_LEGACY_FIGURE));
        assertThat(lines).filteredOn(line -> line.status() == Status.NO_LEGACY_FIGURE)
                .allSatisfy(line -> assertThat(line.legacy()).isNull());
        assertThat(Reconciliation.proved(lines)).as("every figure entered is matched").isTrue();
        assertThat(Reconciliation.summary(lines)).containsEntry(Status.MATCH, 1L).containsEntry(Status.NO_LEGACY_FIGURE, 2L);
    }

    @Test
    @DisplayName("nothing entered proves nothing")
    void nothingEntered() {
        assertThat(Reconciliation.compare(List.of(), batch())).isEmpty();
        assertThat(Reconciliation.proved(List.of())).isFalse();
    }

    @Test
    @DisplayName("a key is held one way: a number without its noughts, a name upper-cased with its spacing collapsed")
    void keys() {
        assertThat(Reconciliation.key(Dimension.TABLE_CODE, "013")).isEqualTo("13");
        assertThat(Reconciliation.key(Dimension.EMPLOYER, "  Absa   Bank ")).isEqualTo("ABSA BANK");
        assertThat(Reconciliation.key(Dimension.TOTAL, "anything")).isEmpty();
        assertThat(Reconciliation.key(Dimension.AREA, null)).isEmpty();
    }

    @Test
    @DisplayName("a suspension code counts the suspended, a deletion code the leavers -- and nothing else")
    void measuresByDimension() {
        assertThat(Dimension.SUSPENSION_CODE.measures()).containsExactly(Measure.SUSPENDED);
        assertThat(Dimension.DELETION_CODE.measures()).containsExactly(Measure.LEFT);
        assertThat(Dimension.AREA.measures()).containsExactlyInAnyOrder(Measure.values());
    }
}
