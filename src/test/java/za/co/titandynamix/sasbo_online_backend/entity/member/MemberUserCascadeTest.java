package za.co.titandynamix.sasbo_online_backend.entity.member;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.CascadeType;
import jakarta.persistence.OneToOne;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A member's login outlives the member.
 *
 * <p>{@code Member.user} cascaded {@code ALL}, which includes {@code REMOVE}: deleting a member
 * would have deleted the login linked to it. Nothing deletes a member today, which is exactly why
 * nothing else would notice the day something did.
 *
 * <p>Read off the annotation the JPA provider reads -- the fact itself, not the source text, so a
 * reformatted declaration cannot slip past it.
 */
@DisplayName("Member.user -- what the association cascades")
class MemberUserCascadeTest {

    @Test
    @DisplayName("deleting a member never deletes its login: no REMOVE, and no ALL (which carries it)")
    void aMembersLoginOutlivesTheMember() throws NoSuchFieldException {
        OneToOne mapping = Member.class.getDeclaredField("user").getAnnotation(OneToOne.class);

        assertThat(mapping).as("Member.user is no longer a @OneToOne -- re-aim this test").isNotNull();
        assertThat(mapping.cascade()).doesNotContain(CascadeType.ALL, CascadeType.REMOVE);
    }

    @Test
    @DisplayName("must-pass: it still cascades PERSIST and MERGE, and stays read-only over the wire")
    void theRestOfTheMappingIsUnchanged() throws NoSuchFieldException {
        // The other half: a test that passed on an association with no cascade at all -- or one a
        // request body could now write -- would not be about the narrowing.
        var field = Member.class.getDeclaredField("user");

        assertThat(field.getAnnotation(OneToOne.class).cascade())
                .containsExactlyInAnyOrder(CascadeType.PERSIST, CascadeType.MERGE);
        assertThat(field.getAnnotation(JsonProperty.class).access()).isEqualTo(JsonProperty.Access.READ_ONLY);
    }
}
