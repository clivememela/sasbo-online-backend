package za.co.titandynamix.sasbo_online_backend.legacy.reconcile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sets a batch beside the old system's month-end figures, figure by figure, and says where they differ -- so a move is
 * proved against SASBO's own reports, never against our own sums (MIG-31).
 *
 * <h2>What is compared</h2>
 * Every figure SASBO's pack gives, entered for the month: its value beside the batch's -- the rows that move -- and
 * beside it how many rows of the same key are held back by errors, which is where a difference is most often explained.
 * Where a dimension and measure have figures entered, a key the batch has and the pack does not is listed too: a table
 * code the old system printed no line for is a difference as well.
 */
public final class Reconciliation {

    /** How a figure compares. */
    public enum Status {
        /** The batch gives the old system's figure. */
        MATCH,
        /** It gives another. */
        DIFFERENT,
        /** The old system counts members under a key the batch has no row for at all. */
        LEGACY_ONLY,
        /** The batch counts members under a key the pack printed no figure for. */
        NO_LEGACY_FIGURE
    }

    /** One of the old system's figures, as entered. */
    public record Figure(Dimension dimension, String key, Measure measure, long value) {
    }

    /** What the batch counts under one key: each measure, and the rows of that key held back. */
    public record Counts(long onRecord, long suspended, long members, long joined, long left, long heldBack) {

        public long of(Measure measure) {
            return switch (measure) {
                case ON_RECORD -> onRecord;
                case SUSPENDED -> suspended;
                case MEMBERS -> members;
                case JOINED -> joined;
                case LEFT -> left;
            };
        }
    }

    /** One line of the comparison. {@code legacy} and {@code difference} are {@code null} where no figure was entered. */
    public record Line(Dimension dimension, String key, Measure measure, Long legacy, long batch, long heldBack,
                       Long difference, Status status) {
    }

    private Reconciliation() {
    }

    /**
     * Compares.
     *
     * @param legacy the month's figures as entered
     * @param batch  the batch's counts, by dimension and key -- a key as {@link #key} writes it
     */
    public static List<Line> compare(List<Figure> legacy, Map<Dimension, Map<String, Counts>> batch) {
        List<Line> lines = new ArrayList<>();
        Map<Dimension, Set<Measure>> entered = new EnumMap<>(Dimension.class);
        Map<String, Boolean> seen = new HashMap<>();
        for (Figure figure : legacy) {
            String key = key(figure.dimension(), figure.key());
            Map<String, Counts> byKey = batch.getOrDefault(figure.dimension(), Map.of());
            Counts counts = byKey.get(key);
            long value = counts == null ? 0 : counts.of(figure.measure());
            long heldBack = counts == null ? 0 : counts.heldBack();
            long difference = value - figure.value();
            Status status = difference == 0 ? Status.MATCH : counts == null ? Status.LEGACY_ONLY : Status.DIFFERENT;
            lines.add(new Line(figure.dimension(), key, figure.measure(), figure.value(), value, heldBack, difference,
                    status));
            entered.computeIfAbsent(figure.dimension(), d -> EnumSet.noneOf(Measure.class)).add(figure.measure());
            seen.put(figure.dimension() + "|" + key + "|" + figure.measure(), Boolean.TRUE);
        }
        entered.forEach((dimension, measures) -> {
            for (Map.Entry<String, Counts> row : batch.getOrDefault(dimension, Map.of()).entrySet()) {
                for (Measure measure : measures) {
                    long value = row.getValue().of(measure);
                    if (value > 0 && !seen.containsKey(dimension + "|" + row.getKey() + "|" + measure)) {
                        lines.add(new Line(dimension, row.getKey(), measure, null, value, row.getValue().heldBack(), null,
                                Status.NO_LEGACY_FIGURE));
                    }
                }
            }
        });
        lines.sort(Comparator.comparing(Line::dimension)
                .thenComparing(line -> sortable(line.dimension(), line.key()))
                .thenComparing(Line::measure));
        return lines;
    }

    /** How many lines of each status. */
    public static Map<Status, Long> summary(List<Line> lines) {
        Map<Status, Long> counts = new LinkedHashMap<>();
        for (Status status : Status.values()) {
            counts.put(status, lines.stream().filter(line -> line.status() == status).count());
        }
        return counts;
    }

    /** Proved: at least one figure entered, and every entered figure given exactly. */
    public static boolean proved(List<Line> lines) {
        return lines.stream().anyMatch(line -> line.legacy() != null)
                && lines.stream().filter(line -> line.legacy() != null).allMatch(line -> line.status() == Status.MATCH);
    }

    /**
     * A key as the comparison holds it: blank for the whole membership; a number without its leading noughts; any other
     * name trimmed, its spacing collapsed and upper-cased -- {@code Absa Bank} and {@code ABSA  BANK} are one employer.
     */
    public static String key(Dimension dimension, String key) {
        if (dimension == Dimension.TOTAL || key == null) {
            return "";
        }
        String plain = key.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        if (dimension.numericKey() && plain.matches("[0-9]{1,9}")) {
            return String.valueOf(Integer.parseInt(plain));
        }
        return plain;
    }

    private static String sortable(Dimension dimension, String key) {
        if (dimension.numericKey() && key.matches("[0-9]{1,9}")) {
            return String.format("%09d", Integer.parseInt(key));
        }
        return key;
    }
}
