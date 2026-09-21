package za.co.titandynamix.sasbo_online_backend.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import za.co.titandynamix.sasbo_online_backend.enums.AcbExclusionReason;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Writes {@code data/debit_order_members.json}: the seed's debit-order population, 150 synthetic
 * members a run can actually be prepared from (D33 -- the owner, 2026-09-21: seed data "must just
 * be there").
 *
 * <h2>Why a generator, and why it lives in the tests</h2>
 * Nobody types 150 members, and nobody should hand-edit one of them: a population with a stated
 * expectation per member is only trustworthy while it is exactly what its generator produces. So
 * this class is run two ways, like the API surface and the window fixture: {@code ./gradlew
 * generateApiSurface} writes the file, and the fast lane asserts the committed bytes equal what it
 * would write ({@code DebitOrderSeedGeneratorTest}). The random seed is fixed, the order is fixed,
 * {@code java.util.Random} is specified to the bit -- the same bytes on every machine.
 *
 * <h2>What it is made of, and what it is never made of</h2>
 * Bank names and their public universal branch codes are the only real things in it: the banks SASBO's
 * own July 2026 file is made of, in roughly its proportions -- Standard Bank about half, Absa a fifth,
 * then FNB, Capitec and the rest. People are synthetic: names drawn from fixed lists, identity numbers
 * that pass the Luhn check the capture screen applies, and every account number starts {@value
 * #ACCOUNT_PREFIX} -- one documented, unmistakable prefix. Nothing is derived from the July file or
 * the returns report, which are member data and never leave the machine they arrived on.
 *
 * <h2>Each member says what should happen to it</h2>
 * {@code expectedOutcome} is {@code QUALIFIES} or the one exclusion reason the population rule must
 * give -- so a test asserts the run as a POPULATION (the set of numbers), never as a count. Nedbank
 * stays on its {@code PENDING} subscription row, so a real bank sits on the "left out, and why" sheet
 * for a real reason, and every listed reason has at least one member behind it.
 *
 * <h2>Numbered BELOW the original seed</h2>
 * M-099851 to M-100000. The capture sequence starts at 100161, exactly where {@code members.json}
 * ends (V22), and its {@code setval} runs while {@code members} is still empty: a member numbered
 * upward from M-100161 would collide with the first real capture -- A61's defect, made again (F48).
 */
public final class DebitOrderSeedGenerator {

    /** Where the seeder reads it, relative to the project root. */
    public static final Path FILE = Paths.get("src", "main", "resources", "data", "debit_order_members.json");

    /** The fixed random seed: the day the owner asked for the data. */
    static final long SEED = 20_260_921L;

    /** The block below {@code members.json}'s M-100001..M-100160 (F48). */
    static final int FIRST_NUMBER = 99_851;
    static final int LAST_NUMBER = 100_000;

    /** Every generated account number starts with this, so a person reading a file can tell. */
    static final String ACCOUNT_PREFIX = "9999";

    /** RFC 2606: reserved for documentation -- no mail is ever delivered to it. */
    static final String MAIL_DOMAIN = "example.org";

    /** SASBO's own due dates, and how often each carries a line -- every one of them does. */
    static final List<Integer> DUE_DATES = List.of(20, 25, 27, 29, 30, 31);
    private static final int[] DUE_DATE_WEIGHTS = {9, 4, 2, 2, 2, 1};

    /** A qualifying member -- the population rule debits them. */
    public static final String QUALIFIES = "QUALIFIES";

    private static final String STANDARD_BANK = "The Standard Bank of South Africa Limited";
    private static final String ABSA = "Absa Bank Limited";
    private static final String FNB = "First National Bank (FNB)";
    private static final String CAPITEC = "Capitec Bank Limited";
    private static final String INVESTEC = "Investec Bank Limited";
    private static final String DISCOVERY = "Discovery Bank Limited";
    private static final String AFRICAN_BANK = "African Bank Limited";
    private static final String NEDBANK = "Nedbank Limited";

    /**
     * Who is in the file, as (bank on record, how many, the outcome each must get). The banks with one
     * ACTIVE subscription row qualify; the rest each exercise one reason, first rule failed first.
     */
    private static final List<Slot> PLAN = List.of(
            new Slot(STANDARD_BANK, 67, QUALIFIES),
            new Slot(ABSA, 29, QUALIFIES),
            new Slot(FNB, 16, QUALIFIES),
            new Slot(CAPITEC, 12, QUALIFIES),
            new Slot(INVESTEC, 5, QUALIFIES),
            new Slot(DISCOVERY, 4, QUALIFIES),
            new Slot(AFRICAN_BANK, 4, QUALIFIES),
            // SASBO does not collect at Nedbank in the seed: its subscription row is PENDING (A58).
            new Slot(NEDBANK, 6, AcbExclusionReason.NO_SUBSCRIPTION_ROW.name()),
            // Captured as people say them, which is how a bank name ends up matching nothing on record.
            new Slot("Standard Bank", 1, AcbExclusionReason.NO_BANK_ON_RECORD.name()),
            new Slot("ABSA", 1, AcbExclusionReason.NO_BANK_ON_RECORD.name()),
            new Slot(STANDARD_BANK, 1, AcbExclusionReason.DAY_OUT_OF_WINDOW.name()),
            new Slot(ABSA, 1, AcbExclusionReason.DAY_OUT_OF_WINDOW.name()),
            new Slot(FNB, 1, AcbExclusionReason.ACCOUNT_SHAPE_INVALID.name()),
            new Slot(CAPITEC, 1, AcbExclusionReason.ACCOUNT_SHAPE_INVALID.name()),
            new Slot(STANDARD_BANK, 1, AcbExclusionReason.SNAPSHOT_INCOMPLETE.name()));

    /** How many qualifying members are left without an address -- their letters are held NO_EMAIL. */
    private static final int WITHOUT_ADDRESS = 4;

    private static final List<String> FEMALE = List.of("Amahle", "Busisiwe", "Candice", "Dimakatso", "Elna",
            "Fatima", "Gugulethu", "Hannelie", "Itumeleng", "Johanna", "Lerato", "Marelize", "Nomvula", "Precious",
            "Refilwe", "Shireen", "Thandeka", "Unathi", "Vanessa", "Wilma", "Yolanda", "Zanele", "Anele", "Kerry");
    private static final List<String> MALE = List.of("Andile", "Bongani", "Craig", "Dumisani", "Elias",
            "Francois", "Gerhard", "Hendrik", "Imraan", "Johan", "Kabelo", "Lwazi", "Mandla", "Neo", "Owen",
            "Pieter", "Rajesh", "Sipho", "Thabo", "Vusi", "Werner", "Xolani", "Yusuf", "Zakhele");
    private static final List<String> SURNAMES = List.of("Mokoena", "Dlamini", "Nkosi", "Van der Merwe", "Botha",
            "Naidoo", "Pillay", "Khumalo", "Mahlangu", "Ndlovu", "Mthembu", "Pretorius", "Smith", "Jacobs",
            "Adams", "Ngcobo", "Zulu", "Molefe", "Sithole", "Venter", "Coetzee", "Govender", "Maharaj",
            "Petersen", "Williams", "Mabena", "Radebe", "Shabalala", "Motaung", "Le Roux", "Fourie",
            "Du Plessis", "Hendricks", "Moodley", "Chetty", "Tshabalala", "Masilela", "Baloyi", "Maluleke", "Nel");
    private static final List<String> RACES = List.of("African", "Coloured", "Indian", "White");
    private static final List<String> BRANCHES = List.of("Johannesburg Central", "Sandton", "Pretoria",
            "Durban", "Cape Town", "Gqeberha", "Bloemfontein", "Polokwane", "Mbombela", "East London",
            "Kimberley", "Rustenburg", "Pietermaritzburg", "George", "Midrand");
    private static final List<String> STREETS = List.of("Main Road", "Church Street", "Voortrekker Road",
            "Jan Smuts Avenue", "Oxford Road", "Long Street", "Beach Road", "Market Street", "Park Lane",
            "Station Road");

    private DebitOrderSeedGenerator() {
    }

    /** One slot of the plan. */
    private record Slot(String bankName, int count, String outcome) {
    }

    /** One generated member, its fields in the order the file writes them. */
    record Generated(Map<String, Object> fields) {
        String membershipNumber() {
            return (String) fields.get("membershipNumber");
        }

        String expectedOutcome() {
            return (String) fields.get("expectedOutcome");
        }
    }

    /** The file's bytes. */
    public static String render() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"generator\": \"DebitOrderSeedGenerator, in the server's test sources. Regenerate with ")
                .append("./gradlew generateApiSurface -- never edit by hand\",\n");
        json.append("  \"seed\": ").append(SEED).append(",\n");
        json.append("  \"members\": [\n");
        List<String> rows = new ArrayList<>();
        for (Generated member : members()) {
            rows.add("    " + object(member.fields()));
        }
        json.append(String.join(",\n", rows)).append("\n");
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    /** Every member, in membership-number order. */
    static List<Generated> members() {
        Random random = new Random(SEED);
        Map<String, String> branchCodes = universalBranchCodes();
        Set<String> takenIdentityNumbers = new HashSet<>(identityNumbersIn("data/members.json"));

        List<Slot> slots = new ArrayList<>();
        for (Slot slot : PLAN) {
            for (int i = 0; i < slot.count(); i++) {
                slots.add(slot);
            }
        }
        if (slots.size() != LAST_NUMBER - FIRST_NUMBER + 1) {
            throw new IllegalStateException("the plan has " + slots.size() + " members for a block of "
                    + (LAST_NUMBER - FIRST_NUMBER + 1));
        }
        Collections.shuffle(slots, random);

        List<Integer> dueDates = dueDates((int) slots.stream().filter(s -> s.outcome().equals(QUALIFIES)).count(),
                random);
        List<Integer> withoutAddress = new ArrayList<>();
        List<Generated> members = new ArrayList<>();
        int nextDue = 0;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            int number = FIRST_NUMBER + i;
            boolean female = random.nextBoolean();
            String first = pick(female ? FEMALE : MALE, random);
            String surname = pick(SURNAMES, random);
            LocalDate born = LocalDate.of(1966, 1, 1).plusDays(random.nextInt(365 * 33));
            String identityNumber = identityNumber(born, female, number, takenIdentityNumbers);
            boolean qualifies = slot.outcome().equals(QUALIFIES);

            int debitDay;
            if (slot.outcome().equals(AcbExclusionReason.DAY_OUT_OF_WINDOW.name())) {
                // The two placeholder days the original seed is full of: mid-month, and the 1st.
                debitDay = slot.bankName().equals(STANDARD_BANK) ? 15 : 1;
            } else if (qualifies) {
                debitDay = dueDates.get(nextDue++);
            } else {
                debitDay = DUE_DATES.get(random.nextInt(DUE_DATES.size()));
            }

            String account = ACCOUNT_PREFIX + String.format(Locale.ROOT, "%07d", random.nextInt(10_000_000));
            if (slot.outcome().equals(AcbExclusionReason.ACCOUNT_SHAPE_INVALID.name())) {
                // Too long for the file's eleven digits, or typed with a separator -- both seen in capture.
                account = slot.bankName().equals(FNB) ? account + "0" : ACCOUNT_PREFIX + "-" + account.substring(4);
            }

            Map<String, Object> f = new LinkedHashMap<>();
            f.put("membershipNumber", String.format(Locale.ROOT, "M-%06d", number));
            f.put("expectedOutcome", slot.outcome());
            f.put("employeeNumber", "EMP-" + number);
            f.put("title", title(female, random));
            f.put("initials", initials(first, random));
            f.put("firstNames", first);
            f.put("surname", slot.outcome().equals(AcbExclusionReason.SNAPSHOT_INCOMPLETE.name()) ? "" : surname);
            f.put("identityType", "I.D. Number");
            f.put("identityNumber", identityNumber);
            f.put("dateOfBirth", born.toString());
            f.put("gender", female ? "Female" : "Male");
            f.put("race", pick(RACES, random));
            f.put("status", "Active");
            f.put("joinDate", LocalDate.of(2006, 1, 1).plusDays(random.nextInt(365 * 20)).toString());
            f.put("personalEmail", address(first, surname, number));
            f.put("preferredEmail", "personal");
            String phone = String.format(Locale.ROOT, "+27 60 555 %04d", number % 10_000);
            f.put("phone", phone);
            f.put("cellTel", phone);
            f.put("address", (1 + random.nextInt(200)) + " " + pick(STREETS, random) + ", " + pick(BRANCHES, random));
            f.put("branch", pick(BRANCHES, random));
            f.put("bankName", slot.bankName());
            f.put("bankAccountNumber", account);
            f.put("accountType", accountType(i));
            f.put("ibtNo", branchCodes.get(slot.bankName()));
            f.put("paymentType", "Debit Order");
            f.put("debitDay", debitDay);
            f.put("monthlySubscription", "174.00");
            f.put("subscriptionFrequency", "Monthly");
            f.put("currentBalance", "0.00");
            f.put("arrearsBalance", "0.00");
            f.put("lastMonthBalance", "0.00");
            f.put("dateLastChanged", "2026-09-21");
            members.add(new Generated(f));
            if (qualifies) {
                withoutAddress.add(i);
            }
        }
        // A few qualifying members with no address on record, chosen by the same seed: every letter a
        // seeded member is sent goes nowhere real, and these show what happens when there is nowhere at all.
        Collections.shuffle(withoutAddress, random);
        for (int index : withoutAddress.subList(0, WITHOUT_ADDRESS)) {
            members.get(index).fields().put("personalEmail", null);
        }
        return members;
    }

    /** The due dates the qualifying members are on: every date carries lines, the 20th most. */
    private static List<Integer> dueDates(int count, Random random) {
        int total = 0;
        for (int weight : DUE_DATE_WEIGHTS) {
            total += weight;
        }
        List<Integer> days = new ArrayList<>();
        for (int d = 0; d < DUE_DATES.size(); d++) {
            int share = Math.max(1, Math.round((float) count * DUE_DATE_WEIGHTS[d] / total));
            for (int i = 0; i < share; i++) {
                days.add(DUE_DATES.get(d));
            }
        }
        while (days.size() > count) {
            days.remove(days.indexOf(20));
        }
        while (days.size() < count) {
            days.add(20);
        }
        Collections.shuffle(days, random);
        return days;
    }

    /** The one member of the 150 whose account is a transmission account -- not on a savings position. */
    static final int TRANSMISSION_AT = 72;

    /**
     * 00.5 -- the member's account type, in July 2026's proportions (2,746 · 449 · 19 of cheque, savings and
     * transmission): every seventh member Savings, one Transmission, the rest Cheque -- 128 · 21 · 1. By
     * position, never from {@code random}: a draw here would move every value drawn after it, and every
     * other field in the resource would change with it.
     */
    static String accountType(int index) {
        if (index == TRANSMISSION_AT) {
            return "Transmission";
        }
        return index % 7 == 3 ? "Savings" : "Cheque";
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

    /**
     * A South African identity number the capture screen accepts: born {@code born}, the gender digits
     * (0000-4999 female, 5000-9999 male) taken from the membership number so no two collide, a citizen,
     * and the Luhn check digit -- computed exactly as the client's {@code validateSAID} checks it.
     */
    static String identityNumber(LocalDate born, boolean female, int number, Set<String> taken) {
        int genderBase = female ? 0 : 5000;
        for (int attempt = 0; attempt < 5000; attempt++) {
            // A retry stays inside the gender's half, so the number never says the other gender.
            int sequence = genderBase + (number + attempt) % 5000;
            String first12 = String.format(Locale.ROOT, "%02d%02d%02d%04d08",
                    born.getYear() % 100, born.getMonthValue(), born.getDayOfMonth(), sequence);
            String id = first12 + luhnCheckDigit(first12);
            if (taken.add(id)) {
                return id;
            }
        }
        throw new IllegalStateException("no free identity number for a member born " + born);
    }

    /** The client's rule: odd positions doubled (less nine above nine), the check makes the sum a multiple of ten. */
    static int luhnCheckDigit(String first12) {
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = first12.charAt(i) - '0';
            if (i % 2 != 0) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
        }
        return (10 - sum % 10) % 10;
    }

    private static <T> T pick(List<T> from, Random random) {
        return from.get(random.nextInt(from.size()));
    }

    /** Each bank's public universal branch code, from the bank list the seeder loads. */
    private static Map<String, String> universalBranchCodes() {
        Map<String, String> codes = new HashMap<>();
        for (JsonNode bank : read("data/banks_master.json")) {
            codes.put(bank.path("name").asText(), bank.path("universalBranchCode").asText());
        }
        return codes;
    }

    private static List<String> identityNumbersIn(String resource) {
        List<String> numbers = new ArrayList<>();
        for (JsonNode member : read(resource)) {
            if (member.hasNonNull("identityNumber")) {
                numbers.add(member.get("identityNumber").asText());
            }
        }
        return numbers;
    }

    private static JsonNode read(String resource) {
        try (InputStream stream = DebitOrderSeedGenerator.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException(resource + " is not on the classpath");
            }
            return new ObjectMapper().readTree(stream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** One member as one line of JSON: keys in the order given, strings escaped, nulls written as null. */
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
