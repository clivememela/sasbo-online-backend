import { Given } from '../support/fixtures.js'

/**
 * The steps of ACB's scenarios that wait on B1 -- every one tagged `@known-broken @defect:B1 @skip`.
 *
 * 🔴 WHY THEY THROW. Each needs something the application cannot do while the API answers anyone: an
 * administrator who is signed in, a run that was approved, a run that was submitted to the bank. There is
 * no honest way to write them against a system without sign-in -- a step that pretended would be a
 * scenario that passes while asserting nothing. So they are PENDING, and say so in their own words the one
 * way they could ever run: if somebody deletes the tags before B1 closes.
 *
 * `playwright-bdd` generates every scenario, skipped or not, and refuses a step it cannot match -- which is
 * the only reason these are defined at all. The day B1 closes, each is replaced by a real one, the tags go,
 * and this file shrinks to nothing. Keywords do not matter to matching, so all are registered once.
 */
const WAITS_ON_B1 = [
  // acb-runs.feature
  'a signed-in administrator approves the run',
  'the run is approved',
  "the run's lines can no longer be changed",
  'the debit order run for the next month has been submitted to the bank',
  'an administrator records that the bank declined the file for a wrong debit date',
  "the run is declined, with the bank's reason",
  'the month can be prepared again',
  // acb-returns.feature
  'a submitted debit order run debits a member minted for this scenario',
  "an administrator records that member's debit as unpaid with the bank's code {string}",
  'they record the same unpaid again',
  'the run shows one unpaid for that member, for {string}',
  "the member's letter for that unpaid is the {string} letter",
  'a submitted debit order run has two unpaids, one marked present again and one marked suspend',
  'an administrator prepares the debit order run for the following month',
  "the run debits the first member twice: the month's own debit and the unpaid presented again",
  'the run does not present the suspended unpaid again',
  'a member has two unpaids marked present again, from two submitted months',
  "the run presents the older unpaid and holds the newer one over for the member's permission",
  "a member's debits were returned unpaid three times, the last three presentations",
  'an administrator opens the termination report',
  'the member is listed with the three unpaid months named',
  "no member's status has been changed by the report",
]

const pending = (text) => {
  throw new Error(`B1: "${text}" needs an administrator who is signed in, and a run approved and submitted -- `
    + 'none of which exists while the API answers anyone. This scenario is @skip until B1 closes; '
    + 'write this step then, and delete the tags.')
}

/*
 * ⚠️ A STEP'S FUNCTION DECLARES ITS PARAMETERS. playwright-bdd checks the arity -- the fixtures object, then one
 * argument per `{string}` -- and refuses a mismatch ("Function has 0 arguments, but expected 2"). It checks only
 * the features it compiles, and it compiles NO file whose every scenario is skipped, so a wrong arity here would
 * surface the day B1's tags come off. Measured on an export of this branch with one tag removed.
 */
for (const text of WAITS_ON_B1) {
  const parameters = (text.match(/\{string\}/g) ?? []).length
  if (parameters === 0) Given(text, async () => pending(text))
  else if (parameters === 1) Given(text, async ({ world }, value) => pending(`${text} [${value}]`))
  else throw new Error(`a B1 step with ${parameters} parameters needs its own arity: ${text}`)
}
