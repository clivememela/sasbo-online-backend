package za.co.titandynamix.sasbo_online_backend.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

/**
 * Writes {@code data/demonstration_members.json}: the invented members who stand at SASBO's workplaces (month end
 * 01.4, MD30, MD33, MD35).
 *
 * <h2>Why a generator, and why it lives in the tests</h2>
 * As {@link DebitOrderSeedGenerator}'s: nobody types a thousand members, and a population is trustworthy only while
 * it is exactly what its generator writes. {@code ./gradlew generateApiSurface} writes the file; the fast lane asserts
 * the committed bytes are what it would write ({@code DemonstrationMemberGeneratorTest}). The random seed and every
 * order are fixed, and {@code java.util.Random} is specified to the bit -- the same bytes on every machine.
 *
 * <h2>What it stands on, and what it never stands on</h2>
 * The workplaces of WHOLE AREAS -- {@value #AREAS_AS_WRITTEN}, proposed (MD35) -- with their staff complement, from
 * SASBO's branch list as the seed already holds it; and the NINE GROUP-LEVEL PENETRATIONS of 31 August 2026, as
 * proportions. Each workplace stands at its group's penetration: {@code round(complement x penetration)} members.
 * No count of members at a workplace, an employer or a table code is taken from SASBO's files, and none is written.
 * Every person is invented: names from fixed lists, identity numbers that pass the capture screen's check and are
 * distinct across the three seed files, mail at {@value #MAIL_DOMAIN}, telephone numbers in the range the seed's
 * other members use (MR-SEC-14, MR-SEC-15).
 *
 * <h2>The seed's other 310 members stand there too</h2>
 * {@code members.json}'s 160 and the debit-order population's 150 are given a workplace in the same areas, by
 * placements this file carries (MR-ROB-32) -- drawn first, workplace by workplace in proportion to what each still
 * holds, so that a workplace's members are ITS TARGET, the seed's and the invented together. The invented fill what
 * the placements leave.
 *
 * <h2>Numbered downwards from M-099850</h2>
 * Below the debit-order population's M-099851 to M-100000, which sits below {@code members.json}'s block (BR38,
 * F48): never where the capture sequence will reach.
 */
public final class DemonstrationMemberGenerator {

    /** Where the seeder reads it, relative to the project root. */
    public static final Path FILE = Paths.get("src", "main", "resources", "data", "demonstration_members.json");

    /** The fixed random seed: the day the owner said to build. */
    static final long SEED = 20_260_929L;

    /** The first number, and the direction: downwards (BR38). */
    static final int FIRST_NUMBER = 99_850;

    /** The whole areas the invented members stand in (MD35: proposed, and measured before they stand -- MD59). */
    static final List<String> AREAS = List.of("L", "W");
    static final String AREAS_AS_WRITTEN = "L and W";

    /** RFC 2606: reserved for documentation -- no mail is ever delivered to it. */
    static final String MAIL_DOMAIN = "example.org";

    /** The last day any member's record changed: SASBO's last month end on record -- a PAST month (BR40). */
    static final LocalDate LAST_CHANGE = LocalDate.of(2026, 8, 31);
    private static final LocalDate FIRST_JOIN = LocalDate.of(2006, 1, 1);
    private static final LocalDate LAST_JOIN = LocalDate.of(2026, 6, 30);

    /**
     * The nine group-level penetrations of SASBO's month end of 31 August 2026 ({@code grlist}): members over staff
     * complement, per employer group, PER THOUSAND -- proportions, never a count, and the only thing of SASBO's
     * membership this file stands on (MD35). Transcribed from {@code planning/branches/monthly-menu/legacy/
     * referenceData.md} section 2; the file carries them, so a test compares its members with what it was given.
     */
    static final Map<String, Integer> PER_THOUSAND = penetrations();

    private static Map<String, Integer> penetrations() {
        Map<String, Integer> groups = new LinkedHashMap<>();
        groups.put("ABSA BANK GROUP", 772);
        groups.put("FIRST RAND GROUP", 787);
        groups.put("STANDARD BANK GROUP", 767);
        groups.put("NEDBANK GROUP", 699);
        groups.put("OLD MUTUAL GROUP", 318);
        groups.put("RECOGNITION AND EMERGING GROUP CORE B", 533);
        groups.put("RECOGNITION AND EMERGING GROUP CORE C", 297);
        groups.put("CAPITEC GROUP", 639);
        groups.put("NON RECOGNITION GROUP", 41);
        return groups;
    }

    /**
     * 03.7 -- SASBO's August, per employer group, PER TEN THOUSAND members: who left in the month, and who stood
     * suspended at its end ({@code grlist}, {@code legacy/referenceData.md} section 2). Proportions again, never a
     * count; each month of the demonstration's past is drawn from them, group by group.
     */
    static final Map<String, int[]> MONTHLY_PER_TEN_THOUSAND = monthly();

    private static Map<String, int[]> monthly() {
        Map<String, int[]> groups = new LinkedHashMap<>();
        groups.put("ABSA BANK GROUP", new int[]{13, 7});
        groups.put("FIRST RAND GROUP", new int[]{68, 43});
        groups.put("STANDARD BANK GROUP", new int[]{56, 1});
        groups.put("NEDBANK GROUP", new int[]{45, 2});
        groups.put("OLD MUTUAL GROUP", new int[]{315, 148});
        groups.put("RECOGNITION AND EMERGING GROUP CORE B", new int[]{59, 2});
        groups.put("RECOGNITION AND EMERGING GROUP CORE C", new int[]{79, 0});
        groups.put("CAPITEC GROUP", new int[]{16, 14});
        groups.put("NON RECOGNITION GROUP", new int[]{251, 42});
        return groups;
    }

    /** SASBO's deletions of August, by code, across the union ({@code prt_del_summ}): the weights a leaver's code is drawn by. */
    static final int[][] DELETION_WEIGHTS = {{1, 239}, {2, 106}, {4, 3}, {5, 18}, {8, 31}, {9, 3}};

    /** SASBO's suspended at 31 August, by code: the weights a suspension's code is drawn by. */
    static final int[][] SUSPENSION_WEIGHTS = {{11, 139}, {13, 2}, {19, 4}};

    /** One leaver in eight of the earliest month comes back in the latest -- ours, a proposal until MM63. */
    static final int BACK_ONE_IN = 8;

    /** Only a member who joined before this has a past: none of it may fall before they joined. */
    static final LocalDate JOINED_BEFORE = LocalDate.of(2026, 1, 1);

    private static final List<String> FEMALE = List.of("Ayanda", "Bontle", "Chantel", "Dineo", "Esther", "Faith",
            "Grace", "Hlengiwe", "Ilse", "Jabulile", "Kgomotso", "Lindiwe", "Mpho", "Nokuthula", "Ofentse", "Palesa",
            "Rethabile", "Sibongile", "Tebogo", "Tshepiso", "Vuyelwa", "Wendy", "Xoliswa", "Zodwa", "Anika", "Nadia");
    private static final List<String> MALE = List.of("Ahmed", "Bheki", "Charl", "Dawid", "Ernest", "Fanie",
            "Given", "Hlulani", "Isaac", "Jacques", "Khaya", "Lungile", "Musa", "Nhlanhla", "Oupa", "Percy",
            "Riaan", "Sifiso", "Themba", "Tumelo", "Vincent", "Wandile", "Xander", "Zola", "Ashwin", "Kagiso");
    private static final List<String> SURNAMES = List.of("Mabaso", "Dube", "Ngubane", "Van Wyk", "Steyn", "Reddy",
            "Moodley", "Cele", "Mkhize", "Zwane", "Buthelezi", "Kruger", "Brown", "Isaacs", "Abrahams", "Gumede",
            "Hadebe", "Mokwena", "Sibiya", "Nortje", "Olivier", "Padayachee", "Singh", "Hendrikse", "Davids",
            "Mashaba", "Ramaphosa", "Shezi", "Mofokeng", "De Villiers", "Swanepoel", "Du Toit", "Arendse",
            "Naicker", "Pather", "Makhubela", "Mnisi", "Chauke", "Rikhotso", "Visser");
    private static final List<String> RACES = List.of("African", "Coloured", "Indian", "White");
    private static final List<String> TOWNS = List.of("Durban", "Pinetown", "Umhlanga", "Pietermaritzburg",
            "Ballito", "Richards Bay", "Newcastle", "Empangeni", "Amanzimtoti", "Westville");
    private static final List<String> STREETS = List.of("Florida Road", "Musgrave Road", "Umgeni Road",
            "Chatsworth Drive", "Lighthouse Road", "Old Main Road", "Berea Road", "Ridge Road", "Point Road",
            "Marine Parade");

    private DemonstrationMemberGenerator() {
    }

    /** One of SASBO's workplaces in the areas, as the seed's resource holds it -- and its group's rate. */
    record Workplace(int raNumber, String name, String employer, String group, int complement, int perThousand) {
        int target() {
            return (complement * perThousand + 500) / 1000;
        }
    }

    /** One generated member, its fields in the order the file writes them. */
    record Generated(Map<String, Object> fields) {
    }

    /** One of the seed's other members, and where it stands. */
    record Placed(String membershipNumber, int raNumber) {
    }

    /** Everything the file says. */
    record Plan(List<Workplace> workplaces, List<Generated> members, List<Placed> placements,
                List<Map<String, Object>> past) {
    }

    /** The file's bytes. */
    public static String render() {
        Plan plan = plan();
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"generator\": \"DemonstrationMemberGenerator, in the server's test sources. Regenerate with ")
                .append("./gradlew generateApiSurface -- never edit by hand\",\n");
        json.append("  \"seed\": ").append(SEED).append(",\n");
        json.append("  \"count\": ").append(plan.members().size()).append(",\n");
        json.append("  \"generatedFrom\": {\n");
        json.append("    \"areas\": [").append(String.join(", ", AREAS.stream().map(a -> "\"" + a + "\"").toList()))
                .append("],\n");
        json.append("    \"workplaces\": \"").append(WorkplaceSeeder.RESOURCE).append("\",\n");
        json.append("    \"penetrationsPerThousand\": { ");
        List<String> rates = new ArrayList<>();
        PER_THOUSAND.forEach((group, rate) -> rates.add("\"" + group + "\": " + rate));
        json.append(String.join(", ", rates)).append(" },\n");
        json.append("    \"holds\": \"invented members only -- no count of members at a workplace, an employer or a table")
                .append(" code of SASBO's (MD35)\"\n");
        json.append("  },\n");
        json.append("  \"members\": [\n");
        List<String> rows = new ArrayList<>();
        for (Generated member : plan.members()) {
            rows.add("    " + object(member.fields()));
        }
        json.append(String.join(",\n", rows)).append("\n");
        json.append("  ],\n");
        json.append("  \"placements\": [\n");
        List<String> placed = new ArrayList<>();
        for (Placed placement : plan.placements()) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("membershipNumber", placement.membershipNumber());
            fields.put("workplaceRaNumber", placement.raNumber());
            placed.add("    " + object(fields));
        }
        json.append(String.join(",\n", placed)).append("\n");
        json.append("  ],\n");
        json.append("  \"past\": [\n");
        List<String> past = new ArrayList<>();
        for (Map<String, Object> change : plan.past()) {
            past.add("    " + object(change));
        }
        json.append(String.join(",\n", past)).append("\n");
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    /** The workplaces of the areas, the seed's members placed among them, and the invented who fill what is left. */
    static Plan plan() {
        List<Workplace> workplaces = workplacesInTheAreas();
        Random random = new Random(SEED);

        Map<Integer, Integer> placedAt = new HashMap<>();
        List<Placed> placements = new ArrayList<>();
        for (String number : seedMembershipNumbers()) {
            Workplace at = drawByWhatIsLeft(workplaces, placedAt, random);
            placedAt.merge(at.raNumber(), 1, Integer::sum);
            placements.add(new Placed(number, at.raNumber()));
        }

        Set<String> takenIdentityNumbers = new HashSet<>();
        takenIdentityNumbers.addAll(identityNumbersIn("data/members.json"));
        takenIdentityNumbers.addAll(identityNumbersIn(DebitOrderMemberSeeder.SEED_FILE));
        List<Generated> members = new ArrayList<>();
        int number = FIRST_NUMBER;
        for (Workplace workplace : workplaces) {
            int invented = workplace.target() - placedAt.getOrDefault(workplace.raNumber(), 0);
            for (int i = 0; i < invented; i++) {
                members.add(member(number--, workplace, random, takenIdentityNumbers));
            }
        }
        // 03.7 draws AFTER every member, from the same random: the members above are the bytes they were.
        return new Plan(workplaces, members, placements, past(workplaces, members, random));
    }

    /**
     * 03.7 -- the demonstration's past: for each of the three months before the clock's, group by group, its leavers
     * (each with a deletion code drawn by SASBO's August weights) and its suspended (a suspension code, likewise), from
     * the group's own rates; and one leaver in {@value #BACK_ONE_IN} of the earliest month reinstated in the latest.
     * Only invented members, and only those who joined before {@link #JOINED_BEFORE}; no member leaves twice.
     */
    private static List<Map<String, Object>> past(List<Workplace> workplaces, List<Generated> members, Random random) {
        Map<Integer, String> groupOf = new HashMap<>();
        for (Workplace workplace : workplaces) {
            groupOf.put(workplace.raNumber(), workplace.group());
        }
        Map<String, List<Generated>> eligible = new LinkedHashMap<>();
        MONTHLY_PER_TEN_THOUSAND.keySet().forEach(group -> eligible.put(group, new ArrayList<>()));
        for (Generated member : members) {
            String group = groupOf.get((Integer) member.fields().get("workplaceRaNumber"));
            if (group != null && LocalDate.parse((String) member.fields().get("joinDate")).isBefore(JOINED_BEFORE)) {
                eligible.get(group).add(member);
            }
        }
        Map<String, Integer> population = new HashMap<>();
        eligible.forEach((group, pool) -> population.put(group, pool.size()));

        List<Map<String, Object>> changes = new ArrayList<>();
        List<String> leftInTheEarliest = new ArrayList<>();
        for (int monthsBack = 3; monthsBack >= 1; monthsBack--) {
            for (Map.Entry<String, List<Generated>> group : eligible.entrySet()) {
                int[] rates = MONTHLY_PER_TEN_THOUSAND.get(group.getKey());
                List<Generated> pool = group.getValue();
                int leavers = drawn((long) population.get(group.getKey()) * rates[0], random);
                int suspended = drawn((long) population.get(group.getKey()) * rates[1], random);
                for (int i = 0; i < leavers && !pool.isEmpty(); i++) {
                    String who = (String) pool.remove(random.nextInt(pool.size())).fields().get("membershipNumber");
                    int code = weighted(DELETION_WEIGHTS, random);
                    changes.add(change(who, monthsBack, 1 + random.nextInt(28), leavingStatus(code), "DELETION_CODE",
                            code, null));
                    if (monthsBack == 3) {
                        leftInTheEarliest.add(who);
                    }
                }
                for (int i = 0; i < suspended && !pool.isEmpty(); i++) {
                    String who = (String) pool.remove(random.nextInt(pool.size())).fields().get("membershipNumber");
                    changes.add(change(who, monthsBack, 1 + random.nextInt(28), "Suspended", "SUSPENSION_CODE", null,
                            weighted(SUSPENSION_WEIGHTS, random)));
                }
            }
        }
        for (int i = 0; i < leftInTheEarliest.size(); i += BACK_ONE_IN) {
            changes.add(change(leftInTheEarliest.get(i), 1, 1 + random.nextInt(28), "Active", "REINSTATED", null, null));
        }
        changes.sort(Comparator.<Map<String, Object>>comparingInt(c -> -(Integer) c.get("monthsBack"))
                .thenComparingInt(c -> (Integer) c.get("day"))
                .thenComparing(c -> (String) c.get("membershipNumber")));
        return changes;
    }

    private static Map<String, Object> change(String who, int monthsBack, int day, String toStatus, String reason,
                                              Integer deletionCode, Integer suspensionCode) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("membershipNumber", who);
        f.put("monthsBack", monthsBack);
        f.put("day", day);
        f.put("toStatus", toStatus);
        f.put("reason", reason);
        f.put("deletionCode", deletionCode);
        f.put("suspensionCode", suspensionCode);
        return f;
    }

    /** The status a leaver with SASBO's code stands in: 1 left service is Retired, 2 Resigned, the rest Terminated (MM49). */
    static String leavingStatus(int deletionCode) {
        return switch (deletionCode) {
            case 1 -> "Retired";
            case 2 -> "Resigned";
            default -> "Terminated";
        };
    }

    /** An expected count given per ten thousand, drawn: its whole part, and one more with the chance of its fraction. */
    static int drawn(long perTenThousand, Random random) {
        return (int) (perTenThousand / 10_000) + (random.nextInt(10_000) < perTenThousand % 10_000 ? 1 : 0);
    }

    /** A code drawn by its weight. */
    static int weighted(int[][] weights, Random random) {
        int total = 0;
        for (int[] weight : weights) {
            total += weight[1];
        }
        int draw = random.nextInt(total);
        for (int[] weight : weights) {
            draw -= weight[1];
            if (draw < 0) {
                return weight[0];
            }
        }
        throw new IllegalStateException("unreachable: the draw fell outside the weights it was drawn from");
    }

    /** A workplace, drawn in proportion to how many members it still holds room for. */
    private static Workplace drawByWhatIsLeft(List<Workplace> workplaces, Map<Integer, Integer> placedAt,
                                              Random random) {
        int room = 0;
        for (Workplace w : workplaces) {
            room += w.target() - placedAt.getOrDefault(w.raNumber(), 0);
        }
        if (room <= 0) {
            throw new IllegalStateException("the areas " + AREAS_AS_WRITTEN + " have no room left for the seed's members");
        }
        int draw = random.nextInt(room);
        for (Workplace w : workplaces) {
            draw -= w.target() - placedAt.getOrDefault(w.raNumber(), 0);
            if (draw < 0) {
                return w;
            }
        }
        throw new IllegalStateException("unreachable: the draw fell outside the room it was drawn from");
    }

    private static Generated member(int number, Workplace workplace, Random random, Set<String> taken) {
        boolean female = random.nextBoolean();
        String first = pick(female ? FEMALE : MALE, random);
        String surname = pick(SURNAMES, random);
        LocalDate born = LocalDate.of(1966, 1, 1).plusDays(random.nextInt(365 * 33));
        LocalDate joined = FIRST_JOIN.plusDays(random.nextInt((int) ChronoUnit.DAYS.between(FIRST_JOIN, LAST_JOIN) + 1));
        LocalDate changed = joined.plusDays(random.nextInt((int) ChronoUnit.DAYS.between(joined, LAST_CHANGE) + 1));
        String phone = String.format(Locale.ROOT, "+27 60 555 %04d", number % 10_000);

        Map<String, Object> f = new LinkedHashMap<>();
        f.put("membershipNumber", String.format(Locale.ROOT, "M-%06d", number));
        f.put("workplaceRaNumber", workplace.raNumber());
        f.put("employeeNumber", "EMP-" + number);
        f.put("title", title(female, random));
        f.put("initials", initials(first, random));
        f.put("firstNames", first);
        f.put("surname", surname);
        f.put("identityType", "I.D. Number");
        f.put("identityNumber", DebitOrderSeedGenerator.identityNumber(born, female, number, taken));
        f.put("dateOfBirth", born.toString());
        f.put("gender", female ? "Female" : "Male");
        f.put("race", pick(RACES, random));
        f.put("status", "Active");
        f.put("joinDate", joined.toString());
        f.put("personalEmail", address(first, surname, number));
        f.put("preferredEmail", "personal");
        f.put("phone", phone);
        f.put("cellTel", phone);
        f.put("address", (1 + random.nextInt(200)) + " " + pick(STREETS, random) + ", " + pick(TOWNS, random));
        f.put("branch", workplace.name());
        f.put("bankName", workplace.employer());
        f.put("paymentType", "Payroll Deduction");
        f.put("monthlySubscription", "174.00");
        f.put("subscriptionFrequency", "Monthly");
        f.put("currentBalance", "0.00");
        f.put("arrearsBalance", "0.00");
        f.put("lastMonthBalance", "0.00");
        f.put("dateLastChanged", changed.toString());
        return new Generated(f);
    }

    /** The workplaces of the areas, by R/A number, each with its group's rate -- through its table code's employer. */
    static List<Workplace> workplacesInTheAreas() {
        JsonNode lists = read(ReferenceListSeeder.RESOURCE).path("lists");
        Map<Integer, Integer> employerOfCode = new HashMap<>();
        Map<Integer, String> groupOfEmployer = new HashMap<>();
        for (JsonNode list : lists) {
            String name = list.path("list").asText();
            for (JsonNode row : list.path("rows")) {
                if (name.equals("tableCodes") && row.hasNonNull("employer")) {
                    employerOfCode.put(row.path("code").asInt(), row.path("employer").asInt());
                } else if (name.equals("employers")) {
                    groupOfEmployer.put(row.path("ordinal").asInt(), row.path("group").asText());
                }
            }
        }
        List<Workplace> chosen = new ArrayList<>();
        for (JsonNode w : read(WorkplaceSeeder.RESOURCE).path("workplaces")) {
            if (!AREAS.contains(w.path("area").asText())) {
                continue;
            }
            Integer employer = w.hasNonNull("tableCode") ? employerOfCode.get(w.path("tableCode").asInt()) : null;
            String group = employer == null ? null : groupOfEmployer.get(employer);
            if (group != null && !PER_THOUSAND.containsKey(group)) {
                throw new IllegalStateException("a group with no penetration given: " + group);
            }
            chosen.add(new Workplace(w.path("raNumber").asInt(), w.path("name").asText(), w.path("employer").asText(),
                    group, w.path("staffComplement").asInt(), group == null ? 0 : PER_THOUSAND.get(group)));
        }
        chosen.sort(Comparator.comparingInt(Workplace::raNumber));
        return chosen;
    }

    /** The seed's other members -- {@code members.json}'s and the debit-order population's -- in number order. */
    static List<String> seedMembershipNumbers() {
        Set<String> numbers = new TreeSet<>();
        for (String file : List.of("data/members.json", DebitOrderMemberSeeder.SEED_FILE)) {
            JsonNode root = read(file);
            for (JsonNode member : root.isArray() ? root : root.path("members")) {
                String number = member.path("membershipNumber").asText("");
                if (!number.isBlank()) {
                    numbers.add(number.trim());
                }
            }
        }
        return new ArrayList<>(numbers);
    }

    private static String title(boolean female, Random random) {
        int roll = random.nextInt(20);
        if (roll == 0) {
            return "Dr";
        }
        return female ? (roll % 2 == 0 ? "Mrs" : "Ms") : "Mr";
    }

    private static String initials(String first, Random random) {
        String second = random.nextInt(2) == 0 ? "" : String.valueOf((char) ('A' + random.nextInt(26)));
        return first.substring(0, 1) + second;
    }

    /** {@code first.surname.number@example.org}, ASCII and lower case: unique by the number. */
    private static String address(String first, String surname, int number) {
        String local = (first + "." + surname.replace(" ", "")).toLowerCase(Locale.ROOT);
        return local + "." + String.format(Locale.ROOT, "%06d", number) + "@" + MAIL_DOMAIN;
    }

    private static <T> T pick(List<T> from, Random random) {
        return from.get(random.nextInt(from.size()));
    }

    static List<String> identityNumbersIn(String resource) {
        List<String> numbers = new ArrayList<>();
        JsonNode root = read(resource);
        for (JsonNode member : root.isArray() ? root : root.path("members")) {
            if (member.hasNonNull("identityNumber")) {
                numbers.add(member.get("identityNumber").asText());
            }
        }
        return numbers;
    }

    static JsonNode read(String resource) {
        try (InputStream stream = DemonstrationMemberGenerator.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException(resource + " is not on the classpath");
            }
            return new ObjectMapper().readTree(stream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** One object as one line of JSON: keys in the order given, strings escaped, ASCII by construction. */
    private static String object(Map<String, Object> fields) {
        List<String> pairs = new ArrayList<>();
        fields.forEach((key, value) -> pairs.add("\"" + key + "\": " + literal(value)));
        return "{ " + String.join(", ", pairs) + " }";
    }

    private static String literal(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toString().toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> {
                    if (c < 0x20 || c > 0x7e) {
                        throw new IllegalStateException("the seed is ASCII by construction; found U+"
                                + Integer.toHexString(c));
                    }
                    out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }
}
