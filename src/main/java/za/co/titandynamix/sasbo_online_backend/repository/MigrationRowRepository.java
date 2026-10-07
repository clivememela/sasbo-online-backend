package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.titandynamix.sasbo_online_backend.legacy.reconcile.Dimension;
import za.co.titandynamix.sasbo_online_backend.legacy.reconcile.Reconciliation;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.RuleCode;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A batch's staged rows (V80): written in JDBC batches as a file is read, read back a page at a time by their id --
 * never all at once -- and checked in place. The rows of one file are checked against one another and against the
 * members on record by four statements here, never by holding the file's identity numbers in memory.
 *
 * <p>Every fragment of every statement is written here; a request chooses an outcome and a rule's code -- both from
 * fixed vocabularies -- and binds its values. Both databases accept every statement here.
 */
@Repository
public class MigrationRowRepository {

    /** How long a list of problem codes may grow when a statement adds one: past it, the row is held back anyway. */
    private static final int CODES_CEILING = 1900;

    /** A row as read: its number in the file, its values as JSON, what went wrong reading it. */
    public record Staged(long id, int rowNumber, String rawJson, String readProblems) {

        @Override
        public String toString() {
            return "Staged[" + id + ", row " + rowNumber + "]";
        }
    }

    /** A row to stage, before it has an id. */
    public record ToStage(int rowNumber, String rawJson, String readProblems) {

        @Override
        public String toString() {
            return "ToStage[row " + rowNumber + "]";
        }
    }

    /** A row after the check: everything the check writes back to it. */
    public record Checked(long id, String outcome, String errorCodes, String warningCodes, String normalisedJson,
                          String identityKey, String membershipKey, Long membershipSeq, String statusKind,
                          String areaLetter, Integer tableCode, Long workplaceId, Integer suspensionCode,
                          Integer deletionCode, LocalDate joinDate, LocalDate leftOn) {

        @Override
        public String toString() {
            return "Checked[" + id + ", " + outcome + "]";
        }
    }

    /** A row a move writes: its fields as JSON, its membership number as it will be written. */
    public record Movable(long id, int rowNumber, String normalisedJson, String membershipKey) {

        @Override
        public String toString() {
            return "Movable[" + id + ", row " + rowNumber + "]";
        }
    }

    /** A row with problems, for the report: codes and numbers only. */
    public record Problems(long id, int rowNumber, String errorCodes, String warningCodes) {
    }

    /** A row as the list of rows shows it to someone allowed to see it. */
    public record Listed(long id, int rowNumber, String outcome, String errorCodes, String warningCodes, String rawJson,
                         String normalisedJson) {

        @Override
        public String toString() {
            return "Listed[" + id + ", row " + rowNumber + "]";
        }
    }

    /** A page of listed rows, and how many there are. */
    public record Slice(List<Listed> rows, long total) {
    }

    private final NamedParameterJdbcTemplate named;
    private final JdbcTemplate jdbc;

    public MigrationRowRepository(NamedParameterJdbcTemplate named) {
        this.named = named;
        this.jdbc = named.getJdbcTemplate();
    }

    // ------------------------------------------------------------------------------------------------ staging

    /** One chunk of rows, in one JDBC batch. */
    public void stage(long batchId, List<ToStage> rows) {
        List<Object[]> args = new ArrayList<>(rows.size());
        for (ToStage row : rows) {
            args.add(new Object[] {batchId, row.rowNumber(), row.rawJson(), row.readProblems()});
        }
        jdbc.batchUpdate("INSERT INTO migration_rows (batch_id, row_number, raw_json, read_problems) VALUES (?, ?, ?, ?)",
                args, new int[] {Types.BIGINT, Types.INTEGER, Types.VARCHAR, Types.VARCHAR});
    }

    /** The next page of rows after {@code afterId}, in the order they were read. */
    public List<Staged> stagedPage(long batchId, long afterId, int size) {
        return named.query("""
                SELECT id, row_number, raw_json, read_problems FROM migration_rows
                WHERE batch_id = :batch AND id > :after ORDER BY id LIMIT :size
                """, new MapSqlParameterSource("batch", batchId).addValue("after", afterId).addValue("size", size),
                (rs, n) -> new Staged(rs.getLong("id"), rs.getInt("row_number"), rs.getString("raw_json"),
                        rs.getString("read_problems")));
    }

    // ------------------------------------------------------------------------------------------------ the check

    /** One page of checked rows written back, in one JDBC batch. */
    public void saveChecked(List<Checked> rows) {
        List<Object[]> args = new ArrayList<>(rows.size());
        for (Checked row : rows) {
            args.add(new Object[] {row.outcome(), row.errorCodes(), row.warningCodes(), row.normalisedJson(),
                    row.identityKey(), row.membershipKey(), row.membershipSeq(), row.statusKind(), row.areaLetter(),
                    row.tableCode(), row.workplaceId(), row.suspensionCode(), row.deletionCode(),
                    row.joinDate() == null ? null : Date.valueOf(row.joinDate()),
                    row.leftOn() == null ? null : Date.valueOf(row.leftOn()), row.id()});
        }
        jdbc.batchUpdate("""
                UPDATE migration_rows SET outcome = ?, error_codes = ?, warning_codes = ?, normalised_json = ?,
                  identity_key = ?, membership_key = ?, membership_seq = ?, status_kind = ?, area_letter = ?,
                  table_code = ?, workplace_id = ?, suspension_code = ?, deletion_code = ?, join_date = ?, left_on = ?
                WHERE id = ?
                """, args, new int[] {Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                Types.VARCHAR, Types.BIGINT, Types.VARCHAR, Types.VARCHAR, Types.INTEGER, Types.BIGINT, Types.INTEGER,
                Types.INTEGER, Types.DATE, Types.DATE, Types.BIGINT});
    }

    /**
     * The four checks about two rows, as statements: an identity number or a membership number twice in the file, or
     * already a member's. Each marks every row it concerns -- both rows of a pair: which of two is the member is SASBO's
     * to say (MIG-19, MIG-20) -- and never a row already moved, nor twice.
     *
     * @param movableOnly only rows that would still move -- the re-check a move makes for members captured since
     * @return the rows marked, by the code they were marked with
     */
    public Map<RuleCode, Integer> markAcrossRows(long batchId, boolean movableOnly) {
        Map<RuleCode, Integer> marked = new EnumMap<>(RuleCode.class);
        String scope = movableOnly ? " AND outcome IN ('VALID', 'WARNING')" : "";
        String unlinked = " AND NOT EXISTS (SELECT 1 FROM migration_member_links k WHERE k.row_id = migration_rows.id)";
        if (!movableOnly) {
            marked.put(RuleCode.ID_NUMBER_DUPLICATE_IN_BATCH, mark(batchId, RuleCode.ID_NUMBER_DUPLICATE_IN_BATCH,
                    "identityNumber", " AND identity_key IN (SELECT d.identity_key FROM migration_rows d"
                            + " WHERE d.batch_id = :batch AND d.identity_key IS NOT NULL"
                            + " GROUP BY d.identity_key HAVING COUNT(*) > 1)"));
            marked.put(RuleCode.MEMBERSHIP_NUMBER_DUPLICATE_IN_BATCH, mark(batchId,
                    RuleCode.MEMBERSHIP_NUMBER_DUPLICATE_IN_BATCH, "membershipNumber",
                    " AND membership_key IN (SELECT d.membership_key FROM migration_rows d"
                            + " WHERE d.batch_id = :batch AND d.membership_key IS NOT NULL"
                            + " GROUP BY d.membership_key HAVING COUNT(*) > 1)"));
        }
        marked.put(RuleCode.ID_NUMBER_ALREADY_MEMBER, mark(batchId, RuleCode.ID_NUMBER_ALREADY_MEMBER, "identityNumber",
                scope + unlinked + " AND identity_key IS NOT NULL AND EXISTS (SELECT 1 FROM members m"
                        + " WHERE m.identity_number = migration_rows.identity_key)"));
        marked.put(RuleCode.MEMBERSHIP_NUMBER_ALREADY_USED, mark(batchId, RuleCode.MEMBERSHIP_NUMBER_ALREADY_USED,
                "membershipNumber", scope + unlinked + " AND membership_key IS NOT NULL AND EXISTS (SELECT 1 FROM members m"
                        + " WHERE m.membership_number = migration_rows.membership_key)"));
        return marked;
    }

    private int mark(long batchId, RuleCode code, String field, String where) {
        String token = code.name() + "@" + field;
        return named.update("""
                UPDATE migration_rows SET outcome = 'ERROR',
                  error_codes = CASE WHEN error_codes IS NULL THEN :token
                                     WHEN LENGTH(error_codes) > :ceiling THEN error_codes
                                     ELSE error_codes || ',' || :token END
                WHERE batch_id = :batch AND outcome IS NOT NULL
                  AND (error_codes IS NULL OR error_codes NOT LIKE :seen ESCAPE '!')
                """ + where, new MapSqlParameterSource("batch", batchId).addValue("token", token)
                .addValue("ceiling", CODES_CEILING).addValue("seen", "%" + token.replace("_", "!_") + "%"));
    }

    /** How many rows of each outcome. */
    public Map<String, Integer> countByOutcome(long batchId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        named.query("SELECT outcome, COUNT(*) AS n FROM migration_rows WHERE batch_id = :batch GROUP BY outcome",
                new MapSqlParameterSource("batch", batchId), rs -> {
                    counts.put(rs.getString("outcome"), rs.getInt("n"));
                });
        return counts;
    }

    // ------------------------------------------------------------------------------------------------ the report

    /** The next page of rows with a problem: their numbers and codes only. */
    public List<Problems> problemsPage(long batchId, long afterId, int size) {
        return named.query("""
                SELECT id, row_number, error_codes, warning_codes FROM migration_rows
                WHERE batch_id = :batch AND id > :after AND (error_codes IS NOT NULL OR warning_codes IS NOT NULL)
                ORDER BY id LIMIT :size
                """, new MapSqlParameterSource("batch", batchId).addValue("after", afterId).addValue("size", size),
                (rs, n) -> new Problems(rs.getLong("id"), rs.getInt("row_number"), rs.getString("error_codes"),
                        rs.getString("warning_codes")));
    }

    /**
     * One page of rows in the order of the file, narrowed to an outcome and to a rule's code -- both from fixed
     * vocabularies; a code's underscores escaped, so it matches only itself.
     */
    public Slice list(long batchId, String outcome, RuleCode code, int page, int size) {
        MapSqlParameterSource args = new MapSqlParameterSource("batch", batchId).addValue("size", size)
                .addValue("offset", (long) page * size);
        StringBuilder where = new StringBuilder("WHERE batch_id = :batch");
        if (outcome != null) {
            where.append(" AND outcome = :outcome");
            args.addValue("outcome", outcome);
        }
        if (code != null) {
            where.append(" AND (COALESCE(error_codes, '') || ',' || COALESCE(warning_codes, '')) LIKE :code ESCAPE '!'");
            args.addValue("code", "%" + code.name().replace("_", "!_") + "%");
        }
        List<long[]> totals = new ArrayList<>();
        List<Listed> rows = named.query("SELECT id, row_number, outcome, error_codes, warning_codes, raw_json,"
                + " normalised_json, COUNT(*) OVER () AS total_rows FROM migration_rows " + where
                + " ORDER BY row_number LIMIT :size OFFSET :offset", args, (rs, n) -> {
                    totals.add(new long[] {rs.getLong("total_rows")});
                    return new Listed(rs.getLong("id"), rs.getInt("row_number"), rs.getString("outcome"),
                            rs.getString("error_codes"), rs.getString("warning_codes"), rs.getString("raw_json"),
                            rs.getString("normalised_json"));
                });
        long total = totals.isEmpty() ? 0 : totals.get(0)[0];
        return new Slice(rows, total);
    }

    // ------------------------------------------------------------------------------------------------ the move

    /** The next page of rows a move writes: checked to move, past the cursor, and not moved already. */
    public List<Movable> movablePage(long batchId, long afterId, int size) {
        return named.query("""
                SELECT r.id, r.row_number, r.normalised_json, r.membership_key FROM migration_rows r
                WHERE r.batch_id = :batch AND r.id > :after AND r.outcome IN ('VALID', 'WARNING')
                  AND NOT EXISTS (SELECT 1 FROM migration_member_links k WHERE k.row_id = r.id)
                ORDER BY r.id LIMIT :size
                """, new MapSqlParameterSource("batch", batchId).addValue("after", afterId).addValue("size", size),
                (rs, n) -> new Movable(rs.getLong("id"), rs.getInt("row_number"), rs.getString("normalised_json"),
                        rs.getString("membership_key")));
    }

    /** The highest membership number of the rows that move, as a number -- the capture sequence is moved past it. */
    public long maxMembershipSeq(long batchId) {
        Long max = named.queryForObject("SELECT MAX(membership_seq) FROM migration_rows WHERE batch_id = :batch"
                + " AND outcome IN ('VALID', 'WARNING')", new MapSqlParameterSource("batch", batchId), Long.class);
        return max == null ? 0 : max;
    }

    /** A membership number given here, where the file had none: written to the row, so its member is traced by it. */
    public void allocated(long rowId, String membershipKey, long seq) {
        named.update("UPDATE migration_rows SET membership_key = :key, membership_seq = :seq WHERE id = :id",
                new MapSqlParameterSource("id", rowId).addValue("key", membershipKey).addValue("seq", seq));
    }

    // ------------------------------------------------------------------------------------------------ the reconciliation

    /**
     * What a batch counts, by every dimension of SASBO's month end -- each key as {@link Reconciliation#key} holds it.
     * The rows that move are counted by measure; the rows held back by errors are counted beside them, by key: where a
     * figure differs, they are most often why. One statement a dimension; the grouping fragment is chosen from the
     * dimension, never from a request.
     */
    public Map<Dimension, Map<String, Reconciliation.Counts>> figures(long batchId, LocalDate monthStart,
                                                                      LocalDate nextMonth) {
        Map<Dimension, Map<String, Reconciliation.Counts>> figures = new EnumMap<>(Dimension.class);
        MapSqlParameterSource args = new MapSqlParameterSource("batch", batchId)
                .addValue("start", Date.valueOf(monthStart)).addValue("next", Date.valueOf(nextMonth));
        for (Dimension dimension : Dimension.values()) {
            Map<String, Reconciliation.Counts> byKey = new LinkedHashMap<>();
            String key = keyOf(dimension);
            // The whole membership is one line: an aggregate with no grouping (PostgreSQL refuses to group by a constant).
            String groupBy = dimension == Dimension.TOTAL ? "" : "GROUP BY " + key;
            named.query("""
                    SELECT %1$s AS item_key,
                      SUM(CASE WHEN r.outcome IN ('VALID', 'WARNING') AND r.status_kind IN ('N', 'S') THEN 1 ELSE 0 END) AS on_record,
                      SUM(CASE WHEN r.outcome IN ('VALID', 'WARNING') AND r.status_kind = 'S' THEN 1 ELSE 0 END) AS suspended,
                      SUM(CASE WHEN r.outcome IN ('VALID', 'WARNING') AND r.status_kind = 'N' THEN 1 ELSE 0 END) AS members,
                      SUM(CASE WHEN r.outcome IN ('VALID', 'WARNING') AND r.join_date >= :start AND r.join_date < :next
                               THEN 1 ELSE 0 END) AS joined,
                      SUM(CASE WHEN r.outcome IN ('VALID', 'WARNING') AND r.status_kind = 'L' AND r.left_on >= :start
                               AND r.left_on < :next THEN 1 ELSE 0 END) AS left_n,
                      SUM(CASE WHEN r.outcome = 'ERROR' THEN 1 ELSE 0 END) AS held_back
                    FROM migration_rows r
                    LEFT JOIN table_codes t ON t.code = r.table_code
                    LEFT JOIN employers e ON e.id = t.employer_id
                    LEFT JOIN employer_groups g ON g.id = e.group_id
                    LEFT JOIN sasbo_areas a ON a.letter = r.area_letter
                    LEFT JOIN sasbo_regions rg ON rg.id = a.region_id
                    WHERE r.batch_id = :batch
                    %2$s
                    """.formatted(key, groupBy), args, rs -> {
                        String itemKey = Reconciliation.key(dimension, rs.getString("item_key"));
                        Reconciliation.Counts counts = new Reconciliation.Counts(rs.getLong("on_record"),
                                rs.getLong("suspended"), rs.getLong("members"), rs.getLong("joined"), rs.getLong("left_n"),
                                rs.getLong("held_back"));
                        byKey.merge(itemKey, counts, MigrationRowRepository::sum);
                    });
            figures.put(dimension, byKey);
        }
        return figures;
    }

    private static String keyOf(Dimension dimension) {
        return switch (dimension) {
            case TOTAL -> "CAST('' AS VARCHAR(1))";
            case TABLE_CODE -> "CAST(r.table_code AS VARCHAR(12))";
            case EMPLOYER -> "e.name";
            case EMPLOYER_GROUP -> "g.name";
            case AREA -> "r.area_letter";
            case REGION -> "rg.code";
            case SUSPENSION_CODE -> "CAST(r.suspension_code AS VARCHAR(12))";
            case DELETION_CODE -> "CAST(r.deletion_code AS VARCHAR(12))";
        };
    }

    private static Reconciliation.Counts sum(Reconciliation.Counts a, Reconciliation.Counts b) {
        return new Reconciliation.Counts(a.onRecord() + b.onRecord(), a.suspended() + b.suspended(),
                a.members() + b.members(), a.joined() + b.joined(), a.left() + b.left(), a.heldBack() + b.heldBack());
    }

    static LocalDate date(ResultSet rs, String column) throws SQLException {
        Date date = rs.getDate(column);
        return date == null ? null : date.toLocalDate();
    }
}
