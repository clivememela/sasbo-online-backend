package za.co.titandynamix.sasbo_online_backend.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * What the payroll integration tests put on record: members by JDBC (a batch, for the hundred thousand of the
 * performance test), the people of a fixtures manifest as members the way its {@code people} say, and the samples.
 */
public final class PayrollItSupport {

    private PayrollItSupport() {
    }

    /** One member row: invented, on payroll deduction unless said otherwise. */
    public record MemberRow(String number, String staff, String id, String title, String initials, String firstNames,
                            String surname, String status, String paymentType, String bankName, BigDecimal arrears) {
    }

    public static void insert(JdbcTemplate jdbc, List<MemberRow> rows) {
        Timestamp now = Timestamp.from(Instant.parse("2026-09-01T08:00:00Z"));
        List<Object[]> batch = new ArrayList<>();
        for (MemberRow r : rows) {
            batch.add(new Object[] {r.number(), r.staff(), r.title(), r.initials(), r.firstNames(), r.surname(),
                    r.id() == null ? null : "I.D. Number", r.id(), r.status(), Date.valueOf(LocalDate.of(2015, 3, 1)),
                    r.bankName(), r.paymentType(), r.arrears(), now, now});
        }
        for (int from = 0; from < batch.size(); from += 2000) {
            jdbc.batchUpdate("INSERT INTO members (membership_number, employee_number, title, initials, first_names, "
                    + "surname, identity_type, identity_number, status, join_date, bank_name, payment_type, "
                    + "arrears_balance_numeric, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    batch.subList(from, Math.min(batch.size(), from + 2000)));
        }
    }

    /** The members a manifest's {@code people} say to put on record, at {@code bankName}. */
    public static void seedManifest(JdbcTemplate jdbc, JsonNode manifest, String bankName) {
        List<MemberRow> rows = new ArrayList<>();
        int n = 0;
        for (JsonNode p : manifest.path("people")) {
            if (!p.path("seedAsMember").asBoolean()) {
                continue;
            }
            rows.add(new MemberRow(String.format("T-%05d", ++n), text(p, "employeeNumberOnRecord"),
                    text(p, "identityNumber"), text(p, "title"), text(p, "initials"), text(p, "firstNames"),
                    text(p, "surname"), p.path("seedStatus").asText("Active"), "Payroll Deduction", bankName,
                    new BigDecimal(p.path("seedArrears").asText("0.00"))));
        }
        insert(jdbc, rows);
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).isNull() || node.path(field).isMissingNode() ? null : node.path(field).asText();
    }

    public static JsonNode manifest(String slug) {
        try {
            return new ObjectMapper().readTree(resource(slug + ".manifest.json"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] resource(String name) {
        try (InputStream stream = PayrollItSupport.class.getResourceAsStream("/payroll/" + name)) {
            if (stream == null) {
                throw new IllegalStateException(name + " is not among the test resources");
            }
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The member id of a membership number. */
    public static int memberId(JdbcTemplate jdbc, String number) {
        Integer id = jdbc.queryForObject("SELECT id FROM members WHERE membership_number = ?", Integer.class, number);
        return id == null ? -1 : id;
    }
}
