package za.co.titandynamix.sasbo_online_backend.utils;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

/**
 * Writes {@code data/demonstration_population.json}: the October build's demonstration population, so that no figure
 * of month end reads nought on a demonstration (the 5 Oct Show &amp; Tell: "we don't want to be seeing zeros"; the
 * owner, 6 Oct: "some good seed data that reflect all of these work"). The contract other seeders build on is
 * {@code C:/tmp/sasbo-work/oct/contracts/demo-population.md}.
 *
 * <h2>Why a generator, and why it lives in the tests</h2>
 * As {@link DemonstrationMemberGenerator}'s: a population is trustworthy only while it is exactly what its generator
 * writes. {@code ./gradlew generateApiSurface} writes the file; the fast lane asserts the committed bytes are what it
 * would write ({@code DemonstrationPopulationGeneratorTest}). The random seed and every order are fixed, and
 * {@code java.util.Random} and {@code Collections.shuffle} are specified to the bit.
 *
 * <h2>Where they stand</h2>
 * In every pack the month-end packs are cut by -- JHB (areas A, F, K, X), JHB2 (H, N, P), CPT, PTA, PE, BLM and DBN --
 * in seventeen areas beside the existing population's L and W. In each area a few of SASBO's workplaces are chosen and
 * each holds {@code round(complement x its group's penetration)} members, the nine proportions the existing population
 * stands on (MD35): no chosen workplace over its complement, none under {@value #AT_LEAST} so rule C never hides it.
 * Every employer group gets members, and the one table code the existing seed leaves at fewer than five is lifted, so
 * the membership sheets hide nothing (the 5 Oct sheet that "came out short"). No count of SASBO's members is used.
 *
 * <h2>What happens to them</h2>
 * In each of the three months before the clock's: joiners and leavers in every area, leavers under every one of
 * SASBO's deletion codes and every reason under code 2; the suspended under every code of Stephne's sheet, suspended
 * in the first or second month back so that none has lapsed; a few suspended and lifted; a few who left and came back
 * (rule R). Representatives, committee members and Memnet members in every area. Joiners carry a recruiter by position.
 *
 * <h2>Numbered downwards from M-098500</h2>
 * The contract's block, M-096001 to M-098500: below the existing seeds (M-098802 up), clear of the other streams'
 * blocks (M-095001 to M-096000), and never where the capture sequence (100161 up) will reach.
 */
public final class DemonstrationPopulationGenerator {

    /** Where the seeder reads it, relative to the project root. */
    public static final Path FILE = Paths.get("src", "main", "resources", "data", "demonstration_population.json");

    /** The fixed random seed: the day the owner asked for seed data that reflects the work. */
    static final long SEED = 20_261_006L;

    /** The first number, the floor of the block, and the direction: downwards. */
    static final int FIRST_NUMBER = 98_500;
    static final int FLOOR = 96_001;

    /** RFC 2606: reserved for documentation -- no mail is ever delivered to it. */
    static final String MAIL_DOMAIN = "example.org";

    /** The seed's telephone convention (the debit-order and demonstration files): the number's last four digits. */
    static final String TELEPHONE = "+27 60 555 ";

    /** The packs (the month-end packs' regions, JHB in two) and the areas seeded in each, with each area's share. */
    static final Map<String, Map<String, Integer>> PACKS = packs();

    private static Map<String, Map<String, Integer>> packs() {
        Map<String, Map<String, Integer>> packs = new LinkedHashMap<>();
        packs.put("JHB", areas("A", 70, "F", 70, "K", 70, "X", 70));
        packs.put("JHB2", areas("H", 70, "N", 70, "P", 70));
        packs.put("CPT", areas("B", 80, "J", 80, "T", 60, "U", 60));
        packs.put("PTA", areas("D", 80, "G", 80));
        packs.put("PE", areas("I", 75, "M", 75));
        packs.put("BLM", areas("E", 75, "S", 75));
        // L and W hold the existing population at their targets: topped up below their complement, so that they too
        // have joiners and leavers in each past month.
        packs.put("DBN", areas("C", 60, "O", 60, "R", 60, "V", 60, "L", 15, "W", 12));
        return Collections.unmodifiableMap(packs);
    }

    private static Map<String, Integer> areas(Object... letterThenShare) {
        Map<String, Integer> areas = new LinkedHashMap<>();
        for (int i = 0; i < letterThenShare.length; i += 2) {
            areas.put((String) letterThenShare[i], (Integer) letterThenShare[i + 1]);
        }
        return areas;
    }

    /**
     * The areas the existing population stands in, its workplaces at their targets: a workplace there is chosen only for
     * the room between its target and its complement, and holds a share of that room, never the target again.
     */
    static final Set<String> EXISTING_AREAS = Set.copyOf(DemonstrationMemberGenerator.AREAS);

    /**
     * A chosen workplace holds at least this many -- rule C hides a named row of one to four -- and at most this many;
     * and it keeps at least {@code AT_LEAST} on record in EVERY month: a joiner is missing from the months before they
     * joined and a leaver from the months after, so each workplace has room for only {@code members - AT_LEAST} of them.
     */
    static final int AT_LEAST = 5;
    static final int AT_MOST = 40;

    /** Workplaces holding at least this many are chosen first, so an area has room for its movement. */
    static final int ROOMY = 8;

    /** The months before the clock's that the past falls in -- the three the seed takes (MonthEndTakeSeeder). */
    static final int MONTHS = MonthEndTakeSeeder.MONTHS;

    /** Joiners, an area a month; leavers, an area a month. */
    static final int JOINED_A_MONTH = 2;
    static final int LEFT_A_MONTH = 1;

    /**
     * SASBO's deletion codes 1 to 10 and how many leave under each IN EACH MONTH -- one leaver an area (23) and one at an
     * employer outside the core groups: every code every month, code 2 once for each of the five reasons under it, most
     * leaving service or resigning as most of SASBO's August leavers did.
     */
    static final int[][] LEAVING_A_MONTH = {{1, 6}, {2, 5}, {3, 1}, {4, 1}, {5, 2}, {6, 1}, {7, 1}, {8, 3}, {9, 3},
            {10, 1}};

    /** Stephne's suspension codes and how many stand suspended under each at the end -- every code at least three. */
    static final int[][] SUSPENDED = {{11, 8}, {12, 4}, {13, 3}, {15, 3}, {16, 3}, {18, 3}, {19, 3}, {21, 4}, {30, 3},
            {31, 3}, {32, 3}, {33, 3}};

    /** Suspended in the third month back and lifted in the first: "suspensions that was probably uplifted" (MEN-22). */
    static final int[] LIFTED = {11, 12, 21, 11, 12, 21};

    /** Who left in the third month back and came back in the first (rule R): code 2 with a reason, 1, code 2 again. */
    static final Object[][] REJOINED = {{2, "Personal"}, {1, null}, {2, "Other Union"}};

    /** An area's elected representatives, committee members and Memnet members. */
    static final int SHOP_STEWARDS = 2;
    static final int BEC_MEMBERS = 1;
    static final int MEMNET_MEMBERS = 2;

    /** The recruiters a joiner is credited to, by position among those on record: the first this many by id. */
    static final int RECRUITERS = 12;

    /**
     * The codes of Stephne's DOD "Suspension codes" sheet that SASBO's August printouts lack (handover 2026-10-06,
     * section B), in her words, upper case as dod's V70 writes them -- so whichever runs first, the names agree.
     */
    static final Map<Integer, String> NEW_SUSPENSION_CODES = newCodes();
    static final String CODES_SOURCE = "Stephne, DOD 'Suspension codes' sheet (handover 2026-10-06, section B)";

    private static Map<Integer, String> newCodes() {
        Map<Integer, String> codes = new LinkedHashMap<>();
        codes.put(12, "MATERNITY LEAVE");
        codes.put(15, "ACCOUNT CLOSED");
        codes.put(16, "PAYMENT STOPPED");
        codes.put(18, "INSUFFICIENT FUNDS");
        codes.put(21, "SICK LEAVE");
        for (int code = 30; code <= 33; code++) {
            codes.put(code, "NO AUTHORITY TO DEBIT ACCOUNT");
        }
        return Collections.unmodifiableMap(codes);
    }

    /** Joining long before the three months, and the last day anything about the long-standing members changed. */
    static final LocalDate FIRST_JOIN = LocalDate.of(2008, 1, 1);
    static final LocalDate LAST_JOIN = LocalDate.of(2025, 12, 31);
    static final LocalDate LAST_CHANGE = LocalDate.of(2026, 6, 30);
    private static final LocalDate FIRST_ELECTION = LocalDate.of(2023, 1, 1);
    private static final LocalDate LAST_ELECTION = LocalDate.of(2025, 12, 31);

    /** Invented names: common first names and surnames, combined at random -- nobody in particular. */
    public static final List<String> FEMALE = List.of("Amahle", "Busisiwe", "Carla", "Dimakatso", "Elna", "Fikile", "Gugu",
            "Hannelie", "Itumeleng", "Jolene", "Karabo", "Lerato", "Marelize", "Naledi", "Onalenna", "Phindile",
            "Refilwe", "Simone", "Thandeka", "Unathi", "Vanessa", "Yolanda", "Zanele", "Bianca", "Lindie", "Mbali");
    public static final List<String> MALE = List.of("Andile", "Bongani", "Clint", "Dumisani", "Eugene", "Francois", "Gerhard",
            "Hendrik", "Johan", "Kabelo", "Lwazi", "Mandla", "Neo", "Olwethu", "Pieter", "Rudi", "Sizwe", "Thabo",
            "Ulrich", "Vusi", "Wikus", "Xolani", "Yusuf", "Zakhele", "Brandon", "Lesego");
    public static final List<String> SURNAMES = List.of("Adams", "Bezuidenhout", "Botha", "Coetzee", "Daniels", "Dlamini",
            "Erasmus", "Fourie", "Govender", "Hlongwane", "Jacobs", "Khumalo", "Langa", "Le Roux", "Mahlangu", "Maseko",
            "Mokoena", "Moloi", "Mthembu", "Naidoo", "Ndlovu", "Nel", "Ngcobo", "Nkosi", "Petersen", "Pillay",
            "Pretorius", "Radebe", "Sithole", "Smit", "Tshabalala", "Venter", "Williams", "Xaba", "Mabuza", "Kekana",
            "Ferreira", "Hartley", "Joubert", "Molefe");
    private static final List<String> RACES = List.of("African", "Coloured", "Indian", "White");
    private static final List<String> STREETS = List.of("Church Street", "Main Road", "Park Avenue", "Station Road",
            "High Street", "Market Street", "Oak Avenue", "Hill Street", "Long Street", "Rose Avenue");
    private static final Map<String, List<String>> TOWNS = Map.of(
            "JHB", List.of("Johannesburg", "Sandton", "Randburg", "Roodepoort", "Midrand", "Germiston", "Benoni"),
            "JHB2", List.of("Johannesburg", "Soweto", "Boksburg", "Kempton Park", "Alberton", "Edenvale"),
            "CPT", List.of("Cape Town", "Bellville", "Stellenbosch", "Paarl", "Somerset West", "Durbanville"),
            "PTA", List.of("Pretoria", "Centurion", "Hatfield", "Mamelodi", "Silverton", "Akasia"),
            "PE", List.of("Gqeberha", "Kariega", "Makhanda", "East London", "Mthatha", "Jeffreys Bay"),
            "BLM", List.of("Bloemfontein", "Welkom", "Kimberley", "Bethlehem", "Kroonstad", "Polokwane"),
            "DBN", List.of("Durban", "Pinetown", "Pietermaritzburg", "Newcastle", "Empangeni", "Westville"));

    private DemonstrationPopulationGenerator() {
    }

    // ================================================================================================ the plan

    /** One of SASBO's workplaces, as the seed's resource holds it, with its lineage and its group's rate. */
    record Workplace(int raNumber, String name, String employerPrinted, int tableCode, int employer, String employerName,
                     String group, String area, String committee, int complement, int perThousand) {
        int target() {
            return (complement * perThousand + 500) / 1000;
        }
    }

    /** A workplace chosen, the pack it is counted in, and how many members it holds. */
    record Chosen(Workplace workplace, String pack, int members) {
    }

    /** One member being written: the file's fields, in the order the file writes them, and where it stands. */
    static final class Person {
        final Map<String, Object> fields = new LinkedHashMap<>();
        final Chosen at;
        boolean used;

        Person(Chosen at) {
            this.at = at;
        }

        String number() {
            return (String) fields.get("membershipNumber");
        }
    }

    /** Everything the file says. */
    record Plan(List<Chosen> chosen, List<Person> members, List<Map<String, Object>> past, Map<String, Object> summary) {
    }

    /** The file's bytes. */
    public static String render() {
        Plan plan = plan();
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"generator\": \"DemonstrationPopulationGenerator, in the server's test sources. Regenerate with ")
                .append("./gradlew generateApiSurface -- never edit by hand\",\n");
        json.append("  \"seed\": ").append(SEED).append(",\n");
        json.append("  \"count\": ").append(plan.members().size()).append(",\n");
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("first", String.format(Locale.ROOT, "M-%06d", FIRST_NUMBER));
        block.put("floor", String.format(Locale.ROOT, "M-%06d", FLOOR));
        block.put("direction", "downwards");
        json.append("  \"block\": ").append(object(block)).append(",\n");
        json.append("  \"generatedFrom\": ").append(object(generatedFrom())).append(",\n");
        json.append("  \"suspensionCodes\": [\n");
        List<String> codes = new ArrayList<>();
        NEW_SUSPENSION_CODES.forEach((code, name) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", code);
            row.put("name", name);
            row.put("source", CODES_SOURCE);
            codes.add("    " + object(row));
        });
        json.append(String.join(",\n", codes)).append("\n  ],\n");
        json.append("  \"summary\": {\n");
        List<String> summary = new ArrayList<>();
        plan.summary().forEach((key, value) -> summary.add("    \"" + key + "\": " + literal(value)));
        json.append(String.join(",\n", summary)).append("\n  },\n");
        json.append("  \"members\": [\n");
        List<String> rows = new ArrayList<>();
        for (Person member : plan.members()) {
            rows.add("    " + object(member.fields));
        }
        json.append(String.join(",\n", rows)).append("\n  ],\n");
        json.append("  \"past\": [\n");
        List<String> past = new ArrayList<>();
        for (Map<String, Object> change : plan.past()) {
            past.add("    " + object(change));
        }
        json.append(String.join(",\n", past)).append("\n  ]\n");
        json.append("}\n");
        return json.toString();
    }

    private static Map<String, Object> generatedFrom() {
        Map<String, Object> from = new LinkedHashMap<>();
        Map<String, Object> packs = new LinkedHashMap<>();
        PACKS.forEach((pack, areas) -> packs.put(pack, new ArrayList<>(areas.keySet())));
        from.put("packs", packs);
        from.put("workplaces", WorkplaceSeeder.RESOURCE);
        from.put("lists", ReferenceListSeeder.RESOURCE);
        Map<String, Object> rates = new LinkedHashMap<>(DemonstrationMemberGenerator.PER_THOUSAND);
        from.put("penetrationsPerThousand", rates);
        from.put("holds", "invented members only -- no count of members at a workplace, an employer or a table code of "
                + "SASBO's (MD35)");
        return from;
    }

    /** The workplaces chosen, the members who stand at them, and their past. */
    static Plan plan() {
        Lists lists = lists();
        List<Workplace> all = workplaces(lists);
        Random random = new Random(SEED);
        Map<Integer, Long> existing = existingOnRecordByTableCode();

        // 1. Each area's share, from workplaces chosen in a shuffled order.
        Map<String, List<Chosen>> byArea = new LinkedHashMap<>();
        Set<Integer> taken = new HashSet<>();
        PACKS.forEach((pack, areas) -> areas.forEach((area, share) -> {
            boolean topUp = EXISTING_AREAS.contains(area);
            List<Workplace> candidates = new ArrayList<>(all.stream().filter(w -> w.area().equals(area))
                    .filter(w -> topUp ? w.complement() - w.target() >= ROOMY
                            : w.target() >= AT_LEAST && w.target() <= AT_MOST).toList());
            Collections.shuffle(candidates, random);
            // The roomy first, in their shuffled order: a workplace of five has no room for a joiner or a leaver.
            candidates.sort(Comparator.comparingInt(w -> topUp || w.target() >= ROOMY ? 0 : 1));
            List<Chosen> chosen = new ArrayList<>();
            int running = 0;
            for (Workplace w : candidates) {
                if (running >= share - 2) {
                    break;
                }
                // A workplace of a new area holds its target; one topped up holds what the share still wants, within
                // the room below its complement.
                int members = topUp ? Math.min(Math.min(w.complement() - w.target(), AT_MOST),
                        Math.max(ROOMY, share + 2 - running)) : w.target();
                if (running + members > share + 8) {
                    continue;
                }
                chosen.add(new Chosen(w, pack, members));
                taken.add(w.raNumber());
                running += members;
            }
            byArea.put(area, chosen);
        }));

        // 2. Every employer group has members: a group the shares left out gets its largest workplace in the areas.
        Set<String> groups = new HashSet<>();
        byArea.values().forEach(list -> list.forEach(c -> groups.add(c.workplace().group())));
        for (String group : DemonstrationMemberGenerator.PER_THOUSAND.keySet()) {
            if (groups.contains(group)) {
                continue;
            }
            // The group's workplace whose own share fits best -- within the bounds if any is, else the largest -- never a
            // head office whose share would be hundreds.
            all.stream().filter(w -> w.group().equals(group) && packOf(w.area()) != null && byArea.containsKey(w.area())
                            && !EXISTING_AREAS.contains(w.area()) && !taken.contains(w.raNumber())
                            && w.complement() >= AT_LEAST)
                    .max(Comparator.comparingInt((Workplace w) -> w.target() >= AT_LEAST && w.target() <= AT_MOST ? 1 : 0)
                            .thenComparingInt(w -> w.target() <= AT_MOST ? w.target() : -w.target())
                            .thenComparingInt(Workplace::complement).thenComparingInt(w -> -w.raNumber()))
                    .ifPresent(w -> {
                        byArea.get(w.area()).add(new Chosen(w, packOf(w.area()), atLeastFive(w)));
                        taken.add(w.raNumber());
                        groups.add(group);
                    });
        }

        // 3. A table code the existing seed leaves at one to four on record is lifted to five or more, at its largest
        // workplace outside L and W -- one of the areas above where it can be.
        Set<Integer> coveredCodes = new HashSet<>();
        byArea.values().forEach(list -> list.forEach(c -> coveredCodes.add(c.workplace().tableCode())));
        for (Map.Entry<Integer, Long> code : new TreeMap<>(existing).entrySet()) {
            if (code.getValue() < 1 || code.getValue() >= AT_LEAST || coveredCodes.contains(code.getKey())) {
                continue;
            }
            all.stream().filter(w -> w.tableCode() == code.getKey() && !EXISTING_AREAS.contains(w.area())
                            && packOf(w.area()) != null && !taken.contains(w.raNumber()) && w.complement() >= AT_LEAST)
                    .max(Comparator.comparingInt((Workplace w) -> byArea.containsKey(w.area()) ? 1 : 0)
                            .thenComparingInt(Workplace::complement).thenComparingInt(w -> -w.raNumber()))
                    .ifPresent(w -> {
                        byArea.computeIfAbsent(w.area(), a -> new ArrayList<>())
                                .add(new Chosen(w, packOf(w.area()), atLeastFive(w)));
                        taken.add(w.raNumber());
                        coveredCodes.add(w.tableCode());
                    });
        }

        // 4. The members, area by area, workplace by workplace in R/A order, numbered downwards.
        // Clear of every other seed file's identity numbers: V26 holds one member to a number, so a clash would refuse
        // the whole batch at a start.
        Set<String> identities = new HashSet<>();
        for (String seedFile : SeedMemberNumbers.SEED_FILES) {
            if (!seedFile.equals(DemonstrationPopulationSeeder.SEED_FILE)) {
                identities.addAll(DemonstrationMemberGenerator.identityNumbersIn(seedFile));
            }
        }
        List<Chosen> chosen = new ArrayList<>();
        List<Person> members = new ArrayList<>();
        Map<String, List<Person>> peopleOf = new LinkedHashMap<>();
        int number = FIRST_NUMBER;
        for (Map.Entry<String, List<Chosen>> area : byArea.entrySet()) {
            List<Chosen> here = new ArrayList<>(area.getValue());
            here.sort(Comparator.comparingInt(c -> c.workplace().raNumber()));
            for (Chosen c : here) {
                chosen.add(c);
                for (int i = 0; i < c.members(); i++) {
                    if (number < FLOOR) {
                        throw new IllegalStateException("the population has outgrown its block: M-" + FLOOR + " reached");
                    }
                    Person person = person(number--, c, random, identities);
                    members.add(person);
                    peopleOf.computeIfAbsent(area.getKey(), a -> new ArrayList<>()).add(person);
                }
            }
        }

        // 5. Each planned area's joiners and leavers in each of the three months -- each at a workplace with room for
        // one more mover, so that every workplace keeps five on record in every month.
        // A workplace topped up keeps the existing population's members beside these, so all of these may move but what
        // its own target leaves short of five.
        Map<Integer, Integer> room = new HashMap<>();
        chosen.forEach(c -> room.put(c.workplace().raNumber(), EXISTING_AREAS.contains(c.workplace().area())
                ? c.members() - Math.max(0, AT_LEAST - c.workplace().target()) : c.members() - AT_LEAST));
        // Each month its own codes, shuffled: every deletion code, and code 2 once for every reason under it, in EVERY
        // month -- so the month a slide shows has no column of nought.
        List<String> reasons = lists.reasonsUnderTwo();
        List<Deque<Integer>> leaving = new ArrayList<>();
        int[] reasonTurn = new int[MONTHS + 1];
        for (int monthsBack = 1; monthsBack <= MONTHS; monthsBack++) {
            List<Integer> codes = expand(LEAVING_A_MONTH);
            Collections.shuffle(codes, random);
            leaving.add(new ArrayDeque<>(codes));
        }
        int[] recruiterTurn = {0};
        List<Map<String, Object>> past = new ArrayList<>();
        List<List<Person>> movable = new ArrayList<>();
        for (Map.Entry<String, List<Person>> area : peopleOf.entrySet()) {
            // Only the planned areas move: a workplace added to lift a group or a table code holds what lifts it.
            if (inThePacks(area.getKey())) {
                List<Person> people = new ArrayList<>(area.getValue());
                Collections.shuffle(people, random);
                movable.add(people);
            }
        }
        // First, each month, a joiner and a leaver at an employer outside the core groups: "other" is a column of its
        // own in the new and deleted members, and must move too.
        List<Person> outsideTheCore = new ArrayList<>();
        movable.forEach(people -> people.stream().filter(p -> !lists.coreGroups().contains(p.at.workplace().group()))
                .forEach(outsideTheCore::add));
        for (int monthsBack = 1; monthsBack <= MONTHS; monthsBack++) {
            Person joiner = firstWithRoom(outsideTheCore, room);
            Person leaver = firstWithRoom(outsideTheCore, room);
            if (joiner == null || leaver == null) {
                throw new IllegalStateException("no room outside the core groups for month " + monthsBack + "'s movement");
            }
            joins(joiner, monthsBack, recruiterTurn, random);
            past.add(leaver(leaver, monthsBack, leaving.get(monthsBack - 1).poll(), reasons, reasonTurn, random));
        }
        // Then every planned area's own: two joiners and one leaver a month.
        for (List<Person> people : movable) {
            int joined = 0;
            for (Person p : people) {
                if (joined == MONTHS * JOINED_A_MONTH) {
                    break;
                }
                if (takeRoom(p, room)) {
                    joins(p, 1 + joined / JOINED_A_MONTH, recruiterTurn, random);
                    joined++;
                }
            }
            for (int monthsBack = 1; monthsBack <= MONTHS; monthsBack++) {
                Deque<Integer> codes = leaving.get(monthsBack - 1);
                for (int i = 0; i < LEFT_A_MONTH && !codes.isEmpty(); i++) {
                    Person p = firstWithRoom(people, room);
                    if (p != null) {
                        past.add(leaver(p, monthsBack, codes.poll(), reasons, reasonTurn, random));
                    }
                }
            }
        }
        // A month's codes left over where an area had no room: placed in that month, area by area, until none is left.
        for (int monthsBack = 1; monthsBack <= MONTHS; monthsBack++) {
            Deque<Integer> codes = leaving.get(monthsBack - 1);
            while (!codes.isEmpty()) {
                boolean placed = false;
                for (List<Person> people : movable) {
                    Person p = codes.isEmpty() ? null : firstWithRoom(people, room);
                    if (p != null) {
                        past.add(leaver(p, monthsBack, codes.poll(), reasons, reasonTurn, random));
                        placed = true;
                    }
                }
                if (!placed) {
                    throw new IllegalStateException(codes.size() + " leavers of month " + monthsBack
                            + " found no workplace with room to lose them");
                }
            }
        }

        // 6. Across the areas in turn: the suspended, the suspended and lifted, and those who left and came back.
        List<Deque<Person>> queues = new ArrayList<>();
        for (Map.Entry<String, List<Person>> area : peopleOf.entrySet()) {
            if (!inThePacks(area.getKey())) {
                continue;
            }
            List<Person> free = new ArrayList<>(area.getValue().stream().filter(p -> !p.used).toList());
            Collections.shuffle(free, random);
            queues.add(new ArrayDeque<>(free));
        }
        int[] turn = {0};
        List<Integer> suspendedCodes = expand(SUSPENDED);
        Collections.shuffle(suspendedCodes, random);
        for (int i = 0; i < suspendedCodes.size(); i++) {
            Person p = next(queues, turn);
            past.add(change(p.number(), 1 + i % 2, 1 + random.nextInt(28), "Suspended", "SUSPENSION_CODE", null, null,
                    suspendedCodes.get(i)));
        }
        for (int code : LIFTED) {
            Person p = next(queues, turn);
            past.add(change(p.number(), MONTHS, 1 + random.nextInt(28), "Suspended", "SUSPENSION_CODE", null, null, code));
            past.add(change(p.number(), 1, 1 + random.nextInt(28), "Active", "REINSTATED", null, null, null));
        }
        for (Object[] rejoin : REJOINED) {
            // Off the record in the months between: they need room, as a leaver does.
            Person p = nextWithRoom(queues, turn, room);
            int code = (Integer) rejoin[0];
            past.add(change(p.number(), MONTHS, 1 + random.nextInt(28), DemonstrationMemberGenerator.leavingStatus(code),
                    "DELETION_CODE", code, (String) rejoin[1], null));
            past.add(change(p.number(), 1, 1 + random.nextInt(28), "Active", "REINSTATED", null, null, null));
        }

        // 7. Each area's representatives, committee members and Memnet members, from those nothing else happened to.
        for (Deque<Person> queue : queues) {
            int stewards = 0;
            int bec = 0;
            int memnet = 0;
            for (Person p : new ArrayList<>(queue)) {
                if (stewards < SHOP_STEWARDS) {
                    p.fields.put("shopSteward", true);
                    p.fields.put("shopStewardElectionDate", between(FIRST_ELECTION, LAST_ELECTION, random).toString());
                    stewards++;
                } else if (bec < BEC_MEMBERS && p.at.workplace().committee() != null) {
                    p.fields.put("becCommitteeCode", p.at.workplace().committee());
                    bec++;
                } else if (memnet < MEMNET_MEMBERS) {
                    p.fields.put("memnet", true);
                    memnet++;
                } else {
                    break;
                }
                queue.remove(p);
            }
        }

        past.sort(Comparator.<Map<String, Object>>comparingInt(c -> -(Integer) c.get("monthsBack"))
                .thenComparingInt(c -> (Integer) c.get("day"))
                .thenComparing(c -> (String) c.get("membershipNumber"))
                .thenComparing(c -> (String) c.get("reason")));
        return new Plan(chosen, members, past, summary(chosen, members, past, lists));
    }

    /** At least five, at most the bound and never more than the complement: a group or a table code made visible. */
    private static int atLeastFive(Workplace w) {
        return Math.min(Math.max(w.target(), AT_LEAST), Math.min(AT_MOST, w.complement()));
    }

    /** Takes room for one mover at the member's workplace, where there is any; marks the member used. */
    private static boolean takeRoom(Person p, Map<Integer, Integer> room) {
        int ra = p.at.workplace().raNumber();
        if (p.used || room.getOrDefault(ra, 0) <= 0) {
            return false;
        }
        room.merge(ra, -1, Integer::sum);
        p.used = true;
        return true;
    }

    /** The first member of a shuffled area nothing has happened to, at a workplace with room; {@code null} if none. */
    private static Person firstWithRoom(List<Person> people, Map<Integer, Integer> room) {
        for (Person p : people) {
            if (takeRoom(p, room)) {
                return p;
            }
        }
        return null;
    }

    /**
     * A leaving under SASBO's code -- and, under code 2, the month's next reason SASBO keeps there: a turn a month, so
     * that each month's code-2 leavers give every reason.
     */
    private static Map<String, Object> leaver(Person p, int monthsBack, int code, List<String> reasons, int[] reasonTurn,
                                              Random random) {
        String reason = code == 2 ? reasons.get(reasonTurn[monthsBack]++ % reasons.size()) : null;
        return change(p.number(), monthsBack, 1 + random.nextInt(28), DemonstrationMemberGenerator.leavingStatus(code),
                "DELETION_CODE", code, reason, null);
    }

    /** A joiner of the month {@code monthsBack} before the clock's, on a day of it, credited to the next recruiter. */
    private static void joins(Person p, int monthsBack, int[] recruiterTurn, Random random) {
        p.fields.put("joinDate", null);
        p.fields.put("joinedMonthsBack", monthsBack);
        p.fields.put("joinDay", 1 + random.nextInt(28));
        p.fields.put("dateLastChanged", null);
        p.fields.put("recruiter", 1 + recruiterTurn[0]++ % RECRUITERS);
    }

    /** The next member in turn at a workplace with room, area by area. */
    private static Person nextWithRoom(List<Deque<Person>> queues, int[] turn, Map<Integer, Integer> room) {
        for (int tried = 0; tried < queues.size(); tried++) {
            Deque<Person> queue = queues.get(turn[0]++ % queues.size());
            for (Person p : new ArrayList<>(queue)) {
                if (takeRoom(p, room)) {
                    queue.remove(p);
                    return p;
                }
            }
        }
        throw new IllegalStateException("the population has nobody left with room for its past");
    }

    /** The next member in turn, area by area, skipping an area that has nobody left. */
    private static Person next(List<Deque<Person>> queues, int[] turn) {
        for (int tried = 0; tried < queues.size(); tried++) {
            Deque<Person> queue = queues.get(turn[0]++ % queues.size());
            Person p = queue.poll();
            if (p != null) {
                p.used = true;
                return p;
            }
        }
        throw new IllegalStateException("the population has nobody left for its past");
    }

    private static Map<String, Object> change(String who, int monthsBack, int day, String toStatus, String reason,
                                              Integer deletionCode, String reasonUnderCode, Integer suspensionCode) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("membershipNumber", who);
        f.put("monthsBack", monthsBack);
        f.put("day", day);
        f.put("toStatus", toStatus);
        f.put("reason", reason);
        f.put("deletionCode", deletionCode);
        f.put("reasonUnderCode", reasonUnderCode);
        f.put("suspensionCode", suspensionCode);
        return f;
    }

    private static List<Integer> expand(int[][] codeThenCount) {
        List<Integer> codes = new ArrayList<>();
        for (int[] row : codeThenCount) {
            for (int i = 0; i < row[1]; i++) {
                codes.add(row[0]);
            }
        }
        return codes;
    }

    /** Whether an area is one the population is planned in -- not one a lifted table code took it to. */
    static boolean inThePacks(String area) {
        return PACKS.values().stream().anyMatch(areas -> areas.containsKey(area));
    }

    /** The pack an area's members are counted in: its region, Johannesburg's areas split as the packs split them. */
    static String packOf(String area) {
        for (Map.Entry<String, Map<String, Integer>> pack : PACKS.entrySet()) {
            if (pack.getValue().containsKey(area)) {
                return pack.getKey();
            }
        }
        return switch (area) {
            case "A", "F", "K", "X" -> "JHB";
            case "H", "N", "P" -> "JHB2";
            case "B", "J", "T", "U" -> "CPT";
            case "D", "G" -> "PTA";
            case "I", "M" -> "PE";
            case "E", "S" -> "BLM";
            case "C", "L", "O", "R", "V", "W" -> "DBN";
            default -> null;
        };
    }

    // ================================================================================================ one member

    private static Person person(int number, Chosen at, Random random, Set<String> identities) {
        boolean female = random.nextBoolean();
        String first = pick(female ? FEMALE : MALE, random);
        String surname = pick(SURNAMES, random);
        LocalDate born = LocalDate.of(1966, 1, 1).plusDays(random.nextInt(365 * 33));
        LocalDate joined = between(FIRST_JOIN, LAST_JOIN, random);
        LocalDate changed = between(joined, LAST_CHANGE, random);
        String phone = TELEPHONE + String.format(Locale.ROOT, "%04d", number % 10_000);
        Workplace w = at.workplace();

        Person p = new Person(at);
        Map<String, Object> f = p.fields;
        f.put("membershipNumber", String.format(Locale.ROOT, "M-%06d", number));
        f.put("pack", at.pack());
        f.put("area", w.area());
        f.put("workplaceRaNumber", w.raNumber());
        f.put("employeeNumber", "EMP-" + number);
        f.put("title", title(female, random));
        f.put("initials", initials(first, random));
        f.put("firstNames", first);
        f.put("surname", surname);
        f.put("identityType", "I.D. Number");
        f.put("identityNumber", DebitOrderSeedGenerator.identityNumber(born, female, number, identities));
        f.put("dateOfBirth", born.toString());
        f.put("gender", female ? "Female" : "Male");
        f.put("race", pick(RACES, random));
        f.put("joinDate", joined.toString());
        f.put("joinedMonthsBack", null);
        f.put("joinDay", null);
        f.put("personalEmail", address(first, surname, number));
        f.put("preferredEmail", "personal");
        f.put("phone", phone);
        f.put("address", (1 + random.nextInt(200)) + " " + pick(STREETS, random) + ", "
                + pick(TOWNS.get(at.pack()), random));
        f.put("branch", w.name());
        f.put("bankName", w.employerPrinted());
        f.put("paymentType", "Payroll Deduction");
        f.put("monthlySubscription", "174.00");
        f.put("subscriptionFrequency", "Monthly");
        f.put("dateLastChanged", changed.toString());
        f.put("shopSteward", false);
        f.put("shopStewardElectionDate", null);
        f.put("becCommitteeCode", null);
        f.put("memnet", false);
        f.put("recruiter", null);
        return p;
    }

    private static LocalDate between(LocalDate from, LocalDate to, Random random) {
        return from.plusDays(random.nextInt((int) ChronoUnit.DAYS.between(from, to) + 1));
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

    // ================================================================================================ the summary

    private static Map<String, Object> summary(List<Chosen> chosen, List<Person> members, List<Map<String, Object>> past,
                                               Lists lists) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("members", members.size());
        s.put("workplaces", chosen.size());
        Map<String, Object> byPack = new LinkedHashMap<>();
        PACKS.keySet().forEach(pack -> byPack.put(pack, 0));
        Map<String, Object> byArea = new LinkedHashMap<>();
        Map<String, Object> byGroup = new LinkedHashMap<>();
        DemonstrationMemberGenerator.PER_THOUSAND.keySet().forEach(group -> byGroup.put(group, 0));
        Map<String, Object> byEmployer = new TreeMap<>();
        for (Person p : members) {
            byPack.merge(p.at.pack(), 1, (a, b) -> (Integer) a + (Integer) b);
            byArea.merge(p.at.workplace().area(), 1, (a, b) -> (Integer) a + (Integer) b);
            byGroup.merge(p.at.workplace().group(), 1, (a, b) -> (Integer) a + (Integer) b);
            byEmployer.merge(p.at.workplace().employerName(), 1, (a, b) -> (Integer) a + (Integer) b);
        }
        s.put("byPack", byPack);
        s.put("byArea", byArea);
        s.put("byGroup", byGroup);
        s.put("byEmployer", byEmployer);
        Map<String, Object> joined = new TreeMap<>();
        members.stream().filter(p -> p.fields.get("joinedMonthsBack") != null)
                .forEach(p -> joined.merge(String.valueOf(p.fields.get("joinedMonthsBack")), 1,
                        (a, b) -> (Integer) a + (Integer) b));
        s.put("joinedByMonthsBack", joined);
        Map<String, Object> leftByMonth = new TreeMap<>();
        Map<String, Object> leftByCode = new TreeMap<>(Comparator.comparingInt(Integer::parseInt));
        Map<String, Object> resigned = new LinkedHashMap<>();
        lists.reasonsUnderTwo().forEach(reason -> resigned.put(reason, 0));
        Map<String, Integer> lastStatus = new HashMap<>();
        Map<String, Integer> suspendedCode = new HashMap<>();
        Map<String, Object> lifted = new TreeMap<>(Comparator.comparingInt(Integer::parseInt));
        int reinstated = 0;
        for (Map<String, Object> c : past) {
            String who = (String) c.get("membershipNumber");
            switch ((String) c.get("reason")) {
                case "DELETION_CODE" -> {
                    leftByMonth.merge(String.valueOf(c.get("monthsBack")), 1, (a, b) -> (Integer) a + (Integer) b);
                    leftByCode.merge(String.valueOf(c.get("deletionCode")), 1, (a, b) -> (Integer) a + (Integer) b);
                    if (c.get("reasonUnderCode") != null) {
                        resigned.merge((String) c.get("reasonUnderCode"), 1, (a, b) -> (Integer) a + (Integer) b);
                    }
                    lastStatus.put(who, 0);
                }
                case "SUSPENSION_CODE" -> {
                    suspendedCode.put(who, (Integer) c.get("suspensionCode"));
                    lastStatus.put(who, 1);
                }
                default -> {
                    if (Integer.valueOf(0).equals(lastStatus.get(who))) {
                        reinstated++;
                    } else if (suspendedCode.containsKey(who)) {
                        lifted.merge(String.valueOf(suspendedCode.remove(who)), 1, (a, b) -> (Integer) a + (Integer) b);
                    }
                    lastStatus.put(who, 2);
                }
            }
        }
        Map<String, Object> stillSuspended = new TreeMap<>(Comparator.comparingInt(Integer::parseInt));
        suspendedCode.forEach((who, code) -> stillSuspended.merge(String.valueOf(code), 1,
                (a, b) -> (Integer) a + (Integer) b));
        s.put("leftByMonthsBack", leftByMonth);
        s.put("leftByCode", leftByCode);
        s.put("resignedByReason", resigned);
        s.put("reinstated", reinstated);
        s.put("suspendedByCode", stillSuspended);
        s.put("suspendedAndLiftedByCode", lifted);
        s.put("shopStewards", members.stream().filter(p -> Boolean.TRUE.equals(p.fields.get("shopSteward"))).count());
        s.put("becMembers", members.stream().filter(p -> p.fields.get("becCommitteeCode") != null).count());
        s.put("memnetMembers", members.stream().filter(p -> Boolean.TRUE.equals(p.fields.get("memnet"))).count());
        s.put("recruitersCredited", RECRUITERS);
        return s;
    }

    // ================================================================================================ what it reads

    /**
     * SASBO's lists as the resource holds them: a table code's employer, an employer's name and group, the reasons under
     * code 2, and which groups are core.
     */
    record Lists(Map<Integer, Integer> employerOfCode, Map<Integer, String> employerName, Map<Integer, String> groupOf,
                 List<String> reasonsUnderTwo, Set<String> coreGroups) {
    }

    static Lists lists() {
        Map<Integer, Integer> employerOfCode = new HashMap<>();
        Map<Integer, String> employerName = new HashMap<>();
        Map<Integer, String> groupOf = new HashMap<>();
        List<String> reasons = new ArrayList<>();
        Set<String> core = new HashSet<>();
        Set<Integer> numbered = new HashSet<>();
        for (JsonNode list : DemonstrationMemberGenerator.read(ReferenceListSeeder.RESOURCE).path("lists")) {
            String name = list.path("list").asText();
            for (JsonNode row : list.path("rows")) {
                switch (name) {
                    case "tableCodes" -> {
                        if (row.hasNonNull("employer")) {
                            employerOfCode.put(row.path("code").asInt(), row.path("employer").asInt());
                        }
                    }
                    case "employers" -> {
                        employerName.put(row.path("ordinal").asInt(), row.path("name").asText());
                        groupOf.put(row.path("ordinal").asInt(), row.path("group").asText());
                    }
                    case "employerGroups" -> {
                        if (row.path("core").asBoolean()) {
                            core.add(row.path("name").asText());
                        }
                    }
                    case "deletionCodes" -> {
                        if (row.path("sasboCode").isNumber()) {
                            numbered.add(row.path("sasboCode").asInt());
                        } else if (row.path("parent").asInt() == 2) {
                            reasons.add(row.path("name").asText());
                        }
                    }
                    default -> {
                        // the other lists stand behind the workplaces, which carry what is needed of them
                    }
                }
            }
        }
        for (int[] code : LEAVING_A_MONTH) {
            if (!numbered.contains(code[0])) {
                throw new IllegalStateException("deletion code " + code[0] + " is not on SASBO's list");
            }
            if (code[0] == 2 && code[1] < reasons.size()) {
                throw new IllegalStateException("code 2 leaves fewer times a month than SASBO keeps reasons under it");
            }
        }
        if (reasons.isEmpty()) {
            throw new IllegalStateException("SASBO's list keeps no reason under code 2");
        }
        return new Lists(employerOfCode, employerName, groupOf, List.copyOf(reasons), Set.copyOf(core));
    }

    /** Every workplace of the resource that reaches an employer group through its table code, in R/A order. */
    static List<Workplace> workplaces(Lists lists) {
        List<Workplace> found = new ArrayList<>();
        for (JsonNode w : DemonstrationMemberGenerator.read(WorkplaceSeeder.RESOURCE).path("workplaces")) {
            if (!w.hasNonNull("tableCode")) {
                continue;
            }
            int code = w.path("tableCode").asInt();
            Integer employer = lists.employerOfCode().get(code);
            String group = employer == null ? null : lists.groupOf().get(employer);
            if (group == null) {
                continue;
            }
            Integer rate = DemonstrationMemberGenerator.PER_THOUSAND.get(group);
            if (rate == null) {
                throw new IllegalStateException("a group with no penetration given: " + group);
            }
            found.add(new Workplace(w.path("raNumber").asInt(), w.path("name").asText(), w.path("employer").asText(),
                    code, employer, lists.employerName().get(employer), group, w.path("area").asText(),
                    w.hasNonNull("committee") ? w.path("committee").asText() : null, w.path("staffComplement").asInt(),
                    rate));
        }
        found.sort(Comparator.comparingInt(Workplace::raNumber));
        return found;
    }

    /**
     * The existing seed's members on record, by the table code of the workplace each stands at -- the invented of
     * {@code demonstration_members.json} (less the leavers of their past who did not come back) and the 310 placed --
     * as the seed files state their statuses. What decides which table code needs lifting.
     */
    static Map<Integer, Long> existingOnRecordByTableCode() {
        Map<Integer, Integer> codeOf = new HashMap<>();
        for (JsonNode w : DemonstrationMemberGenerator.read(WorkplaceSeeder.RESOURCE).path("workplaces")) {
            if (w.hasNonNull("tableCode")) {
                codeOf.put(w.path("raNumber").asInt(), w.path("tableCode").asInt());
            }
        }
        Map<String, String> status = new HashMap<>();
        for (String file : List.of("data/members.json", DebitOrderMemberSeeder.SEED_FILE,
                DemonstrationMemberSeeder.SEED_FILE)) {
            JsonNode root = DemonstrationMemberGenerator.read(file);
            for (JsonNode m : root.isArray() ? root : root.path("members")) {
                status.put(m.path("membershipNumber").asText(), m.path("status").asText());
            }
        }
        JsonNode existing = DemonstrationMemberGenerator.read(DemonstrationMemberSeeder.SEED_FILE);
        for (JsonNode change : existing.path("past")) {
            status.put(change.path("membershipNumber").asText(), change.path("toStatus").asText());
        }
        Map<String, Integer> at = new HashMap<>();
        for (JsonNode m : existing.path("members")) {
            at.put(m.path("membershipNumber").asText(), m.path("workplaceRaNumber").asInt());
        }
        for (JsonNode p : existing.path("placements")) {
            at.put(p.path("membershipNumber").asText(), p.path("workplaceRaNumber").asInt());
        }
        Map<Integer, Long> counts = new HashMap<>();
        at.forEach((number, ra) -> {
            Integer code = codeOf.get(ra);
            String s = status.get(number);
            if (code != null && s != null && MemberStatuses.kindOf(s) != null
                    && MemberStatuses.kindOf(s) != za.co.titandynamix.sasbo_online_backend.enums.MemberStatusKind.LEAVING) {
                counts.merge(code, 1L, Long::sum);
            }
        });
        return counts;
    }

    // ================================================================================================ writing

    /** One object as one line of JSON: keys in the order given, strings escaped, ASCII by construction. */
    private static String object(Map<String, ?> fields) {
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
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> ordered = new LinkedHashMap<>();
            map.forEach((k, v) -> ordered.put(String.valueOf(k), v));
            return object(ordered);
        }
        if (value instanceof List<?> list) {
            List<String> items = new ArrayList<>();
            list.forEach(item -> items.add(literal(item)));
            return "[" + String.join(", ", items) + "]";
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
