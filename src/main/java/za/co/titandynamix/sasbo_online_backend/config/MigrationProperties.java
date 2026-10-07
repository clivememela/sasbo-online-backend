package za.co.titandynamix.sasbo_online_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import za.co.titandynamix.sasbo_online_backend.legacy.read.ReadLimits;
import za.co.titandynamix.sasbo_online_backend.legacy.read.SourceFormat;

/**
 * The data migration's ceilings, stated rather than inherited ({@code migration.*}).
 *
 * <p>The member file of 31 August 2026 held 549,992 records for 65,714 members on record (legacy/README.md, §3.1): a
 * move is of the order of half a million rows, so the row ceiling is a million and the upload's 300 MB -- larger than
 * any export of that size, small enough that a mistaken upload is stopped. A workbook is opened from a file the
 * application writes, and its ceiling is lower: Excel holds at most a million rows, and a workbook of a member export is
 * a few megabytes.
 *
 * @param uploadMaxBytes  a CSV or fixed-width file's ceiling
 * @param xlsxMaxBytes    a workbook's ceiling
 * @param maxRows         data rows a file may hold
 * @param maxColumns      cells a row may hold
 * @param maxCellChars    characters a cell may hold
 * @param chunkSize       rows staged, checked, moved or undone at a time -- and so the most a job holds at once
 * @param staleJobMinutes a job whose heartbeat is older than this died with its instance, and is swept
 */
@ConfigurationProperties(prefix = "migration")
public record MigrationProperties(
        @DefaultValue("314572800") long uploadMaxBytes,
        @DefaultValue("52428800") long xlsxMaxBytes,
        @DefaultValue("1000000") int maxRows,
        @DefaultValue("200") int maxColumns,
        @DefaultValue("1000") int maxCellChars,
        @DefaultValue("1000") int chunkSize,
        @DefaultValue("10") int staleJobMinutes) {

    public MigrationProperties {
        if (uploadMaxBytes <= 0 || xlsxMaxBytes <= 0 || maxRows <= 0 || maxColumns <= 0 || maxCellChars <= 0
                || chunkSize <= 0 || chunkSize > 10_000 || staleJobMinutes <= 0) {
            throw new IllegalArgumentException("every migration.* ceiling is positive, and a chunk at most 10,000 rows");
        }
    }

    /** The defaults, for tests that construct a service by hand. */
    public static MigrationProperties defaults() {
        return new MigrationProperties(314_572_800L, 52_428_800L, 1_000_000, 200, 1000, 1000, 10);
    }

    /** The read limits for a file of this format. */
    public ReadLimits limits(SourceFormat format) {
        return new ReadLimits(format == SourceFormat.XLSX ? xlsxMaxBytes : uploadMaxBytes, maxRows, maxColumns,
                maxCellChars);
    }

    /** The same ceilings, moved in chunks of {@code rows} -- for a test that watches a move chunk by chunk. */
    public MigrationProperties withChunkSize(int rows) {
        return new MigrationProperties(uploadMaxBytes, xlsxMaxBytes, maxRows, maxColumns, maxCellChars, rows,
                staleJobMinutes);
    }
}
