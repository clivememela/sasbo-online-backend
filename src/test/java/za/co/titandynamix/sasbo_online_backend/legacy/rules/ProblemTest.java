package za.co.titandynamix.sasbo_online_backend.legacy.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A problem as stored -- {@code CODE@field}, in a list cut at whole tokens -- and every code with a sentence and a severity. */
@DisplayName("Problem and RuleCode")
class ProblemTest {

    @Test
    @DisplayName("a token is the code and the field; it reads back to the same problem, and an unknown code is dropped")
    void tokens() {
        Problem problem = new Problem(RuleCode.PHONE_INVALID, "cellTel");

        assertThat(problem.token()).isEqualTo("PHONE_INVALID@cellTel");
        assertThat(Problem.parse("PHONE_INVALID@cellTel")).isEqualTo(problem);
        assertThat(Problem.parse("ROW_SHORT")).isEqualTo(new Problem(RuleCode.ROW_SHORT, null));
        assertThat(Problem.parse("NO_SUCH_CODE@x")).isNull();
        assertThat(Problem.parseAll("ROW_SHORT,NO_SUCH_CODE,PHONE_INVALID@cellTel")).containsExactly(
                new Problem(RuleCode.ROW_SHORT, null), problem);
        assertThat(Problem.parseAll(null)).isEmpty();
    }

    @Test
    @DisplayName("a column whose name would break the list is named by its code alone")
    void unsafeColumnNames() {
        assertThat(new Problem(RuleCode.FIELD_TOO_LONG, "NAME, FIRST").token()).isEqualTo("FIELD_TOO_LONG");
        assertThat(new Problem(RuleCode.FIELD_TOO_LONG, "a@b").token()).isEqualTo("FIELD_TOO_LONG");
    }

    @Test
    @DisplayName("a list holds each token once, and is cut at whole tokens -- never mid-code")
    void joining() {
        List<Problem> problems = List.of(new Problem(RuleCode.ROW_SHORT, null), new Problem(RuleCode.ROW_SHORT, null),
                new Problem(RuleCode.PHONE_INVALID, "cellTel"), new Problem(RuleCode.EMAIL_INVALID, "workEmail"));

        assertThat(Problem.joinAll(problems, 2000)).isEqualTo("ROW_SHORT,PHONE_INVALID@cellTel,EMAIL_INVALID@workEmail");
        assertThat(Problem.joinAll(problems, 30)).isEqualTo("ROW_SHORT");
        assertThat(Problem.joinAll(List.of(), 100)).isNull();
    }

    @Test
    @DisplayName("every code has a sentence for the person fixing the file; only the identity and workplace checks may be made warnings")
    void everyCode() {
        for (RuleCode code : RuleCode.values()) {
            assertThat(code.sentence()).as(code.name()).isNotBlank().endsWith(".");
            assertThat(code.severity()).as(code.name()).isNotNull();
            if (code.downgradable()) {
                assertThat(code.severity()).as(code.name()).isEqualTo(Severity.ERROR);
            }
        }
        assertThat(List.of(RuleCode.values())).filteredOn(RuleCode::downgradable).containsExactlyInAnyOrder(
                RuleCode.IDENTITY_MISSING, RuleCode.ID_NUMBER_FORMAT, RuleCode.ID_NUMBER_DATE,
                RuleCode.ID_NUMBER_CHECK_DIGIT, RuleCode.WORKPLACE_UNKNOWN);
    }
}
