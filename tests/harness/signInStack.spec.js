import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { repoRelative, sourceFiles } from '../../support/sourceFiles.js'
import { demoPassword } from '../../support/demoUsers.js'

/**
 * The stack signs its demonstration users in -- and their password is written nowhere.
 *
 * WHY THIS IS ASSERTED AS TEXT, LIKE THE WORKPLACES SWITCH. The October build's sign-in seeds one user per position
 * only when `SASBO_SIGNIN_DEMO_USERS` is true and `SASBO_SIGNIN_DEMO_PASSWORD` is set; the journeys that need a
 * permission sign in as them. Dropped from the compose file, every such journey fails at its first step for a reason
 * that has nothing to do with it -- and a password typed into the compose file instead of passed through would pass
 * every journey while putting a credential into history, which no other guard here reads: the committed-secret scan
 * knows an assignment to `password`, not to a variable whose name merely ends in it.
 */
const compose = (() => {
  const file = sourceFiles().find((f) => repoRelative(f) === 'docker-compose.yml')
  if (!file) throw new Error('docker-compose.yml was not found by sourceFiles()')
  return readFileSync(file, 'utf8')
})()

/** One service's block, from its name to the next service. */
const serviceBlock = (name) => compose.match(new RegExp(`^ {2}${name}:\\n(?:(?! {2}\\w).*\\n?)*`, 'm'))?.[0] ?? ''

/** A variable SET in a block -- anchored after indentation, so a mention in a comment cannot stand in for it. */
const setting = (block, name) => block.match(new RegExp(`^ +${name}: *(.*?) *$`, 'm'))?.[1]

/** The only way the password may be given: passed through from the environment compose runs in. */
const PASSED_THROUGH = /^\$\{SASBO_SIGNIN_DEMO_PASSWORD(:?-)?\}$/

test.describe('the stack signs its demonstration users in', () => {
  test('the server runs sign-in open, with its demonstration users on', () => {
    const server = serviceBlock('server')
    expect(server, 'the `server:` service was not found in docker-compose.yml').not.toBe('')
    expect(setting(server, 'SASBO_SIGNIN'), 'the stack runs the mode the hosted link runs').toBe('open')
    expect(setting(server, 'SASBO_SIGNIN_DEMO_USERS')?.replace(/['"]/g, ''), 'the demonstration users are seeded').toBe('true')
  })

  test('the server and the suite both take the password from the environment, and neither writes one', () => {
    for (const name of ['server', 'suite']) {
      const block = serviceBlock(name)
      expect(block, `the \`${name}:\` service was not found in docker-compose.yml`).not.toBe('')
      expect(
        setting(block, 'SASBO_SIGNIN_DEMO_PASSWORD'),
        `the ${name} service must pass SASBO_SIGNIN_DEMO_PASSWORD through from the environment -- never a value`,
      ).toMatch(PASSED_THROUGH)
    }
  })

  test('the pass-through check tells a passed-through password from a written one', () => {
    // Its canary: the predicate above, shown to refuse the thing it exists to refuse.
    expect('${SASBO_SIGNIN_DEMO_PASSWORD:-}').toMatch(PASSED_THROUGH)
    expect('${SASBO_SIGNIN_DEMO_PASSWORD}').toMatch(PASSED_THROUGH)
    expect("'a-password-typed-in'").not.toMatch(PASSED_THROUGH)
    expect('${SASBO_SIGNIN_DEMO_PASSWORD:-a-default-typed-in}').not.toMatch(PASSED_THROUGH)
  })

  test('a run without the password refuses to guess one', () => {
    // A wrong guess is a failed sign-in, and five lock the user out for every other journey (`demoPassword`).
    expect(() => demoPassword({})).toThrow(/SASBO_SIGNIN_DEMO_PASSWORD is not set/)
    expect(() => demoPassword({ SASBO_SIGNIN_DEMO_PASSWORD: '   ' })).toThrow(/is not set/)
    // And a given one is used exactly as given -- a password is never trimmed.
    expect(demoPassword({ SASBO_SIGNIN_DEMO_PASSWORD: ' given ' })).toBe(' given ')
  })
})
