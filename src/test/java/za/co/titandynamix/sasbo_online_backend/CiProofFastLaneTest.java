package za.co.titandynamix.sasbo_online_backend;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * SCRATCH, NEVER MERGED: the reddening proof R1-3 for this repository's CI. An untagged test that fails, so the
 * {@code build} job (Checkstyle and the fast lane) must go red while the {@code integration} job stays green. The pull
 * request carrying it is a draft, closed unmerged, and its branch is deleted.
 */
class CiProofFastLaneTest {

    @Test
    void theFastLaneGoesRedOnAFailingUntaggedTest() {
        fail("SCRATCH R1-3: a deliberately failing untagged test -- the build job must go red, integration green");
    }
}
