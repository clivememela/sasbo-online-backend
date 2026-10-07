package za.co.titandynamix.sasbo_online_backend.legacy.mapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import za.co.titandynamix.sasbo_online_backend.enums.MemberStatusKind;
import za.co.titandynamix.sasbo_online_backend.legacy.read.FixedWidthField;
import za.co.titandynamix.sasbo_online_backend.legacy.read.ReaderSettings;
import za.co.titandynamix.sasbo_online_backend.legacy.read.SourceFormat;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.RuleCode;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.Severity;
import za.co.titandynamix.sasbo_online_backend.utils.MemberStatuses;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Reads a mapping definition, STRICTLY: every setting is one this class knows, every field a member field, every
 * transform, code list, severity and date format checked -- and every problem found is listed with its path, never the
 * first alone. A setting the application's JSON reader would ignore is a problem here, because a mapping with a
 * misspelt key silently moves the wrong data.
 *
 * <h2>The shape</h2>
 * <pre>{@code
 * { "key": "legacy-member-export-csv", "name": "...", "description": "...",
 *   "format": { "type": "CSV", "encoding": "windows-1252", "delimiter": ",", "quote": "\"", "header": true,
 *               "skipRows": 0 },
 *   "fields": { "surname": { "column": "SURNAME", "transforms": ["trim", "title"] },
 *               "joinDate": { "column": "DATE JOINED", "transforms": ["removeSpaces"], "dateFormats": ["d/M/yyyy"] },
 *               "status": { "column": "STATUS", "codes": "status" } },
 *   "codes": { "status": { "A": "ACTIVE", "S": "SUSPENDED", "D": "DELETED" },
 *              "deletionStatus": { "1": "Retired", "2": "Resigned", "*": "Terminated" } },
 *   "severity": { "WORKPLACE_UNKNOWN": "WARNING" },
 *   "membershipNumber": { "prefix": "M-", "pattern": "[0-9]{1,9}", "allocate": false } }
 * }</pre>
 * A fixed-width format has a {@code layout} of {@code {name, start, length}} and may name {@code skipLinesStartingWith};
 * a workbook may name its {@code sheet}.
 */
public final class MappingParser {

    public static final int MAX_DEFINITION_CHARS = 60_000;

    private static final Pattern KEY = Pattern.compile("[a-z0-9][a-z0-9-]{1,59}");
    private static final Pattern LIST_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9]{0,39}");
    private static final Pattern LAYOUT_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9 _/.#()-]{0,59}");
    private static final Pattern POSITION = Pattern.compile("#([1-9][0-9]{0,2})");
    private static final Pattern PREFIX = Pattern.compile("[A-Z0-9-]{0,5}");

    private static final Set<String> ROOT_KEYS = Set.of("key", "name", "description", "format", "fields", "codes",
            "severity", "membershipNumber");
    private static final Set<String> FORMAT_KEYS = Set.of("type", "encoding", "delimiter", "quote", "header",
            "skipRows", "sheet", "layout", "skipLinesStartingWith");
    private static final Set<String> FIELD_KEYS = Set.of("column", "transforms", "dateFormats", "codes", "default");
    private static final Set<String> LAYOUT_KEYS = Set.of("name", "start", "length");
    private static final Set<String> MEMBERSHIP_KEYS = Set.of("prefix", "pattern", "allocate");

    /** The values a list may give the fields whose vocabulary is fixed. */
    private static final Map<MemberField, Set<String>> VOCABULARY = Map.of(
            MemberField.STATUS, Set.of("ACTIVE", "SUSPENDED", "DELETED"),
            MemberField.GENDER, Set.of("Male", "Female"),
            MemberField.PAYMENT_TYPE, Set.of("Debit Order", "Payroll Deduction"),
            MemberField.ACCOUNT_TYPE, Set.of("Cheque", "Savings", "Transmission"));

    private final ObjectMapper json = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    /** Reads a definition from its text. */
    public MappingDefinition parse(String text) {
        if (text == null || text.isBlank()) {
            throw new MappingInvalid(List.of("the definition is empty"));
        }
        if (text.length() > MAX_DEFINITION_CHARS) {
            throw new MappingInvalid(List.of("the definition is longer than " + MAX_DEFINITION_CHARS + " characters"));
        }
        JsonNode root;
        try {
            root = json.readTree(text);
        } catch (JsonProcessingException notJson) {
            throw new MappingInvalid(List.of("the definition is not JSON, or names a setting twice"));
        }
        return parse(root);
    }

    /** Reads a definition already parsed. */
    public MappingDefinition parse(JsonNode root) {
        Problems problems = new Problems();
        if (root == null || !root.isObject()) {
            throw new MappingInvalid(List.of("the definition must be a JSON object"));
        }
        problems.onlyKeys(root, "", ROOT_KEYS);
        String key = problems.text(root, "key", "key", true, 60);
        if (key != null && !KEY.matcher(key).matches()) {
            problems.add("key", "lower-case letters, digits and hyphens, 2 to 60 of them, starting with a letter or digit");
        }
        String name = problems.text(root, "name", "name", true, 120);
        String description = problems.text(root, "description", "description", false, 500);
        ReaderSettings format = format(root.get("format"), problems);
        Map<String, Map<String, String>> codes = codes(root.get("codes"), problems);
        MembershipNumberRule membership = membership(root.get("membershipNumber"), problems);
        Map<MemberField, FieldMapping> fields = fields(root.get("fields"), format, codes, membership, problems);
        Map<RuleCode, Severity> severity = severity(root.get("severity"), problems);
        deletionStatus(codes, problems);
        if (!problems.isEmpty()) {
            throw new MappingInvalid(problems.list());
        }
        String stored;
        try {
            stored = json.writeValueAsString(root);
        } catch (JsonProcessingException unwritable) {
            throw new MappingInvalid(List.of("the definition could not be stored"));
        }
        if (stored.length() > MAX_DEFINITION_CHARS) {
            throw new MappingInvalid(List.of("the definition is longer than " + MAX_DEFINITION_CHARS + " characters"));
        }
        return new MappingDefinition(key, name, description, format, fields, codes, severity, membership, stored);
    }

    // ------------------------------------------------------------------------------------------------ format

    private static ReaderSettings format(JsonNode node, Problems problems) {
        if (node == null || !node.isObject()) {
            problems.add("format", "required: an object saying how the file is read");
            return null;
        }
        problems.onlyKeys(node, "format", FORMAT_KEYS);
        String typeText = problems.text(node, "format.type", "type", true, 20);
        SourceFormat type = null;
        if (typeText != null) {
            try {
                type = SourceFormat.valueOf(typeText);
            } catch (IllegalArgumentException unknown) {
                problems.add("format.type", "one of CSV, XLSX or FIXED_WIDTH");
            }
        }
        Charset charset = StandardCharsets.UTF_8;
        String encoding = problems.text(node, "format.encoding", "encoding", false, 40);
        if (encoding != null) {
            if (type == SourceFormat.XLSX) {
                problems.add("format.encoding", "a workbook carries its own encoding");
            } else {
                try {
                    charset = Charset.forName(encoding);
                } catch (IllegalArgumentException unknown) {
                    problems.add("format.encoding", "not an encoding this server knows (try windows-1252 or UTF-8)");
                }
            }
        }
        char delimiter = character(node, "delimiter", ',', type == SourceFormat.CSV, problems);
        char quote = character(node, "quote", '"', type == SourceFormat.CSV, problems);
        if (delimiter == quote) {
            problems.add("format.quote", "the quote cannot be the delimiter");
        }
        boolean header = true;
        if (node.has("header")) {
            if (type == SourceFormat.FIXED_WIDTH) {
                problems.add("format.header", "a fixed-width file has no header row: use skipRows");
            } else if (!node.get("header").isBoolean()) {
                problems.add("format.header", "true or false");
            } else {
                header = node.get("header").asBoolean();
            }
        }
        int skipRows = problems.integer(node, "format.skipRows", "skipRows", 0, 0, 100);
        String sheet = problems.text(node, "format.sheet", "sheet", false, 31);
        if (sheet != null && type != SourceFormat.XLSX) {
            problems.add("format.sheet", "only a workbook has sheets");
        }
        List<FixedWidthField> layout = List.of();
        if (type == SourceFormat.FIXED_WIDTH) {
            layout = layout(node.get("layout"), problems);
        } else if (node.has("layout")) {
            problems.add("format.layout", "only a fixed-width file has a layout");
        }
        List<String> skipLines = new ArrayList<>();
        if (node.has("skipLinesStartingWith")) {
            JsonNode markers = node.get("skipLinesStartingWith");
            if (type != SourceFormat.FIXED_WIDTH) {
                problems.add("format.skipLinesStartingWith", "only a fixed-width file skips lines by their start");
            } else if (!markers.isArray() || markers.size() > 10) {
                problems.add("format.skipLinesStartingWith", "a list of at most 10 markers");
            } else {
                for (int i = 0; i < markers.size(); i++) {
                    String marker = markers.get(i).isTextual() ? markers.get(i).asText() : null;
                    if (marker == null || marker.isEmpty() || marker.length() > 20) {
                        problems.add("format.skipLinesStartingWith[" + i + "]", "text of 1 to 20 characters");
                    } else {
                        skipLines.add(marker);
                    }
                }
            }
        }
        if (type == null) {
            return null;
        }
        return new ReaderSettings(type, charset, delimiter, quote, header, skipRows, sheet, layout, skipLines);
    }

    private static char character(JsonNode node, String name, char fallback, boolean allowed, Problems problems) {
        if (!node.has(name)) {
            return fallback;
        }
        String path = "format." + name;
        if (!allowed) {
            problems.add(path, "only a CSV file has a " + name);
            return fallback;
        }
        JsonNode value = node.get(name);
        if (!value.isTextual() || value.asText().length() != 1) {
            problems.add(path, "exactly one character");
            return fallback;
        }
        char c = value.asText().charAt(0);
        if (c == '\r' || c == '\n') {
            problems.add(path, "not a line break");
            return fallback;
        }
        return c;
    }

    private static List<FixedWidthField> layout(JsonNode node, Problems problems) {
        if (node == null || !node.isArray() || node.isEmpty() || node.size() > 200) {
            problems.add("format.layout", "required for a fixed-width file: a list of 1 to 200 fields");
            return List.of();
        }
        List<FixedWidthField> fields = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < node.size(); i++) {
            String path = "format.layout[" + i + "]";
            JsonNode field = node.get(i);
            if (!field.isObject()) {
                problems.add(path, "an object with a name, a start and a length");
                continue;
            }
            problems.onlyKeys(field, path, LAYOUT_KEYS);
            String name = problems.text(field, path + ".name", "name", true, 60);
            if (!field.has("start")) {
                problems.add(path + ".start", "required");
            }
            if (!field.has("length")) {
                problems.add(path + ".length", "required");
            }
            // 0 where missing or out of range -- each already a problem.
            int start = problems.integer(field, path + ".start", "start", 0, 1, 10_000);
            int length = problems.integer(field, path + ".length", "length", 0, 1, 1000);
            if (name == null || start == 0 || length == 0) {
                continue;
            }
            if (!LAYOUT_NAME.matcher(name).matches()) {
                problems.add(path + ".name", "letters, digits, spaces and _ / . # ( ) -, up to 60");
            } else if (!names.add(name.toUpperCase(Locale.ROOT))) {
                problems.add(path + ".name", "the same name twice");
            }
            if (start - 1 + length > 10_000) {
                problems.add(path, "ends past character 10,000");
            }
            fields.add(new FixedWidthField(name, start, length));
        }
        List<FixedWidthField> ordered = new ArrayList<>(fields);
        ordered.sort(Comparator.comparingInt(FixedWidthField::start));
        for (int i = 1; i < ordered.size(); i++) {
            if (ordered.get(i).start() - 1 < ordered.get(i - 1).end()) {
                problems.add("format.layout", "fields overlap: " + Problems.safe(ordered.get(i - 1).name()) + " and "
                        + Problems.safe(ordered.get(i).name()));
            }
        }
        return fields;
    }

    // ------------------------------------------------------------------------------------------------ codes

    private static Map<String, Map<String, String>> codes(JsonNode node, Problems problems) {
        Map<String, Map<String, String>> lists = new LinkedHashMap<>();
        if (node == null) {
            return lists;
        }
        if (!node.isObject()) {
            problems.add("codes", "an object of lists");
            return lists;
        }
        Iterator<Map.Entry<String, JsonNode>> named = node.fields();
        while (named.hasNext()) {
            Map.Entry<String, JsonNode> list = named.next();
            String path = "codes." + Problems.safe(list.getKey());
            if (!LIST_NAME.matcher(list.getKey()).matches()) {
                problems.add(path, "a list's name is a letter and up to 39 more letters or digits");
                continue;
            }
            JsonNode entries = list.getValue();
            if (!entries.isObject()) {
                problems.add(path, "an object of the old value to ours");
                continue;
            }
            if (entries.size() > 500) {
                problems.add(path, "more than 500 entries");
                continue;
            }
            Map<String, String> map = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> pairs = entries.fields();
            while (pairs.hasNext()) {
                Map.Entry<String, JsonNode> pair = pairs.next();
                String from = pair.getKey();
                String entryPath = path + "." + Problems.safe(from);
                JsonNode to = pair.getValue();
                if (from.isBlank() || from.length() > 60) {
                    problems.add(path, "an old value is blank or longer than 60 characters");
                } else if (!to.isTextual() || to.asText().isBlank() || to.asText().length() > 60) {
                    problems.add(entryPath, "ours is text of 1 to 60 characters");
                } else if (map.put(MappingDefinition.keyOf(from), to.asText().strip()) != null) {
                    problems.add(entryPath, "the same old value twice, ignoring case and spacing");
                }
            }
            lists.put(list.getKey(), map);
        }
        return lists;
    }

    /** The reserved list: each of SASBO's deletion codes, or {@code *} for the rest, to one of the leaving statuses. */
    private static void deletionStatus(Map<String, Map<String, String>> codes, Problems problems) {
        Map<String, String> list = codes.get(MappingDefinition.DELETION_STATUS_LIST);
        if (list == null) {
            return;
        }
        List<String> leaving = MemberStatuses.ofKind(MemberStatusKind.LEAVING);
        list.forEach((code, status) -> {
            String path = "codes." + MappingDefinition.DELETION_STATUS_LIST + "." + Problems.safe(code);
            if (!code.equals("*") && !code.matches("[1-9][0-9]{0,2}")) {
                problems.add(path, "a deletion code's number, or * for every other code");
            }
            if (!leaving.contains(status)) {
                problems.add(path, "one of the statuses a member leaves with: " + String.join(", ", leaving));
            }
        });
    }

    // ------------------------------------------------------------------------------------------------ membership

    private static MembershipNumberRule membership(JsonNode node, Problems problems) {
        MembershipNumberRule defaults = MembershipNumberRule.defaults();
        if (node == null) {
            return defaults;
        }
        if (!node.isObject()) {
            problems.add("membershipNumber", "an object: prefix, pattern, allocate");
            return defaults;
        }
        problems.onlyKeys(node, "membershipNumber", MEMBERSHIP_KEYS);
        String prefix = defaults.prefix();
        if (node.has("prefix")) {
            JsonNode value = node.get("prefix");
            if (!value.isTextual() || !PREFIX.matcher(value.asText()).matches()) {
                problems.add("membershipNumber.prefix", "up to 5 capital letters, digits and hyphens");
            } else {
                prefix = value.asText();
            }
        }
        Pattern pattern = defaults.pattern();
        String patternText = problems.text(node, "membershipNumber.pattern", "pattern", false, 60);
        if (patternText != null) {
            try {
                pattern = Pattern.compile(patternText);
            } catch (PatternSyntaxException broken) {
                problems.add("membershipNumber.pattern", "not a pattern this server can read");
            }
        }
        boolean allocate = false;
        if (node.has("allocate")) {
            if (!node.get("allocate").isBoolean()) {
                problems.add("membershipNumber.allocate", "true or false");
            } else {
                allocate = node.get("allocate").asBoolean();
            }
        }
        return new MembershipNumberRule(prefix, pattern, allocate);
    }

    // ------------------------------------------------------------------------------------------------ fields

    private static Map<MemberField, FieldMapping> fields(JsonNode node, ReaderSettings format,
                                                         Map<String, Map<String, String>> codes,
                                                         MembershipNumberRule membership, Problems problems) {
        Map<MemberField, FieldMapping> fields = new EnumMap<>(MemberField.class);
        if (node == null || !node.isObject()) {
            problems.add("fields", "required: an object of member fields");
            return fields;
        }
        Iterator<Map.Entry<String, JsonNode>> named = node.fields();
        while (named.hasNext()) {
            Map.Entry<String, JsonNode> entry = named.next();
            String path = "fields." + Problems.safe(entry.getKey());
            Optional<MemberField> field = MemberField.fromWire(entry.getKey());
            if (field.isEmpty()) {
                problems.add(path, "not a member field a migration writes");
                continue;
            }
            JsonNode spec = entry.getValue();
            if (!spec.isObject()) {
                problems.add(path, "an object with a column, and what is done to it");
                continue;
            }
            problems.onlyKeys(spec, path, FIELD_KEYS);
            String column = problems.text(spec, path + ".column", "column", false, 100);
            String defaultValue = problems.text(spec, path + ".default", "default", false, 255);
            if (column == null && defaultValue == null && !spec.has("column") && !spec.has("default")) {
                problems.add(path, "needs a column, or a default");
            }
            if (column != null && format != null) {
                column(column, path + ".column", format, problems);
            }
            List<Transform> transforms = transforms(spec.get("transforms"), path + ".transforms", problems);
            List<String> patterns = new ArrayList<>();
            List<DateTimeFormatter> formats = new ArrayList<>();
            if (field.get().isDate()) {
                dates(spec.get("dateFormats"), path + ".dateFormats", patterns, formats, problems);
            } else if (spec.has("dateFormats")) {
                problems.add(path + ".dateFormats", "only a date field has date formats");
            }
            String list = problems.text(spec, path + ".codes", "codes", false, 40);
            if (list != null) {
                if (!codes.containsKey(list)) {
                    problems.add(path + ".codes", "no list of that name under codes");
                } else if (VOCABULARY.containsKey(field.get())) {
                    Set<String> allowed = VOCABULARY.get(field.get());
                    for (String value : codes.get(list).values()) {
                        if (!allowed.contains(value)) {
                            problems.add(path + ".codes", "the list's values must each be one of: "
                                    + String.join(", ", allowed.stream().sorted().toList()));
                            break;
                        }
                    }
                }
            }
            fields.put(field.get(), new FieldMapping(column, transforms, patterns, formats, list, defaultValue));
        }
        if (!fields.containsKey(MemberField.SURNAME)) {
            problems.add("fields", "needs surname");
        }
        if (!fields.containsKey(MemberField.JOIN_DATE)) {
            problems.add("fields", "needs joinDate");
        }
        if (!membership.allocate() && !fields.containsKey(MemberField.MEMBERSHIP_NUMBER)) {
            problems.add("fields", "needs membershipNumber, or membershipNumber.allocate");
        }
        if (!fields.containsKey(MemberField.IDENTITY_NUMBER) && !fields.containsKey(MemberField.PASSPORT_NUMBER)) {
            problems.add("fields", "needs identityNumber or passportNumber");
        }
        if (!fields.containsKey(MemberField.INITIALS) && !fields.containsKey(MemberField.FIRST_NAMES)) {
            problems.add("fields", "needs initials or firstNames");
        }
        return fields;
    }

    private static void column(String column, String path, ReaderSettings format, Problems problems) {
        if (format.format() == SourceFormat.FIXED_WIDTH) {
            boolean inLayout = format.layout().stream().anyMatch(field -> field.name().equalsIgnoreCase(column));
            if (!inLayout) {
                problems.add(path, "not a field of the layout");
            }
        } else if (!format.header() && !POSITION.matcher(column).matches()) {
            problems.add(path, "a file with no header row names a column by its position: #1, #2 ...");
        }
    }

    private static List<Transform> transforms(JsonNode node, String path, Problems problems) {
        List<Transform> transforms = new ArrayList<>();
        if (node == null) {
            return transforms;
        }
        if (!node.isArray() || node.size() > 10) {
            problems.add(path, "a list of at most 10 transforms");
            return transforms;
        }
        for (int i = 0; i < node.size(); i++) {
            Optional<Transform> transform = node.get(i).isTextual()
                    ? Transform.fromWire(node.get(i).asText()) : Optional.empty();
            if (transform.isEmpty()) {
                problems.add(path + "[" + i + "]", "not a transform: trim, upper, lower, title, collapseSpaces, "
                        + "removeSpaces, digitsOnly or stripLeadingZeros");
            } else {
                transforms.add(transform.get());
            }
        }
        return transforms;
    }

    private static void dates(JsonNode node, String path, List<String> patterns, List<DateTimeFormatter> formats,
                              Problems problems) {
        List<String> given = new ArrayList<>();
        if (node == null) {
            given.add("yyyy-MM-dd");
        } else if (!node.isArray() || node.isEmpty() || node.size() > 10) {
            problems.add(path, "a list of 1 to 10 date formats");
            return;
        } else {
            for (int i = 0; i < node.size(); i++) {
                given.add(node.get(i).isTextual() ? node.get(i).asText() : "");
            }
        }
        for (int i = 0; i < given.size(); i++) {
            String pattern = given.get(i);
            String at = node == null ? path : path + "[" + i + "]";
            if (pattern.isEmpty() || pattern.length() > 30 || !(pattern.contains("yyyy") || pattern.contains("uuuu"))
                    || !pattern.contains("M") || !pattern.contains("d")) {
                problems.add(at, "a date format with a four-digit year, a month and a day, such as d/M/yyyy");
                continue;
            }
            try {
                formats.add(dateFormat(pattern));
                patterns.add(pattern);
            } catch (IllegalArgumentException unreadable) {
                problems.add(at, "not a date pattern this server can read");
            }
        }
    }

    /** A date pattern, strict: a year of four digits ({@code y} read as {@code u}, so no era is needed), case ignored. */
    static DateTimeFormatter dateFormat(String pattern) {
        return new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern(pattern.replace('y', 'u'))
                .toFormatter(Locale.ENGLISH)
                .withResolverStyle(ResolverStyle.STRICT);
    }

    // ------------------------------------------------------------------------------------------------ severity

    private static Map<RuleCode, Severity> severity(JsonNode node, Problems problems) {
        Map<RuleCode, Severity> chosen = new EnumMap<>(RuleCode.class);
        if (node == null) {
            return chosen;
        }
        if (!node.isObject()) {
            problems.add("severity", "an object of a rule's code to ERROR or WARNING");
            return chosen;
        }
        Iterator<Map.Entry<String, JsonNode>> named = node.fields();
        while (named.hasNext()) {
            Map.Entry<String, JsonNode> entry = named.next();
            String path = "severity." + Problems.safe(entry.getKey());
            RuleCode code;
            try {
                code = RuleCode.valueOf(entry.getKey());
            } catch (IllegalArgumentException unknown) {
                problems.add(path, "not one of the rules' codes");
                continue;
            }
            Severity severity;
            try {
                severity = Severity.valueOf(entry.getValue().asText(""));
            } catch (IllegalArgumentException unknown) {
                problems.add(path, "ERROR or WARNING");
                continue;
            }
            if (severity == Severity.WARNING && code.severity() == Severity.ERROR && !code.downgradable()) {
                problems.add(path, "cannot be made a warning: a row with it cannot be moved safely");
                continue;
            }
            chosen.put(code, severity);
        }
        return chosen;
    }

    // ------------------------------------------------------------------------------------------------ problems

    /** Every problem found, each a path and what is wrong there -- at most fifty, which is enough to start on. */
    private static final class Problems {

        private final List<String> found = new ArrayList<>();

        void add(String path, String what) {
            if (found.size() < 50) {
                found.add(path.isEmpty() ? what : path + ": " + what);
            }
        }

        boolean isEmpty() {
            return found.isEmpty();
        }

        List<String> list() {
            return List.copyOf(found);
        }

        void onlyKeys(JsonNode node, String path, Set<String> allowed) {
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (!allowed.contains(name)) {
                    add(path.isEmpty() ? safe(name) : path + "." + safe(name), "not a setting a mapping has here");
                }
            }
        }

        String text(JsonNode parent, String path, String name, boolean required, int max) {
            JsonNode value = parent.get(name);
            if (value == null || value.isNull()) {
                if (required) {
                    add(path, "required");
                }
                return null;
            }
            if (!value.isTextual() || value.asText().isBlank()) {
                add(path, "text, not blank");
                return null;
            }
            if (value.asText().length() > max) {
                add(path, "longer than " + max + " characters");
                return null;
            }
            return value.asText().strip();
        }

        int integer(JsonNode parent, String path, String name, int fallback, int min, int max) {
            JsonNode value = parent.get(name);
            if (value == null) {
                return fallback;
            }
            if (!value.isIntegralNumber() || value.asLong() < min || value.asLong() > max) {
                add(path, "a whole number from " + min + " to " + max);
                return 0;
            }
            return value.asInt();
        }

        /** A name of the definition's own, as a path shows it: short, and of a plain alphabet. */
        static String safe(String name) {
            String shown = name.length() > 40 ? name.substring(0, 40) : name;
            return shown.replaceAll("[^A-Za-z0-9_#./ ()*-]", "?");
        }
    }
}
