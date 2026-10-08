import { beforeAll, describe, expect, it } from 'vitest'
import { ESLint } from 'eslint'

/**
 * THE LOCAL `jsx-usage/jsx-uses-vars` RULE, PROVED IN BOTH DIRECTIONS.
 *
 * WHY THE RULE EXISTS. ESLint's scope analysis does not know that `<Icon />` reads the
 * binding `Icon`, so core `no-unused-vars` reports every JSX-only reference as unused.
 * `eslint.config.js` carries a fifteen-line local rule that marks them used, in place of a
 * dependency on `eslint-plugin-react` for the one rule this repository needs from it.
 *
 * 🔴 WHY IT IS TESTED AT ALL, AND WHY ONE ASSERTION WOULD BE WORSE THAN NONE. A rule that
 * suppresses TOO MUCH is indistinguishable from a rule that works, because both produce a
 * quiet report -- and "quiet" is what a hand-rolled linter rule fails into. `markVariableAsUsed`
 * with a wrong name, a `create` that throws and is swallowed, a plugin key that stops matching
 * the rule id: every one of those ends in fewer warnings, which reads as success.
 *
 * So this asserts the PAIR (contract ⑪, and the non-vacuity half the arc keeps restating):
 *
 *   ① a component referenced ONLY as a JSX element is NOT reported  -- the rule does its job
 *   ② a component that is genuinely never referenced IS STILL reported -- it did not do more
 *
 * ② is the one that matters. Delete the `/^[A-Z_$]/` guard, or call `markVariableAsUsed` for
 * every identifier rather than element names, and ① stays green while ② goes red.
 *
 * ITS OWN REDDENING CHANGE, run and reverted at `05.4`: commenting out the
 * `'jsx-usage/jsx-uses-vars': 'warn'` line in `eslint.config.js` turns ① red, naming `Icon`.
 * Changing the rule to mark every JSXIdentifier used turns ② red.
 *
 * It runs the real ESLint over the real `eslint.config.js` rather than a fixture config, so
 * the thing under test is the configuration this repository actually lints with.
 */

/** Lint a source string as if it were a file under `src/`, through the repo's own config. */
/**
 * One instance for the file, not one per call.
 *
 * `lintText` is stateless with respect to the instance, but config resolution and rule
 * compilation are not: measured here, the first `lintText` pays ~1,170ms for those, a second
 * call on a NEW instance pays 67ms, and on a reused instance 9.8ms. Hoisting saves ~57ms of
 * pure setup and changes nothing about what is linted.
 *
 * ⚠️ THE DOMINANT COSTS ARE IRREDUCIBLE AND WORTH KNOWING ON THIS BOX: importing `eslint` is
 * ~426ms and ~63 MB, and RSS settles around 150 MB. This one test file is therefore a ~150 MB
 * worker, and with `VITEST_MAX_WORKERS=2` it sits alongside one other. That is the price of the
 * guard's whole point — running the REAL ESLint over the REAL `eslint.config.js` — and it is
 * paid deliberately rather than by accident.
 *
 * No `cwd`: ESLint defaults to the process working directory, which is the repository root
 * under vitest. Naming it explicitly would need `process`, and this config declares browser
 * globals only -- so reaching for it is a `no-undef` error in the guard itself.
 */
const eslint = new ESLint()

const lint = async (code) => {
  // The path decides which config block applies. It must look like a source file under
  // `src/`, and must not be a real one -- `lintText` does not read it, but a name that
  // collides with a tracked file makes the fixture hard to find later.
  const [result] = await eslint.lintText(code, { filePath: 'src/__jsxUsageFixture.jsx' })
  return result.messages.filter((m) => m.ruleId === 'no-unused-vars')
}

/** ① Used only as a JSX element -- the shape `About.jsx:213` was falsely flagged for. */
const USED_ONLY_IN_JSX = `
export function A({ items }) {
  return items.map(({ href, icon: Icon, name }) => (
    <a key={name} href={href}>
      <Icon />
      {name}
    </a>
  ))
}
`

/** ② The same shape with the reference removed. Genuinely dead. */
const NEVER_REFERENCED = `
export function C({ items }) {
  return items.map(({ href, icon: Icon, name }) => (
    <a key={name} href={href}>
      {name}
    </a>
  ))
}
`

describe('no-unused-vars can tell a used component from a dead one', () => {
  /*
   * 🔴 THE COLD START IS PAID HERE, NOT BY WHICHEVER TEST HAPPENS TO RUN FIRST.
   *
   * The first `lintText` resolves the config and compiles every rule (~1,170ms measured on a
   * quiet box, above). Test order is shuffled, so that cost used to land inside ONE test's
   * 20s budget -- and under a loaded full-suite run it did not fit: on 2026-09-16 "STILL
   * reports..." timed out at 27.8s, and on 2026-09-17 "does not report..." timed out at 27.3s,
   * each time the test that ran first. Both pass in isolation in ~1s. That is a flake, and the
   * fix is to bill the setup to a hook with a budget of its own rather than to raise the
   * tests' timeout, which would also hide a genuinely slow lint.
   */
  beforeAll(async () => {
    await eslint.lintText('export {}\n', { filePath: 'src/__jsxUsageFixture.jsx' })
  }, 120_000)

  it('does not report a binding referenced only by a JSX element', async () => {
    const messages = await lint(USED_ONLY_IN_JSX)

    expect(
      messages.map((m) => m.message),
      'A component used as <Icon /> is being reported as unused. `jsx-usage/jsx-uses-vars` ' +
        'is not running, so every JSX-only reference in this repository is a phantom ' +
        'warning -- and `10.4` flips this gate to a hard 0/0, which would make correct ' +
        'code unlintable and the only way past it deleting the binding the JSX needs.',
    ).toEqual([])
  })

  it('STILL reports a binding that really is never referenced', async () => {
    const messages = await lint(NEVER_REFERENCED)

    // The non-vacuity half. Without it, a rule that marked EVERY identifier used would
    // pass the test above and quietly disable `no-unused-vars` for the whole repository.
    expect(
      messages.map((m) => m.message).join(' | '),
      'A destructured binding that is never referenced anywhere is no longer reported. ' +
        '`jsx-uses-vars` is suppressing more than JSX element names, so `no-unused-vars` ' +
        'has been disarmed rather than corrected -- which looks exactly like it working.',
    ).toMatch(/'Icon' is defined but never used/)
  })
})

/*
 * 🔴 THE CANARY PAIR FOR THE RULES THAT REPLACED `10.4`'s SOURCE SCANS -- linted through the real config here rather
 * than in a new file, because each ESLint instance is a ~150 MB worker on this machine.
 *
 * Both halves, for the reason `assertDetectorCanary` states: a rule that matches nothing passes the acquit half, and
 * one that matches everything passes the convict half. The convict list carries the shapes the regex scans MISSED --
 * the conditional `type`, a `<` inside an attribute before it -- and the acquit list a shape they WRONGLY convicted,
 * so the move is proved to be an improvement and not only a relocation.
 */
const RESTRICTED = new Set(['no-restricted-globals', 'no-restricted-properties', 'no-restricted-syntax'])
const restricted = async (code) => {
  const [result] = await eslint.lintText(code, { filePath: 'src/__restrictedFixture.jsx' })
  return result.messages.filter((m) => RESTRICTED.has(m.ruleId))
}

const MUST_CONVICT = {
  'a bare confirm()': "export const f = () => confirm('Delete?')",
  'window.alert()': 'export const f = (e) => window.alert(e)',
  'globalThis.prompt()': "export const f = () => globalThis.prompt('Name?')",
  'confirm destructured from window': "const { confirm } = window; export const f = () => confirm('x')",
  'a native date input': 'export const F = () => <input type="date" />',
  'datetime-local, in braces': "export const F = () => <input type={'datetime-local'} />",
  'a conditional type (the regex scan missed this)': "export const F = ({ d }) => <input type={d ? 'date' : 'text'} />",
  'a < inside an attribute before type (the regex scan missed this)':
    'export const F = ({ a, b }) => <input min={a < b ? 1 : 2} type="date" />',
  'a handler with => before type': 'export const F = ({ s }) => <input onChange={(e) => s(e)} type="month" />',
}
const MUST_ACQUIT = {
  'Field routes type="date" to DateField': 'export const F = ({ Field }) => <Field type="date" />',
  'an ordinary input': 'export const F = () => <input type="text" />',
  'setConfirmed, and a ConfirmDialog handler': 'export const f = ({ setConfirmed, onConfirm }) => { setConfirmed(true); onConfirm() }',
  'a LOCAL function named confirm': 'const confirm = (x) => x; export const f = () => confirm(1)',
  'the word inside a string (the regex scan convicted this)': "export const s = 'please confirm (twice)'",
  'role="alert"': 'export const F = () => <div role="alert" />',
  'a method that is not the global': "export const f = (dialogs) => dialogs.alert('x')",
}

describe('the native-dialog and native-date-input rules convict what they must, and nothing else', () => {
  beforeAll(async () => {
    await eslint.lintText('export {}\n', { filePath: 'src/__restrictedFixture.jsx' })
  }, 120_000)

  it('convicts every shape it is written to catch', async () => {
    const missed = []
    for (const [name, code] of Object.entries(MUST_CONVICT)) {
      if ((await restricted(code)).length === 0) missed.push(name)
    }
    expect(missed, 'A rule stopped seeing a native dialog or date input; the gate is blind to it now.').toEqual([])
  })

  it('the must-pass: acquits every shape that is correct', async () => {
    const convicted = []
    for (const [name, code] of Object.entries(MUST_ACQUIT)) {
      const found = await restricted(code)
      if (found.length) convicted.push(`${name}: ${found.map((m) => m.ruleId).join(', ')}`)
    }
    expect(convicted, 'A rule convicted correct code -- fix the rule, never the code it accuses.').toEqual([])
  })
})
