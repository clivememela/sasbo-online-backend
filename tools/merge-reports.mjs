#!/usr/bin/env node
/**
 * Merge the three SASBO Online test suites into one report.
 *
 * ── Why this is a Node script ────────────────────────────────────────────────
 * CI runners are Linux. A `.ps1` merge step is unrunnable on `ubuntu-latest`, so
 * the pipeline would have had to reimplement the merge -- the exact drift the
 * standalone-suites invariant exists to prevent. `test-all.mjs` beside this file
 * is a thin local convenience wrapper that calls this script; CI calls this
 * script directly and never touches the wrapper. A convenience wrapper that is
 * the only implementation is not a wrapper.
 *
 * That wrapper WAS PowerShell, which made the merged gate runnable on one of
 * the two developers' machines and on no CI runner at all. It is Node now, for
 * that reason and two others -- see the header of `test-all.mjs`.
 *
 * ── Why zero dependencies ─────────────────────────────────────────────────────
 * The plan's open question proposed a Cucumber-JSON HTML reporter run via `npx`.
 * That was resolved against, and the reasoning is recorded here because the
 * alternative is re-proposed by anyone who reads only the plan:
 *
 *   1. ONLY ONE OF THE THREE SUITES EMITS CUCUMBER JSON. `server/` and `client/`
 *      emit JUnit XML and nothing else. A Cucumber-JSON reporter cannot read
 *      them, so two thirds of the report would have had no input -- or we would
 *      have had to synthesise fake Cucumber JSON from JUnit XML, which is more
 *      code than reading the JUnit XML directly.
 *   2. JUNIT XML IS THE ONE FORMAT ALL THREE ALREADY EMIT. That is not an
 *      accident -- it is a contract the three repositories agreed to, precisely
 *      so that one reader covers all three.
 *   3. `npx <package>` RESOLVES FROM THE NETWORK AT RUN TIME, unpinned. That
 *      contradicts the "everything pinned" property in the same contract, fails
 *      on a machine whose TLS is intercepted, fails offline, and puts a
 *      supply-chain surface underneath a report generator.
 *   4. ZERO DEPENDENCIES KEEPS THE INVARIANT HONEST -- "the merge is a
 *      convenience over three independent suites, never a dependency of any".
 *      This file imports nothing outside Node's standard library, and the
 *      repository it lives in pins exactly two devDependencies, neither of
 *      which it uses.
 *
 * ── What it reads ─────────────────────────────────────────────────────────────
 * JUnit XML is the spine: pass, fail, skip, timing, per test, for all three.
 *
 * The suite additionally emits Cucumber JSON, and that is read for ONE reason:
 * it is the only place a scenario's tags survive. Playwright's JUnit reporter
 * writes `<properties>` from `test.annotations`, and playwright-bdd carries tags
 * in its own data structure rather than as annotations -- so `@known-broken` and
 * `@defect:A30` reach the Cucumber JSON and never reach the XML. Verified by
 * reading both reporters' source, not assumed.
 *
 * ── What it cannot read, and says so ──────────────────────────────────────────
 * 🔴 The server's skip reasons are the richest of the three and reach no
 * machine-readable artifact at all. `@Disabled(KnownDefects.SOME_ROW)` takes a
 * compile-time constant carrying the whole finding, and Gradle's JUnit XML
 * writer emits a bare `<skipped/>`: no message attribute, no reason element, and
 * a display name that does not name the register row either.
 *
 * This script does NOT paper over that. A skipped test whose reason is not in
 * the artifacts is rendered as "reason not carried in this suite's report",
 * because a merged report that silently shows a skip with no reason reads
 * identically to a skip that never had one. Filed as F10.
 *
 * ── The exit code is half the claim ───────────────────────────────────────────
 * `--exit <key>:<code>` tells this script what a suite's process actually
 * returned, and the report then asserts BOTH halves against each other. MobiZone
 * observed a client suite reporting 941/941 passing while the process exited 1;
 * reading only the report calls that green, and reading only the exit code never
 * says which test. A disagreement in either direction is its own loud state and
 * is not resolved in favour of whichever number is more convenient.
 *
 * The flag is optional. Omitted -- CI running the three suites as separate jobs,
 * say -- the check is simply not made, rather than assumed to have passed.
 *
 * `--step <key>:<name>:<code>` is the rest of a lane: a command the caller ran
 * OUTSIDE the tests -- lint, the build, the bundle budget -- and what it returned.
 * One that failed fails the lane however green its tests are, and is named.
 *
 * ── Exit codes ────────────────────────────────────────────────────────────────
 *   0  every configured suite ran, and every test passed
 *   1  at least one test failed, a suite's report and exit code disagree, or a
 *      step outside the tests failed
 *   2  no failures, but at least one suite did not run or is missing artifacts
 *
 * 🔑 THE REPORT IS WRITTEN BEFORE THE PROCESS EXITS, ALWAYS, INCLUDING ON A
 * FAILURE. A merge step that swallows a failure is worse than no merge step; a
 * merge step that produces nothing when it matters most is not much better.
 *
 * ── Usage ─────────────────────────────────────────────────────────────────────
 *   node tools/merge-reports.mjs
 *   node tools/merge-reports.mjs --server <dir> --client <dir> --suite <dir>
 *   node tools/merge-reports.mjs --out <dir>
 *
 * The directory overrides exist because the checkouts are not always the sibling
 * directories: this arc runs from git worktrees under `.worktrees/`, and CI
 * checks each repository out wherever it likes. A merge tool that can only read
 * one hard-coded layout is a merge tool CI has to reimplement.
 */

import { readFileSync, readdirSync, writeFileSync, mkdirSync, statSync, existsSync } from 'node:fs'
import { join, resolve, relative, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

/** Where the merged report goes unless a caller says otherwise. */
export const DEFAULT_OUT = 'reports/merged'

/**
 * The three suites, and where each one leaves its evidence.
 *
 * `junit` entries are either a file or a directory scanned for `*.xml`. Every
 * path is relative to that repository's own checkout, which is what keeps this
 * table true for a worktree, a fresh clone and a CI workspace alike.
 */
export const SUITES = [
  {
    key: 'server',
    dir: 'server',
    label: 'server',
    what: 'Spring Boot API — fast lane and integration lane',
    command: './gradlew verify',
    junit: ['reports/junit/test', 'reports/junit/integrationTest'],
  },
  {
    key: 'client',
    dir: 'client',
    label: 'client',
    what: 'React browser application — Vitest',
    command: 'npm test',
    junit: ['reports/junit.xml'],
  },
  {
    key: 'suite',
    dir: 'sasbo-automation-testing',
    label: 'sasbo-automation-testing',
    what: 'browser and API journeys — Playwright + Cucumber',
    command: 'npm test',
    junit: ['reports/junit.xml'],
    // 🔑 `cucumberPath`, not `cucumber`. This is a PATH; the parsed result of
    // reading it is `section.cucumberTags`. They were both called `cucumber`,
    // at two levels of the same object, and the "did not run" branch then
    // rendered `[object Object]` into its Expected list when the file existed
    // and omitted the file entirely when it did not -- having just pushed that
    // same file onto `missing` as a thing that WAS expected. Wrong in both
    // directions, in the one branch a green run never exercises, which is why
    // it was possible to write and not see.
    cucumberPath: 'reports/cucumber/report.json',
  },
]

/**
 * The directory the three checkouts sit in.
 *
 * 🔑 `SASBO_WORKSPACE` FIRST, and that is the mechanism rather than a
 * convenience. This repository forbids any file from naming a sibling
 * checkout, because a suite that reaches up into one runs only where all three
 * repositories happen to sit side by side on somebody's disk. An orchestrator
 * of three has to find them somehow, so it is TOLD -- the same shape the suite
 * uses for `SASBO_CLIENT_URL` and `SASBO_API_URL`. CI sets it.
 *
 * 🔴 UNSET, IT IS DISCOVERED BY WALKING UP -- never by counting directories,
 * and the counting version was wrong twice. From a normal checkout this file is
 * at `<workspace>/sasbo-automation-testing/tools/`, so "two levels up" is
 * right; from a git worktree it is two levels deeper and the same hop lands
 * inside `.worktrees/`. Then the walk itself matched `.worktrees/`, because
 * that directory holds a folder named after each repository -- so `.git` is
 * part of the test, which is what separates a checkout from a folder sharing
 * its name. (Worktrees carry it as a FILE; `existsSync` treats both alike.)
 *
 * ⚠️ A wrong answer is quiet in the worst way: every stage reports "no such
 * directory", the run takes seconds, the report says nothing ran, and the exit
 * code is a plausible 2. Hence the refusal rather than a guess.
 *
 * 🔑 IT LIVES HERE, BESIDE `SUITES`, so both tools resolve the workspace the
 * same way. `test-all.mjs` had the walk and this file kept the fixed hop it
 * replaced -- and since CI calls THIS file directly, the known-bad half was the
 * one CI would have used.
 */
export function findWorkspace(from = dirname(fileURLToPath(import.meta.url))) {
  if (process.env.SASBO_WORKSPACE) return resolve(process.env.SASBO_WORKSPACE)

  for (let dir = from; ; dir = dirname(dir)) {
    if (SUITES.every((s) => existsSync(join(dir, s.dir, '.git')))) return dir

    const parent = dirname(dir)
    if (parent === dir) return null // reached the filesystem root
  }
}

const ROOT = findWorkspace()

/**
 * Where ONE repository is checked out: the per-repository override if it was
 * given, otherwise the default from `SUITES`, resolved against the workspace.
 *
 * 🔑 IT LIVES HERE FOR THE SAME REASON `findWorkspace` DOES, one function above:
 * both tools have to answer this identically. `test-all.mjs` had its own copy
 * and so did this file -- `opts.dirs[s.key] || s.dir` in `main()`, then
 * `resolve(ROOT, dir)` inside `gather()`, with the override rule and the
 * resolution in two different functions.
 *
 * ⚠️ WHAT THAT COST IF IT DRIFTED, which is the argument for moving it rather
 * than for tidiness. The stages run in one directory and the merge reads
 * another, so three suites execute and the report says all three DID NOT RUN --
 * with a plausible exit `2`. That is the exact failure `findWorkspace`'s
 * docstring records happening once already, one function over, for the same
 * reason: two statements of where things are.
 *
 * `dirs` is the parsed `--server`/`--client`/`--suite` map, or `{}`.
 */
export function checkoutFor(key, dirs = {}, root = ROOT) {
  const declared = SUITES.find((suite) => suite.key === key)?.dir
  return resolve(root, dirs[key] || declared)
}

// ── A very small XML reader ───────────────────────────────────────────────────
//
// JUnit XML is simple enough not to need a parser dependency, but it is NOT
// simple enough to regex safely: `<system-out>` carries CDATA holding whole
// application logs, and those logs contain angle brackets, quotes and things
// that look exactly like tags. Skipping CDATA, comments and declarations is the
// difference between reading a report and reading a stack trace as if it were
// markup.

const ENTITIES = { lt: '<', gt: '>', amp: '&', quot: '"', apos: "'" }

function unescapeXml(s) {
  return s.replace(/&(#x?[0-9a-fA-F]+|[a-zA-Z]+);/g, (whole, body) => {
    if (body[0] === '#') {
      const code = body[1] === 'x' || body[1] === 'X'
        ? parseInt(body.slice(2), 16)
        : parseInt(body.slice(1), 10)
      return Number.isFinite(code) ? String.fromCodePoint(code) : whole
    }
    return body in ENTITIES ? ENTITIES[body] : whole
  })
}

function parseAttrs(source) {
  const attrs = {}
  const re = /([\w.:-]+)\s*=\s*("([^"]*)"|'([^']*)')/g
  let m
  while ((m = re.exec(source)) !== null) {
    attrs[m[1]] = unescapeXml(m[3] !== undefined ? m[3] : m[4])
  }
  return attrs
}

/** Yields open/close tag events, skipping CDATA, comments and declarations. */
function* tags(xml) {
  let i = 0
  while (i < xml.length) {
    const lt = xml.indexOf('<', i)
    if (lt < 0) return
    if (xml.startsWith('<![CDATA[', lt)) {
      const end = xml.indexOf(']]>', lt)
      i = end < 0 ? xml.length : end + 3
      continue
    }
    if (xml.startsWith('<!--', lt)) {
      const end = xml.indexOf('-->', lt)
      i = end < 0 ? xml.length : end + 3
      continue
    }
    if (xml.startsWith('<?', lt) || xml.startsWith('<!', lt)) {
      const end = xml.indexOf('>', lt)
      i = end < 0 ? xml.length : end + 1
      continue
    }
    // Walk to the closing '>', respecting quoted attribute values, which may
    // themselves contain '>' -- the client's test names do.
    let j = lt + 1
    let quote = null
    while (j < xml.length) {
      const c = xml[j]
      if (quote) {
        if (c === quote) quote = null
      } else if (c === '"' || c === "'") {
        quote = c
      } else if (c === '>') {
        break
      }
      j++
    }
    const raw = xml.slice(lt + 1, j)
    i = j + 1
    if (!raw) continue
    if (raw[0] === '/') {
      yield { kind: 'close', name: raw.slice(1).trim() }
      continue
    }
    const selfClosing = raw.endsWith('/')
    const body = selfClosing ? raw.slice(0, -1) : raw
    const nameMatch = /^([\w.:-]+)/.exec(body)
    if (!nameMatch) continue
    yield {
      kind: 'open',
      name: nameMatch[1],
      attrs: parseAttrs(body.slice(nameMatch[1].length)),
      selfClosing,
    }
  }
}

/**
 * Read one JUnit XML file into a flat list of test cases.
 *
 * Deliberately derives the counts from the test cases themselves rather than
 * trusting the `tests=` / `failures=` attributes on `<testsuite>`. Those are
 * written by three different tools here, and F6 in the register is a case of a
 * report that stayed green while the evidence underneath it was silently short.
 */
function readJUnitFile(file) {
  const xml = readFileSync(file, 'utf8')
  const cases = []
  let suiteName = ''
  let current = null

  for (const t of tags(xml)) {
    if (t.kind === 'open' && t.name === 'testsuite') {
      suiteName = t.attrs.name || suiteName
      continue
    }
    if (t.kind === 'open' && t.name === 'testcase') {
      current = {
        name: t.attrs.name || '(unnamed)',
        classname: t.attrs.classname || suiteName,
        suite: suiteName,
        time: Number(t.attrs.time) || 0,
        status: 'passed',
        message: '',
        properties: {},
      }
      cases.push(current)
      if (t.selfClosing) current = null
      continue
    }
    if (!current) continue
    if (t.kind === 'close' && t.name === 'testcase') {
      current = null
      continue
    }
    if (t.kind !== 'open') continue
    if (t.name === 'skipped') {
      current.status = 'skipped'
      if (t.attrs.message) current.message = t.attrs.message
    } else if (t.name === 'failure' || t.name === 'error') {
      current.status = 'failed'
      current.message = t.attrs.message || current.message
    } else if (t.name === 'property' && t.attrs.name) {
      current.properties[t.attrs.name] = t.attrs.value || ''
    }
  }
  return cases
}

/** Every `*.xml` under a directory, recursively; or the single file itself. */
function junitFilesFor(base, entry) {
  const target = join(base, entry)
  if (!existsSync(target)) return []
  const st = statSync(target)
  if (st.isFile()) return target.endsWith('.xml') ? [target] : []
  const out = []
  for (const child of readdirSync(target, { withFileTypes: true })) {
    if (child.isDirectory()) out.push(...junitFilesFor(target, child.name))
    else if (child.name.endsWith('.xml')) out.push(join(target, child.name))
  }
  return out.sort()
}

/**
 * Scenario tags, recovered from Cucumber JSON.
 *
 * Keyed both as "Feature › Scenario" -- which is how Playwright's JUnit reporter
 * names a playwright-bdd test, being `titlePath().slice(3).join(' › ')` -- and
 * by the bare scenario name, so a change to either side degrades to a partial
 * match rather than to a silent absence of tags.
 */
function readCucumberTags(file) {
  const byName = new Map()
  let features = 0
  let scenarios = 0
  try {
    const doc = JSON.parse(readFileSync(file, 'utf8'))
    for (const feature of Array.isArray(doc) ? doc : []) {
      features++
      const featureName = feature.name || ''
      for (const element of feature.elements || []) {
        scenarios++
        const tagList = (element.tags || []).map((t) => t.name).filter(Boolean)
        if (!tagList.length) continue
        const scenarioName = element.name || ''
        byName.set(`${featureName} › ${scenarioName}`, tagList)
        if (!byName.has(scenarioName)) byName.set(scenarioName, tagList)
      }
    }
  } catch (err) {
    return { byName, features, scenarios, error: String(err && err.message ? err.message : err) }
  }
  return { byName, features, scenarios, error: null }
}

function tagsFor(testCase, cucumber) {
  if (!cucumber) return null
  const direct = cucumber.byName.get(testCase.name)
  if (direct) return direct
  const parts = testCase.name.split(' › ')
  const bare = parts[parts.length - 1]
  return cucumber.byName.get(bare) || null
}

/**
 * Where a skipped test's reason comes from, in order of trustworthiness -- and
 * an explicit `null` when there is none, which is the case this exists to make
 * visible rather than to smooth over.
 */
function skipReason(testCase, tags) {
  if (testCase.message) return { source: 'message', text: testCase.message }
  if (tags && tags.length) return { source: 'tags', text: tags.join(' ') }
  const annotated = Object.entries(testCase.properties)
    .filter(([k]) => k !== 'test-id')
    .map(([k, v]) => (v ? `${k}: ${v}` : k))
  if (annotated.length) return { source: 'annotations', text: annotated.join(' · ') }
  // The client encodes the whole finding in the test name. That is a convention,
  // not a format, so it is recognised rather than parsed: the name is already
  // rendered beside the reason, so saying "in the test name" points at it.
  if (/KNOWN DEFECT|STILL REPRODUCES|defect/i.test(testCase.name)) {
    return { source: 'name', text: null }
  }
  return null
}

// ── Gathering ─────────────────────────────────────────────────────────────────

function gather(suite, dir, processExit, outOfScope = false, steps = []) {
  const abs = resolve(ROOT, dir)
  const section = {
    ...suite,
    dir: abs,
    processExit: typeof processExit === 'number' ? processExit : null,
    outOfScope,
    steps: outOfScope ? [] : steps,
    exists: existsSync(abs),
    files: [],
    missing: [],
    cases: [],
    cucumberTags: null,
    newest: 0,
    parseErrors: [],
  }

  for (const entry of suite.junit) {
    const found = junitFilesFor(abs, entry)
    if (!found.length) section.missing.push(entry)
    section.files.push(...found)
  }

  for (const file of section.files) {
    try {
      section.cases.push(...readJUnitFile(file))
      const mtime = statSync(file).mtimeMs
      if (mtime > section.newest) section.newest = mtime
    } catch (err) {
      section.parseErrors.push({ file, error: String(err && err.message ? err.message : err) })
    }
  }

  if (suite.cucumberPath) {
    const file = join(abs, suite.cucumberPath)
    if (existsSync(file)) {
      section.cucumberTags = readCucumberTags(file)
      section.cucumberFile = file
    } else {
      section.missing.push(suite.cucumberPath)
    }
  }

  // 🔴 A STAGE THE CALLER DID NOT RUN CONTRIBUTES NO NUMBERS, and forcing only
  // its STATUS was not enough -- which is this fix's own first draft, caught by
  // review.
  //
  // The counts are computed here, BEFORE the status derivation below, and the
  // roll-up reduces over every section regardless of status. So an excluded
  // stage's stale `failed` was still added to `totalFailures`: a `--only suite`
  // run with a genuinely red suite and a stale client report would print
  // "FAILED -- 7 tests failed in sasbo-automation-testing", a wrong number
  // attributed to the wrong repository, read from the very file this change
  // decided to ignore.
  //
  // 🔑 Emptied at the source rather than filtered at each of the three reduces,
  // because "ignore this section" is one fact and three filters are three places
  // to forget it.
  if (outOfScope) section.cases = []

  section.passed = section.cases.filter((c) => c.status === 'passed').length
  section.failed = section.cases.filter((c) => c.status === 'failed').length
  section.skipped = section.cases.filter((c) => c.status === 'skipped').length
  section.total = section.cases.length
  section.duration = section.cases.reduce((sum, c) => sum + c.time, 0)

  // 🔑 A COUNT EXPIRES, AND THE EXIT CODE IS PART OF THE CLAIM.
  //
  // MobiZone observed a client suite reporting 941/941 passing while the process
  // exited 1. Reading only the report would have called that a green run; reading
  // only the exit code would not have said which test. So when the caller tells
  // us what the suite's process actually returned, the two are asserted against
  // each other and a disagreement is its own loud state -- in BOTH directions,
  // because a report full of failures under a zero exit is the same bug wearing
  // the other face.
  //
  // `processExit` is null when nobody told us -- CI running the three suites as
  // separate jobs, say -- and then this check is simply not made rather than
  // being quietly assumed to pass.
  // 🔴 AND A STAGE THE CALLER DID NOT RUN IS `did-not-run` WHATEVER IS ON DISK.
  //
  // This was the one hole left in the stale-report defence, and it produced a
  // false green on 2026-09-09: a `--only suite` run reported *"client PASSED 8
  // tests"*, read from a `junit.xml` written by a different command two hours
  // earlier. `test-all.mjs` clears each stage's reports before running it, which
  // covers a stage that dies before writing one -- it cannot cover a stage that
  // was never in scope, because it never touches that directory.
  //
  // 🔑 THE SIGNAL WAS ALREADY FLOWING and only needed reading. The orchestrator
  // passes `--exit <key>:<code>` for exactly the stages it ran, so a key with no
  // `--exit` when others have one was deliberately excluded. `outOfScope` is that
  // deduction, made by the caller.
  //
  // ⚠️ AND THE CI CASE IS UNCHANGED, which is why this is not simply "trust the
  // exit codes". With NO `--exit` at all -- three separate pipeline jobs, each
  // writing its report, nobody able to hand over an exit code -- every stage is
  // in scope and the report is the only evidence there is. Then this check does
  // not fire, exactly as `processExit` being null does not fire the one below.
  if (section.outOfScope) section.status = 'did-not-run'
  else if (!section.files.length) section.status = 'did-not-run'
  else if (section.failed > 0 && section.processExit === 0) section.status = 'disagreement'
  else if (section.failed > 0) section.status = 'failed'
  else if (section.processExit !== null && section.processExit !== 0) section.status = 'disagreement'
  else if (section.missing.length || section.parseErrors.length) section.status = 'incomplete'
  else section.status = 'passed'

  // 🔴 A LANE IS MORE THAN ITS TESTS. The GitHub gate's client lane lints, tests,
  // builds and checks the bundle budget, and test-all ran only the tests: F12's
  // bundle, 670 KiB against a 650 KiB ceiling, was red on the runner and never
  // here. A failed step overrides only a status that would otherwise read as
  // clean or merely short; failing tests and a disagreement keep theirs, and
  // the steps are shown either way. `did-not-run` keeps its own explanation --
  // the verdict still counts the step (see `stepFailuresOf`).
  if (section.steps.some((s) => s.code !== 0) && (section.status === 'passed' || section.status === 'incomplete')) {
    section.status = 'step-failed'
  }

  return section
}

/** Every failed step outside the tests, as "<lane>: <step> exited <code>". */
function stepFailuresOf(sections) {
  return sections.flatMap((s) => s.steps.filter((x) => x.code !== 0).map((x) => `${s.label}: ${x.name} exited ${x.code}`))
}

// ── Rendering ─────────────────────────────────────────────────────────────────

// The map is a module constant rather than an object literal built inside the
// replacer, which allocated a fresh five-key object for every matched
// character. Negligible on a green run of short strings, and it grows with the
// failure count -- which is exactly when this report is being read.
const ESCAPES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }

const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ESCAPES[c])

const STATUS_TEXT = {
  passed: 'PASSED',
  failed: 'FAILED',
  incomplete: 'INCOMPLETE',
  disagreement: 'REPORT DISAGREES WITH EXIT CODE',
  'did-not-run': 'DID NOT RUN',
  'step-failed': 'FAILED OUTSIDE ITS TESTS',
}

// One `<dt>`/`<dd>` pair naming each step a lane ran outside its tests, or
// nothing for a lane that ran none.
function renderSteps(section) {
  if (!section.steps.length) return ''
  const each = section.steps.map((x) =>
    x.code === 0
      ? `<code>${esc(x.name)}</code> exited 0`
      : `<code>${esc(x.name)}</code> <strong class="stepfail">exited ${esc(x.code)}</strong>`,
  )
  return `<dt>Outside the tests</dt><dd>${each.join(' · ')}</dd>`
}

function fmtDuration(seconds) {
  if (!seconds) return '—'
  if (seconds < 60) return `${seconds.toFixed(1)}s`
  const m = Math.floor(seconds / 60)
  return `${m}m ${Math.round(seconds - m * 60)}s`
}

function fmtTime(ms) {
  if (!ms || !Number.isFinite(ms)) return '—'
  return new Date(ms).toISOString().replace('T', ' ').replace(/\.\d+Z$/, 'Z')
}

function renderSkipped(section) {
  const skipped = section.cases.filter((c) => c.status === 'skipped')
  if (!skipped.length) return '<p class="none">No skipped tests.</p>'

  // Resolved once per test and then used twice -- for the row and for the
  // count in the warning above it. Computing it twice invites the two halves
  // to disagree after an edit to only one of them.
  const resolved = skipped.map((c) => ({ test: c, reason: skipReason(c, tagsFor(c, section.cucumberTags)) }))

  const rows = resolved
    .map(({ test: c, reason }) => {
      let reasonCell
      if (!reason) {
        reasonCell =
          '<span class="norsn">reason not carried in this suite&#39;s report</span>'
      } else if (reason.source === 'name') {
        reasonCell = '<span class="insitu">in the test name, left</span>'
      } else {
        reasonCell = `<span class="rsn">${esc(reason.text)}</span>`
      }
      return `<tr>
        <td class="tname">${esc(c.name)}<div class="cls">${esc(c.classname)}</div></td>
        <td class="rcell">${reasonCell}</td>
      </tr>`
    })
    .join('\n')

  const withoutReason = resolved.filter((r) => !r.reason).length
  const warning = withoutReason
    ? `<p class="warn"><strong>${withoutReason} of ${skipped.length} skipped tests carry no reason in any artifact this merge can read.</strong>
       A skip with no reason is indistinguishable from a skip that never had one. See <code>F10</code> in the defect register.</p>`
    : ''

  return `${warning}
    <table class="tests">
      <thead><tr><th>Skipped test</th><th>Reason, and where it came from</th></tr></thead>
      <tbody>${rows}</tbody>
    </table>`
}

function renderFailures(section) {
  const failures = section.cases.filter((c) => c.status === 'failed')
  if (!failures.length) return ''
  const rows = failures
    .map(
      (c) => `<tr>
        <td class="repo-tag">${esc(section.label)}</td>
        <td class="tname">${esc(c.name)}<div class="cls">${esc(c.classname)}</div></td>
        <td class="msg">${esc(c.message || '(no message in the report)')}</td>
      </tr>`,
    )
    .join('\n')
  return `<h3 class="fail-h">Failures (${failures.length})</h3>
    <table class="tests fail">
      <thead><tr><th>Repo</th><th>Test</th><th>Message</th></tr></thead>
      <tbody>${rows}</tbody>
    </table>`
}

function renderSection(section) {
  const badge = `<span class="badge ${section.status}">${STATUS_TEXT[section.status]}</span>`

  if (section.status === 'did-not-run') {
    return `<section id="${section.key}">
      <h2>${esc(section.label)} ${badge}</h2>
      <p class="what">${esc(section.what)}</p>
      <div class="didnotrun">
        <p><strong>${
          // 🔑 THE TWO REASONS, SAID APART -- and this is the artifact a
          // stakeholder actually opens, so the distinction matters more here than
          // in the console line beside it. "Its report was not found" sends a
          // reader hunting for a missing file. For an excluded stage the file may
          // be sitting right there, green, deliberately unread -- and somebody who
          // opens it deserves to know why rather than concluding the merge is
          // broken.
          section.outOfScope
            ? 'This suite was NOT PART OF THIS RUN, so nothing here reports on it.'
            : 'This suite did not run, or its report was not found.'
        }</strong></p>
        <p>Nothing below is a statement about whether this repository&#39;s tests pass. Omitting the
           section entirely would have read identically to &ldquo;nothing was wrong&rdquo;, which is why
           it is here saying so.</p>
        ${
          section.outOfScope
            ? `<p class="warn">A report may well exist in that directory. It is from an EARLIER run and
               is deliberately ignored: reading it would present another run&#39;s result as this
               one&#39;s, which is the failure this section exists to prevent.</p>`
            : ''
        }
        <dl>
          <dt>Looked in</dt><dd><code>${esc(section.dir)}</code>${section.exists ? '' : ' <em>(directory does not exist)</em>'}</dd>
          <dt>Expected</dt><dd>${section.junit.concat(section.cucumberPath ? [section.cucumberPath] : []).map((p) => `<code>${esc(p)}</code>`).join(', ')}</dd>
          <dt>Produced by</dt><dd><code>${esc(section.command)}</code>, run in that directory</dd>
          ${renderSteps(section)}
        </dl>
      </div>
    </section>`
  }

  const disagreementNote =
    section.status === 'disagreement'
      ? `<p class="warn danger"><strong>This suite&#39;s report and its exit code do not agree.</strong>
         The process returned <code>${section.processExit}</code> while the report shows
         <strong>${section.failed} failed</strong> of ${section.total}.
         ${section.failed === 0
           ? `A suite can exit non-zero with every test passing — an unhandled rejection after the run, a
              reporter that could not write, a lint or coverage gate outside the test list. The tests are
              not the whole claim, so this is <strong>not</strong> a green run.`
           : `A suite can exit zero with tests failing, which is worse: every automated reader downstream
              believes it passed.`}
         Neither number is trusted over the other here; both are shown because the disagreement is the finding.</p>`
      : ''

  const stepNote =
    section.status === 'step-failed'
      ? `<p class="warn danger"><strong>No test failed here, and the lane still failed</strong> on a command
         outside its tests: ${stepFailuresOf([section]).map(esc).join('; ')}. The GitHub gate runs the same
         commands and fails the lane the same way, so this is <strong>not</strong> a green run.</p>`
      : ''

  const exitRow =
    section.processExit === null
      ? ''
      : `<dt>Process exit code</dt><dd><code>${section.processExit}</code>${
          section.status === 'disagreement' ? ' <strong>— disagrees with the counts above</strong>' : ''
        }</dd>`

  const incompleteNote = section.missing.length
    ? `<p class="warn"><strong>Incomplete:</strong> expected ${section.missing
        .map((p) => `<code>${esc(p)}</code>`)
        .join(', ')} and did not find it. The counts below are what was found, not necessarily what ran.</p>`
    : ''

  const parseNote = section.parseErrors.length
    ? `<p class="warn"><strong>${section.parseErrors.length} report file(s) could not be read:</strong>
       ${section.parseErrors.map((e) => `<code>${esc(relative(section.dir, e.file))}</code> — ${esc(e.error)}`).join('; ')}</p>`
    : ''

  const cuke = section.cucumberTags
    ? `<dt>Scenario tags</dt><dd>${section.cucumberTags.error
        ? `<span class="norsn">could not be read — ${esc(section.cucumberTags.error)}</span>`
        : `${section.cucumberTags.scenarios} scenarios across ${section.cucumberTags.features} features, from <code>${esc(relative(section.dir, section.cucumberFile))}</code>`}</dd>`
    : ''

  return `<section id="${section.key}">
    <h2>${esc(section.label)} ${badge}</h2>
    <p class="what">${esc(section.what)}</p>
    ${disagreementNote}
    ${stepNote}
    ${incompleteNote}
    ${parseNote}
    <div class="counts">
      <div class="c"><b>${section.total}</b><span>tests</span></div>
      <div class="c pass"><b>${section.passed}</b><span>passed</span></div>
      <div class="c ${section.failed ? 'failnum' : ''}"><b>${section.failed}</b><span>failed</span></div>
      <div class="c skip"><b>${section.skipped}</b><span>skipped</span></div>
      <div class="c"><b>${fmtDuration(section.duration)}</b><span>test time</span></div>
    </div>
    <dl class="meta">
      <dt>Command</dt><dd><code>${esc(section.command)}</code></dd>
      <dt>Read from</dt><dd><code>${esc(section.dir)}</code></dd>
      <dt>Evidence</dt><dd>${section.files.length} JUnit XML file(s), written ${esc(fmtTime(section.newest))}</dd>
      ${exitRow}
      ${renderSteps(section)}
      ${cuke}
    </dl>
    ${renderFailures(section)}
    <h3>Skipped (${section.skipped})</h3>
    ${renderSkipped(section)}
  </section>`
}

const CSS = `
:root{--bg:#fbfbfa;--fg:#1c1c1a;--mut:#6b6b66;--line:#e2e2dd;--card:#fff;
--pass:#1f7a4d;--fail:#b3261e;--skip:#8a6d1f;--none:#6b6b66;--accent:#2f4f8f}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--fg);
font:15px/1.55 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif}
.wrap{max-width:1080px;margin:0 auto;padding:32px 24px 72px}
h1{font-size:26px;margin:0 0 4px}
h2{font-size:20px;margin:0 0 2px;display:flex;align-items:center;gap:10px;flex-wrap:wrap}
h3{font-size:15px;margin:22px 0 8px}
code{font-family:ui-monospace,SFMono-Regular,Consolas,monospace;font-size:.9em;
background:rgba(0,0,0,.05);padding:1px 5px;border-radius:3px}
.sub{color:var(--mut);margin:0 0 24px}
.verdict{border:1px solid var(--line);border-left-width:5px;background:var(--card);
border-radius:6px;padding:16px 18px;margin:0 0 26px}
.verdict.ok{border-left-color:var(--pass)}
.verdict.bad{border-left-color:var(--fail)}
.verdict h2{font-size:17px;margin:0 0 6px}
.verdict p{margin:6px 0 0;color:var(--mut)}
.badge{font-size:11px;font-weight:700;letter-spacing:.07em;padding:3px 9px;border-radius:999px;
border:1px solid currentColor}
.badge.passed{color:var(--pass)}
.badge.failed{color:var(--fail)}
.badge.incomplete{color:var(--skip)}
.badge[class~="did-not-run"]{color:var(--none)}
table.summary{width:100%;border-collapse:collapse;margin:0 0 30px;background:var(--card);
border:1px solid var(--line);border-radius:6px;overflow:hidden}
table.summary th,table.summary td{text-align:left;padding:10px 12px;border-bottom:1px solid var(--line)}
table.summary th{font-size:12px;text-transform:uppercase;letter-spacing:.05em;color:var(--mut);
background:rgba(0,0,0,.02)}
table.summary tr:last-child td{border-bottom:none}
table.summary td.num{text-align:right;font-variant-numeric:tabular-nums}
section{background:var(--card);border:1px solid var(--line);border-radius:6px;
padding:20px 22px;margin:0 0 20px}
.what{color:var(--mut);margin:0 0 14px;font-size:13px}
.counts{display:flex;gap:10px;flex-wrap:wrap;margin:0 0 14px}
.counts .c{border:1px solid var(--line);border-radius:5px;padding:8px 14px;min-width:82px}
.counts .c b{display:block;font-size:19px;font-variant-numeric:tabular-nums}
.counts .c span{font-size:11px;color:var(--mut);text-transform:uppercase;letter-spacing:.05em}
.counts .c.pass b{color:var(--pass)}
.counts .c.failnum b{color:var(--fail)}
.counts .c.skip b{color:var(--skip)}
dl.meta{display:grid;grid-template-columns:auto 1fr;gap:4px 16px;margin:0;font-size:13px}
dl.meta dt{color:var(--mut)}
dl.meta dd{margin:0;word-break:break-word}
table.tests{width:100%;border-collapse:collapse;font-size:13px;margin:6px 0 0}
table.tests th{text-align:left;font-size:11px;text-transform:uppercase;letter-spacing:.05em;
color:var(--mut);padding:6px 8px;border-bottom:1px solid var(--line)}
table.tests td{padding:8px;border-bottom:1px solid var(--line);vertical-align:top}
table.tests tr:last-child td{border-bottom:none}
td.tname{width:58%}
td.cls,div.cls{color:var(--mut);font-size:11.5px;margin-top:2px;
font-family:ui-monospace,SFMono-Regular,Consolas,monospace}
td.repo-tag{font-weight:600;white-space:nowrap;color:var(--accent)}
td.msg{color:var(--fail);font-family:ui-monospace,SFMono-Regular,Consolas,monospace;font-size:12px}
.fail-h{color:var(--fail)}
.rsn{font-family:ui-monospace,SFMono-Regular,Consolas,monospace;font-size:12px;color:var(--skip)}
.norsn{color:var(--fail);font-style:italic}
.insitu{color:var(--mut);font-style:italic}
.badge.disagreement{color:var(--fail)}
.badge.step-failed,.stepfail{color:var(--fail)}
.warn{background:#fdf6e3;border:1px solid #e8d9a8;border-radius:5px;padding:10px 12px;
font-size:13px;margin:0 0 14px}
.warn.danger{background:#fdeceb;border-color:#efc0bb}
.didnotrun{background:#f4f4f2;border:1px dashed #c9c9c2;border-radius:5px;padding:14px 16px}
.didnotrun p{margin:0 0 8px}
.didnotrun dl{display:grid;grid-template-columns:auto 1fr;gap:4px 16px;margin:10px 0 0;font-size:13px}
.didnotrun dt{color:var(--mut)}
.didnotrun dd{margin:0;word-break:break-word}
.none{color:var(--mut);font-size:13px;margin:6px 0 0}
footer{color:var(--mut);font-size:12.5px;margin-top:34px;border-top:1px solid var(--line);padding-top:16px}
footer p{margin:0 0 8px}
@media (prefers-color-scheme:dark){
:root{--bg:#16161a;--fg:#e8e8e4;--mut:#9a9a94;--line:#2e2e34;--card:#1d1d22;
--pass:#5fbf8c;--fail:#f08a80;--skip:#d9b866;--none:#9a9a94;--accent:#8fa9dd}
code{background:rgba(255,255,255,.07)}
table.summary th{background:rgba(255,255,255,.03)}
.warn{background:#2a2515;border-color:#4d4326}
.warn.danger{background:#2e1a18;border-color:#5c302b}
.didnotrun{background:#212127;border-color:#3d3d45}
}
`

function renderReport(sections, verdict) {
  const generated = new Date().toISOString().replace('T', ' ').replace(/\.\d+Z$/, ' UTC')

  const ran = sections.filter((s) => s.status !== 'did-not-run')
  const spread =
    ran.length > 1 ? Math.max(...ran.map((s) => s.newest)) - Math.min(...ran.map((s) => s.newest)) : 0
  const staleWarning =
    spread > 2 * 60 * 60 * 1000
      ? `<p class="warn"><strong>These sections are not all from the same run.</strong> Their newest report files
         are ${fmtDuration(spread / 1000)} apart. A section whose artifacts are left over from an earlier run
         reports that run&#39;s result as if it were this one&#39;s — read the per-section timestamps before
         trusting a green.</p>`
      : ''

  const summaryRows = sections
    .map(
      (s) => `<tr>
        <td><a href="#${s.key}"><strong>${esc(s.label)}</strong></a><div class="cls">${esc(s.what)}</div></td>
        <td><span class="badge ${s.status}">${STATUS_TEXT[s.status]}</span></td>
        <td class="num">${s.status === 'did-not-run' ? '—' : s.total}</td>
        <td class="num">${s.status === 'did-not-run' ? '—' : s.passed}</td>
        <td class="num">${s.status === 'did-not-run' ? '—' : s.failed}</td>
        <td class="num">${s.status === 'did-not-run' ? '—' : s.skipped}</td>
        <td class="num">${s.status === 'did-not-run' ? '—' : fmtDuration(s.duration)}</td>
      </tr>`,
    )
    .join('\n')

  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>SASBO Online — merged test report</title>
<style>${CSS}</style>
</head>
<body>
<div class="wrap">
  <h1>SASBO Online — merged test report</h1>
  <p class="sub">Three independent suites, three repositories, one document. Generated ${esc(generated)}.</p>

  <div class="verdict ${verdict.code === 0 ? 'ok' : 'bad'}">
    <h2>${esc(verdict.headline)}</h2>
    <p>${verdict.detail}</p>
    <p>Process exit code: <code>${verdict.code}</code> — <code>0</code> every suite ran and passed,
       <code>1</code> a test failed, <code>2</code> a suite did not run or is missing artifacts.</p>
  </div>

  ${staleWarning}

  <table class="summary">
    <thead><tr><th>Repository</th><th>Result</th><th>Tests</th><th>Passed</th><th>Failed</th><th>Skipped</th><th>Time</th></tr></thead>
    <tbody>${summaryRows}</tbody>
  </table>

  ${sections.map(renderSection).join('\n')}

  <footer>
    <p><strong>Every section is labelled by repository, including every individual failure row.</strong>
       An unlabelled failing scenario sends the reader to the wrong codebase, which is this merge&#39;s
       only real failure mode.</p>
    <p><strong>A suite that did not run says so.</strong> Omitting its section would read identically to
       &ldquo;nothing was wrong&rdquo;.</p>
    <p>Each suite also runs standalone in a fresh clone of its own repository. This merge is a
       convenience over three independent suites, never a dependency of any — nothing here is required
       for any one of them to run.</p>
  </footer>
</div>
</body>
</html>
`
}

// ── Main ──────────────────────────────────────────────────────────────────────

function parseArgv(argv) {
  const opts = { out: 'reports/merged', dirs: {}, exits: {}, only: [], steps: {} }
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    if (a === '--out') opts.out = argv[++i]
    else if (a === '--help' || a === '-h') opts.help = true
    else if (a === '--only') {
      // 🔴 WHICH STAGES THE CALLER ACTUALLY RAN, stated rather than deduced --
      // and the first draft of this deduced it, which review caught as this
      // bug's own class mirrored.
      //
      // That draft read "no `--exit` for this key while others have one" as
      // "excluded". It happens to be true today, because `test-all.mjs` records
      // an exit code for every stage it starts, including `127` for a directory
      // that is not there. But the invariant lives in ANOTHER FILE: the day that
      // orchestrator gains a fail-fast `break`, or a stage throws before its code
      // is recorded, every remaining stage would be reported as *"excluded from
      // this invocation"* -- a stage that ran and produced nothing, labelled as
      // one nobody asked to run. Exactly the confusion this change exists to end.
      //
      // ⚠️ `--exit` and `--only` are ORTHOGONAL and are now encoded that way. A
      // stage can be in scope with no exit code -- which is precisely the CI
      // shape, and which the previous encoding could not express at all.
      //
      // 🔑 NO `--only` AT ALL MEANS EVERYTHING IS IN SCOPE. Three pipeline jobs,
      // each writing its own report, nobody able to hand over an exit code: then
      // the reports are the only evidence there is and every one of them stands.
      const key = String(argv[++i] || '')
      if (!SUITES.some((s) => s.key === key)) {
        process.stderr.write(`merge-reports: --only expects <server|client|suite>\n`)
        process.exit(2)
      }
      opts.only.push(key)
    } else if (a === '--exit') {
      // --exit <key>:<code>, e.g. --exit client:1. Optional: supplied by a
      // caller that ran the suites and therefore knows what they returned.
      const [key, code] = String(argv[++i] || '').split(':')
      if (!SUITES.some((s) => s.key === key) || !/^-?\d+$/.test(code || '')) {
        process.stderr.write(`merge-reports: --exit expects <server|client|suite>:<number>\n`)
        process.exit(2)
      }
      opts.exits[key] = Number(code)
    } else if (a === '--step') {
      // --step <key>:<name>:<code>, e.g. --step client:budget:1: a command the
      // caller ran for that lane outside its tests, and what it returned. In the
      // order given, which is the order they ran.
      const m = /^([a-z]+):([a-z][a-z0-9-]*):(-?\d+)$/.exec(String(argv[++i] || ''))
      if (!m || !SUITES.some((s) => s.key === m[1])) {
        process.stderr.write(`merge-reports: --step expects <server|client|suite>:<name>:<number>\n`)
        process.exit(2)
      }
      ;(opts.steps[m[1]] ||= []).push({ name: m[2], code: Number(m[3]) })
    } else if (a === '--contract') {
      // --contract <clean|drift>: what the caller's contract-artifact comparison
      // found. Recorded in result.json and nowhere else -- this script does not
      // compare the artifacts itself, and it does not fold the answer into its
      // exit code (the caller does, as test-all.mjs always has).
      const state = String(argv[++i] || '')
      if (state !== 'clean' && state !== 'drift') {
        process.stderr.write(`merge-reports: --contract expects clean or drift\n`)
        process.exit(2)
      }
      opts.contract = state
    } else if (a.startsWith('--')) {
      const key = a.slice(2)
      if (SUITES.some((s) => s.key === key)) opts.dirs[key] = argv[++i]
      else {
        process.stderr.write(`merge-reports: unknown option ${a}\n`)
        process.exit(2)
      }
    }
  }
  return opts
}

/** How many failing tests `result.json` names, and how much of each message it keeps. */
export const FAILURES_NAMED = 10
export const MESSAGE_EXCERPT_CHARS = 240

/**
 * The failing tests `result.json` names by name -- the cause, for the first
 * screen of whatever reads the verdict.
 *
 * 🔴 THE HEADLINE NAMES THE LANE, NEVER THE TEST. The deploy arc's runner proof
 * R6-5a (2026-09-21) failed one client test on purpose: the gate said "client"
 * and the test's name was only in `index.html` and vitest's log, one download
 * away from the run's first screen.
 *
 * 🔑 EVERY RED SECTION IS NAMED BEFORE ANY SECTION IS NAMED TWICE. Cut at the
 * cap in table order, forty server failures would fill the list and a client
 * that also failed would not appear in it. Slots go round the sections in turn;
 * the rows then come out in the failures table's own order -- section, then
 * report -- so the list reads like the report it summarises.
 *
 * ⚠️ A LANE CAN BE RED WITH NOTHING HERE. Only failing test CASES are listed:
 * lint, a crash before the report, or an exit that disagrees with a clean report
 * reddens a section without one. Each section's `status` and `processExit` say
 * that; an empty list never means green. `failuresTotal` (in `resultOf`) says how
 * many exist, so a reader can say "and N more" rather than imply a whole list.
 *
 * The message is an excerpt -- whitespace folded to single spaces, because
 * AssertJ's begin with a newline, and cut at `MESSAGE_EXCERPT_CHARS` code points.
 * The whole one is in `index.html`.
 */
export function failuresOf(sections, cap = FAILURES_NAMED) {
  const failing = sections.map((s) => s.cases.filter((c) => c.status === 'failed'))
  const quota = failing.map(() => 0)
  for (let left = cap, grew = true; left > 0 && grew; ) {
    grew = false
    for (let i = 0; i < failing.length && left > 0; i++) {
      if (quota[i] < failing[i].length) {
        quota[i] += 1
        left -= 1
        grew = true
      }
    }
  }
  return sections.flatMap((s, i) =>
    failing[i].slice(0, quota[i]).map((c) => ({
      key: s.key,
      label: s.label,
      name: c.name,
      classname: c.classname,
      message: excerpt(c.message),
    })),
  )
}

function excerpt(message) {
  const points = Array.from(String(message || '').replace(/\s+/g, ' ').trim())
  return points.length > MESSAGE_EXCERPT_CHARS
    ? `${points.slice(0, MESSAGE_EXCERPT_CHARS - 1).join('')}…`
    : points.join('')
}

/**
 * The verdict as data -- `result.json`, written beside `index.html`.
 *
 * 🔑 WHY A MACHINE HAS TO BE ABLE TO READ THIS WITHOUT PARSING PROSE. The exit
 * code answers one question -- did everything run and pass -- and a partial run
 * answers it "no" (2) by design, because a partial run is never the gate. But a
 * pipeline that CHOSE its lanes needs the other question answered: did every
 * lane it asked for run and pass? That lives in the section statuses, which were
 * only ever printed as sentences. The deploy arc's GitHub gate (P6, 2026-09-21)
 * needs it to conclude a lane-scoped run as green without reading the words.
 *
 * `inScopePassed` is that answer. `verdict.code` stays exactly the exit code.
 * `contract` is what the caller's artifact comparison found (`clean`, `drift`),
 * or `null` when no caller said -- this script never compares them itself, and
 * a drift is its own flag here so a reader can say "drift" rather than "failed".
 * `failures` names the first failing tests (see `failuresOf`); `failuresTotal` is
 * every failing test, the sum of the sections' `failed`.
 */
export function resultOf(sections, verdict, contract = null) {
  const inScope = sections.filter((s) => !s.outOfScope)
  return {
    schema: 1,
    // `suites` here; `test-all --contract-only` writes the same shape with `contract`.
    scope: 'suites',
    verdict: { code: verdict.code, headline: verdict.headline },
    inScopePassed: inScope.length > 0 && inScope.every((s) => s.status === 'passed'),
    contract: contract ?? null,
    // Which artifact drifted is `test-all --contract-only`'s to say; this script
    // is told the state alone. The key is here so both writers share one shape.
    contractArtifacts: null,
    failuresTotal: sections.reduce((n, s) => n + s.failed, 0),
    failures: failuresOf(sections),
    sections: sections.map((s) => ({
      key: s.key,
      label: s.label,
      status: s.status,
      outOfScope: s.outOfScope,
      processExit: s.processExit,
      total: s.total,
      passed: s.passed,
      failed: s.failed,
      skipped: s.skipped,
      // What the caller ran outside the tests, [{name, code}] in run order; empty
      // for a lane that ran none, or one out of scope.
      steps: s.steps,
    })),
  }
}

function main() {
  const opts = parseArgv(process.argv.slice(2))
  if (opts.help) {
    process.stdout.write(
      'Usage: node tools/merge-reports.mjs [--server <dir>] [--client <dir>] [--suite <dir>]\n' +
        '                             [--exit <key>:<code>]... [--only <key>]...\n' +
        '                             [--step <key>:<name>:<code>]...\n' +
        '                             [--contract clean|drift] [--out <dir>]\n' +
        'Writes index.html and result.json into --out.\n',
    )
    return 0
  }

  // 🔴 REFUSED, NOT CRASHED. With no workspace found and none named, `ROOT` is
  // null and the first `resolve(ROOT, ...)` threw a TypeError: exit 1, which
  // reads as "a test failed", and no report. `test-all` refuses the same case
  // plainly; so does this, with the parser's own code for a call it cannot serve.
  if (!ROOT) {
    process.stderr.write(
      `merge-reports: could not find the directory holding ${SUITES.map((s) => s.dir).join(', ')}.\n` +
        `Searched upwards from ${dirname(fileURLToPath(import.meta.url))}. Set SASBO_WORKSPACE to point at it.\n`,
    )
    return 2
  }

  const sections = SUITES.map((s) =>
    // Out of scope when the caller named the stages it ran and this is not one of
    // them. No `--only` at all means everything is in scope -- see `--only`'s own
    // note in the parser for why this is passed rather than inferred.
    gather(
      s,
      checkoutFor(s.key, opts.dirs),
      opts.exits[s.key],
      opts.only.length > 0 && !opts.only.includes(s.key),
      opts.steps[s.key] || [],
    ),
  )

  const disagreeing = sections.filter((s) => s.status === 'disagreement')
  const failedSuites = sections.filter((s) => s.status === 'failed')
  const absentSuites = sections.filter((s) => s.status === 'did-not-run' || s.status === 'incomplete')
  const totalFailures = sections.reduce((n, s) => n + s.failed, 0)
  // Counted from every section, including one that did not report: a lint that
  // failed is a failure whether or not the tests after it left a file.
  const stepFailures = stepFailuresOf(sections)

  let verdict
  if (disagreeing.length) {
    verdict = {
      code: 1,
      headline: `FAILED — ${disagreeing
        .map((s) => s.label)
        .join(', ')}: the report and the exit code disagree${stepFailures.length ? `; ${stepFailures.join('; ')}` : ''}`,
      detail: `A suite's counts and its exit code are two halves of one claim, and here they contradict
               each other. That is treated as a failure rather than resolved in either direction.`,
    }
  } else if (failedSuites.length || stepFailures.length) {
    const tests = failedSuites.length
      ? [`${totalFailures} test${totalFailures === 1 ? '' : 's'} failed in ${failedSuites.map((s) => s.label).join(', ')}`]
      : []
    verdict = {
      code: 1,
      headline: `FAILED — ${[...tests, ...stepFailures].join('; ')}`,
      detail: failedSuites.length
        ? `The failing repository is named here and beside every failure row below, so the next
           person to look opens the right codebase.`
        : `No test failed, and a lane still did: a command it runs outside its tests — lint, the build,
           the bundle budget — returned non-zero. The GitHub gate runs the same commands.`,
    }
  } else if (absentSuites.length) {
    verdict = {
      code: 2,
      headline: `INCOMPLETE — ${absentSuites.map((s) => s.label).join(', ')} did not report`,
      detail: `No test failed in what was found, but this is <strong>not</strong> a green run: at least one
               suite produced no evidence at all. A missing section is not the same as a passing one.`,
    }
  } else {
    const total = sections.reduce((n, s) => n + s.total, 0)
    const skipped = sections.reduce((n, s) => n + s.skipped, 0)
    verdict = {
      code: 0,
      headline: `PASSED — ${total} tests across three repositories, ${skipped} skipped by design`,
      detail: `Every configured suite produced a report and every test in it passed. The skipped tests are
               the arc's deliberate assertions of known defects; each is listed with its reason below.`,
    }
  }

  // 🔑 The report is written BEFORE the exit code is applied, so a failing run
  // still produces the document a failing run is exactly when you need.
  const outDir = resolve(ROOT, opts.out)
  mkdirSync(outDir, { recursive: true })
  const outFile = join(outDir, 'index.html')
  writeFileSync(outFile, renderReport(sections, verdict), 'utf8')
  writeFileSync(join(outDir, 'result.json'), `${JSON.stringify(resultOf(sections, verdict, opts.contract), null, 2)}\n`, 'utf8')

  // Padded to the widest label that could appear rather than to a constant, so
  // the columns stay lined up when the long "disagrees" status shows up.
  const width = Math.max(...sections.map((s) => STATUS_TEXT[s.status].length))
  for (const s of sections) {
    const steps = s.steps.length ? `; outside the tests: ${s.steps.map((x) => `${x.name} ${x.code}`).join(', ')}` : ''
    const line =
      s.status === 'did-not-run'
        ? // Two different reasons, said apart. "No report found" sends the reader
          // looking for a missing file; for an excluded stage the file may be
          // sitting right there and is deliberately not being read, and a reader
          // who opens it and finds a green report deserves to know why it was
          // ignored rather than concluding the merge is broken.
          `${s.label.padEnd(26)} DID NOT RUN — ${
            s.outOfScope
              ? 'excluded from this invocation; any report under it is from an earlier run'
              : `no report found under ${s.dir}`
          }`
        : `${s.label.padEnd(26)} ${STATUS_TEXT[s.status].padEnd(width)} ${s.total} tests, ${s.passed} passed, ${s.failed} failed, ${s.skipped} skipped` +
          (s.processExit === null ? '' : `, process exited ${s.processExit}`)
    process.stdout.write(`${line}${steps}\n`)
  }
  process.stdout.write(`\n${verdict.headline}\nReport: ${outFile}\n`)
  return verdict.code
}

// 🔑 ONLY WHEN RUN AS A PROGRAM. `tools/test-all.mjs` imports `SUITES` from
// here so that the three repositories -- their keys, their directories and the
// one documented command for each -- are declared ONCE. Without this guard that
// import would run the whole merge as a side effect.
//
// CI still invokes this file directly and is unaffected.
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.exit(main())
}
