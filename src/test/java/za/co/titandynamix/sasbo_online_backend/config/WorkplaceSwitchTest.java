package za.co.titandynamix.sasbo_online_backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * THE SWITCH (month end, MD45, MD50, MD62): a word of three values, off unless it is set, and a word it does not know
 * stops the start.
 */
@DisplayName("The switch for SASBO's workplaces")
class WorkplaceSwitchTest {

    @Configuration
    @EnableConfigurationProperties(WorkplaceSwitch.class)
    static class JustTheSwitch {
    }

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(JustTheSwitch.class);

    @Test
    @DisplayName("🔒 MD50: unset, blank and whitespace are none -- off unless it is set")
    void offUnlessItIsSet() {
        for (String blank : new String[] {null, "", "   "}) {
            WorkplaceSwitch off = new WorkplaceSwitch(blank);
            assertThat(off.mode()).isEqualTo(WorkplaceSwitch.Mode.NONE);
            assertThat(off.workplacesOnRecord()).isFalse();
            assertThat(off.staffNumbersServed()).isFalse();
        }
        context.run(started -> assertThat(started.getBean(WorkplaceSwitch.class).mode())
                .as("a deployment that says nothing -- which is what a hosted stack is until SASBO answers MM79")
                .isEqualTo(WorkplaceSwitch.Mode.NONE));
    }

    @Test
    @DisplayName("the three words, trimmed and in any case, and what each serves (MR-SEC-19)")
    void theThreeWords() {
        assertThat(new WorkplaceSwitch(" None ").mode()).isEqualTo(WorkplaceSwitch.Mode.NONE);
        WorkplaceSwitch names = new WorkplaceSwitch("NAMES");
        assertThat(names.workplacesOnRecord()).isTrue();
        assertThat(names.staffNumbersServed())
                .as("at names nothing made from a staff number is answered: no complement, no visit, no penetration")
                .isFalse();
        WorkplaceSwitch all = new WorkplaceSwitch("all ");
        assertThat(all.workplacesOnRecord()).isTrue();
        assertThat(all.staffNumbersServed()).isTrue();
    }

    @Test
    @DisplayName("🔴 a word it does not know stops the start, saying what it accepts -- never a guess at SASBO's answer")
    void anUnknownWordStopsTheStart() {
        for (String misspelt : new String[] {"al", "yes", "on", "true", "name"}) {
            assertThatThrownBy(() -> new WorkplaceSwitch(misspelt))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("'none', 'names' or 'all'");
        }
        context.withPropertyValues("sasbo.workplaces=yes")
                .run(started -> assertThat(started).hasFailed());
    }

    @Test
    @DisplayName("the start says in one line which of the three this deployment is")
    void theStartSaysWhichItIs() {
        assertThat(new WorkplaceSwitch(null).describe()).contains("none");
        assertThat(new WorkplaceSwitch("names").describe()).contains("names");
        assertThat(new WorkplaceSwitch("all").describe()).contains("all");
    }
}
