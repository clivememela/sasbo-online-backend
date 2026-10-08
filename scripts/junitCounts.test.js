import { describe, expect, it } from 'vitest'

import { junitCounts } from './lib/junitCounts.mjs'

/**
 * The counter behind the discovery floor, tested against reports that lie.
 *
 * 🔴 WHY THIS FILE EXISTS. `scripts/suite-floor.mjs` is the only thing that can
 * notice register row F11 -- a run whose workers did not all start, so tests
 * were ABSENT rather than failing and the total was quietly short. Nothing
 * running INSIDE a short run can see that it was short.
 *
 * That makes this counter load-bearing, and until now it was the one guard in
 * this repository with no test of its own: it lived inline in a script whose
 * first act is to spawn the whole suite, so a test could not reach it without
 * running the suite inside itself. Extracting it is what made these possible.
 *
 * Every case below is a report that would fool a naive reader.
 */
describe('junitCounts', () => {
  it('counts elements rather than believing the attributes', () => {
    // 🔑 THE CASE THE WHOLE DESIGN IS FOR. This report CLAIMS 400 tests and
    // contains two. Attributes are written by the reporter; elements are
    // written by the run. When they disagree, the elements are the run.
    const xml = `<testsuites>
      <testsuite name="a" tests="400" failures="0" skipped="0">
        <testcase name="one"/>
        <testcase name="two"/>
      </testsuite>
    </testsuites>`

    expect(junitCounts(xml)).toEqual({ suites: 1, total: 2, failures: 0, skipped: 0 })
  })

  it('does not count a logged line that looks like a testcase', () => {
    // The suite logs request URLs and, on failure, whole response bodies. A
    // logged string containing "<testcase" would inflate the total, and an
    // inflated total is the direction that HIDES a short run -- it is what
    // pushes a run that lost 62 tests back over the floor.
    const xml = `<testsuites>
      <testsuite name="a" tests="1">
        <testcase name="real"/>
        <system-out><![CDATA[
          responded with <testcase name="not a test"/> and
          <failure message="also not real"/> and <skipped/>
        ]]></system-out>
      </testsuite>
    </testsuites>`

    expect(junitCounts(xml)).toEqual({ suites: 1, total: 1, failures: 0, skipped: 0 })
  })

  it('counts failures and errors together, and skips separately', () => {
    // `<error>` is what a reporter writes when a test did not fail so much as
    // fall over -- a worker dying mid-file produces these. Counting only
    // `<failure>` would let exactly the F11 shape through.
    const xml = `<testsuites>
      <testsuite name="a">
        <testcase name="p"/>
        <testcase name="f"><failure message="x"/></testcase>
        <testcase name="e"><error message="worker exited"/></testcase>
        <testcase name="s"><skipped/></testcase>
      </testsuite>
    </testsuites>`

    expect(junitCounts(xml)).toEqual({ suites: 1, total: 4, failures: 2, skipped: 1 })
  })

  it('counts across several suite files', () => {
    const xml = `<testsuites>
      <testsuite name="a"><testcase name="1"/></testsuite>
      <testsuite name="b"><testcase name="2"/><testcase name="3"/></testsuite>
    </testsuites>`

    expect(junitCounts(xml)).toEqual({ suites: 2, total: 3, failures: 0, skipped: 0 })
  })

  it('returns zeroes for a report with no tests, rather than throwing', () => {
    // 🔑 IT MUST NOT THROW HERE. A report the runner produced before it died is
    // well-formed and empty, and the caller's job is to compare the total
    // against the floor and fail LOUDLY with that number. A crash in the
    // counter would fail too, but with a stack trace about regular expressions
    // instead of "the suite discovered 0 tests and the floor is 260".
    expect(junitCounts('<testsuites/>')).toEqual({
      suites: 0,
      total: 0,
      failures: 0,
      skipped: 0,
    })
  })

  it('is not fooled by a SIBLING element that shares a counted prefix', () => {
    // The `\b` in each pattern is what makes this hold, and this is the case
    // that actually discriminates it. The `<testsuites>` wrapper in every other
    // test here does not: a reader assumes it does, but `\b` matches at the
    // `s`, so a broken pattern would have to be broken differently to be caught
    // by those. A reporter that adds `<testsuite-summary>` or
    // `<testcase-metadata>` -- neither is exotic -- would inflate both counts,
    // and an inflated total is the direction that HIDES a short run.
    const xml = `<testsuites>
      <testsuite name="a">
        <testcase name="1"/>
        <testcase-metadata owner="someone"/>
      </testsuite>
      <testsuite-summary total="99"/>
    </testsuites>`

    expect(junitCounts(xml)).toEqual({ suites: 1, total: 1, failures: 0, skipped: 0 })
  })
})
