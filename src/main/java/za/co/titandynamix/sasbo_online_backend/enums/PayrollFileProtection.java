package za.co.titandynamix.sasbo_online_backend.enums;

/** How a file that leaves SASBO for an employer is protected -- the same words as month end's bank files. */
public enum PayrollFileProtection {

    /** An Excel workbook under Excel's own encryption (agile, AES-256, SHA-512): Excel asks for the password. */
    EXCEL_AES_256,

    /** A PDF encrypted with AES-256: the PDF reader asks for the password. */
    PDF_AES_256
}
