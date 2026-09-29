package za.co.titandynamix.sasbo_online_backend.guards;



import org.junit.jupiter.api.DisplayName;

import org.junit.jupiter.api.Test;

import za.co.titandynamix.sasbo_online_backend.support.Floors;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory;

import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory.MainClassSource;



import java.util.ArrayList;

import java.util.List;

import java.util.Locale;

import java.util.Map;

import java.util.Set;



import static org.assertj.core.api.Assertions.assertThat;

import static za.co.titandynamix.sasbo_online_backend.support.NonVacuousScan.assertNonVacuousScan;



/**

 * The STATIC half of the PII-in-logs guard: no type may generate a

 * {@code toString()} over a field that carries personal information.

 *

 * <h2>Why it is not in {@code PiiInLogsTest}</h2>

 * That class is {@code @Isolated} -- it captures a Logback logger, which is

 * process-global and which Spring Boot resets whenever an application context

 * starts, so nothing may run beside it. Isolation is the right price for the

 * behavioural half and the wrong price for this one: these two tests read

 * source files and touch no logger at all, and while an isolated class runs the

 * whole suite is single-threaded, so every second spent here is added to the

 * wall clock rather than shared with it.

 *

 * <p>Splitting them took the fast lane from ~80s back to its measured range.

 * The confession stays where it is needed and stops covering work that never

 * needed it.

 *

 * <h2>Written against the field name, not against the class</h2>

 * Types are discovered by scanning for PII-shaped field names, so a new entity

 * carrying an {@code identityNumber} is covered the day it is written. Naming

 * today's two offenders would have closed this exact hole and left the hole

 * open.

 */

@DisplayName("PII in generated toString")

class PiiToStringTest {



    /**

     * Field names that carry personal information. Substring-matched

     * case-insensitively, so {@code memberIdentityNumber} and

     * {@code accountNumber} are both caught without listing either.

     */

    private static final Set<String> PII_FIELD_MARKERS = Set.of(

            "identitynumber", "idnumber", "passportnumber", "accountnumber",

            "dateofbirth", "taxnumber");



    /**

     * Types that can hold ANY value of a member under a name this guard cannot know -- a history row's

     * {@code oldValue} and {@code newValue} (month end 03.5, BR31). Named, and checked like any type whose field names

     * say PII; {@link #everyValueCarrierIsNamed} holds the list to the fields, so a type added with an old and a new

     * value, and not named here, reddens.

     */

    private static final Set<String> CARRIES_ANY_VALUE = Set.of("MemberChange");



    /** Lombok annotations that generate a {@code toString()} over every field. */

    private static final List<String> TOSTRING_GENERATORS = List.of("@Data", "@ToString");



    /**

     * Types that generate a {@code toString()} over PII today, and what each

     * is. Both are response DTOs annotated {@code @Data}, which is the default

     * reach for a DTO and the reason this is a guard rather than a review note.

     *

     * <p>Asserted, not suppressed: each entry is checked to still be a real

     * violation, so removing {@code @Data} from one reddens this test until the

     * entry goes. Changing that is a production change and is written up as a

     * finding rather than applied by a test suite.

     */

    private static final Map<String, String> KNOWN_TOSTRING_OVER_PII = Map.of(

            "MemberDto",

            "@Data over identityNumber, passportNumber, dateOfBirth and bankAccountNumber",

            "BankingDetailsDto",

            "@Data over accountNumber");



    @Test

    @DisplayName("no new type generates a toString over a PII field")

    void noTypeGeneratesAToStringOverPii() {

        List<MainClassSource> dataTypes = MainSourceInventory.dataTypes();



        assertNonVacuousScan(dataTypes, Floors.DATA_TYPES, "entities and DTOs");



        List<String> offenders = new ArrayList<>();

        for (MainClassSource type : dataTypes) {

            if (KNOWN_TOSTRING_OVER_PII.containsKey(type.name()) || !carriesPii(type)) {

                continue;

            }

            for (String generator : TOSTRING_GENERATORS) {

                if (type.linesContaining(generator).isEmpty()) {

                    continue;

                }

                offenders.add(type.at(type.linesContaining(generator).get(0))

                        + " " + generator + " over a PII field");

            }

        }



        assertThat(offenders)

                .as("a generated toString() over personal information puts every field one log "

                        + "format argument away from the log file. Use @Getter/@Setter instead "

                        + "of @Data, or @ToString.Exclude the field")

                .isEmpty();

    }



    @Test

    @DisplayName("every listed toString-over-PII exception is still real")

    void knownToStringViolationsAreStillViolations() {

        List<MainClassSource> dataTypes = MainSourceInventory.dataTypes();



        for (Map.Entry<String, String> entry : KNOWN_TOSTRING_OVER_PII.entrySet()) {

            MainClassSource type = dataTypes.stream()

                    .filter(t -> t.name().equals(entry.getKey()))

                    .findFirst()

                    .orElseThrow(() -> new AssertionError(

                            entry.getKey() + " is listed but no longer exists. Remove the entry."));



            boolean stillGenerates = TOSTRING_GENERATORS.stream()

                    .anyMatch(g -> !type.linesContaining(g).isEmpty());



            assertThat(stillGenerates && carriesPii(type))

                    .as("%s is listed as generating a toString over PII -- \"%s\" -- but no "

                            + "longer does. Delete the entry", entry.getKey(), entry.getValue())

                    .isTrue();

        }

    }



    @Test

    @DisplayName("every type that holds any value of a member -- an old and a new value -- is named to this guard (BR31)")

    void everyValueCarrierIsNamed() {

        List<String> unnamed = new ArrayList<>();

        for (MainClassSource type : MainSourceInventory.dataTypes()) {

            String lowered = type.code().toLowerCase(Locale.ROOT);

            if (lowered.contains(" oldvalue;") && lowered.contains(" newvalue;") && !CARRIES_ANY_VALUE.contains(type.name())) {

                unnamed.add(type.name());

            }

        }

        assertThat(unnamed).as("types that carry a member's values under names PII_FIELD_MARKERS cannot know").isEmpty();

        assertThat(MainSourceInventory.dataTypes()).extracting(MainClassSource::name).containsAll(CARRIES_ANY_VALUE);

    }



    private static boolean carriesPii(MainClassSource type) {

        String lowered = type.code().toLowerCase(Locale.ROOT);

        return PII_FIELD_MARKERS.stream().anyMatch(lowered::contains)

                || CARRIES_ANY_VALUE.contains(type.name());

    }

}

