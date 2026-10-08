import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { assertNonVacuousScan, FLOORS } from '../../support/assertNonVacuousScan.js'
import { repoRelative, sourceFiles } from '../../support/sourceFiles.js'

/**
 * The invariants a human used to check by running `grep` and pasting the
 * output, plus the compose-file guards that grew up beside them. They are
 * executable here instead, for the reason this whole suite exists: a check that
 * depends on somebody remembering to run it is a check that is not being run.
 *
 * ⚠️ No count in this file's prose. There were three of these once; every later
 * batch added one and left the number behind, which is the drift the compose
 * file's own comments were cleaned up to stop.
 *
 * The forbidden strings are ASSEMBLED rather than written out, so that this
 * file does not match its own scan. A guard that has to exclude itself by
 * filename is a guard that stops working the day it is renamed.
 */
const SIBLING_CLIENT = ['..', 'client'].join('/')
const SIBLING_SERVER = ['..', 'server'].join('/')
const FIXED_WAIT = ['wait', 'For', 'Timeout'].join('')
const PLANNING_PATH = ['planning', ''].join('/')

/**
 * Every source file, paired with its text, read ONCE for the whole module.
 *
 * The four scans below previously each re-walked the tree and re-read every
 * matched file. Playwright evaluates a spec module once per worker and these
 * read only source, which cannot change while the run is in flight, so holding
 * the read is free -- the same rule the server's inventories state explicitly.
 */
const corpus = sourceFiles().map((file) => ({
  path: repoRelative(file),
  text: readFileSync(file, 'utf8'),
}))

function scanFor(needle) {
  return corpus.filter((f) => f.text.includes(needle)).map((f) => f.path)
}

/**
 * `docker-compose.yml`, taken FROM the corpus rather than read again.
 *
 * Several tests below read this file and each previously opened it itself --
 * repeated reads of one file where the module's own docblock says the corpus is
 * held precisely so there is one. Going through the corpus also makes a silent
 * disagreement impossible: if `sourceFiles()` ever stopped discovering the
 * compose file, guards reading it directly would carry on while every other
 * scan skipped it, and the two would disagree about what this repository
 * contains without anything going red.
 */
const compose = corpus.find((f) => f.path === 'docker-compose.yml')?.text ?? ''

/** Every match of `re` in the compose file, or an empty list. One idiom, four callers. */
const inCompose = (re) => compose.match(re) ?? []

test.describe('suite-wide invariants', () => {
  test('the scan reaches the suite at all', () => {
    // Completeness by discovery, plus the canary. Without this, every
    // absence-shaped assertion below would pass by finding nothing in nothing --
    // exactly the fail-open mode the canary exists to convert into a red run.
    assertNonVacuousScan(corpus, FLOORS.SUITE_SOURCE_FILES, 'suite source files')
  })

  test('no file contains a path to a sibling repository', () => {
    // This is the invariant that makes a three-repo pipeline possible later. A
    // suite that reaches up into a sibling checkout runs only where all three
    // repositories happen to sit side by side on one disk. Targets come from
    // SASBO_CLIENT_URL and SASBO_API_URL -- see support/env.js.
    //
    // This test caught its first two violations within a minute of being
    // written, both of them prose in comments explaining the rule. That is the
    // point: a rule stated in a comment is a rule until someone copies the
    // comment.
    expect(scanFor(SIBLING_CLIENT), 'files reaching the client repository').toEqual([])
    expect(scanFor(SIBLING_SERVER), 'files reaching the server repository').toEqual([])
  })

  test('no file contains a fixed wait', () => {
    // Playwright's assertions retry on their own. A hard sleep is a flake with
    // a delay attached, and it hides precisely the races that running the suite
    // under constrained CPU is meant to expose.
    expect(scanFor(FIXED_WAIT), 'files using a fixed wait').toEqual([])
  })

  test('the stack can be brought up twice at once', () => {
    // 🔴 THE REGRESSION THIS CATCHES IS SILENT AND DESTRUCTIVE. Compose keys
    // containers, networks and volumes off the project name, so a CONSTANT one
    // means two sessions running `up` from two worktrees address one stack --
    // and `down -v` in either drops the other's database mid-run. The surviving
    // session sees a connection reset and reads it as flake in its own suite.
    //
    // Both halves are asserted because either alone is insufficient: an
    // isolated project name still collides on the fixed host ports, and free
    // ports without an isolated name still share the containers.
    expect(
      /^name:.*\$\{SASBO_STACK_ID/m.test(compose),
      'the compose project name must come from SASBO_STACK_ID, not be a constant',
    ).toBe(true)

    // `${VAR-default}` and NOT `${VAR:-default}`: the colon collapses "unset"
    // and "empty" into one case, and it is the empty case -- publish on an
    // ephemeral port -- that lets a second stack exist at all.
    const collapsed = inCompose(/\$\{SASBO_\w*HOST_PORT:-[^}]*\}/g)
    expect(
      collapsed,
      'host ports using ${VAR:-x} cannot be blanked, so two stacks collide at `up`',
    ).toEqual([])

    const publishable = inCompose(/\$\{SASBO_\w*HOST_PORT-[^}]*\}/g)
    assertNonVacuousScan(publishable, FLOORS.BLANKABLE_HOST_PORTS, 'blankable published host ports')
  })

  test('the server service supplies every variable the image will not start without', () => {
    // 🔴 THIS FAILURE CANNOT REPORT ITSELF FROM INSIDE THE STACK, which is why
    // it is asserted here as text. The application interpolates these with NO
    // defaults, so a missing one kills the server container at startup; `suite`
    // depends on `client` depends on `server`, all `service_healthy`, so in
    // exactly the case worth catching THE SUITE NEVER STARTS -- a test asserting
    // the server's health could not run and would report nothing. Compose does
    // surface it, as a non-zero `up --wait`, but only once the stack is asked to
    // start. This runs in the `harness` project: no browser, no images, no
    // daemon. It reddens at edit time on a machine with Docker down.
    //
    // 🔑 THIS LIST IS THE ONE PLACE THE MANDATORY SET IS WRITTEN. The compose
    // file's comments say only WHY each is needed and point here for WHAT --
    // they used to count them in prose, in two places, and the two counts had
    // already drifted apart. Deriving the set instead from markers in the
    // compose file would be self-referential: deleting a variable together with
    // its marker would pass. A hardcoded list cannot pass vacuously -- an empty
    // scan leaves every name missing and goes red.
    //
    // ⚠️ Regex over text, deliberately: this repository pins two dependencies
    // exactly and adds none for a guard. A YAML parse is tracked as X8.
    const service = compose.match(/^ {2}server:\n(?:(?! {2}\w).*\n?)*/m)?.[0] ?? ''
    expect(service, 'the `server:` service was not found in docker-compose.yml').not.toBe('')

    // Anchored at the start of the line, after indentation only. That is what
    // separates a set variable from one mentioned in a comment -- `# NAME:` in
    // prose must not satisfy this, or the guard passes on a commented-out
    // variable, which is the exact shape of the bug it exists to catch.
    const declared = service.match(/^ +[A-Z][A-Z0-9_]*(?=:)/gm)?.map((s) => s.trim()) ?? []
    const missing = [
      // Re-derived 2026-09-14 against both server branches in play: none of these has a default.
      'SPRING_DATASOURCE_URL',
      'SPRING_DATASOURCE_USERNAME',
      'SPRING_DATASOURCE_PASSWORD',
    ].filter((name) => !declared.includes(name))

    expect(missing, 'mandatory variables the server image will not start without').toEqual([])
  })

  test("the server serves SASBO's workplaces in this stack -- month end's switch at all (MD50)", () => {
    // Month end's switch is `none` unless it is set -- the safe value on a host, where the backend is public and
    // SASBO has not said the branch list may be shown (MD45, MM79). This stack is the one place every journey runs
    // against the WHOLE of month end, so it is set here, to `all`; dropped, the stack quietly tests a month end with
    // no workplace, no invented member and no month taken, and every journey that reads one still "passes" on the
    // empty answers. Asserted as text, for the reason the tests above give.
    const service = compose.match(/^ {2}server:\n(?:(?! {2}\w).*\n?)*/m)?.[0] ?? ''
    expect(service, 'the `server:` service was not found in docker-compose.yml').not.toBe('')

    const setting = (name) => service.match(new RegExp(`^ +${name}: *['"]?([^'"\\s#]+)`, 'm'))?.[1]
    expect(setting('SASBO_WORKPLACES'), "month end's switch must be `all` in the suite's stack").toBe('all')
  })

  test('the server builds its schema from the migrations and only validates it', () => {
    // 🔴 2026-09-16: production's `ddl-auto: create-drop` (server 74fdd09) dropped the live
    // database, and the next commit added a mapped column no migration creates (C12, C13).
    // This stack is the one place the three repositories run together against a database
    // BUILT FROM EMPTY BY FLYWAY -- and it is only that while these two overrides stand.
    //
    // Delete `validate` and the image falls back to production's `update`: Hibernate
    // quietly creates whatever a migration forgot, every journey passes, and the stack
    // stops being evidence that the migration chain describes the schema. With it, a
    // missing column kills the server at boot and `up --wait` refuses the stack -- which
    // is exactly how C13 surfaced here. Asserted as text for the reason given above.
    const service = compose.match(/^ {2}server:\n(?:(?! {2}\w).*\n?)*/m)?.[0] ?? ''
    expect(service, 'the `server:` service was not found in docker-compose.yml').not.toBe('')

    const setting = (name) => service.match(new RegExp(`^ +${name}: *['"]?([^'"\\s#]+)`, 'm'))?.[1]

    expect(
      setting('SPRING_JPA_HIBERNATE_DDL_AUTO'),
      'the stack must VALIDATE the schema. Anything else lets Hibernate write it, and a ' +
        'missing migration then passes every journey instead of failing the stack',
    ).toBe('validate')
    expect(
      setting('SPRING_FLYWAY_ENABLED'),
      'Flyway must build the schema, or `validate` has nothing honest to compare against',
    ).toBe('true')
  })

  test('the server is healthy only once it is ready, and only while it serves data', () => {
    // 🔴 REGISTER E20. Tomcat answers before the ApplicationRunners run, so a
    // healthcheck on /api/banks alone passed while DataSeeder was still writing,
    // and the suite could start against half a seed. Nothing inside the stack
    // can see that: the suite simply starts early, and stays green unless a
    // scenario lands in the gap. So it is asserted here, as text, for the reason
    // given above -- the compose file's comment says why the order is this one.
    const service = compose.match(/^ {2}server:\n(?:(?! {2}\w).*\n?)*/m)?.[0] ?? ''
    expect(service, 'the `server:` service was not found in docker-compose.yml').not.toBe('')

    // Anchored after indentation only, so a `# test:` in a comment cannot stand in
    // for the command. The server service has one `test:` key: its healthcheck's.
    const command = service.match(/^ +test: *\[.*'([^']*)'\] *$/m)?.[1] ?? ''
    expect(command, 'the server service has no healthcheck command').not.toBe('')

    // The ROOT health path (SecurityConfig permits exactly it), AND THEN the data.
    // `&&` is the point: with `||`, or either half alone, a passing /api/banks
    // makes the stack healthy mid-seed again.
    const readyThenData = (cmd) =>
      /wget [^&|;]*http:\/\/127\.0\.0\.1:8080\/actuator\/health(?= ) *&& *wget [^&|;]*http:\/\/127\.0\.0\.1:8080\/api\/banks\b/.test(
        cmd,
      )
    // The two that must fail it, so the predicate is shown to tell them apart:
    // the check E20 replaced, and the right two questions joined the wrong way.
    expect(readyThenData('wget -q -O- http://127.0.0.1:8080/api/banks || exit 1')).toBe(false)
    expect(
      readyThenData('wget -q -O- http://127.0.0.1:8080/actuator/health || wget -q -O- http://127.0.0.1:8080/api/banks'),
    ).toBe(false)

    expect(
      readyThenData(command),
      `the server's healthcheck must ask /actuator/health (not UP until the seeders have ` +
        `returned) AND THEN /api/banks; it is: ${command}`,
    ).toBe(true)
  })

  test('the suite does not depend on the orchestrator', () => {
    // 🔴 THE PROPERTY THAT LETS `tools/` LIVE HERE AT ALL, and the one that
    // stops being true by accident.
    //
    // `tools/` holds the three-repository orchestrator -- it runs the server's
    // gate, the client's, and this one, and merges the three reports. It was
    // previously two files at the workspace root, tracked by NO repository:
    // no history, no diff to review, no revert, and nothing to restore from.
    //
    // Bringing it in here buys version control. What it must not buy is a
    // dependency: each of the three suites still runs standalone in a fresh
    // clone of its own repository, and none of them knows the orchestrator
    // exists. That is a real property of this repository and it is asserted
    // rather than asserted-in-prose, because the way it dies is somebody
    // importing one helper from `tools/` into a step definition on a Tuesday.
    //
    // 🔑 NOTE WHAT IS NOT WEAKENED. `tools/` is scanned by every invariant
    // above -- no sibling paths, no fixed waits, no planning paths -- exactly
    // like the rest of the repository. The orchestrator finds the three
    // checkouts through SASBO_WORKSPACE instead of by naming them, which is the
    // same mechanism the suite uses for SASBO_CLIENT_URL and SASBO_API_URL. If
    // a future change to it ever writes a sibling path out, the invariant above
    // is SUPPOSED to redden, and the answer is the environment variable rather
    // than an exemption for this directory.
    //
    // (Writing that path here as an example would itself have tripped that
    // scan -- it did, while this test was being written. The rule at the top of
    // this file about assembling forbidden strings rather than spelling them is
    // not decoration.)
    const tools = corpus.filter((f) => f.path.startsWith('tools/'))

    // The orchestrator is not optional furniture: if this scan found nothing,
    // the dependency check below would pass over an empty set and say nothing.
    //
    // A FLOOR rather than an exact list, using the mechanism this repository
    // already has for exactly this. Pinning the two filenames would redden a
    // test called "the suite does not depend on the orchestrator" for the
    // entirely legitimate act of adding a third tool -- with a message saying
    // the orchestrator is missing, which would be false.
    assertNonVacuousScan(tools, FLOORS.ORCHESTRATOR_FILES, 'orchestrator files under tools/')

    const importers = corpus
      .filter((f) => !f.path.startsWith('tools/'))
      .filter((f) => /\bfrom\s+['"][^'"]*\btools\/|\brequire\(\s*['"][^'"]*\btools\//.test(f.text))
      .map((f) => f.path)

    expect(importers, 'suite files importing from the orchestrator').toEqual([])
  })

  test('the Playwright image matches the @playwright/test this suite installs', () => {
    // 🔴 THE PIN THAT WAS MAINTAINED BY MEMORY. The compose file calls the exact
    // version "the whole point" -- the image ships the browsers, package.json
    // ships the driver that speaks to them, and a mismatch is a driver talking
    // to browsers it was not built for. Nothing asserted it: a grep for
    // `playwright:v`, `mcr.microsoft` or `PLAYWRIGHT` across this repository
    // found the compose file and nothing else.
    //
    // 🔑 IT IS ALSO WHAT MAKES THE ARCHITECTURE SUFFIX SAFE. The image tag now
    // ends in `-${SASBO_PLAYWRIGHT_ARCH}` so an arm64 machine can run this at
    // all, and the reason it varies only the SUFFIX rather than the whole image
    // is that a whole-image variable would let the version drift too. That
    // argument is only worth anything if something checks the version, which is
    // this.
    const image = compose.match(/mcr\.microsoft\.com\/playwright:v([\d.]+)-/)
    expect(image, 'no pinned Playwright image found in docker-compose.yml').not.toBeNull()

    const pkg = JSON.parse(corpus.find((f) => f.path === 'package.json')?.text ?? '{}')
    const declared = pkg.devDependencies?.['@playwright/test']
    expect(declared, '@playwright/test is not a devDependency').toBeTruthy()

    // The dependency is pinned exactly here -- no caret, no tilde -- so a plain
    // comparison is right, and a range would be its own finding.
    expect(
      declared,
      `docker-compose.yml runs the v${image[1]} image while package.json installs ` +
        `${declared}. The image carries the browsers and the package carries the driver; ` +
        'they are one version or they are a bug',
    ).toBe(image[1])
  })

  test('every image reference is a well-formed reference', () => {
    // 🔴 EVERY OTHER COMPOSE GUARD HERE IS A STRING MATCH, and a string match
    // has no opinion about whether the file would actually start. An `image:`
    // value of `://microsoft.com{...}` passes all of them and fails at `up`
    // with `invalid reference format` -- the stack never starts, and the guards
    // were green about it.
    //
    // Nothing here parses YAML: this repository pins two dependencies exactly
    // and adds none for a guard, and `docker compose config` -- which would
    // validate the whole file properly -- needs a daemon, which this project
    // deliberately does not. So this checks the one part that is cheap and has
    // actually broken: the shape of each reference. `tools/test-all.mjs` runs
    // the real `docker compose config` before the suite stage, where a daemon
    // exists.
    const images = inCompose(/^\s*image:\s*\S+\s*$/gm).map((line) => line.trim().slice('image:'.length).trim())
    assertNonVacuousScan(images, FLOORS.COMPOSE_IMAGES, 'image references in docker-compose.yml')

    // name[:tag] where name may carry a registry host and path segments, and
    // either part may contain a ${VAR} or ${VAR:-default} substitution.
    const REFERENCE = /^[\w.\-/${}:]+$/
    const malformed = images.filter((i) => !REFERENCE.test(i) || i.startsWith(':') || i.includes('://'))
    expect(malformed, 'image references docker cannot resolve').toEqual([])
  })

  test('the client proxy forwards the port in Host and leaves Origin alone', () => {
    // 🔴 NOTHING ELSE WOULD NOTICE THIS REGRESS. The containerised suite passes with
    // either `$host` or `$http_host`, because its browser's Host carries no port. Only
    // a browser on a published port -- `npm run demo` -- is judged cross-origin when
    // the port is dropped, and every save there answers 403. Blanking `Origin` instead
    // hides real CORS refusals from every browser request the stack makes (C10).
    const dockerfile = corpus.find((f) => f.path === 'docker/client.Dockerfile')?.text ?? ''
    const api = dockerfile.match(/location \/api\/ \{[^}]*\}/)?.[0] ?? ''
    expect(api, 'no `location /api/` block found in docker/client.Dockerfile').not.toBe('')
    expect(api, 'the /api proxy must forward the port: proxy_set_header Host $http_host').toContain(
      'proxy_set_header Host $http_host;',
    )
    expect(api, 'the /api proxy must not blank or rewrite Origin').not.toMatch(/proxy_set_header\s+Origin/)
  })

  test('no file cites a path under the plans', () => {
    // The plans read the code; the code never reads the plans. A reader of this
    // repository may have only this repository, so anything they need to know
    // is written here rather than pointed at.
    expect(scanFor(PLANNING_PATH), 'files citing a planning path').toEqual([])
  })
})
