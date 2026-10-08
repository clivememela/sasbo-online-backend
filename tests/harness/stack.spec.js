import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { repoRelative, sourceFiles } from '../../support/sourceFiles.js'
import { dockerSocket } from '../../support/stack.js'

/**
 * `support/stack.js` is the module that can stop containers, and until now it
 * was the only module in this repository with no spec at all.
 *
 * 🔴 THE UNTESTED BRANCH WAS THE SECURITY-RELEVANT ONE. `projectName()` decides
 * WHICH compose stack this suite is allowed to touch, and `containerIdFor()`
 * refuses to act outside it. Everything protecting another session's database
 * from this suite's teardown runs through those two functions, and nothing
 * exercised either.
 *
 * <h2>Why these are structural assertions and not unit calls</h2>
 * The module talks to the Docker Engine over a socket at import time of its own
 * constants, and its interesting paths need a daemon, a compose-labelled
 * container, and a second stack to not-touch. Standing that up inside the
 * harness project -- which deliberately needs no browser, no application and no
 * daemon -- would make this the one spec that cannot run on a machine with
 * Docker down, and it would be testing Docker rather than the guard.
 *
 * So this asserts the PROPERTIES OF THE SOURCE that the safety argument rests
 * on. Each one below is a specific way the guard has been, or could be, quietly
 * disarmed by an ordinary-looking edit. The behavioural half is covered by
 * `features/serial/`, which stops the API for real and puts it back.
 */
const source = (() => {
  const file = sourceFiles().find(
    (f) => repoRelative(f) === 'support/stack.js',
  )
  if (!file) throw new Error('support/stack.js was not found by sourceFiles()')
  return readFileSync(file, 'utf8')
})()

test.describe('support/stack.js -- the module that can stop containers', () => {
  test('the project name is READ from the container, not rebuilt from the environment', () => {
    // 🔑 THE WHOLE SAFETY ARGUMENT IN ONE LINE. The compose project is
    // `sasbo-automation-testing-${SASBO_STACK_ID}`, so two sessions run two
    // stacks -- and a lookup that rebuilt that string from environment
    // variables would stop the OTHER session's server the moment the two
    // disagreed about a default. The container's own compose label cannot
    // disagree with the stack it is running in.
    expect(
      source.includes("Labels?.['com.docker.compose.project']"),
      'projectName() must read the compose project from the suite container\'s own label',
    ).toBe(true)
  })

  test('the lookup does not swallow every failure on its way to a guess', () => {
    // 🔴 THIS IS THE REGRESSION THAT ALREADY HAPPENED. The lookup was wrapped
    // in a bare `catch {}` whose comment named ONE cause -- "not running inside
    // a compose-managed container" -- while the code admitted every cause: a
    // socket permission error, an API version mismatch, a daemon hiccup, a
    // hostname that is not a container id. All of them fell through to a
    // RECONSTRUCTED name.
    //
    // And the reconstructed name is exactly what compose calls the default
    // stack, so the guess is usually right and the failure is therefore
    // invisible -- it diverges only when a second session exists, which is the
    // only time it matters.
    //
    // A bare catch here is not a style question. It is the difference between
    // refusing and guessing, in the function that decides what may be stopped.
    const bareCatch = /\}\s*catch\s*\{/.test(source)
    expect(bareCatch, 'support/stack.js must not swallow an unclassified failure').toBe(false)
  })

  test('an unrecognised failure is rethrown rather than returned as a name', () => {
    // The other half of the previous assertion: it is not enough to inspect the
    // error, the unrecognised case has to actually stop. A `catch` that logs
    // and continues would satisfy "not a bare catch" and change nothing.
    expect(
      /catch\s*\(\s*\w+\s*\)[\s\S]{0,2600}?\bthrow new Error\(/.test(source),
      'the catch in projectName() must rethrow when it cannot classify the failure',
    ).toBe(true)
  })

  test('every container action is scoped by the compose project label', () => {
    // `containerIdFor()` is the only path to a container id, and it filters on
    // BOTH labels. Dropping the project label would still find a container
    // called `server` -- somebody else's.
    expect(
      source.includes('com.docker.compose.project=${project}'),
      'the container lookup must filter on the compose project',
    ).toBe(true)
    expect(
      source.includes('com.docker.compose.service=${service}'),
      'the container lookup must filter on the compose service',
    ).toBe(true)
  })

  test('finding no container is an error, never an empty result', () => {
    // A lookup that returned undefined here would make `stopService` a no-op
    // and the serial journeys would pass while proving nothing -- a suite that
    // asserts a failed state without ever causing the failure.
    expect(
      /if \(!found\?\.length\)[\s\S]{0,400}?throw new Error\(/.test(source),
      'containerIdFor() must throw when the filtered lookup finds nothing',
    ).toBe(true)
  })
})

/**
 * The socket resolution is CALLED rather than read, because it is the one part
 * of this module that can be.
 *
 * 🔑 It needs no daemon, no container and no second stack -- it is a pure
 * function of an environment and a platform -- so none of the justification for
 * the source assertions above applies to it. It was first written as two string
 * searches, and a mutation replacing the whole function with a hardcoded path
 * left both of them GREEN, because the names they searched for still appeared
 * in the comment above the code. This repository already has the rule: a rule
 * stated in a comment is a rule until somebody copies the comment.
 */
test.describe('dockerSocket()', () => {
  test('SASBO_DOCKER_SOCKET wins over everything', () => {
    // 🔴 The failure this prevents: a macOS machine where the default socket
    // path does not exist. `dockerSocket()`'s own docblock has the detail.
    expect(
      dockerSocket({ SASBO_DOCKER_SOCKET: '/home/me/.docker/run/docker.sock' }, 'darwin'),
    ).toBe('/home/me/.docker/run/docker.sock')

    // Precedence is asserted rather than assumed: somebody with DOCKER_HOST
    // already set must still be able to override it for this suite alone.
    expect(
      dockerSocket(
        { SASBO_DOCKER_SOCKET: '/explicit.sock', DOCKER_HOST: 'unix:///other.sock' },
        'linux',
      ),
    ).toBe('/explicit.sock')
  })

  test('DOCKER_HOST is honoured, with the scheme stripped', () => {
    // A developer whose daemon is not in the default place has usually set this
    // already, and should not have to learn a second name for the same fact.
    expect(dockerSocket({ DOCKER_HOST: 'unix:///var/run/docker.sock' }, 'linux')).toBe(
      '/var/run/docker.sock',
    )
  })

  test('a tcp:// DOCKER_HOST is refused rather than half-supported', () => {
    // This module speaks to the socket directly; a TCP daemon needs a different
    // transport. Ignoring the variable would silently point the suite at the
    // WRONG daemon -- which, for the module that stops containers, is the one
    // outcome worth being loud about.
    expect(() => dockerSocket({ DOCKER_HOST: 'tcp://127.0.0.1:2375' }, 'linux')).toThrow(
      /socket directly rather than over TCP/,
    )
  })

  test('falls back per platform when nothing is set', () => {
    // ⚠️ Windows has no unix socket. Node accepts a named pipe as a
    // `socketPath`, and that is what makes a NATIVE run work at all -- which is
    // exactly when somebody is debugging these scenarios.
    // Written with `String.raw` rather than escaped: the escaped spelling of a
    // Windows named pipe is four backslashes to mean two, and a tool that eats
    // one level silently turns this assertion into a different, passing string.
    // That happened to this very line, and the gate is what caught it.
    expect(dockerSocket({}, 'win32')).toBe(String.raw`\\.\pipe\docker_engine`)
    expect(dockerSocket({}, 'linux')).toBe('/var/run/docker.sock')
    expect(dockerSocket({}, 'darwin')).toBe('/var/run/docker.sock')
  })
})
