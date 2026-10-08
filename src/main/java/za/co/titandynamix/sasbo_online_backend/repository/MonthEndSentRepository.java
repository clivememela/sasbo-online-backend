package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * The files that left SASBO, and the record of what was sent to whom (October build) -- a region's pack, a bank's or a
 * provider's file, and every delivery of one. One statement each; a file's bytes are written once and streamed back
 * out, never read into a list of rows. Nothing here opens a transaction.
 */
@Repository
public class MonthEndSentRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public MonthEndSentRepository(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    // ------------------------------------------------------------------------------------------------ region packs

    /** A region's pack without its bytes: what the screen lists. Sizes are the database's count of the stored bytes. */
    public record RegionPack(long id, long monthId, LocalDate monthStart, int takeNumber, String setCode,
                             int generation, String setLabel, String regionCode, List<String> areaLetters, String position, int joined,
                             int left, int suspended, String pdfName, long pdfBytes, String pdfSha256, String xlsxName,
                             long xlsxBytes, String xlsxSha256, String producedBy, Instant producedAt) {
    }

    /** What a pack is written with. */
    public record NewRegionPack(long monthId, LocalDate monthStart, String setCode, String setLabel, String regionCode,
                                List<String> areaLetters, String position, int joined, int left, int suspended,
                                String pdfName, byte[] pdf, String pdfSha256, String xlsxName, byte[] xlsx,
                                String xlsxSha256, String producedBy, Instant producedAt) {

        /** Never the bytes: a pack names members. */
        @Override
        public String toString() {
            return "NewRegionPack[" + setCode + ", " + monthStart + "]";
        }
    }

    private static final String REGION_PACKS = """
            SELECT p.id, p.month_id, p.month_start, mm.take_number, p.set_code, p.generation, p.set_label,
              p.region_code,
              p.area_letters, p.position, p.joined, p.left_count, p.suspended, p.pdf_name,
              OCTET_LENGTH(p.pdf_file) AS pdf_bytes, p.pdf_sha256, p.xlsx_name, OCTET_LENGTH(p.xlsx_file) AS xlsx_bytes,
              p.xlsx_sha256, p.produced_by, p.produced_at
            FROM month_end_region_packs p
            JOIN month_end_months mm ON mm.id = p.month_id
            """;

    /**
     * Writes a pack as {@code generation} of its take and set -- refused by {@code month_end_region_packs_one_a_generation}
     * where another ask wrote that generation first.
     */
    public long insertRegionPack(NewRegionPack pack, int generation) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO month_end_region_packs (month_id, month_start, set_code, generation, set_label, region_code,
                  area_letters, position, joined, left_count, suspended, pdf_name, pdf_file, pdf_sha256, xlsx_name,
                  xlsx_file, xlsx_sha256, produced_by, produced_at)
                VALUES (:monthId, :monthStart, :setCode, :generation, :setLabel, :region, :areas, :position, :joined,
                  :left, :suspended, :pdfName, :pdf, :pdfSha256, :xlsxName, :xlsx, :xlsxSha256, :producedBy, :producedAt)
                """, new MapSqlParameterSource()
                .addValue("generation", generation)
                .addValue("monthId", pack.monthId())
                .addValue("monthStart", pack.monthStart())
                .addValue("setCode", pack.setCode())
                .addValue("setLabel", pack.setLabel())
                .addValue("region", pack.regionCode())
                .addValue("areas", pack.areaLetters().isEmpty() ? null : String.join(",", pack.areaLetters()))
                .addValue("position", pack.position())
                .addValue("joined", pack.joined())
                .addValue("left", pack.left())
                .addValue("suspended", pack.suspended())
                .addValue("pdfName", pack.pdfName())
                .addValue("pdf", pack.pdf())
                .addValue("pdfSha256", pack.pdfSha256())
                .addValue("xlsxName", pack.xlsxName())
                .addValue("xlsx", pack.xlsx())
                .addValue("xlsxSha256", pack.xlsxSha256())
                .addValue("producedBy", pack.producedBy())
                .addValue("producedAt", utc(pack.producedAt())), key, new String[] {"id"});
        return idOf(key);
    }

    /** The newest pack of a take for each set -- one statement, newest first within a set. */
    public List<RegionPack> regionPacksOf(long monthId) {
        return jdbc.query(REGION_PACKS + " WHERE p.month_id = :monthId ORDER BY p.set_code, p.id DESC",
                new MapSqlParameterSource("monthId", monthId), MonthEndSentRepository::regionPack);
    }

    /** The newest pack of a take for one set. */
    public Optional<RegionPack> newestRegionPack(long monthId, String setCode) {
        return jdbc.query(REGION_PACKS + " WHERE p.month_id = :monthId AND p.set_code = :set ORDER BY p.id DESC",
                new MapSqlParameterSource("monthId", monthId).addValue("set", setCode),
                MonthEndSentRepository::regionPack).stream().findFirst();
    }

    public Optional<RegionPack> regionPack(long id) {
        return jdbc.query(REGION_PACKS + " WHERE p.id = :id", new MapSqlParameterSource("id", id),
                MonthEndSentRepository::regionPack).stream().findFirst();
    }

    /** A pack's file read into memory -- for an e-mail's attachment, which a mail server takes whole. */
    public Optional<byte[]> regionPackBytes(long id, boolean pdf) {
        return jdbc.query("SELECT " + (pdf ? "pdf_file" : "xlsx_file") + " FROM month_end_region_packs WHERE id = :id",
                new MapSqlParameterSource("id", id), (rs, n) -> rs.getBytes(1)).stream().findFirst();
    }

    /** A pack's file written to {@code out} as the driver hands it over: a download, never a second copy in memory. */
    public boolean streamRegionPack(long id, boolean pdf, OutputStream out) {
        return stream("SELECT " + (pdf ? "pdf_file" : "xlsx_file") + " FROM month_end_region_packs WHERE id = :id",
                id, out);
    }

    private static RegionPack regionPack(ResultSet rs, int n) throws SQLException {
        String areas = rs.getString("area_letters");
        return new RegionPack(rs.getLong("id"), rs.getLong("month_id"), rs.getDate("month_start").toLocalDate(),
                rs.getInt("take_number"), rs.getString("set_code"), rs.getInt("generation"), rs.getString("set_label"),
                rs.getString("region_code"), areas == null || areas.isBlank() ? List.of()
                : Arrays.asList(areas.split(",")), rs.getString("position"), rs.getInt("joined"),
                rs.getInt("left_count"), rs.getInt("suspended"), rs.getString("pdf_name"), rs.getLong("pdf_bytes"),
                rs.getString("pdf_sha256"), rs.getString("xlsx_name"), rs.getLong("xlsx_bytes"),
                rs.getString("xlsx_sha256"), rs.getString("produced_by"),
                rs.getObject("produced_at", OffsetDateTime.class).toInstant());
    }

    // ------------------------------------------------------------------------------------------------ files

    /** A bank's or provider's file without its bytes. */
    public record SentFile(long id, long monthId, LocalDate monthStart, int takeNumber, String kind,
                           String recipientCode, String recipientName, String layout, boolean fullList,
                           String fileName, String format, String protection, int rows, int newRows, int deletedRows,
                           int leftOut, long bytes, String sha256, String producedBy, Instant producedAt,
                           long downloads, Instant lastDeliveredAt) {
    }

    /** What a file is written with: its bytes are ALREADY ENCRYPTED, which the table's own rule insists on. */
    public record NewFile(long monthId, LocalDate monthStart, String kind, String recipientCode, String recipientName,
                          String layout, boolean fullList, String fileName, String format, String protection, int rows,
                          int newRows, int deletedRows, int leftOut, byte[] content, String sha256,
                          String producedBy, Instant producedAt) {

        /** Never the bytes -- encrypted as they are. */
        @Override
        public String toString() {
            return "NewFile[" + kind + ", " + recipientCode + ", " + monthStart + "]";
        }
    }

    private static final String FILES = """
            SELECT f.id, f.month_id, f.month_start, mm.take_number, f.kind, f.recipient_code, f.recipient_name, f.layout,
              f.full_list, f.file_name, f.format, f.protection, f.rows_written, f.new_rows, f.deleted_rows, f.left_out,
              OCTET_LENGTH(f.content) AS bytes, f.sha256, f.produced_by, f.produced_at,
              (SELECT COUNT(*) FROM month_end_deliveries d WHERE d.file_id = f.id AND d.channel = 'DOWNLOAD') AS downloads,
              (SELECT MAX(d.delivered_at) FROM month_end_deliveries d WHERE d.file_id = f.id AND d.outcome = 'SENT')
                AS last_delivered_at
            FROM month_end_files f
            JOIN month_end_months mm ON mm.id = f.month_id
            """;

    /** Writes a file -- its bytes ALREADY ENCRYPTED, which the table's own rule insists on. */
    public long insertFile(NewFile file) {
        KeyHolder key = new GeneratedKeyHolder();
        MapSqlParameterSource arguments = new MapSqlParameterSource()
                .addValue("monthId", file.monthId())
                .addValue("monthStart", file.monthStart())
                .addValue("kind", file.kind())
                .addValue("code", file.recipientCode())
                .addValue("name", file.recipientName())
                .addValue("layout", file.layout())
                .addValue("fullList", file.fullList())
                .addValue("fileName", file.fileName())
                .addValue("format", file.format())
                .addValue("protection", file.protection())
                .addValue("rows", file.rows())
                .addValue("newRows", file.newRows())
                .addValue("deletedRows", file.deletedRows())
                .addValue("leftOut", file.leftOut())
                .addValue("content", file.content())
                .addValue("sha256", file.sha256())
                .addValue("producedBy", file.producedBy())
                .addValue("producedAt", utc(file.producedAt()));
        jdbc.update("""
                INSERT INTO month_end_files (month_id, month_start, kind, recipient_code, recipient_name, layout,
                  full_list, file_name, format, protection, rows_written, new_rows, deleted_rows, left_out, content,
                  sha256, produced_by, produced_at)
                VALUES (:monthId, :monthStart, :kind, :code, :name, :layout, :fullList, :fileName, :format, :protection,
                  :rows, :newRows, :deletedRows, :leftOut, :content, :sha256, :producedBy, :producedAt)
                """, arguments, key, new String[] {"id"});
        return idOf(key);
    }

    /** A take's files, newest first within each recipient. */
    public List<SentFile> filesOf(long monthId) {
        return jdbc.query(FILES + " WHERE f.month_id = :monthId ORDER BY f.kind, f.recipient_code, f.id DESC",
                new MapSqlParameterSource("monthId", monthId), MonthEndSentRepository::sentFile);
    }

    public Optional<SentFile> file(long id) {
        return jdbc.query(FILES + " WHERE f.id = :id", new MapSqlParameterSource("id", id),
                MonthEndSentRepository::sentFile).stream().findFirst();
    }

    /** A file's bytes into memory -- for an SFTP upload, which is of the file as stored. */
    public Optional<byte[]> fileBytes(long id) {
        return jdbc.query("SELECT content FROM month_end_files WHERE id = :id", new MapSqlParameterSource("id", id),
                (rs, n) -> rs.getBytes(1)).stream().findFirst();
    }

    /** A file's bytes written to {@code out} as the driver hands them over. */
    public boolean streamFile(long id, OutputStream out) {
        return stream("SELECT content FROM month_end_files WHERE id = :id", id, out);
    }

    private static SentFile sentFile(ResultSet rs, int n) throws SQLException {
        OffsetDateTime last = rs.getObject("last_delivered_at", OffsetDateTime.class);
        return new SentFile(rs.getLong("id"), rs.getLong("month_id"), rs.getDate("month_start").toLocalDate(),
                rs.getInt("take_number"), rs.getString("kind"), rs.getString("recipient_code"),
                rs.getString("recipient_name"), rs.getString("layout"), rs.getBoolean("full_list"),
                rs.getString("file_name"), rs.getString("format"), rs.getString("protection"),
                rs.getInt("rows_written"), rs.getInt("new_rows"), rs.getInt("deleted_rows"), rs.getInt("left_out"),
                rs.getLong("bytes"), rs.getString("sha256"), rs.getString("produced_by"),
                rs.getObject("produced_at", OffsetDateTime.class).toInstant(), rs.getLong("downloads"),
                last == null ? null : last.toInstant());
    }

    // ------------------------------------------------------------------------------------------------ deliveries

    /** A delivery, as recorded. */
    public record Delivery(long id, Long packId, Long fileId, LocalDate monthStart, String kind, String recipientCode,
                           String recipientName, String channel, List<String> formats, String address, String outcome,
                           String code, String fileName, String deliveredBy, Instant deliveredAt) {
    }

    /** What a delivery is recorded with. */
    public record NewDelivery(Long packId, Long fileId, LocalDate monthStart, String kind, String recipientCode,
                              String recipientName, String channel, List<String> formats, String address,
                              String outcome, String code, String fileName, String deliveredBy, Instant deliveredAt) {
    }

    private static final String DELIVERIES = """
            SELECT id, pack_id, file_id, month_start, kind, recipient_code, recipient_name, channel, formats, address,
              outcome, code, file_name, delivered_by, delivered_at
            FROM month_end_deliveries
            """;

    /** Records a delivery -- in its own statement, which commits by itself: a record of an attempt outlives it. */
    public long insertDelivery(NewDelivery delivery) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO month_end_deliveries (pack_id, file_id, month_start, kind, recipient_code, recipient_name,
                  channel, formats, address, outcome, code, file_name, delivered_by, delivered_at)
                VALUES (:pack, :file, :monthStart, :kind, :code, :name, :channel, :formats, :address, :outcome,
                  :refusal, :fileName, :by, :at)
                """, new MapSqlParameterSource()
                .addValue("pack", delivery.packId(), java.sql.Types.BIGINT)
                .addValue("file", delivery.fileId(), java.sql.Types.BIGINT)
                .addValue("monthStart", delivery.monthStart())
                .addValue("kind", delivery.kind())
                .addValue("code", delivery.recipientCode())
                .addValue("name", delivery.recipientName())
                .addValue("channel", delivery.channel())
                .addValue("formats", String.join(",", delivery.formats()))
                .addValue("address", delivery.address())
                .addValue("outcome", delivery.outcome())
                .addValue("refusal", delivery.code())
                .addValue("fileName", delivery.fileName())
                .addValue("by", delivery.deliveredBy())
                .addValue("at", utc(delivery.deliveredAt())), key, new String[] {"id"});
        return idOf(key);
    }

    public Optional<Delivery> delivery(long id) {
        return jdbc.query(DELIVERIES + " WHERE id = :id", new MapSqlParameterSource("id", id),
                MonthEndSentRepository::delivery).stream().findFirst();
    }

    /** A pack's newest delivery. */
    public Optional<Delivery> newestDeliveryOfPack(long packId) {
        return jdbc.query(DELIVERIES + " WHERE pack_id = :pack ORDER BY id DESC LIMIT 1",
                new MapSqlParameterSource("pack", packId), MonthEndSentRepository::delivery).stream().findFirst();
    }

    /** A pack's deliveries, newest first, at most {@code limit}. */
    public List<Delivery> deliveriesOfPacks(Collection<Long> packIds, int limit) {
        if (packIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(DELIVERIES + " WHERE pack_id IN (:packs) ORDER BY id DESC",
                new MapSqlParameterSource("packs", packIds), MonthEndSentRepository::delivery).stream()
                .limit((long) limit * packIds.size()).toList();
    }

    /**
     * A page of a month's deliveries, newest first -- every one, or only those of region packs of {@code setCodes} for a
     * caller who sees part of the country (a bank's or provider's file is no region's).
     */
    public List<Delivery> deliveries(LocalDate monthStart, Collection<String> setCodes, int page, int size) {
        MapSqlParameterSource arguments = new MapSqlParameterSource("month", monthStart)
                .addValue("limit", size).addValue("offset", (long) page * size);
        String scoped = "";
        if (setCodes != null) {
            scoped = " AND kind = 'REGION_PACK' AND recipient_code IN (:sets)";
            arguments.addValue("sets", setCodes.isEmpty() ? List.of("~") : setCodes);
        }
        return jdbc.query(DELIVERIES + " WHERE month_start = :month" + scoped
                + " ORDER BY id DESC LIMIT :limit OFFSET :offset", arguments, MonthEndSentRepository::delivery);
    }

    public long countDeliveries(LocalDate monthStart, Collection<String> setCodes) {
        MapSqlParameterSource arguments = new MapSqlParameterSource("month", monthStart);
        String scoped = "";
        if (setCodes != null) {
            scoped = " AND kind = 'REGION_PACK' AND recipient_code IN (:sets)";
            arguments.addValue("sets", setCodes.isEmpty() ? List.of("~") : setCodes);
        }
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM month_end_deliveries WHERE month_start = :month" + scoped,
                arguments, Long.class);
        return n == null ? 0 : n;
    }

    private static Delivery delivery(ResultSet rs, int n) throws SQLException {
        long pack = rs.getLong("pack_id");
        Long packId = rs.wasNull() ? null : pack;
        long file = rs.getLong("file_id");
        Long fileId = rs.wasNull() ? null : file;
        String formats = rs.getString("formats");
        return new Delivery(rs.getLong("id"), packId, fileId, rs.getDate("month_start").toLocalDate(),
                rs.getString("kind"), rs.getString("recipient_code"), rs.getString("recipient_name"),
                rs.getString("channel"), formats == null || formats.isBlank() ? List.of()
                : Arrays.asList(formats.split(",")), rs.getString("address"), rs.getString("outcome"),
                rs.getString("code"), rs.getString("file_name"), rs.getString("delivered_by"),
                rs.getObject("delivered_at", OffsetDateTime.class).toInstant());
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private boolean stream(String sql, long id, OutputStream out) {
        Boolean found = jdbc.query(sql, new MapSqlParameterSource("id", id), rs -> {
            if (!rs.next()) {
                return false;
            }
            try (InputStream in = rs.getBinaryStream(1)) {
                in.transferTo(out);
            } catch (IOException broken) {
                throw new UncheckedIOException(broken);
            }
            return true;
        });
        return Boolean.TRUE.equals(found);
    }

    private static long idOf(KeyHolder key) {
        Number id = key.getKey();
        if (id == null) {
            throw new IllegalStateException("a row was written and no id came back");
        }
        return id.longValue();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
