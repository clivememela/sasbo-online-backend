package za.co.titandynamix.sasbo_online_backend.legacy.read;

/**
 * The three shapes a legacy export can arrive in. The mapping says which a file is -- never its name or its extension,
 * which are whatever the person saving it typed.
 */
public enum SourceFormat {
    /** Delimited text: a delimiter, a quote, an encoding -- legacy exports are often Windows-1252. */
    CSV,
    /** A workbook: one sheet, read row by row from its XML, never loaded whole. */
    XLSX,
    /** Columns by position: a layout of field, start and length, as old systems print their records. */
    FIXED_WIDTH
}
