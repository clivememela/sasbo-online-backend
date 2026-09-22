package za.co.titandynamix.sasbo_online_backend;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * SCRATCH, NEVER MERGED: the reddening proof R1-4 for this repository's CI. An {@code integration}-tagged test that
 * fails, so the {@code integration} job must go red while the {@code build} job, which excludes the tag, stays green.
 * The pull request carrying it is a draft, closed unmerged, and its branch is deleted.
 */
@Tag("integration")
class CiProofIntegrationLaneTest {

    @Test
    void theIntegrationLaneGoesRedOnAFailingTaggedTest() {
        fail("SCRATCH R1-4: a deliberately failing integration-tagged test -- the integration job must go red, build green");
    }
}
