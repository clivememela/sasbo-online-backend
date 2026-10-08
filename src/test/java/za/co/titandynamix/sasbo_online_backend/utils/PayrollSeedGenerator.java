package za.co.titandynamix.sasbo_online_backend.utils;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Writes {@code data/payroll_members.json}: the payroll demonstration's invented members, in the block the parent gave
 * payroll (M-095001 to M-095400), each with the employer whose files they are on and the role the demonstration
 * plants on them -- and the people on those files who are no members. Generated, never copied; the same seed gives the
 * same bytes, checked by {@code PayrollSeedGeneratorTest} and regenerated with {@code ./gradlew generateApiSurface}.
 *
 * <ul>
 *   <li>Employee numbers in each employer's own shape (fixtures' {@code LAYOUTS.md}): Absa 8 digits, Capitec 5 with a
 *       leading nought, FNB 7 with a leading nought, Standard Bank 6.</li>
 *   <li>Surnames made of payroll words -- {@code Ledgerwood}, {@code Tallyburn} -- obviously invented, and distinct, so
 *       a name matches one member and the demonstration's matches by name are certain.</li>
 *   <li>Identity numbers the capture screen accepts (the debit-order generator's rule and check digit), distinct from
 *       every other seed file's; e-mail at {@code example.org}; telephone {@code +27 60 555} and the number's last
 *       four.</li>
 * </ul>
 */
public final class PayrollSeedGenerator {

    static final long SEED = 20_261_007L;
    static final int FIRST_NUMBER = 95_001;
    static final int LAST_NUMBER = 95_400;

    /** Each employer: its code, the employer text members carry, how its staff numbers look, and its roles. */
    record Plan(String employer, List<String> bankNames, String staffPattern, int firstStaff, List<String> roles) {
    }

    static final List<Plan> PLANS = List.of(
            new Plan("ABSA", List.of("ABSA BANK"), "%08d", 99_010_001, roles(12, "RETRO", "MISSED_THEN_CAUGHT_UP",
                    "DUPLICATE", "DUPLICATE_OWED", "OLD_RATE", "INITIALS", "STAFF_NUMBER", "RESIGNED", "MISSING")),
            new Plan("CAPITEC", List.of("CAPITEC BANK"), "%05d", 9_101, roles(10, "DUPLICATE", "DUPLICATE_OWED",
                    "CATCH_UP_ARREARS", "OVER_BY_MONTHS", "OLD_RATE", "INITIALS", "STAFF_NUMBER", "RESIGNED", "MISSING")),
            new Plan("FNB_WESBANK", List.of("FIRSTRAND BANK", "WESBANK"), "%07d", 910_001, roles(10, "DUPLICATE",
                    "DUPLICATE_OWED", "STAFF_BY_ID", "ID_DIFFERS", "NAME_BY_ID", "ADJUSTMENT_CATCH_UP", "OLD_RATE",
                    "INITIALS", "RESIGNED", "MISSING")),
            new Plan("STANDARD_BANK", List.of("STANDARD BANK"), "%06d", 991_001, roles(10, "DUPLICATE",
                    "DUPLICATE_OWED", "CATCH_UP_ARREARS", "ZERO", "OLD_RATE", "INITIALS", "STAFF_NUMBER", "RESIGNED",
                    "MISSING")),
            new Plan("GBS", List.of("GBS MUTUAL BANK"), "%06d", 881_001, roles(5, "DUPLICATE", "OLD_RATE", "RESIGNED",
                    "MISSING")));

    /** The people on the files who are no members: employer, role. */
    static final List<String[]> STRANGERS = List.of(new String[] {"ABSA", "STRANGER"}, new String[] {"ABSA", "UNREADABLE"},
            new String[] {"CAPITEC", "STRANGER"}, new String[] {"CAPITEC", "UNREADABLE"},
            new String[] {"FNB_WESBANK", "STRANGER"}, new String[] {"FNB_WESBANK", "UNREADABLE"},
            new String[] {"FNB_WESBANK", "OTHER_ID"}, new String[] {"STANDARD_BANK", "STRANGER"},
            new String[] {"STANDARD_BANK", "UNREADABLE"}, new String[] {"GBS", "STRANGER"});

    /** Roles whose member owes a month on the record: what makes a catch-up a catch-up (PAY-25, PAY-26). */
    static final Set<String> OWING = Set.of("DUPLICATE_OWED", "CATCH_UP_ARREARS", "ADJUSTMENT_CATCH_UP");

    private static final List<String> STEMS = List.of("Ledger", "Tally", "Remit", "Accrual", "Credit", "Debit",
            "Salary", "Stub", "Wage", "Payslip", "Deduct", "Arrear", "Rebate", "Voucher", "Journal", "Bonus", "Gross",
            "Netpay", "Levy", "Annuity", "Payroll", "Payout");
    private static final List<String> ENDINGS = List.of("wood", "burn", "ford", "dene", "vale", "sane", "beck",
            "worth", "field", "kop", "hoek", "berg", "ton", "wane", "dale", "mere");
    private static final List<String> FEMALE = List.of("Payla", "Netta", "Grossa", "Bonna", "Levva", "Credda",
            "Salla", "Rebba", "Arra", "Vouchella", "Journa", "Annua");
    private static final List<String> MALE = List.of("Taxo", "Remo", "Credo", "Debbo", "Ledgo", "Stubbo", "Wago",
            "Accro", "Deducto", "Payno", "Tallo", "Grosso");
    private static final List<String> RACES = List.of("African", "Coloured", "Indian", "White");

    private PayrollSeedGenerator() {
    }

    private static List<String> roles(int ordinary, String... special) {
        List<String> roles = new ArrayList<>();
        for (int i = 0; i < ordinary; i++) {
            roles.add("ORDINARY");
        }
        roles.addAll(List.of(special));
        return roles;
    }

    /**
     * The areas other seeds stand in -- the demonstration members' L and W, and the demonstration population's
     * seventeen (contracts/demo-population.md) -- so payroll's members go to workplaces nobody else is placed at, one
     * or two a workplace, and no workplace goes over its complement.
     */
    static final Set<String> AREAS_TAKEN = Set.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N",
            "P", "R", "S", "W", "X");

    /** The file's bytes. */
    public static String render() {
        Random random = new Random(SEED);
        Map<String, List<Integer>> workplaces = workplacesByEmployer();
        Set<String> taken = new HashSet<>();
        for (String file : List.of("data/members.json", "data/debit_order_members.json",
                "data/demonstration_members.json")) {
            taken.addAll(identityNumbersIn(file));
        }
        Set<String> surnames = new HashSet<>();
        List<String> members = new ArrayList<>();
        int number = FIRST_NUMBER;
        for (Plan plan : PLANS) {
            int staff = plan.firstStaff();
            int index = 0;
            List<Integer> places = workplaces.getOrDefault(plan.employer(), List.of());
            for (String role : plan.roles()) {
                boolean female = random.nextBoolean();
                String first = pick(female ? FEMALE : MALE, random)
                        + (random.nextInt(10) < 4 ? " " + pick(female ? FEMALE : MALE, random) : "");
                String surname = surname(random, surnames);
                LocalDate born = LocalDate.of(1966, 1, 1).plusDays(random.nextInt(365 * 33));
                LocalDate joined = LocalDate.of(2008, 1, 1).plusDays(random.nextInt(365 * 17));
                String id = DebitOrderSeedGenerator.identityNumber(born, female, number, taken);
                Map<String, Object> f = new LinkedHashMap<>();
                f.put("membershipNumber", String.format(Locale.ROOT, "M-%06d", number));
                f.put("employer", plan.employer());
                f.put("workplaceRaNumber", places.isEmpty() ? null : places.get(index % places.size()));
                f.put("role", role);
                f.put("employeeNumber", String.format(Locale.ROOT, plan.staffPattern(), staff++));
                f.put("title", female ? (random.nextBoolean() ? "Ms" : "Mrs") : "Mr");
                f.put("initials", initials(first));
                f.put("firstNames", first);
                f.put("surname", surname);
                f.put("identityType", "I.D. Number");
                f.put("identityNumber", id);
                f.put("dateOfBirth", born.toString());
                f.put("gender", female ? "Female" : "Male");
                f.put("race", pick(RACES, random));
                f.put("status", role.equals("RESIGNED") ? "Resigned" : "Active");
                f.put("joinDate", joined.toString());
                f.put("personalEmail", (first.split(" ")[0] + "." + surname + "." + number + "@example.org")
                        .toLowerCase(Locale.ROOT));
                f.put("preferredEmail", "personal");
                String phone = String.format(Locale.ROOT, "+27 60 555 %04d", number % 10_000);
                f.put("phone", phone);
                f.put("cellTel", phone);
                f.put("bankName", plan.bankNames().get(index++ % plan.bankNames().size()));
                f.put("paymentType", "Payroll Deduction");
                f.put("monthlySubscription", new java.math.BigDecimal("174.00"));
                f.put("subscriptionFrequency", "Monthly");
                f.put("arrearsBalance", new java.math.BigDecimal(OWING.contains(role) ? "174.00" : "0.00"));
                f.put("dateLastChanged", joined.toString());
                members.add("    " + object(f));
                number++;
            }
        }
        if (number - 1 > LAST_NUMBER) {
            throw new IllegalStateException("the payroll seed runs past its block");
        }
        List<String> strangers = new ArrayList<>();
        int strangerNumber = 95_300;
        for (String[] stranger : STRANGERS) {
            boolean female = random.nextBoolean();
            String first = pick(female ? FEMALE : MALE, random);
            LocalDate born = LocalDate.of(1966, 1, 1).plusDays(random.nextInt(365 * 33));
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("employer", stranger[0]);
            f.put("role", stranger[1]);
            Plan plan = PLANS.stream().filter(p -> p.employer().equals(stranger[0])).findFirst().orElseThrow();
            f.put("employeeNumber", String.format(Locale.ROOT, plan.staffPattern(), plan.firstStaff() + 900
                    + strangerNumber % 100));
            f.put("title", female ? "Ms" : "Mr");
            f.put("initials", initials(first));
            f.put("firstNames", first);
            f.put("surname", surname(random, surnames));
            f.put("identityNumber", DebitOrderSeedGenerator.identityNumber(born, female, strangerNumber++, taken));
            strangers.add("    " + object(f));
        }
        return "{\n"
                + "  \"generator\": \"PayrollSeedGenerator, in the server's test sources. Regenerate with "
                + "./gradlew generateApiSurface -- never edit by hand\",\n"
                + "  \"seed\": " + SEED + ",\n"
                + "  \"members\": [\n" + String.join(",\n", members) + "\n  ],\n"
                + "  \"strangers\": [\n" + String.join(",\n", strangers) + "\n  ]\n"
                + "}\n";
    }

    /**
     * Each employer's workplaces, from SASBO's branch list and lists (the seed resources): those of the SASBO employers
     * the payroll employer covers, in the areas no other seed uses, largest complement first; then, where there are
     * none, those elsewhere with a complement of twenty or more.
     */
    static Map<String, List<Integer>> workplacesByEmployer() {
        Map<String, Set<Integer>> codes = new LinkedHashMap<>();
        JsonNode employers = read("payroll/employers.json").path("employers");
        JsonNode lists = read("data/sasbo_reference_lists.json").path("lists");
        for (JsonNode employer : employers) {
            Set<Integer> ofEmployer = new java.util.TreeSet<>();
            for (JsonNode name : employer.path("sasboEmployers")) {
                for (JsonNode list : lists) {
                    if (list.path("list").asText().equals("employers")) {
                        for (JsonNode row : list.path("rows")) {
                            if (row.path("name").asText().equals(name.asText())) {
                                row.path("tableCodes").forEach(c -> ofEmployer.add(c.asInt()));
                            }
                        }
                    }
                }
            }
            codes.put(employer.path("code").asText(), ofEmployer);
        }
        List<JsonNode> all = new ArrayList<>();
        read("data/sasbo_workplaces.json").path("workplaces").forEach(all::add);
        Map<String, List<Integer>> places = new LinkedHashMap<>();
        codes.forEach((employer, tableCodes) -> {
            java.util.Comparator<JsonNode> largestFirst = java.util.Comparator.<JsonNode>comparingInt(
                    w -> -w.path("staffComplement").asInt()).thenComparingInt(w -> w.path("raNumber").asInt());
            List<Integer> free = all.stream().filter(w -> tableCodes.contains(w.path("tableCode").asInt()))
                    .filter(w -> !AREAS_TAKEN.contains(w.path("area").asText()))
                    .filter(w -> w.path("staffComplement").asInt() > 0)
                    .sorted(largestFirst).map(w -> w.path("raNumber").asInt()).toList();
            List<Integer> chosen = !free.isEmpty() ? free : all.stream()
                    .filter(w -> tableCodes.contains(w.path("tableCode").asInt()))
                    .filter(w -> w.path("staffComplement").asInt() >= 20)
                    .sorted(largestFirst).map(w -> w.path("raNumber").asInt()).toList();
            places.put(employer, chosen);
        });
        return places;
    }

    private static JsonNode read(String resource) {
        try (InputStream stream = PayrollSeedGenerator.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException(resource + " is not on the classpath");
            }
            return new ObjectMapper().readTree(stream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String surname(Random random, Set<String> used) {
        for (int attempt = 0; attempt < 1000; attempt++) {
            String surname = pick(STEMS, random) + pick(ENDINGS, random);
            if (used.add(surname)) {
                return surname;
            }
        }
        throw new IllegalStateException("no surname left");
    }

    private static String initials(String firstNames) {
        StringBuilder initials = new StringBuilder();
        for (String name : firstNames.split(" ")) {
            initials.append(name.charAt(0));
        }
        return initials.toString();
    }

    private static <T> T pick(List<T> from, Random random) {
        return from.get(random.nextInt(from.size()));
    }

    private static List<String> identityNumbersIn(String resource) {
        List<String> numbers = new ArrayList<>();
        try (InputStream stream = PayrollSeedGenerator.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException(resource + " is not on the classpath");
            }
            JsonNode root = new ObjectMapper().readTree(stream);
            for (JsonNode member : root.isArray() ? root : root.path("members")) {
                if (member.hasNonNull("identityNumber")) {
                    numbers.add(member.get("identityNumber").asText());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return numbers;
    }

    /** One object as one line of JSON: keys in the order given. */
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
        return "\"" + new String(JsonStringEncoder.getInstance().quoteAsString(value.toString())) + "\"";
    }
}
