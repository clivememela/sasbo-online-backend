package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Who gets what at month end (October build): the recipients of the regions' packs, each bank's file and each
 * provider's list -- read and written by plain statements, each one statement, both databases accepting them. A save
 * names the version it read: an update that finds another version changes nothing and says so (0 rows), and the
 * service answers {@code SETTING_CHANGED_SINCE}. Nothing here opens a transaction; the service does where a save is
 * more than one statement.
 */
@Repository
public class MonthEndSendSettingsRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public MonthEndSendSettingsRepository(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    /** A recipient of a region's pack, with its region's name as SASBO's list prints it. */
    public record PackSet(long id, String code, String label, String regionCode, String regionName,
                          List<String> areaLetters, String position, String email, boolean active, int version,
                          String updatedBy, Instant updatedAt) {

        /** Whether the set covers its whole region -- it names no areas. */
        public boolean wholeRegion() {
            return areaLetters.isEmpty();
        }
    }

    /** What a recipient is saved as. */
    public record PackSetValues(String label, String regionCode, List<String> areaLetters, String position,
                                String email, boolean active) {
    }

    /** One bank's monthly file, and the table codes it is made from. */
    public record Bank(long id, String code, String name, List<Integer> tableCodes, boolean fullList, String layout,
                       boolean active, int version, String updatedBy, Instant updatedAt) {
    }

    /** What a bank's file is saved as. */
    public record BankValues(String name, List<Integer> tableCodes, boolean fullList, String layout, boolean active) {
    }

    /** One provider's monthly list. */
    public record Provider(long id, String code, String name, String layout, String delivery, boolean active,
                           int version, String updatedBy, Instant updatedAt) {
    }

    // ------------------------------------------------------------------------------------------------ the pack sets

    private static final String PACK_SETS = """
            SELECT s.id, s.code, s.label, s.region_code, r.name AS region_name, s.area_letters, s.position, s.email,
              s.active, s.version, s.updated_by, s.updated_at
            FROM month_end_pack_sets s
            LEFT JOIN sasbo_regions r ON r.code = s.region_code
            """;

    /** Every recipient of a region's pack, by code. */
    public List<PackSet> packSets() {
        return jdbc.query(PACK_SETS + " ORDER BY s.code", new MapSqlParameterSource(),
                MonthEndSendSettingsRepository::packSet);
    }

    public Optional<PackSet> packSet(String code) {
        return jdbc.query(PACK_SETS + " WHERE s.code = :code", new MapSqlParameterSource("code", code),
                MonthEndSendSettingsRepository::packSet).stream().findFirst();
    }

    public long insertPackSet(String code, PackSetValues values, String actor, Instant at) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO month_end_pack_sets (code, label, region_code, area_letters, position, email, active, version,
                  updated_by, updated_at)
                VALUES (:code, :label, :region, :areas, :position, :email, :active, 0, :actor, :at)
                """, packSetArguments(values, actor, at).addValue("code", code), key, new String[] {"id"});
        return idOf(key);
    }

    /** @return the rows changed: 0 where the set is not there, or is at another version than {@code version} */
    public int updatePackSet(String code, int version, PackSetValues values, String actor, Instant at) {
        return jdbc.update("""
                UPDATE month_end_pack_sets SET label = :label, region_code = :region, area_letters = :areas,
                  position = :position, email = :email, active = :active, version = version + 1, updated_by = :actor,
                  updated_at = :at
                WHERE code = :code AND version = :version
                """, packSetArguments(values, actor, at).addValue("code", code).addValue("version", version));
    }

    private static MapSqlParameterSource packSetArguments(PackSetValues values, String actor, Instant at) {
        return new MapSqlParameterSource()
                .addValue("label", values.label())
                .addValue("region", values.regionCode())
                .addValue("areas", values.areaLetters().isEmpty() ? null : String.join(",", values.areaLetters()))
                .addValue("position", values.position())
                .addValue("email", values.email())
                .addValue("active", values.active())
                .addValue("actor", actor)
                .addValue("at", utc(at));
    }

    private static PackSet packSet(ResultSet rs, int n) throws SQLException {
        String areas = rs.getString("area_letters");
        return new PackSet(rs.getLong("id"), rs.getString("code"), rs.getString("label"), rs.getString("region_code"),
                rs.getString("region_name"), areas == null || areas.isBlank() ? List.of()
                : Arrays.stream(areas.split(",")).map(String::trim).filter(a -> !a.isEmpty()).toList(),
                rs.getString("position"), rs.getString("email"), rs.getBoolean("active"), rs.getInt("version"),
                rs.getString("updated_by"), rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    /** Whether a region code is on SASBO's list here. */
    public boolean regionOnRecord(String code) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM sasbo_regions WHERE code = :code",
                new MapSqlParameterSource("code", code), Long.class);
        return n != null && n > 0;
    }

    /** The area letters SASBO's list places in a region, in letter order. */
    public List<String> areasOf(String regionCode) {
        return jdbc.queryForList("""
                SELECT a.letter FROM sasbo_areas a JOIN sasbo_regions r ON r.id = a.region_id
                WHERE r.code = :code ORDER BY a.letter
                """, new MapSqlParameterSource("code", regionCode), String.class);
    }

    // ------------------------------------------------------------------------------------------------ the banks

    /** Every bank's file, by code, each with its table codes in order. */
    public List<Bank> banks() {
        Map<Long, List<Integer>> codes = new HashMap<>();
        jdbc.query("SELECT bank_id, table_code FROM month_end_bank_table_codes ORDER BY bank_id, table_code",
                new MapSqlParameterSource(), rs -> {
                    codes.computeIfAbsent(rs.getLong("bank_id"), k -> new ArrayList<>()).add(rs.getInt("table_code"));
                });
        return jdbc.query("SELECT * FROM month_end_banks ORDER BY code", new MapSqlParameterSource(),
                (rs, n) -> bank(rs, codes.getOrDefault(rs.getLong("id"), List.of())));
    }

    public Optional<Bank> bank(String code) {
        return banks().stream().filter(b -> b.code().equals(code)).findFirst();
    }

    public long insertBank(String code, BankValues values, String actor, Instant at) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO month_end_banks (code, name, full_list, layout, active, version, updated_by, updated_at)
                VALUES (:code, :name, :fullList, :layout, :active, 0, :actor, :at)
                """, bankArguments(values, actor, at).addValue("code", code), key, new String[] {"id"});
        return idOf(key);
    }

    /** @return the rows changed: 0 where the bank is not there, or is at another version than {@code version} */
    public int updateBank(String code, int version, BankValues values, String actor, Instant at) {
        return jdbc.update("""
                UPDATE month_end_banks SET name = :name, full_list = :fullList, layout = :layout, active = :active,
                  version = version + 1, updated_by = :actor, updated_at = :at
                WHERE code = :code AND version = :version
                """, bankArguments(values, actor, at).addValue("code", code).addValue("version", version));
    }

    /** A bank's table codes, replaced whole: the old rows out, the new in, by batch. Run inside the save's transaction. */
    public void replaceTableCodes(long bankId, Collection<Integer> tableCodes) {
        jdbc.update("DELETE FROM month_end_bank_table_codes WHERE bank_id = :bank", new MapSqlParameterSource("bank",
                bankId));
        MapSqlParameterSource[] rows = tableCodes.stream()
                .map(code -> new MapSqlParameterSource("bank", bankId).addValue("code", code))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate("INSERT INTO month_end_bank_table_codes (bank_id, table_code) VALUES (:bank, :code)", rows);
    }

    /** Which of {@code tableCodes} another bank's file already takes: table code to that bank's code. */
    public Map<Integer, String> heldElsewhere(Collection<Integer> tableCodes, String exceptBank) {
        Map<Integer, String> held = new LinkedHashMap<>();
        if (tableCodes.isEmpty()) {
            return held;
        }
        jdbc.query("""
                SELECT c.table_code, b.code FROM month_end_bank_table_codes c JOIN month_end_banks b ON b.id = c.bank_id
                WHERE c.table_code IN (:codes) AND b.code <> :except ORDER BY c.table_code
                """, new MapSqlParameterSource("codes", tableCodes).addValue("except", exceptBank == null ? "" : exceptBank),
                rs -> {
                    held.put(rs.getInt(1), rs.getString(2));
                });
        return held;
    }

    /** Which of {@code tableCodes} are on SASBO's list here. */
    public Set<Integer> tableCodesOnRecord(Collection<Integer> tableCodes) {
        if (tableCodes.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.queryForList("SELECT code FROM table_codes WHERE code IN (:codes)",
                new MapSqlParameterSource("codes", tableCodes), Integer.class));
    }

    private static MapSqlParameterSource bankArguments(BankValues values, String actor, Instant at) {
        return new MapSqlParameterSource()
                .addValue("name", values.name())
                .addValue("fullList", values.fullList())
                .addValue("layout", values.layout())
                .addValue("active", values.active())
                .addValue("actor", actor)
                .addValue("at", utc(at));
    }

    private static Bank bank(ResultSet rs, List<Integer> tableCodes) throws SQLException {
        return new Bank(rs.getLong("id"), rs.getString("code"), rs.getString("name"), List.copyOf(tableCodes),
                rs.getBoolean("full_list"), rs.getString("layout"), rs.getBoolean("active"), rs.getInt("version"),
                rs.getString("updated_by"), rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    // ------------------------------------------------------------------------------------------------ the providers

    /** Every provider, by code. */
    public List<Provider> providers() {
        return jdbc.query("SELECT * FROM month_end_providers ORDER BY code", new MapSqlParameterSource(),
                MonthEndSendSettingsRepository::provider);
    }

    public Optional<Provider> provider(String code) {
        return jdbc.query("SELECT * FROM month_end_providers WHERE code = :code", new MapSqlParameterSource("code", code),
                MonthEndSendSettingsRepository::provider).stream().findFirst();
    }

    public long insertProvider(String code, String name, String layout, String delivery, boolean active, String actor,
                               Instant at) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO month_end_providers (code, name, layout, delivery, active, version, updated_by, updated_at)
                VALUES (:code, :name, :layout, :delivery, :active, 0, :actor, :at)
                """, new MapSqlParameterSource("code", code).addValue("name", name).addValue("layout", layout)
                .addValue("delivery", delivery).addValue("active", active).addValue("actor", actor)
                .addValue("at", utc(at)), key, new String[] {"id"});
        return idOf(key);
    }

    /** @return the rows changed: 0 where the provider is not there, or is at another version than {@code version} */
    public int updateProviderActive(String code, int version, boolean active, String actor, Instant at) {
        return jdbc.update("""
                UPDATE month_end_providers SET active = :active, version = version + 1, updated_by = :actor,
                  updated_at = :at
                WHERE code = :code AND version = :version
                """, new MapSqlParameterSource("code", code).addValue("version", version).addValue("active", active)
                .addValue("actor", actor).addValue("at", utc(at)));
    }

    private static Provider provider(ResultSet rs, int n) throws SQLException {
        return new Provider(rs.getLong("id"), rs.getString("code"), rs.getString("name"), rs.getString("layout"),
                rs.getString("delivery"), rs.getBoolean("active"), rs.getInt("version"), rs.getString("updated_by"),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static long idOf(KeyHolder key) {
        Number id = key.getKey();
        if (id == null) {
            throw new IllegalStateException("a setting was written and no id came back");
        }
        return id.longValue();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
