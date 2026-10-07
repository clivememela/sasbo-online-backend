package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.ReferenceIndex;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * SASBO's lists as a check looks them up, read once a check: the workplaces with their areas and table codes, the table
 * codes, the area letters, and SASBO's deletion and suspension codes by their numbers -- never the table's id (RC7). A
 * workplace that is closed still stands for the members who worked there (MR-ROB-24).
 */
@Repository
public class MigrationReferenceRepository {

    private final JdbcTemplate jdbc;

    public MigrationReferenceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ReferenceIndex load() {
        Map<Integer, ReferenceIndex.Workplace> workplaces = new HashMap<>();
        jdbc.query("SELECT w.id, w.ra_number, a.letter, t.code FROM workplaces w JOIN sasbo_areas a ON a.id = w.area_id"
                + " LEFT JOIN table_codes t ON t.id = w.table_code_id", rs -> {
                    int code = rs.getInt("code");
                    boolean noCode = rs.wasNull();
                    workplaces.put(rs.getInt("ra_number"), new ReferenceIndex.Workplace(rs.getLong("id"),
                            rs.getString("letter"), noCode ? null : code));
                });
        Set<Integer> tableCodes = new HashSet<>(jdbc.queryForList("SELECT code FROM table_codes", Integer.class));
        Set<String> areas = new HashSet<>(jdbc.queryForList("SELECT letter FROM sasbo_areas", String.class));
        Map<Integer, Long> deletion = new HashMap<>();
        jdbc.query("SELECT sasbo_code, id FROM deletion_codes WHERE sasbo_code IS NOT NULL",
                rs -> {
                    deletion.put(rs.getInt("sasbo_code"), rs.getLong("id"));
                });
        Map<Integer, Long> suspension = new HashMap<>();
        jdbc.query("SELECT code, id FROM suspension_codes", rs -> {
            suspension.put(rs.getInt("code"), rs.getLong("id"));
        });
        return new ReferenceIndex(workplaces, tableCodes, areas, deletion, suspension);
    }
}
