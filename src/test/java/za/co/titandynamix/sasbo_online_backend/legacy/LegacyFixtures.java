package za.co.titandynamix.sasbo_online_backend.legacy;

import za.co.titandynamix.sasbo_online_backend.legacy.mapping.BuiltInMappings;
import za.co.titandynamix.sasbo_online_backend.legacy.mapping.MappingDefinition;
import za.co.titandynamix.sasbo_online_backend.legacy.mapping.MappingParser;
import za.co.titandynamix.sasbo_online_backend.legacy.read.ReadLimits;
import za.co.titandynamix.sasbo_online_backend.legacy.read.ReaderSettings;
import za.co.titandynamix.sasbo_online_backend.legacy.read.SourceReader;
import za.co.titandynamix.sasbo_online_backend.legacy.read.SourceRow;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.ReferenceIndex;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the data migration's fast tests share: the day they check on, the built-in mappings, reading a file into a list,
 * and SASBO's lists as an in-memory index -- invented workplaces 990001 to 990004 (never SASBO's own R/A numbers), the
 * table codes, area letters and codes the fixtures use. The integration lane puts the same workplaces on record.
 */
public final class LegacyFixtures {

    /** The day every fixture is checked on -- the switched-on integration lane's clock, mid-September 2026. */
    public static final LocalDate TODAY = LocalDate.of(2026, 9, 15);

    /** The month end the fixtures' export represents: SASBO's own, 31 August 2026. */
    public static final LocalDate AS_AT = LocalDate.of(2026, 8, 31);

    public static final String CSV_MAPPING = "legacy-member-export-csv";
    public static final String FIXED_WIDTH_MAPPING = "legacy-member-export-fixed-width";

    public static final String CSV_FIXTURE = "migration/member-export-faults.csv";
    public static final String FIXED_WIDTH_FIXTURE = "migration/member-export-faults.txt";

    /** The identity number the integration lane gives a member already on record, as line 51 of the CSV carries it. */
    public static final String ON_RECORD_ID = "7512125555083";
    /** The membership number of that member, in our form; line 52 of the CSV carries it in the old system's. */
    public static final String ON_RECORD_NUMBER = "M-799998";

    private static final Map<String, MappingDefinition> BUILT_IN = BuiltInMappings.load(new MappingParser());

    private LegacyFixtures() {
    }

    public static MappingDefinition mapping(String key) {
        MappingDefinition mapping = BUILT_IN.get(key);
        if (mapping == null) {
            throw new IllegalStateException("no built-in mapping " + key);
        }
        return mapping;
    }

    /** Generous limits, for tests about something else. */
    public static ReadLimits limits() {
        return new ReadLimits(50_000_000L, 1_000_000, 200, 1000);
    }

    public static List<SourceRow> read(SourceReader reader, byte[] bytes, ReaderSettings settings, ReadLimits limits)
            throws IOException {
        List<SourceRow> rows = new ArrayList<>();
        reader.read(new ByteArrayInputStream(bytes), settings, limits, rows::add);
        return rows;
    }

    public static byte[] resource(String path) {
        try (InputStream stream = LegacyFixtures.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("no test resource " + path);
            }
            return stream.readAllBytes();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    /** SASBO's lists, as the fixtures need them. */
    public static ReferenceIndex index() {
        Map<Integer, ReferenceIndex.Workplace> workplaces = new HashMap<>();
        workplaces.put(990001, new ReferenceIndex.Workplace(1L, "D", 13));
        workplaces.put(990002, new ReferenceIndex.Workplace(2L, "L", 24));
        workplaces.put(990003, new ReferenceIndex.Workplace(3L, "W", 43));
        workplaces.put(990004, new ReferenceIndex.Workplace(4L, "D", null));
        Set<String> areas = new HashSet<>();
        for (char letter = 'A'; letter <= 'X'; letter++) {
            areas.add(String.valueOf(letter));
        }
        areas.addAll(List.of("Y", "Z", "XX"));
        Map<Integer, Long> deletion = new HashMap<>();
        for (int code = 1; code <= 10; code++) {
            deletion.put(code, 100L + code);
        }
        Map<Integer, Long> suspension = Map.of(11, 211L, 13, 213L, 19, 219L);
        return new ReferenceIndex(workplaces, Set.of(1, 2, 4, 5, 6, 13, 14, 24, 43, 78), areas, deletion, suspension);
    }
}
