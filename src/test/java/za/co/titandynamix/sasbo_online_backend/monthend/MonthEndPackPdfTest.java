package za.co.titandynamix.sasbo_online_backend.monthend;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.monthend.MonthEndPackPdf.Column;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A region's pack as a PDF (October build): A4 landscape, a table that runs over pages carrying its heading onto each,
 * every page stamped "Page n of N" at its foot, a name the font cannot print printed without its accent -- and a cell too
 * long for its column cut, never spilling into the next.
 */
@DisplayName("A pack's PDF")
class MonthEndPackPdfTest {

    @Test
    @DisplayName("a long table runs over pages, its heading on each, and every page is numbered at its foot")
    void aLongTable() throws Exception {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 1; i <= 200; i++) {
            rows.add(List.of("A", "M-99" + String.format("%04d", i), "INVENTED" + i + ", Madeup", "INVENTED BANK"));
        }
        byte[] pdf;
        try (MonthEndPackPdf document = new MonthEndPackPdf("A pack", "SASBO Online · test")) {
            document.title("A pack", List.of("An invented region."));
            document.heading("New members enrolled -- 200");
            document.table(List.of(Column.text("Area", 0.5f), Column.text("Membership no.", 1f),
                    Column.text("Name", 2f), Column.text("Employer", 2f)), rows);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.writeTo(out);
            pdf = out.toByteArray();
        }
        try (PDDocument read = Loader.loadPDF(pdf)) {
            int pages = read.getNumberOfPages();
            assertThat(pages).isGreaterThan(2);
            assertThat(read.getPage(0).getMediaBox().getWidth()).isGreaterThan(read.getPage(0).getMediaBox().getHeight());
            PDFTextStripper stripper = new PDFTextStripper();
            String all = stripper.getText(read);
            assertThat(all).contains("INVENTED1, Madeup").contains("INVENTED200, Madeup")
                    .contains("Page 1 of " + pages).contains("Page " + pages + " of " + pages);
            stripper.setStartPage(pages);
            stripper.setEndPage(pages);
            assertThat(stripper.getText(read)).as("the heading carried onto the last page").contains("Membership no.");
            assertThat(read.getDocumentInformation().getTitle()).isEqualTo("A pack");
        }
    }

    @Test
    @DisplayName("what the standard fonts cannot print is printed without its accent, or as '?'; control characters as spaces")
    void printable() {
        assertThat(MonthEndPackPdf.printable("Zoë Ndlovu")).isEqualTo("Zoë Ndlovu");
        assertThat(MonthEndPackPdf.printable("Ćamil Ŝamwel")).isEqualTo("Camil Samwel");
        assertThat(MonthEndPackPdf.printable("Łukasz")).as("no letter beneath it to print").isEqualTo("?ukasz");
        assertThat(MonthEndPackPdf.printable("a\tb\nc")).isEqualTo("a b c");
        assertThat(MonthEndPackPdf.printable("中")).isEqualTo("?");
        assertThat(MonthEndPackPdf.printable(null)).isEmpty();
    }

    @Test
    @DisplayName("a cell too long for its column is cut with an ellipsis; one that fits is left alone")
    void fit() {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        String longName = "INVENTED EMPLOYER WITH A VERY LONG NAME INDEED";
        String cut = MonthEndPackPdf.fit(longName, font, 8f, 60f);
        assertThat(cut).endsWith("...").hasSizeLessThan(longName.length());
        assertThat(MonthEndPackPdf.width(font, 8f, cut)).isLessThanOrEqualTo(60f);
        assertThat(MonthEndPackPdf.fit("A", font, 8f, 60f)).isEqualTo("A");
    }
}
