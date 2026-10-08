package za.co.titandynamix.sasbo_online_backend.payroll;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * How one employer's file is read: the headings each field goes by, as the employer prints them, and how a name in one
 * column is written. Kept on the employer's profile as JSON ({@code payroll_employers.layout}) and read from the
 * fixtures' layouts of SASBO's samples (6 Oct) -- headings verbatim, never guessed.
 *
 * <p>A heading is compared by its {@link #headingKey key}: upper case, every run of anything that is not a letter or a
 * digit made one space, trimmed -- so Absa's padded {@code ' Pers.No.'} and {@code '               Amount'} read as
 * {@code PERS NO} and {@code AMOUNT}. An alias written {@code re:...} is a regular expression over the key, for headings
 * that carry a month or a code ({@code Sum of 9001-SASBO Union}, Bankserv's {@code September 2026}).
 */
public record PayrollLayout(String key, int version, NameStyle nameStyle, Map<PayrollField, List<String>> headings) {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^A-Z0-9]+");

    public PayrollLayout {
        Map<PayrollField, List<String>> copy = new EnumMap<>(PayrollField.class);
        headings.forEach((field, aliases) -> copy.put(field, List.copyOf(aliases)));
        headings = Collections.unmodifiableMap(copy);
    }

    /** A heading as it is compared: upper case, runs of anything else made one space, trimmed. */
    public static String headingKey(String heading) {
        if (heading == null) {
            return "";
        }
        String upper = heading.toUpperCase(Locale.ROOT);
        return NOT_ALPHANUMERIC.matcher(upper).replaceAll(" ").trim();
    }

    /** Whether {@code heading} is one of {@code field}'s aliases in this layout. */
    public boolean matches(PayrollField field, String heading) {
        List<String> aliases = headings.get(field);
        return aliases != null && matchesAny(aliases, headingKey(heading));
    }

    static boolean matchesAny(List<String> aliases, String key) {
        if (key.isEmpty()) {
            return false;
        }
        for (String alias : aliases) {
            if (alias.startsWith("re:")) {
                if (Pattern.compile(alias.substring(3)).matcher(key).matches()) {
                    return true;
                }
            } else if (headingKey(alias).equals(key)) {
                return true;
            }
        }
        return false;
    }

    /** The layout as stored on the employer's profile. */
    public String toJson() {
        ObjectNode root = JSON.createObjectNode();
        root.put("key", key);
        root.put("version", version);
        root.put("nameStyle", nameStyle.name());
        ObjectNode fields = root.putObject("headings");
        headings.forEach((field, aliases) -> {
            ArrayNode list = fields.putArray(field.wire());
            aliases.forEach(list::add);
        });
        return root.toString();
    }

    /** A layout from its JSON; an unknown field name is a fault in the resource, and says so. */
    public static PayrollLayout fromJson(String json) {
        try {
            return fromNode(JSON.readTree(json));
        } catch (JsonProcessingException unreadable) {
            throw new IllegalStateException("a payroll layout is not JSON", unreadable);
        }
    }

    static PayrollLayout fromNode(JsonNode node) {
        Map<PayrollField, List<String>> headings = new EnumMap<>(PayrollField.class);
        Iterator<Map.Entry<String, JsonNode>> fields = node.path("headings").fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            PayrollField field = PayrollField.fromWire(entry.getKey()).orElseThrow(() ->
                    new IllegalStateException("a payroll layout names a field that does not exist"));
            List<String> aliases = new ArrayList<>();
            entry.getValue().forEach(alias -> aliases.add(alias.asText()));
            headings.put(field, aliases);
        }
        return new PayrollLayout(node.path("key").asText(), node.path("version").asInt(1),
                NameStyle.valueOf(node.path("nameStyle").asText(NameStyle.SEPARATE.name())), headings);
    }

    /**
     * The words any employer's headings are recognised by when its own layout does not find a field -- a heading the
     * employer reworded, or an employer with no layout yet. Taken from the fifteen samples' headings.
     */
    public static final Map<PayrollField, List<String>> VOCABULARY = Map.ofEntries(
            Map.entry(PayrollField.STAFF_NUMBER, List.of("PERS NO", "PERSNO", "PERNR", "PERSONNEL NUMBER",
                    "EMPLOYEE NUMBER", "EMPLOYEE NO", "EMP NO", "EMPLOYEE CODE", "EMPLOYEE ID", "STAFF NUMBER",
                    "STAFF NO", "EMPLOYEE", "PAYROLL NUMBER")),
            Map.entry(PayrollField.ID_NUMBER, List.of("ID NUMBER", "IDNUMBER", "ID NO", "IDENTITY NUMBER",
                    "NATIONAL ID", "ID")),
            Map.entry(PayrollField.INITIALS, List.of("INITIALS", "INITIAL", "INIT", "INITS")),
            Map.entry(PayrollField.FIRST_NAMES, List.of("FIRST NAME", "FIRST NAMES", "FIRSTNAME",
                    "EMPLOYEE FIRST NAME")),
            Map.entry(PayrollField.SURNAME, List.of("SURNAME", "LAST NAME", "EMPLOYEE SURNAME",
                    "EMPLOYEE LAST NAME")),
            Map.entry(PayrollField.FULL_NAME, List.of("EMPLOYEE NAME", "FULL NAME", "NAME",
                    "LAST NAME FIRST NAME", "NAME OF EMPLOYEE OR APPLICANT")),
            Map.entry(PayrollField.AMOUNT, List.of("AMOUNT", "SUB", "SUBS", "SUBSCRIPTION", "COMPONENT VALUE",
                    "COSTED VALUE", "SASBO MEMBERSHIP FEES", "TOTAL", "THIS RUN", "DEDUCTION AMOUNT")),
            Map.entry(PayrollField.FOR_PERIOD, List.of("FOR PERIOD", "FOR PERIOD PAYROLL")));
}
