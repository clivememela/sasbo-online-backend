package za.co.titandynamix.sasbo_online_backend.payroll;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A name on a payroll line, read as initials and a surname, and compared with a member's (Stephne, PAY-10: "Initials
 * and surname"). Every employer writes names its own way -- seven styles in fifteen samples -- and IT's Reconciliation
 * Report shows what a strict text comparison makes of that: most of its exceptions are names written differently, not
 * different people (fixtures' {@code RECON-REPORT.md}). So a name is compared by its parts, never as text.
 *
 * <ul>
 *   <li>A surname is compared by its letters alone, upper case, accents dropped: {@code Test-Samplewell},
 *       {@code TEST SAMPLEWELL} and {@code Test Samplewell} are one surname, as are {@code Van Der Testerson} and
 *       {@code VANDERTESTERSON}.</li>
 *   <li>Initials are compared by their letters; one person's initials fit another's when they are the same or one
 *       begins the other ({@code B} fits {@code BK}) -- a file that prints one initial is not a different person.</li>
 *   <li>Where the style cannot say where a surname ends ({@code Firstname Surname}, {@code SURNAME FIRSTNAME}), every
 *       split is a reading, and a member fits when any reading does.</li>
 * </ul>
 */
public final class PayrollNames {

    /** A name read one way: the initials, and the surname as it is compared. */
    public record Reading(String initials, String surnameKey) {
    }

    /** A name as a line shows it -- initials and surname as a person reads them -- and every way it can be read. */
    public record Name(String initials, String surname, List<Reading> readings) {

        public Name {
            readings = List.copyOf(readings);
        }

        public static final Name NONE = new Name(null, null, List.of());

        public boolean isEmpty() {
            return readings.isEmpty();
        }
    }

    /** Titles that open or close a name and are no part of it. */
    private static final Set<String> TITLES = Set.of("MR", "MRS", "MS", "MISS", "DR", "PROF", "ADV", "REV", "MNR",
            "MEV", "MEJ", "SIR", "MX", "DS");

    /** Words that begin a surname of more than one word: {@code Van Der Testerson}. */
    private static final Set<String> PARTICLES = Set.of("VAN", "DER", "DEN", "DE", "DU", "LE", "LA", "VON", "TEN",
            "TER", "DA", "DOS", "DI", "DEL", "MC");

    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern MARKS = Pattern.compile("\\p{M}");
    private static final Pattern NOT_LETTER = Pattern.compile("[^A-Z]");
    private static final Pattern INITIALS_TOKEN = Pattern.compile("(?:[A-Z]\\.?){1,5}");

    private PayrollNames() {
    }

    // ----------------------------------------------------------------------------------------------------- keys

    /** A surname as it is compared: its letters, upper case, accents dropped; {@code ""} when there are none. */
    public static String surnameKey(String surname) {
        if (surname == null) {
            return "";
        }
        String plain = MARKS.matcher(Normalizer.normalize(surname, Normalizer.Form.NFD)).replaceAll("");
        return NOT_LETTER.matcher(plain.toUpperCase(Locale.ROOT)).replaceAll("");
    }

    /** Initials as they are compared: their letters, upper case. */
    public static String initialsKey(String initials) {
        return surnameKey(initials);
    }

    /** The initials of first names: the first letter of each. */
    public static String initialsOf(String firstNames) {
        if (firstNames == null || firstNames.isBlank()) {
            return "";
        }
        StringBuilder initials = new StringBuilder();
        for (String word : SPACES.split(firstNames.trim())) {
            String letters = surnameKey(word);
            if (!letters.isEmpty() && initials.length() < 10) {
                initials.append(letters.charAt(0));
            }
        }
        return initials.toString();
    }

    /** Whether two sets of initials can be the same person's: the same, or one begins the other. Empty fits anything. */
    public static boolean initialsFit(String a, String b) {
        String x = initialsKey(a);
        String y = initialsKey(b);
        return x.isEmpty() || y.isEmpty() || x.startsWith(y) || y.startsWith(x);
    }

    /** Whether a member's initials and surname fit any reading of a line's name. */
    public static boolean fits(Name name, String memberInitials, String memberSurname) {
        String surname = surnameKey(memberSurname);
        if (surname.isEmpty()) {
            return false;
        }
        for (Reading reading : name.readings()) {
            if (reading.surnameKey().equals(surname) && initialsFit(reading.initials(), memberInitials)) {
                return true;
            }
        }
        return false;
    }

    /** Every reading as one stored key: {@code INITIALS|SURNAME} readings joined by {@code ;}. */
    public static String storedKey(Name name) {
        StringBuilder key = new StringBuilder();
        for (Reading reading : name.readings()) {
            String part = reading.initials() + "|" + reading.surnameKey();
            if (key.length() + part.length() + 1 > 400) {
                break;
            }
            if (!key.isEmpty()) {
                key.append(';');
            }
            key.append(part);
        }
        return key.isEmpty() ? null : key.toString();
    }

    // -------------------------------------------------------------------------------------------------- reading

    /**
     * A line's name in {@code style}: from columns of their own ({@link NameStyle#SEPARATE}), or from one column.
     * Initials come from an initials column, else from first names.
     */
    public static Name read(NameStyle style, String initials, String firstNames, String surname, String fullName) {
        if (style == NameStyle.SEPARATE || fullName == null) {
            if (surname == null && fullName == null) {
                return Name.NONE;
            }
            if (surname == null) {
                return readFull(NameStyle.EITHER_ORDER, fullName);
            }
            String shownInitials = initials != null ? initialsKey(initials) : initialsOf(firstNames);
            String key = surnameKey(surname);
            return key.isEmpty() ? Name.NONE
                    : new Name(blankToNull(shownInitials), tidy(surname), List.of(new Reading(shownInitials, key)));
        }
        return readFull(style, fullName);
    }

    static Name readFull(NameStyle style, String fullName) {
        String text = fullName.trim();
        return switch (style) {
            case SURNAME_COMMA_FIRSTNAMES -> commaSplit(text, false);
            case SURNAME_COMMA_INITIALS -> commaSplit(text, true);
            case TITLE_INITIALS_SURNAME -> titleInitialsSurname(text);
            case FIRSTNAMES_SURNAME -> splits(words(text), false);
            case SURNAME_FIRSTNAMES -> splits(words(text), true);
            case SURNAME_INITIALS_TITLE -> surnameInitialsTitle(text);
            case EITHER_ORDER, SEPARATE -> eitherOrder(words(text));
        };
    }

    /** {@code SURNAME , FIRST NAMES} (Absa) or {@code SURNAME, INITIALS} (GBS); without a comma, surname first. */
    private static Name commaSplit(String text, boolean initialsAfter) {
        int comma = text.indexOf(',');
        if (comma < 0) {
            return splits(words(text), true);
        }
        String surname = tidy(text.substring(0, comma));
        String rest = text.substring(comma + 1).trim();
        List<String> restWords = withoutTitles(words(rest));
        String initials = initialsAfter && restWords.stream().allMatch(w -> INITIALS_TOKEN.matcher(
                w.toUpperCase(Locale.ROOT)).matches())
                ? initialsKey(String.join("", restWords))
                : initialsOf(String.join(" ", restWords));
        String key = surnameKey(surname);
        return key.isEmpty() ? Name.NONE : new Name(blankToNull(initials), surname, List.of(new Reading(initials, key)));
    }

    /** {@code Mr AB Surname} (Bankserv, SBV): the title dropped, a token of capitals the initials, the rest the surname. */
    private static Name titleInitialsSurname(String text) {
        List<String> words = withoutTitles(words(text));
        if (words.size() >= 2 && isInitials(words.get(0))) {
            String initials = initialsKey(words.get(0));
            String surname = String.join(" ", words.subList(1, words.size()));
            return new Name(initials, surname, List.of(new Reading(initials, surnameKey(surname))));
        }
        return splits(words, false);
    }

    /** {@code Surname I.J., Mr.} (SA Reserve Bank): the title after the comma dropped, the initials last. */
    private static Name surnameInitialsTitle(String text) {
        int comma = text.lastIndexOf(',');
        String name = comma < 0 ? text : text.substring(0, comma);
        List<String> words = withoutTitles(words(name));
        if (words.size() >= 2 && isInitials(words.get(words.size() - 1))) {
            String initials = initialsKey(words.get(words.size() - 1));
            String surname = String.join(" ", words.subList(0, words.size() - 1));
            return new Name(initials, surname, List.of(new Reading(initials, surnameKey(surname))));
        }
        return splits(words, true);
    }

    /** A name whose order is not known: both orders' readings. */
    private static Name eitherOrder(List<String> words) {
        Name forwards = splits(words, false);
        Name backwards = splits(words, true);
        Set<Reading> readings = new LinkedHashSet<>(forwards.readings());
        readings.addAll(backwards.readings());
        return new Name(forwards.initials(), forwards.surname(), new ArrayList<>(readings));
    }

    /**
     * Every way of splitting words into first names and a surname. {@code surnameFirst}: the surname's words come
     * first. The reading SHOWN is the likeliest: a one-word surname, or a surname that begins with a particle.
     */
    private static Name splits(List<String> rawWords, boolean surnameFirst) {
        List<String> words = withoutTitles(rawWords);
        if (words.isEmpty()) {
            return Name.NONE;
        }
        if (words.size() == 1) {
            String key = surnameKey(words.get(0));
            return key.isEmpty() ? Name.NONE : new Name(null, tidy(words.get(0)), List.of(new Reading("", key)));
        }
        List<Reading> readings = new ArrayList<>();
        Name shown = null;
        int likeliest = likeliestSplit(words, surnameFirst);
        for (int split = 1; split < words.size(); split++) {
            List<String> surnameWords = surnameFirst ? words.subList(0, split) : words.subList(split, words.size());
            List<String> firstWords = surnameFirst ? words.subList(split, words.size()) : words.subList(0, split);
            String surname = String.join(" ", surnameWords);
            String initials = initialsOf(String.join(" ", firstWords));
            readings.add(new Reading(initials, surnameKey(surname)));
            if (split == likeliest) {
                shown = new Name(blankToNull(initials), tidy(surname), List.of());
            }
        }
        return new Name(shown == null ? null : shown.initials(), shown == null ? null : shown.surname(), readings);
    }

    private static int likeliestSplit(List<String> words, boolean surnameFirst) {
        if (surnameFirst) {
            int split = 1;
            while (split < words.size() - 1 && PARTICLES.contains(words.get(split - 1).toUpperCase(Locale.ROOT))) {
                split++;
            }
            return split;
        }
        int split = words.size() - 1;
        while (split > 1 && PARTICLES.contains(words.get(split - 1).toUpperCase(Locale.ROOT))) {
            split--;
        }
        return split;
    }

    private static boolean isInitials(String word) {
        return INITIALS_TOKEN.matcher(word).matches() && word.length() <= 10;
    }

    private static List<String> words(String text) {
        String cleaned = text.replace(',', ' ').trim();
        return cleaned.isEmpty() ? List.of() : Arrays.asList(SPACES.split(cleaned));
    }

    private static List<String> withoutTitles(List<String> words) {
        List<String> kept = new ArrayList<>();
        for (String word : words) {
            String bare = word.replace(".", "").toUpperCase(Locale.ROOT);
            // "M.R." and "D.R." are initials, not "Mr" and "Dr": a title has at most a full stop at its end.
            int stop = word.indexOf('.');
            boolean dottedInitials = stop >= 0 && stop < word.length() - 1;
            if (!TITLES.contains(bare) || dottedInitials) {
                kept.add(word);
            }
        }
        return kept;
    }

    private static String tidy(String text) {
        String collapsed = SPACES.matcher(text.trim()).replaceAll(" ");
        return collapsed.length() > 100 ? collapsed.substring(0, 100) : collapsed;
    }

    private static String blankToNull(String text) {
        return text == null || text.isEmpty() ? null : text;
    }
}
