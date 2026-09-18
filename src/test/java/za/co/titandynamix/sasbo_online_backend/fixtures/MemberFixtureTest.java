package za.co.titandynamix.sasbo_online_backend.fixtures;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * THE FIXTURE'S IDENTITY NUMBERS, WHICH SR-5 STANDS ON.
 *
 * <h2>Why a fixture gets its own test</h2>
 * Because the {@code UNIQUE} constraint on {@code members.identity_number} is
 * about to depend on it. If {@code syntheticIdFor} ever repeats a value, the
 * failure does not appear here -- it appears as a constraint violation in
 * whichever unrelated integration test happened to save second, and it reads as
 * a defect in that test. A fixture that other tests trust silently is exactly
 * the kind of thing that has to be checked out loud.
 *
 * <h2>🔴 And the old number was not a valid identity number</h2>
 * {@code 9404275800086} shipped as {@code SYNTHETIC_ID} from before this arc and
 * fails its own Luhn check -- the thirteenth digit should have been 9. The server
 * never verified checksums so nothing complained, while {@code validateSAID.js}
 * on the client would have refused to send it. {@link #theCheckDigitAgreesWithNumbersWeAlreadyTrust}
 * is the assertion that stops that happening again, and it is deliberately
 * anchored to a number this repository already has an independent opinion about
 * rather than to the generator's own output.
 */
@DisplayName("MemberFixture identity numbers")
class MemberFixtureTest {

    /** Luhn, written a second way: sum, then require a multiple of ten. */
    private static boolean passesLuhn(String digits) {
        int sum = 0;
        boolean doubling = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = digits.charAt(i) - '0';
            if (doubling) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubling = !doubling;
        }
        return sum % 10 == 0;
    }

    @Test
    @DisplayName("🔴 the check digit agrees with numbers this repository already trusts")
    void theCheckDigitAgreesWithNumbersWeAlreadyTrust() {
        // 🔑 THE ANCHOR IS NOT THE GENERATOR. Checking syntheticIdFor against a
        // Luhn helper written in the same commit proves only that two pieces of my
        // own arithmetic agree. `9001010000080` is the identity number
        // MemberCaptureRoundTripIT posts to a REAL PostgreSQL and reads back, and
        // it predates this change -- so it is an opinion formed elsewhere.
        assertThat(passesLuhn("9001010000080"))
                .as("the round-trip IT's identity number is a valid one")
                .isTrue();

        // ...and the value that shipped as SYNTHETIC_ID before SR-5 is NOT, which
        // is what makes the assertion above discriminate rather than accept
        // anything thirteen digits long.
        assertThat(passesLuhn("9404275800086"))
                .as("the pre-SR-5 fixture number, which the client would have refused")
                .isFalse();
    }

    @Test
    @DisplayName("every generated number is thirteen digits, male, born 1994-04-27, and valid")
    void everyGeneratedNumberIsWellFormed() {
        List<String> generated = IntStream.range(0, 500)
                .mapToObj(MemberFixture::syntheticIdFor)
                .toList();

        assertThat(generated).allSatisfy(id -> {
            assertThat(id).as("thirteen digits").hasSize(13).containsOnlyDigits();
            assertThat(id).as("the fixed date of birth").startsWith("940427");
            assertThat(Integer.parseInt(id.substring(6, 10)))
                    .as("above the 5000 gender boundary, so the parser still says male")
                    .isGreaterThanOrEqualTo(5000);
            assertThat(passesLuhn(id)).as("valid: %s", id).isTrue();
        });
    }

    @Test
    @DisplayName("🔴 no two fixture members share an identity number — what SR-5 needs")
    void everyMemberGetsItsOwnIdentityNumber() {
        // The claim the UNIQUE constraint rests on, made against the thing the 23
        // save sites actually call rather than against the generator underneath it.
        // aMember() is the method that has to be safe; syntheticIdFor could be
        // perfect and aMember() could still hand every caller sequence 0.
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            Member member = MemberFixture.aMember();
            assertThat(seen.add(member.getIdentityNumber()))
                    .as("member %d repeated an identity number: %s", i, member.getIdentityNumber())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("...and the sequence is still there for a test that WANTS a collision")
    void aNamedSequenceStillRepeatsOnPurpose() {
        // 🔑 THE HALF THAT KEEPS THE CONSTRAINT TESTABLE. "Every member is distinct"
        // is trivially satisfiable by a fixture nobody can pin down -- and then
        // there is no way left to write "saving the same person twice is refused",
        // which is the defect SR-5 is about. Distinct BY DEFAULT, repeatable ON
        // REQUEST is the property, and both halves are load-bearing.
        assertThat(MemberFixture.syntheticIdFor(7))
                .isEqualTo(MemberFixture.syntheticIdFor(7))
                .isNotEqualTo(MemberFixture.syntheticIdFor(8));
    }
}
