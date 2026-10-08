/**
 * The demonstration users a journey signs in as, by the position each holds -- and where their password comes from.
 *
 * WHO THEY ARE. The October build's sign-in seeds one user per provisional position, each with a scope, when the
 * server is told to (`SASBO_SIGNIN_DEMO_USERS`) -- never under the production marker, and on no hosted stack. The
 * usernames are fixed by the sign-in contract, and other streams' seeds name them (the visit drafts are the
 * Organiser's), so they are written here exactly. Scenarios name a POSITION, never a person: SASBO's staff are named
 * by position only, and these users stand for positions.
 *
 * 🔑 THE PASSWORD IS NEVER WRITTEN DOWN. It reaches the server and this suite from ONE environment variable,
 * `SASBO_SIGNIN_DEMO_PASSWORD`, with no default anywhere committed: the server seeds the users only when it is set, and
 * the suite signs them in with the same value. `tools/test-all.mjs` and `tools/demo.mjs` make one for each stack they
 * start; a stack started any other way needs it exported first -- and `docker-compose.yml` passes it to both services.
 * `tests/harness/signInStack.spec.js` holds the compose half.
 */

/** Position -> username, as the sign-in contract fixes them. Scope in the comment: a journey about scope reads it. */
export const DEMO_USERS = Object.freeze({
  'System Administrator': 'demo.sysadmin', // ALL
  'Head Office Administrator': 'demo.headoffice', // ALL
  'Chief Financial Officer': 'demo.cfo', // ALL
  'Senior Secretariat': 'demo.secretariat', // ALL
  'Regional Secretary': 'demo.regional', // REGION DBN
  'Regional Organiser': 'demo.regionalorganiser', // REGION DBN
  Organiser: 'demo.organiser', // AREA L
  MSO: 'demo.mso', // AREA W
})

/** The one area each area-scoped demonstration user sees -- the contract's, where the invented population stands. */
export const DEMO_USER_AREA = Object.freeze({ Organiser: 'L', MSO: 'W' })

/** The username of the demonstration user holding `position`, or a refusal naming the positions there are. */
export function demoUserFor(position) {
  const username = DEMO_USERS[position]
  if (!username) {
    throw new Error(`No demonstration user holds the position ${JSON.stringify(position)}. Positions: ${Object.keys(DEMO_USERS).join(', ')}.`)
  }
  return username
}

/**
 * The password every demonstration user of this stack signs in with, read from the environment it was started with.
 *
 * Refused loudly when absent, rather than tried: a wrong guess is a failed sign-in, five of them lock the user for a
 * quarter of an hour, and every other journey signing that user in would then fail for a reason that has nothing to
 * do with what it tests.
 */
export function demoPassword(env = process.env) {
  // Read exactly as the server reads it: a password is never trimmed, only an empty one refused.
  const given = env.SASBO_SIGNIN_DEMO_PASSWORD ?? ''
  if (given.trim() === '') {
    throw new Error(
      'SASBO_SIGNIN_DEMO_PASSWORD is not set, so this suite cannot sign a demonstration user in -- and the stack, started\n' +
        'with the same environment, seeded none. tools/test-all.mjs and tools/demo.mjs make one for every stack they\n' +
        'start; to start one by hand, export the same value for `docker compose up` and for the run.',
    )
  }
  return given
}
