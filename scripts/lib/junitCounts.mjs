/**
 * Count what a JUnit XML report actually contains.
 *
 * WHY THIS IS ITS OWN MODULE
 * It was inline in `scripts/suite-floor.mjs`, which made it untestable: that
 * script's first act is to spawn the whole suite, so nothing inside the suite
 * could exercise this without running itself. Extracted, the counting is a pure
 * function of a string and `scripts/junitCounts.test.js` can put deliberately
 * broken XML through it.
 *
 * 🔑 THAT MATTERS MORE HERE THAN IT USUALLY WOULD. This function is the
 * mechanism behind register row F11, where a run reported fewer tests than it
 * had because some workers never started. A counting bug here does not produce
 * a wrong number in a log; it produces a short run that reads as a complete
 * one. The numbers live in the register and in the `maxWorkers` comment in
 * `vite.config.js`, where the other half of that fix is -- not here.
 */

/**
 * @param {string} xml raw JUnit XML
 * @returns {{suites: number, total: number, failures: number, skipped: number}}
 */
export function junitCounts(xml) {
  // 🔑 COUNT THE ELEMENTS, DO NOT TRUST THE ATTRIBUTES.
  //
  // This used to read `tests=` / `failures=` / `skipped=` off each
  // <testsuite>. The workspace's `merge-reports.mjs` documents at length why it
  // deliberately does not: "Those are written by three different tools here,
  // and F6 in the register is a case of a report that stayed green while the
  // evidence underneath it was silently short." Two readers of the same
  // artifact class, disagreeing on the exact point one of them names as a
  // defect -- and this is the reader that decides whether the suite discovered
  // itself.
  //
  // CDATA is stripped FIRST: <system-out> carries whole application logs, and a
  // logged line containing "<testcase" would otherwise be counted as a test.
  // That is not theoretical -- this suite's own tests log request URLs.
  const scrubbed = xml.replace(/<!\[CDATA\[[\s\S]*?\]\]>/g, '')

  // 🔴 `[\s>/]` AND NOT `\b`, which is what this used to be and is not enough.
  // A hyphen IS a word boundary, so `<testsuite\b` matches `<testsuite-summary`
  // and `<testcase\b` matches `<testcase-metadata` -- neither element is
  // exotic, and both INFLATE the counts. Inflation is the dangerous direction:
  // it is what pushes a run that silently lost sixty-two tests back over the
  // floor. Requiring whitespace, `>` or `/` after the name is what makes the
  // match an element name rather than a prefix of one, and it still correctly
  // excludes the `<testsuites>` wrapper.
  const after = String.raw`[\s>/]`
  return {
    suites: [...scrubbed.matchAll(new RegExp(`<testsuite${after}`, 'g'))].length,
    total: [...scrubbed.matchAll(new RegExp(`<testcase${after}`, 'g'))].length,
    failures: [...scrubbed.matchAll(new RegExp(`<(?:failure|error)${after}`, 'g'))].length,
    skipped: [...scrubbed.matchAll(new RegExp(`<skipped${after}`, 'g'))].length,
  }
}
