package za.co.titandynamix.sasbo_online_backend.acb.file;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The writer against the file Standard Bank actually accepted -- run by hand, on the one machine that
 * has the file, and never anywhere else.
 *
 * <h2>🔒 The file is member data, and this test is built so that none of it can leak</h2>
 * SASBO's July 2026 bureau file holds 3,214 members' names and account numbers. It lives outside
 * every repository, and this test is skipped unless {@code ACB_JULY_FILE} names it. It rebuilds the
 * run's lines from the file IN MEMORY, writes them with the production writer, and compares the two
 * field by field -- and what it asserts on is a map of FIELD NAMES to mismatch COUNTS. A failure
 * prints "homing account: 3 lines", never an account. No record, no line, no value reaches an
 * assertion message, a log or a report.
 *
 * <h2>What differs on purpose, and is checked as a difference</h2>
 * The old system wrote the 27th in two passes, so July has two contras that day and five in all; the
 * new writer writes one per action date. So the last sequence number, the contra counts, the hash
 * total (which includes each contra's account) and the record count differ by exactly one contra --
 * and the test checks that they differ by exactly that. The one field it skips is SASBO's own
 * two-character bank code in the statement reference, which the new system does not hold (question 36).
 *
 * <p>Run it: {@code ACB_JULY_FILE=<path> ./gradlew test --tests '*BureauFileWriterJulyFileTest'}.
 */
@EnabledIfEnvironmentVariable(named = "ACB_JULY_FILE", matches = ".+")
@DisplayName("BureauFileWriter against the July 2026 file -- by hand, locally")
class BureauFileWriterJulyFileTest {

    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");

    @Test
    @DisplayName("the writer reproduces the accepted file, field by field, apart from the known differences")
    void theWriterReproducesTheJulyFile() throws IOException {
        byte[] original = Files.readAllBytes(Path.of(System.getenv("ACB_JULY_FILE")));
        List<String> july = records(original);
        String h02 = only(july, "02");
        String h04 = only(july, "04");
        String t92 = only(july, "92");
        String t94 = only(july, "94");
        List<String> debits = july.stream().filter(r -> r.startsWith("50")).toList();
        List<String> contras = july.stream().filter(r -> r.startsWith("52")).toList();
        assertThat(debits).as("the file has debits to compare").isNotEmpty();

        BureauFileSettings settings = new BureauFileSettings(
                f(h02, 7, 12).trim(), f(h04, 3, 6), f(debits.get(0), 3, 8), f(debits.get(0), 9, 19),
                f(h02, 13, 22), f(h02, 3, 6), f(h02, 35, 40), f(debits.get(0), 71, 80).trim(),
                f(h04, 41, 50).trim(), f(h02, 47, 50));
        BureauFileWriter.Header header = new BureauFileWriter.Header(
                date(f(h04, 7, 12)), date(f(h04, 13, 18)), date(f(h04, 19, 24)), date(f(h04, 25, 30)));

        Map<Long, String> julyBySequence = new HashMap<>();
        List<BureauFileLine> lines = new ArrayList<>();
        for (String r : debits) {
            long sequence = Long.parseLong(f(r, 24, 29));
            julyBySequence.put(sequence, r);
            String month = f(r, 94, 101).trim();
            lines.add(new BureauFileLine(sequence, f(r, 30, 35), f(r, 36, 46), Integer.parseInt(f(r, 47, 47)),
                    new BigDecimal(f(r, 48, 58)).movePointLeft(2), date(f(r, 59, 64)),
                    f(r, 81, 88).trim(), f(r, 102, 121).trim(), f(r, 122, 125).trim(),
                    month.equals("SUBSCRIP") ? null : YearMonth.of(header.firstActionDate().getYear(), Month.valueOf(month))));
        }

        BureauFileWriter.Written written = BureauFileWriter.write(settings, header, lines);
        List<String> ours = records(written.bytes());

        Map<String, Integer> mismatches = new TreeMap<>();
        count(mismatches, "02 whole record", !only(ours, "02").equals(h02));
        count(mismatches, "04 whole record", !only(ours, "04").equals(h04));

        Map<Long, Integer> ourSequence = written.sequenceByLine();
        Map<Integer, String> oursBySequence = new HashMap<>();
        for (String r : ours) {
            if (r.startsWith("50")) {
                oursBySequence.put(Integer.parseInt(f(r, 24, 29)), r);
            }
        }
        Map<String, int[]> fields = fields();
        for (Map.Entry<Long, String> entry : julyBySequence.entrySet()) {
            String theirs = entry.getValue();
            String mine = oursBySequence.get(ourSequence.get(entry.getKey()));
            for (Map.Entry<String, int[]> field : fields.entrySet()) {
                int[] span = field.getValue();
                count(mismatches, "50 " + field.getKey(), !f(mine, span[0], span[1]).equals(f(theirs, span[0], span[1])));
            }
        }

        // Contras: the day's debits, credited once a day -- July's two contras on the 27th summed.
        Map<String, Long> julyTotalByDate = new LinkedHashMap<>();
        Map<String, String> julyContraByDate = new LinkedHashMap<>();
        for (String r : contras) {
            julyTotalByDate.merge(f(r, 59, 64), Long.parseLong(f(r, 48, 58)), Long::sum);
            julyContraByDate.putIfAbsent(f(r, 59, 64), r);
        }
        List<String> ourContras = ours.stream().filter(r -> r.startsWith("52")).toList();
        count(mismatches, "52 one per action date", ourContras.size() != julyTotalByDate.size());
        for (String r : ourContras) {
            String theirs = julyContraByDate.get(f(r, 59, 64));
            if (theirs == null) {
                count(mismatches, "52 action date the file does not have", true);
                continue;
            }
            count(mismatches, "52 day total", julyTotalByDate.get(f(r, 59, 64)) != Long.parseLong(f(r, 48, 58)));
            count(mismatches, "52 identity", !f(r, 3, 23).equals(f(theirs, 3, 23)) || !f(r, 30, 47).equals(f(theirs, 30, 47)));
            count(mismatches, "52 date, class, references and filler", !f(r, 59, 180).equals(f(theirs, 59, 180)));
        }

        // Trailer 92: equal where the contra count does not enter; off by exactly one contra where it does.
        String our92 = only(ours, "92");
        int contraDelta = contras.size() - ourContras.size();
        count(mismatches, "92 identity and dates", !f(our92, 1, 12).equals(f(t92, 1, 12)) || !f(our92, 19, 36).equals(f(t92, 19, 36)));
        count(mismatches, "92 last sequence", Long.parseLong(f(t92, 13, 18)) - Long.parseLong(f(our92, 13, 18)) != contraDelta);
        count(mismatches, "92 contra counts", Long.parseLong(f(t92, 37, 42)) - Long.parseLong(f(our92, 37, 42)) != contraDelta);
        count(mismatches, "92 values", !f(our92, 49, 72).equals(f(t92, 49, 72)));
        BigInteger nominated = new BigInteger(settings.nominatedAccount());
        BigInteger expectedHash = new BigInteger(f(our92, 73, 84)).add(nominated.multiply(BigInteger.valueOf(contraDelta)))
                .mod(BigInteger.TEN.pow(12));
        count(mismatches, "92 hash total, allowing for the contra count", !expectedHash.equals(new BigInteger(f(t92, 73, 84))));

        // Trailer 94: the 02 header again, and the record count off by the contra delta.
        String our94 = only(ours, "94");
        count(mismatches, "94 header repeated", !f(our94, 3, 50).equals(f(t94, 3, 50)));
        count(mismatches, "94 record count", Long.parseLong(f(t94, 63, 68)) - Long.parseLong(f(our94, 63, 68)) != contraDelta);
        count(mismatches, "94 pairs", !f(our94, 69, 74).equals(f(t94, 69, 74)));
        // The inferred ten-record block count: July's 3,223 records and ours both come to 323, so this
        // cannot tell the inference apart from a coincidence -- it can only catch it being wrong.
        count(mismatches, "94 blocks", !f(our94, 57, 62).equals(f(t94, 57, 62)));

        assertThat(mismatches)
                .as("fields where the writer disagrees with the file the bank accepted -- names and counts only")
                .isEmpty();
        assertThat(written.debitCount()).isEqualTo(debits.size());
    }

    /** The 50 record's fields the writer must reproduce: all but the sequence and SASBO's bank code. */
    private static Map<String, int[]> fields() {
        Map<String, int[]> fields = new LinkedHashMap<>();
        fields.put("user branch, account and code", new int[] {3, 23});
        fields.put("homing branch", new int[] {30, 35});
        fields.put("homing account", new int[] {36, 46});
        fields.put("account type", new int[] {47, 47});
        fields.put("amount", new int[] {48, 58});
        fields.put("action date", new int[] {59, 64});
        fields.put("entry class and tax code", new int[] {65, 70});
        fields.put("user reference", new int[] {71, 80});
        fields.put("membership number", new int[] {81, 88});
        fields.put("reference C", new int[] {89, 89});
        fields.put("reference space-zero", new int[] {92, 93});
        fields.put("month reference", new int[] {94, 101});
        fields.put("surname", new int[] {102, 121});
        fields.put("initials", new int[] {122, 125});
        fields.put("filler and homing institution", new int[] {126, 180});
        return fields;
    }

    private static void count(Map<String, Integer> mismatches, String field, boolean differs) {
        if (differs) {
            mismatches.merge(field, 1, Integer::sum);
        }
    }

    private static List<String> records(byte[] bytes) {
        List<String> records = new ArrayList<>();
        for (String r : new String(bytes, StandardCharsets.US_ASCII).split("\r\n")) {
            if (!r.isEmpty()) {
                records.add(r);
            }
        }
        return records;
    }

    private static String only(List<String> records, String type) {
        List<String> found = records.stream().filter(r -> r.startsWith(type)).toList();
        assertThat(found.size()).as("records of type %s", type).isEqualTo(1);
        return found.get(0);
    }

    /** Positions {@code from} to {@code to}, 1-based and inclusive, as the layout names them. */
    private static String f(String record, int from, int to) {
        return record.substring(from - 1, to);
    }

    private static LocalDate date(String yymmdd) {
        return LocalDate.parse(yymmdd, YYMMDD);
    }
}
