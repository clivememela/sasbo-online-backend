/**
 * The DEFAULT handler set: deliberately empty.
 *
 * Every test declares the responses it depends on, in the test, with
 * `server.use(...)`. A shared happy-path fixture set is how a suite ends up
 * with tests that pass because of something another file arranged -- and with
 * a shuffled suite, that failure only shows up on some seeds.
 *
 * Two things ARE shared, and each is scoped and named rather than global:
 *   ./pageFixtures.js  the mount-time calls a page test has to answer to
 *                      reach the surface it is actually about
 *   ./smokeLayer.js    the broad empty-200 layer the route smoke needs, which
 *                      also records the endpoints it was asked for
 *
 * An earlier version of this file also exported six response-shape helpers --
 * `respondsWithErrorEnvelope`, `neverResponds` and so on -- written before the
 * error contract existed. The contract then grew its own, closer to where they
 * are used, and nothing ever imported these. They were removed in this phase's
 * refactor pass rather than left as a second, subtly different vocabulary for
 * the same four cases.
 */
export const handlers = []
