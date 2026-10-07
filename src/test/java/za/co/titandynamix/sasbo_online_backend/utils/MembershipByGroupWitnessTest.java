package za.co.titandynamix.sasbo_online_backend.utils;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.enums.MemberStatusKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WHY THE 5 OCT SHEET CAME OUT SHORT, AND THAT IT NO LONGER DOES (the owner's promise in the 5 Oct Show &amp; Tell:
 * <em>"Could it be a bug? Just ever look at it properly again"</em>, the transcript's paragraph 92 at 8479 to 8890).
 *
 * <p>The sheet is the membership statistic's "Membership by group" -- complement, members, enrolments, cancellations,
 * suspensions, the groups each followed by its employers. Its rows are settled by rule C ({@link SmallCounts}, MD27 as
 * MD37 refines it): a group of one to four members is "fewer than five", and the smallest group of five or more beside
 * it is withheld with all its employers, so the one cannot be worked out from the total; the core groups' line goes too.
 * That is the rule working as it was decided, not a fault in the code -- and the demonstration's members made it bite.
 *
 * <p>Here the seed's own files are counted as a fresh demonstration database holds them -- each member's status as the
 * files and their past leave it, placed at its workplace, through the table code to the employer and the group, as the
 * facts are -- and the rows put through the very call the sheet makes. Before the October population one group held a
 * single member, and rule C hid two groups; with it, every group holds five or more and nothing is hidden.
 */
@DisplayName("The 5 Oct sheet, Membership by group: why it came out short, and that it no longer does")
class MembershipByGroupWitnessTest {

    private static final List<String> GROUPS = new ArrayList<>(DemonstrationMemberGenerator.PER_THOUSAND.keySet());

    @Test
    @DisplayName("before the October population: NON RECOGNITION GROUP holds one member, is 'fewer than 5', and CORE B -- the smallest group of five or more -- is withheld with it")
    void beforeTheOctoberPopulation() {
        Map<String, Long> groups = groupsOnRecord(false);
        assertThat(groups.get("NON RECOGNITION GROUP")).as("NON RECOGNITION GROUP's members on record").isEqualTo(1);

        Map<String, SmallCounts.State> settled = settle(groups);

        assertThat(settled).containsEntry("NON RECOGNITION GROUP", SmallCounts.State.FEWER_THAN_FIVE);
        String smallestOfFiveOrMore = groups.entrySet().stream().filter(e -> e.getValue() >= 5)
                .min(Map.Entry.comparingByValue()).orElseThrow().getKey();
        assertThat(smallestOfFiveOrMore).isEqualTo("RECOGNITION AND EMERGING GROUP CORE B");
        assertThat(settled).containsEntry(smallestOfFiveOrMore, SmallCounts.State.WITHHELD);
        assertThat(settled.values().stream().filter(s -> s != SmallCounts.State.SHOWN).count())
                .as("two groups hidden -- and with them CORE B's employers and the core groups' line").isEqualTo(2);
    }

    @Test
    @DisplayName("with the October population: every group holds five or more, and rule C hides nothing")
    void withTheOctoberPopulation() {
        Map<String, Long> groups = groupsOnRecord(true);
        assertThat(groups.values()).allSatisfy(n -> assertThat(n).isGreaterThanOrEqualTo(5));
        assertThat(settle(groups).values()).containsOnly(SmallCounts.State.SHOWN);
    }

    /** The sheet's call: every group a guarded row, the total the sum of the rows. */
    private static Map<String, SmallCounts.State> settle(Map<String, Long> groups) {
        long[] counts = new long[GROUPS.size()];
        boolean[] guarded = new boolean[GROUPS.size()];
        long total = 0;
        for (int i = 0; i < GROUPS.size(); i++) {
            counts[i] = groups.getOrDefault(GROUPS.get(i), 0L);
            guarded[i] = true;
            total += counts[i];
        }
        SmallCounts.State[] states = SmallCounts.settle(counts, guarded, total);
        Map<String, SmallCounts.State> settled = new LinkedHashMap<>();
        for (int i = 0; i < GROUPS.size(); i++) {
            settled.put(GROUPS.get(i), states[i]);
        }
        return settled;
    }

    /**
     * Each group's members on record at the end of the seed's past, as a fresh demonstration database holds them: every
     * seed file's members at their workplaces, each in the status its file and its past leave it.
     */
    private static Map<String, Long> groupsOnRecord(boolean withTheOctoberPopulation) {
        Map<Integer, Integer> employerOfCode = new HashMap<>();
        Map<Integer, String> groupOfEmployer = new HashMap<>();
        for (JsonNode list : DemonstrationMemberGenerator.read(ReferenceListSeeder.RESOURCE).path("lists")) {
            for (JsonNode row : list.path("rows")) {
                if (list.path("list").asText().equals("tableCodes") && row.hasNonNull("employer")) {
                    employerOfCode.put(row.path("code").asInt(), row.path("employer").asInt());
                } else if (list.path("list").asText().equals("employers")) {
                    groupOfEmployer.put(row.path("ordinal").asInt(), row.path("group").asText());
                }
            }
        }
        Map<Integer, String> groupAt = new HashMap<>();
        for (JsonNode w : DemonstrationMemberGenerator.read(WorkplaceSeeder.RESOURCE).path("workplaces")) {
            Integer employer = w.hasNonNull("tableCode") ? employerOfCode.get(w.path("tableCode").asInt()) : null;
            if (employer != null) {
                groupAt.put(w.path("raNumber").asInt(), groupOfEmployer.get(employer));
            }
        }

        Map<String, String> status = new HashMap<>();
        Map<String, Integer> at = new HashMap<>();
        for (String file : List.of("data/members.json", DebitOrderMemberSeeder.SEED_FILE)) {
            JsonNode root = DemonstrationMemberGenerator.read(file);
            for (JsonNode m : root.isArray() ? root : root.path("members")) {
                status.put(m.path("membershipNumber").asText(), m.path("status").asText());
            }
        }
        JsonNode existing = DemonstrationMemberGenerator.read(DemonstrationMemberSeeder.SEED_FILE);
        existing.path("members").forEach(m -> {
            status.put(m.path("membershipNumber").asText(), m.path("status").asText());
            at.put(m.path("membershipNumber").asText(), m.path("workplaceRaNumber").asInt());
        });
        existing.path("placements").forEach(p -> at.put(p.path("membershipNumber").asText(),
                p.path("workplaceRaNumber").asInt()));
        existing.path("past").forEach(c -> status.put(c.path("membershipNumber").asText(), c.path("toStatus").asText()));
        if (withTheOctoberPopulation) {
            JsonNode october = DemonstrationMemberGenerator.read(DemonstrationPopulationSeeder.SEED_FILE);
            october.path("members").forEach(m -> {
                status.put(m.path("membershipNumber").asText(), "Active");
                at.put(m.path("membershipNumber").asText(), m.path("workplaceRaNumber").asInt());
            });
            // The past in its order: the last change a member had is the status they end in.
            october.path("past").forEach(c -> status.put(c.path("membershipNumber").asText(),
                    c.path("toStatus").asText()));
        }

        Map<String, Long> groups = new LinkedHashMap<>();
        at.forEach((number, ra) -> {
            MemberStatusKind kind = MemberStatuses.kindOf(status.get(number));
            String group = groupAt.get(ra);
            if (group != null && kind != null && kind != MemberStatusKind.LEAVING) {
                groups.merge(group, 1L, Long::sum);
            }
        });
        return groups;
    }
}
