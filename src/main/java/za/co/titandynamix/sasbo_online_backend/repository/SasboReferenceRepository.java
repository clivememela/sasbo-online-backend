package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.titandynamix.sasbo_online_backend.dto.reference.ReferenceRows;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads SASBO's lists for month end (01.1) -- one statement a list, ordered by the server and by nothing a request
 * names (MR-SEC-22). Read by JDBC, as the rows are written: a list of at most 137 rows, answered straight into its
 * row type with no entity loaded in between. Both databases accept every statement here (BR65).
 */
@Repository
public class SasboReferenceRepository {

    private final JdbcTemplate jdbc;

    public SasboReferenceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ReferenceRows.Region> regions() {
        return jdbc.query("SELECT code, name FROM sasbo_regions ORDER BY code",
                (rs, n) -> new ReferenceRows.Region(rs.getString("code"), rs.getString("name")));
    }

    public List<ReferenceRows.Area> areas() {
        return jdbc.query("SELECT a.letter, r.code AS region FROM sasbo_areas a "
                        + "LEFT JOIN sasbo_regions r ON r.id = a.region_id ORDER BY LENGTH(a.letter), a.letter",
                (rs, n) -> new ReferenceRows.Area(rs.getString("letter"), rs.getString("region")));
    }

    public List<ReferenceRows.EmployerGroup> employerGroups() {
        return jdbc.query("SELECT name, core FROM employer_groups ORDER BY ordinal, name",
                (rs, n) -> new ReferenceRows.EmployerGroup(rs.getString("name"), rs.getBoolean("core")));
    }

    /** Every employer with its group and the table codes that are its members' -- in one statement, then folded. */
    public List<ReferenceRows.Employer> employers() {
        record Head(long id, String name, String group, boolean core) {
        }
        Map<Long, Head> heads = new LinkedHashMap<>();
        Map<Long, List<Integer>> codes = new LinkedHashMap<>();
        jdbc.query("SELECT e.id, e.name, g.name AS group_name, g.core, t.code FROM employers e "
                + "JOIN employer_groups g ON g.id = e.group_id "
                + "LEFT JOIN table_codes t ON t.employer_id = e.id "
                + "ORDER BY e.ordinal, t.code", rs -> {
                    long id = rs.getLong("id");
                    if (!heads.containsKey(id)) {
                        heads.put(id, new Head(id, rs.getString("name"), rs.getString("group_name"), rs.getBoolean("core")));
                        codes.put(id, new ArrayList<>());
                    }
                    int code = rs.getInt("code");
                    if (!rs.wasNull()) {
                        codes.get(id).add(code);
                    }
                });
        return heads.values().stream()
                .map(h -> new ReferenceRows.Employer(h.id(), h.name(), h.group(), h.core(), List.copyOf(codes.get(h.id()))))
                .toList();
    }

    public List<ReferenceRows.TableCode> tableCodes() {
        return jdbc.query("SELECT t.code, t.name, t.employer_id, e.name AS employer, t.part FROM table_codes t "
                        + "LEFT JOIN employers e ON e.id = t.employer_id ORDER BY t.code",
                (rs, n) -> {
                    long employerId = rs.getLong("employer_id");
                    return new ReferenceRows.TableCode(rs.getInt("code"), rs.getString("name"),
                            rs.wasNull() ? null : employerId, rs.getString("employer"), rs.getString("part"));
                });
    }

    public List<ReferenceRows.CommitteeProvince> committeeProvinces() {
        return jdbc.query("SELECT letter, name FROM committee_provinces ORDER BY letter",
                (rs, n) -> new ReferenceRows.CommitteeProvince(rs.getString("letter"), rs.getString("name")));
    }

    public List<ReferenceRows.Committee> committees() {
        return jdbc.query("SELECT c.code, p.letter, c.name, c.recruiting_number FROM committees c "
                        + "JOIN committee_provinces p ON p.id = c.province_id ORDER BY c.code",
                (rs, n) -> {
                    int number = rs.getInt("recruiting_number");
                    return new ReferenceRows.Committee(rs.getString("code"), rs.getString("letter"), rs.getString("name"),
                            rs.wasNull() ? null : number);
                });
    }

    public List<ReferenceRows.SuspensionCode> suspensionCodes() {
        return jdbc.query("SELECT code, name FROM suspension_codes ORDER BY code",
                (rs, n) -> new ReferenceRows.SuspensionCode(rs.getInt("code"), rs.getString("name")));
    }

    /** What {@code reference_lists} says of every list it has a row for, by the list's key. */
    public Map<String, ListRecord> recorded() {
        Map<String, ListRecord> found = new LinkedHashMap<>();
        jdbc.query("SELECT list_key, printed_on, confirmed_on, outcome, failure, row_count, recorded_at FROM reference_lists",
                rs -> {
                    Date printed = rs.getDate("printed_on");
                    Date confirmed = rs.getDate("confirmed_on");
                    Timestamp at = rs.getTimestamp("recorded_at");
                    int rows = rs.getInt("row_count");
                    boolean noRows = rs.wasNull();
                    found.put(rs.getString("list_key"), new ListRecord(
                            printed == null ? null : printed.toLocalDate(),
                            confirmed == null ? null : confirmed.toLocalDate(),
                            rs.getString("outcome"), rs.getString("failure"), noRows ? null : rows,
                            at == null ? null : at.toInstant()));
                });
        return found;
    }

    /**
     * What each list's rows are counted from, in the order the seeder writes them. The deletion codes counted are
     * SASBO's -- the ones with a number of SASBO's, and the reasons for resigning under them -- and not a code an
     * administrator added by hand.
     */
    private static final List<String[]> COUNTED = List.of(
            new String[] {"regions", "sasbo_regions"},
            new String[] {"areas", "sasbo_areas"},
            new String[] {"employerGroups", "employer_groups"},
            new String[] {"employers", "employers"},
            new String[] {"tableCodes", "table_codes"},
            new String[] {"committeeProvinces", "committee_provinces"},
            new String[] {"committees", "committees"},
            new String[] {"suspensionCodes", "suspension_codes"},
            new String[] {"deletionCodes", "deletion_codes WHERE sasbo_code IS NOT NULL OR parent_id IS NOT NULL"},
            new String[] {"workplaces", "workplaces"});

    /** How many rows each list holds, by the list's key -- one statement for all of them. */
    public Map<String, Long> counts() {
        StringBuilder select = new StringBuilder("SELECT ");
        for (int i = 0; i < COUNTED.size(); i++) {
            select.append(i == 0 ? "" : ", ").append("(SELECT count(*) FROM ").append(COUNTED.get(i)[1])
                    .append(") AS c").append(i);
        }
        Map<String, Long> found = new LinkedHashMap<>();
        jdbc.query(select.toString(), rs -> {
            for (int i = 0; i < COUNTED.size(); i++) {
                found.put(COUNTED.get(i)[0], rs.getLong("c" + i));
            }
        });
        return found;
    }

    /** The list keys {@link #counts} answers, in the order the seeder writes them. */
    public static List<String> countedLists() {
        return COUNTED.stream().map(pair -> pair[0]).toList();
    }

    /** One row of {@code reference_lists}. */
    public record ListRecord(LocalDate printedOn, LocalDate confirmedOn, String outcome, String failure,
                             Integer rowCount, Instant recordedAt) {
    }
}
