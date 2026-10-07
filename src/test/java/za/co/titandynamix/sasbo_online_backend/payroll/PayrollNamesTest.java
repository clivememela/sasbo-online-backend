package za.co.titandynamix.sasbo_online_backend.payroll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Names compared by their parts -- Stephne, PAY-10: "Initials and surname" -- in each of the seven styles SASBO's
 * samples write them (fixtures' LAYOUTS.md). IT's report shows what a strict text comparison makes of them.
 */
@DisplayName("Payroll names: initials and surname, in seven styles")
class PayrollNamesTest {

    @Test
    @DisplayName("a surname compares by its letters: case, spaces, hyphens and accents do not make another person")
    void surnames() {
        assertThat(PayrollNames.surnameKey("Test-Samplewell")).isEqualTo(PayrollNames.surnameKey("TEST SAMPLEWELL"));
        assertThat(PayrollNames.surnameKey("Van Der Testerson")).isEqualTo("VANDERTESTERSON");
        assertThat(PayrollNames.surnameKey("Démoleng")).isEqualTo("DEMOLENG");
        assertThat(PayrollNames.initialsOf("Testo Demo")).isEqualTo("TD");
    }

    @Test
    @DisplayName("one person's initials fit another's when the same or one begins the other -- one initial is no stranger")
    void initials() {
        assertThat(PayrollNames.initialsFit("B", "BK")).isTrue();
        assertThat(PayrollNames.initialsFit("BK", "B.K.")).isTrue();
        assertThat(PayrollNames.initialsFit("E", "RT")).isFalse();
        assertThat(PayrollNames.initialsFit("", "RT")).isTrue();
    }

    @Test
    @DisplayName("Absa: SURNAME , FIRST NAMES -- the surname before the comma, initials from the first names")
    void absa() {
        PayrollNames.Name name = PayrollNames.read(NameStyle.SURNAME_COMMA_FIRSTNAMES, null, null, null,
                "VAN DER TESTERSON , TRIALA PILOTA");
        assertThat(name.surname()).isEqualTo("VAN DER TESTERSON");
        assertThat(name.initials()).isEqualTo("TP");
        assertThat(PayrollNames.fits(name, "TP", "Van Der Testerson")).isTrue();
    }

    @Test
    @DisplayName("GBS: SURNAME, INITIALS")
    void gbs() {
        PayrollNames.Name name = PayrollNames.read(NameStyle.SURNAME_COMMA_INITIALS, null, null, null, "LEDGERWOOD, RT");
        assertThat(name.initials()).isEqualTo("RT");
        assertThat(PayrollNames.fits(name, "R", "Ledgerwood")).isTrue();
    }

    @Test
    @DisplayName("Bankserv and SBV: Mr AB Surname -- the title dropped")
    void titleInitialsSurname() {
        PayrollNames.Name name = PayrollNames.read(NameStyle.TITLE_INITIALS_SURNAME, null, null, null,
                "Mrs TP Van Der Testerson");
        assertThat(name.initials()).isEqualTo("TP");
        assertThat(name.surname()).isEqualTo("Van Der Testerson");
    }

    @Test
    @DisplayName("Capitec: Firstname Surname -- every split is a reading, and the likeliest is shown")
    void capitec() {
        PayrollNames.Name name = PayrollNames.read(NameStyle.FIRSTNAMES_SURNAME, null, null, null,
                "Triala Pilota Van Der Testerson");
        assertThat(name.surname()).isEqualTo("Van Der Testerson");
        assertThat(PayrollNames.fits(name, "TP", "Van Der Testerson")).isTrue();
        assertThat(PayrollNames.fits(PayrollNames.read(NameStyle.FIRSTNAMES_SURNAME, null, null, null,
                "Recordo Test-Samplewell"), "R", "Test-Samplewell")).isTrue();
    }

    @Test
    @DisplayName("Land Bank: SURNAME FIRSTNAME, no comma; Metropolitan: either order")
    void surnameFirstAndEitherOrder() {
        assertThat(PayrollNames.fits(PayrollNames.read(NameStyle.SURNAME_FIRSTNAMES, null, null, null,
                "SAMPLEKOP SAMPLA EXEMPLA"), "SE", "Samplekop")).isTrue();
        PayrollNames.Name either = PayrollNames.read(NameStyle.EITHER_ORDER, null, null, null, "DEMA PILOTA DEMOLENG");
        assertThat(PayrollNames.fits(either, "DP", "Demoleng")).isTrue();
        assertThat(PayrollNames.fits(PayrollNames.read(NameStyle.EITHER_ORDER, null, null, null,
                "DEMOLENG DEMA PILOTA"), "DP", "Demoleng")).isTrue();
    }

    @Test
    @DisplayName("SA Reserve Bank: Surname I.J., Mr.")
    void sarb() {
        PayrollNames.Name name = PayrollNames.read(NameStyle.SURNAME_INITIALS_TITLE, null, null, null,
                "Datapoint F.P., Mr.");
        assertThat(name.initials()).isEqualTo("FP");
        assertThat(name.surname()).isEqualTo("Datapoint");
    }

    @Test
    @DisplayName("separate columns: an initials column, or initials from first names")
    void separate() {
        assertThat(PayrollNames.read(NameStyle.SEPARATE, "MP  ", null, "SAMPLEKOP            ", null).initials())
                .isEqualTo("MP");
        assertThat(PayrollNames.read(NameStyle.SEPARATE, null, "Mocka Datia", "Mockwana", null).initials())
                .isEqualTo("MD");
        assertThat(PayrollNames.read(NameStyle.SEPARATE, null, null, null, null).isEmpty()).isTrue();
    }
}
