package za.co.titandynamix.sasbo_online_backend.utils;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Writes {@code data/month_end_rules_members.json}: invented members who show month end's rules working (October
 * build), as {@link DemonstrationMemberGenerator} writes the invented members of 01.4 -- {@code ./gradlew
 * generateApiSurface} writes the file, and {@code MonthEndRulesSeedGeneratorTest} holds the committed bytes to it.
 * Nothing is random: every member and every change is chosen here, by rule, and named for the rule it shows.
 *
 * <h2>Where they stand</h2>
 * At SASBO's workplaces as the seed holds them, in areas no other seed uses: the first {@value #WORKPLACES_PER_BANK}
 * workplaces, by R/A number, of each of six banks in area {@value #AREA} -- {@value #PER_BANK} members a bank, so no
 * bank's figures need another seed's members to be shown (rule C) -- and the one workplace of the table code
 * {@value #OVER_COMPLEMENT_TABLE_CODE}, whose staff complement is small: {@value #OVER_BY} members more than it counts
 * (MEN-26). Its complement is read from the resource, never typed.
 *
 * <h2>Who they are</h2>
 * Invented, every one: names from fixed lists, identity numbers that pass the capture screen's check and are distinct
 * across every seed file, mail at {@value #MAIL_DOMAIN}, telephone numbers in the seed's range by their own number --
 * Active, paying by payroll deduction (MD34), joined years before any change. Numbered downwards from M-096000, the
 * block the parent gave this stream (M-095801 to M-096000).
 *
 * <h2>What happens to them</h2>
 * See {@link #STORIES}: each change is in a month counted back from the clock's, and on a day, so the seed lines up with
 * whatever clock the stack starts on -- the deck's is Wednesday 14 October 2026.
 */
public final class MonthEndRulesSeedGenerator {

    /** Where the seeder reads it, relative to the project root. */
    public static final Path FILE = Paths.get("src", "main", "resources", "data", "month_end_rules_members.json");

    /** The fixed seed of the file's header: the day the rules were built. Nothing here is drawn at random. */
    static final long SEED = 20_261_007L;

    /** The first number, and the direction: downwards, in the block M-095801 to M-096000. */
    static final int FIRST_NUMBER = 96_000;

    /** The lowest number the block allows. */
    static final int LAST_NUMBER = 95_801;

    /** The banks the suspended stand within, by SASBO's table code: Absa, FNB, Standard Bank, Nedbank, Capitec, African. */
    static final List<Integer> BANKS = List.of(24, 1, 5, 13, 43, 44);

    /** The area their workplaces are in -- one no other seed stands in. */
    static final String AREA = "T";

    static final int PER_BANK = 8;
    static final int WORKPLACES_PER_BANK = 2;

    /** HBZ BANK: one workplace, a small complement, in an area no other seed uses. */
    static final int OVER_COMPLEMENT_TABLE_CODE = 155;

    /** How many members more than its complement stand there. */
    static final int OVER_BY = 3;

    /** RFC 2606: no mail is ever delivered to it. */
    static final String MAIL_DOMAIN = "example.org";

    /**
     * One change of a member's past: the bank (its place in {@link #BANKS}), the member within the bank, the month back
     * from the clock's and the day, and the change -- with SASBO's codes and the reasons as the seeder names them.
     */
    record Story(int bank, int member, int monthsBack, int day, String toStatus, String reason, Integer deletionCode,
                 String reasonUnderCode, Integer suspensionCode, String noDeductionReason, String note,
                 boolean undoesTheLeaving, String shows) {
    }

    /**
     * What happens to them -- the rule each change shows in {@code shows}. Days are 1 to 28, so every month has them.
     * With the deck's clock (14 October 2026) the suspensions of April to early July have lapsed, the 25 July one lapses
     * on the 25th, and August's and September's have not.
     */
    static final List<Story> STORIES = List.of(
            suspend(0, 0, 5, 6, 11, "Unpaid leave", "Invented: unpaid leave agreed with the branch.",
                    "MEN-17 a reason from the list with a note; MEN-18 lapsed"),
            suspend(0, 1, 4, 9, 13, null, null, "MEN-18 a debit code within a bank, lapsed"),
            new Story(0, 2, 1, 6, "Resigned", "DELETION_CODE", 2, "Personal", null, null, null, false,
                    "MEN-23 left in September ..."),
            new Story(0, 2, 1, 20, "Active", "REINSTATED", null, null, null, null, null, true,
                    "MEN-23 ... and rejoined in September: membership kept, counted as neither"),
            suspend(1, 0, 4, 2, 12, "Maternity leave", null, "MEN-17 her code 12; MEN-18 lapsed"),
            suspend(1, 1, 2, 11, 21, "Sick leave", "Invented: booked off by a doctor.", "MEN-17 her code 21"),
            new Story(1, 2, 2, 14, "Resigned", "DELETION_CODE", 2, "Other Union", null, null, null, false,
                    "MEN-19 a member who left: capturing them again is refused, and offers to reinstate"),
            suspend(2, 0, 3, 3, 11, "Insufficient funds", null, "MEN-17 a reason without a code of its own; lapsed"),
            suspend(2, 1, 6, 12, 15, null, null, "MEN-18 a debit code, lapsed months ago"),
            new Story(2, 2, 2, 8, "Terminated", "DELETION_CODE", 5, null, null, null, null, false,
                    "rule L: left in August ..."),
            new Story(2, 2, 1, 15, "Active", "REINSTATED", null, null, null, null, null, false,
                    "rule R: ... rejoined in September -- a month later, so counted both times"),
            suspend(3, 0, 3, 25, 11, null, "Invented: on leave without pay until further notice.",
                    "MEN-17 a reason typed, none chosen; MEN-18 lapses on the 25th of the clock's month"),
            suspend(3, 1, 1, 5, 16, null, null, "a debit code, recent"),
            suspend(4, 0, 1, 12, 11, "Paternity leave", null, "MEN-17 a reason her codes do not cover"),
            suspend(4, 1, 5, 20, 19, null, null, "MEN-18 a debit code, lapsed"),
            suspend(4, 2, 2, 22, 18, null, null, "a debit code -- the bank's insufficient funds"),
            suspend(5, 0, 1, 3, 21, "Sick leave", null, "MEN-17 her code 21"),
            suspend(5, 1, 3, 1, 30, null, null, "MEN-18 no authority to debit, lapsed on the 1st"),
            suspend(5, 2, 2, 17, 12, "Maternity leave", null, "MEN-17 her code 12"));

    private static Story suspend(int bank, int member, int monthsBack, int day, int code, String reason, String note,
                                 String shows) {
        return new Story(bank, member, monthsBack, day, "Suspended", "SUSPENSION_CODE", null, null, code, reason, note,
                false, shows);
    }

    private static final List<String> FEMALE = List.of("Amahle", "Busisiwe", "Carla", "Daleen", "Elna", "Fikile",
            "Gugu", "Heidi", "Ingrid", "Jolene", "Karabo", "Lerato", "Marlene", "Noxolo", "Precious", "Refilwe");
    private static final List<String> MALE = List.of("Andile", "Brandon", "Calvin", "Deon", "Elias", "Francois",
            "Gerhard", "Hendrik", "Imraan", "Johan", "Kabelo", "Lwazi", "Mandla", "Neville", "Pieter", "Sipho");
    private static final List<String> SURNAMES = List.of("Abels", "Barnard", "Cupido", "Damons", "Esterhuizen",
            "Fortuin", "Goliath", "Hartnick", "Jacobs", "Klaasen", "Lombard", "Matthys", "Ntsebeza", "October",
            "Petersen", "Qwabe", "Rossouw", "Solomons", "Titus", "Uys", "Vollenhoven", "Williams", "Xaba", "Zondi");
    private static final List<String> STREETS = List.of("Main Road", "Voortrekker Road", "Victoria Road",
            "Kloof Street", "Durban Road", "Station Road", "Church Street", "Long Street");
    private static final List<String> TOWNS = List.of("Bellville", "Goodwood", "Parow", "Wynberg", "Claremont",
            "Somerset West", "Durbanville", "Kuils River");
    private static final List<String> RACES = List.of("African", "Coloured", "Indian", "White");

    private MonthEndRulesSeedGenerator() {
    }

    /** A workplace a member stands at, from the resource. */
    record Workplace(int raNumber, String name, String employer, int tableCode, int complement) {
    }

    /** Everything the file says: the members, by bank and then the over-full workplace, and their past. */
    record Plan(List<Map<String, Object>> members, List<Map<String, Object>> changes, Workplace overFull,
                Map<Integer, List<String>> byBank) {
    }

    /** The file's bytes. */
    public static String render() {
        Plan plan = plan();
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"generator\": \"MonthEndRulesSeedGenerator, in the server's test sources. Regenerate with ")
                .append("./gradlew generateApiSurface -- never edit by hand\",\n");
        json.append("  \"seed\": ").append(SEED).append(",\n");
        json.append("  \"count\": ").append(plan.members().size()).append(",\n");
        json.append("  \"holds\": \"invented members only -- suspended within six banks under SASBO's codes, some ")
                .append("lapsed (MEN-17, MEN-18); a rejoin in the same month (MEN-23); a member who left (MEN-19); and ")
                .append("more members than staff at one workplace (MEN-26). No count of members of SASBO's\",\n");
        json.append("  \"members\": [\n");
        List<String> rows = new ArrayList<>();
        for (Map<String, Object> member : plan.members()) {
            rows.add("    " + object(member));
        }
        json.append(String.join(",\n", rows)).append("\n");
        json.append("  ],\n");
        json.append("  \"changes\": [\n");
        List<String> changes = new ArrayList<>();
        for (Map<String, Object> change : plan.changes()) {
            changes.add("    " + object(change));
        }
        json.append(String.join(",\n", changes)).append("\n");
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    static Plan plan() {
        Set<String> taken = new HashSet<>();
        taken.addAll(DemonstrationMemberGenerator.identityNumbersIn("data/members.json"));
        taken.addAll(DemonstrationMemberGenerator.identityNumbersIn(DebitOrderMemberSeeder.SEED_FILE));
        taken.addAll(DemonstrationMemberGenerator.identityNumbersIn(DemonstrationMemberSeeder.SEED_FILE));
        List<Map<String, Object>> members = new ArrayList<>();
        Map<Integer, List<String>> byBank = new LinkedHashMap<>();
        int number = FIRST_NUMBER;
        for (int bank = 0; bank < BANKS.size(); bank++) {
            List<Workplace> workplaces = workplacesOf(BANKS.get(bank), AREA, WORKPLACES_PER_BANK);
            List<String> numbers = new ArrayList<>();
            for (int i = 0; i < PER_BANK; i++) {
                Map<String, Object> member = member(number--, workplaces.get(i % workplaces.size()), taken);
                members.add(member);
                numbers.add((String) member.get("membershipNumber"));
            }
            byBank.put(bank, numbers);
        }
        Workplace overFull = workplacesOf(OVER_COMPLEMENT_TABLE_CODE, null, 1).get(0);
        for (int i = 0; i < overFull.complement() + OVER_BY; i++) {
            members.add(member(number--, overFull, taken));
        }
        if (number + 1 < LAST_NUMBER) {
            throw new IllegalStateException("the members run past the block M-095801 to M-096000");
        }
        List<Map<String, Object>> changes = new ArrayList<>();
        for (Story story : STORIES) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("membershipNumber", byBank.get(story.bank()).get(story.member()));
            f.put("monthsBack", story.monthsBack());
            f.put("day", story.day());
            f.put("toStatus", story.toStatus());
            f.put("reason", story.reason());
            f.put("deletionCode", story.deletionCode());
            f.put("reasonUnderCode", story.reasonUnderCode());
            f.put("suspensionCode", story.suspensionCode());
            f.put("noDeductionReason", story.noDeductionReason());
            f.put("note", story.note());
            f.put("undoesTheLeaving", story.undoesTheLeaving());
            changes.add(f);
        }
        changes.sort(Comparator.<Map<String, Object>>comparingInt(c -> -(Integer) c.get("monthsBack"))
                .thenComparingInt(c -> (Integer) c.get("day"))
                .thenComparing(c -> (String) c.get("membershipNumber")));
        return new Plan(members, changes, overFull, byBank);
    }

    /** The first {@code count} workplaces of a table code -- in an area, or anywhere -- by R/A number. */
    static List<Workplace> workplacesOf(int tableCode, String area, int count) {
        List<Workplace> found = new ArrayList<>();
        for (JsonNode w : DemonstrationMemberGenerator.read(WorkplaceSeeder.RESOURCE).path("workplaces")) {
            if (w.path("tableCode").isIntegralNumber() && w.path("tableCode").asInt() == tableCode
                    && (area == null || area.equals(w.path("area").asText()))) {
                found.add(new Workplace(w.path("raNumber").asInt(), w.path("name").asText(),
                        w.path("employer").asText(), tableCode, w.path("staffComplement").asInt()));
            }
        }
        found.sort(Comparator.comparingInt(Workplace::raNumber));
        if (found.size() < count) {
            throw new IllegalStateException("table code " + tableCode + " has " + found.size() + " workplace(s) here");
        }
        return found.subList(0, count);
    }

    private static Map<String, Object> member(int number, Workplace workplace, Set<String> taken) {
        boolean female = number % 2 == 0;
        String first = pick(female ? FEMALE : MALE, number / 2);
        String surname = pick(SURNAMES, number / 3);
        LocalDate born = LocalDate.of(1970, 1, 1).plusDays((number * 37L) % (365 * 30));
        LocalDate joined = LocalDate.of(2010, 1, 1).plusDays((number * 53L) % (365 * 14));
        String phone = String.format(Locale.ROOT, "+27 60 555 %04d", number % 10_000);
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("membershipNumber", String.format(Locale.ROOT, "M-%06d", number));
        f.put("workplaceRaNumber", workplace.raNumber());
        f.put("employeeNumber", "EMP-" + number);
        f.put("title", female ? (number % 4 == 0 ? "Mrs" : "Ms") : "Mr");
        f.put("initials", first.substring(0, 1));
        f.put("firstNames", first);
        f.put("surname", surname);
        f.put("identityType", "I.D. Number");
        f.put("identityNumber", DebitOrderSeedGenerator.identityNumber(born, female, number, taken));
        f.put("dateOfBirth", born.toString());
        f.put("gender", female ? "Female" : "Male");
        f.put("race", pick(RACES, number));
        f.put("status", "Active");
        f.put("joinDate", joined.toString());
        f.put("personalEmail", (first + "." + surname).toLowerCase(Locale.ROOT) + "." + String.format(Locale.ROOT,
                "%06d", number) + "@" + MAIL_DOMAIN);
        f.put("preferredEmail", "personal");
        f.put("phone", phone);
        f.put("cellTel", phone);
        f.put("address", (1 + number % 150) + " " + pick(STREETS, number) + ", " + pick(TOWNS, number / 5));
        f.put("branch", workplace.name());
        f.put("bankName", workplace.employer());
        f.put("paymentType", "Payroll Deduction");
        f.put("monthlySubscription", "174.00");
        f.put("subscriptionFrequency", "Monthly");
        f.put("currentBalance", "0.00");
        f.put("arrearsBalance", "0.00");
        f.put("lastMonthBalance", "0.00");
        f.put("dateLastChanged", joined.plusDays(30).toString());
        return f;
    }

    private static <T> T pick(List<T> from, int index) {
        return from.get(Math.floorMod(index, from.size()));
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
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toString().toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> {
                    if (c < 0x20 || c > 0x7e) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
