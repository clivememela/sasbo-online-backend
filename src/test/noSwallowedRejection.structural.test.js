import { describe, expect, it } from 'vitest'

import { assertDetectorCanary, assertScanIsHonest, FLOORS } from './assertNonVacuousScan.js'
import { blankComments, nonTestSources } from './sourceScan.js'

/**
 * NO FAILED REQUEST IS QUIETLY TURNED INTO AN EMPTY ANSWER. `06.4`, F-P54.
 *
 * 🔴 `listStates.test.js` asks whether a page CAN say its fetch failed, and accepts any `catch(` as
 * the answer -- so `.catch(() => [])`, which is the failure turned into the empty state, passes it.
 * That shape is A27's confident wrong answer at its smallest: a down backend and a real empty list
 * resolve to the same value, and every screen above renders "no banks", "nothing matches". Two were
 * still standing when this was written, both one call below the screen that paid for them:
 *
 *   - `bankService.getBankListing`'s inner `/api/banks` lookup, `.catch(() => [])` -- Bank Listing
 *     printed a dash for every universal branch code and answered a search by code with "no banks";
 *   - `jobGradeService.getByBank`'s name->code lookup, `try { ... } catch { }` -- a failed lookup
 *     asked for the grades by the bank's NAME, and the member's bank "had no job grades on file".
 *
 * 🔑 IT CONVICTS THE SHAPE, NOT THE WORD, in its two spellings:
 *
 *   ① a promise `.catch` whose handler IS an empty value -- `[]`, `{}`, `null`, `''`, `false`, `0`,
 *     or an empty body;
 *   ② a `try` whose block MAKES A REQUEST (a `...Service.method(`, `requestJson(` or `fetch(`) and
 *     whose `catch` is empty or returns an empty value.
 *
 * ⚠️ ② IS AIMED AT THE REQUEST, AND THAT IS WHAT KEEPS IT HONEST. An empty `catch` around
 * `JSON.parse`, `new URL` or `localStorage` is a classification, not a swallow -- "this body is not
 * JSON", "no stored theme" -- and this repository has four, each explained at its site. A guard that
 * convicted them would be switched off. A catch that DOES anything (reports, sets an error state,
 * rethrows, hands the error on) is neither shape, and is the four-state tests' to judge.
 */

const PROMISE_SWALLOW = /\.catch\s*\(\s*(?:\(\s*\w*\s*\)|\w+)\s*=>\s*(?:\[\s*\]|\{\s*\}|\(\s*\{\s*\}\s*\)|null|undefined|''|""|false|0)\s*\)/g
const EMPTY_CATCH = /^\s*catch\s*(?:\(\s*\w*\s*\))?\s*\{\s*(?:return\s*(?:\[\s*\]|\{\s*\}|null|undefined|''|""|false|0)?\s*;?\s*)?\}/
const REQUEST = /\b\w+Service\s*\.\s*\w+\s*\(|\brequestJson\w*\s*\(|\bfetch\s*\(/

/** The index just past the `}` closing the `{` at `open`, stepping over string literals. */
function closeOf(code, open) {
  let depth = 0
  for (let i = open; i < code.length; i += 1) {
    const c = code[i]
    if (c === '"' || c === "'" || c === '`') {
      for (i += 1; i < code.length && code[i] !== c; i += 1) if (code[i] === '\\') i += 1
    } else if (c === '{') {
      depth += 1
    } else if (c === '}') {
      depth -= 1
      if (depth === 0) return i + 1
    }
  }
  return -1
}

/** 1-based lines of every swallow in comment-blanked source. */
export function swallowsIn(code) {
  const lineAt = (index) => code.slice(0, index).split('\n').length
  const lines = [...code.matchAll(PROMISE_SWALLOW)].map((m) => lineAt(m.index))

  for (const m of code.matchAll(/\btry\s*\{/g)) {
    const open = m.index + m[0].length - 1
    const close = closeOf(code, open)
    if (close > 0 && REQUEST.test(code.slice(open, close)) && EMPTY_CATCH.test(code.slice(close))) {
      lines.push(lineAt(m.index))
    }
  }

  return lines.sort((a, b) => a - b)
}

describe('no failed request is quietly turned into an empty answer', () => {
  it('the detector convicts both spellings of the swallow, and acquits a failure that is handled or classified', () => {
    assertDetectorCanary(
      {
        mustConvict: [
          'fetchBanks().catch(() => [])',
          'fetchBanks().catch(() => null)',
          'fetchBanks().catch(_ => ({}))',
          'fetchBanks().catch((err) => [])',
          'fetchBanks().catch(() => {})',
          'fetchBanks().catch(() =>\n  [])',
          'try {\n  listing = await bankService.getBankListing()\n} catch {\n  // fall through\n}',
          'try { return await fetch(url) } catch (error) { return [] }',
        ],
        mustAcquit: [
          "fetchBanks().catch((err) => { reportError('banks', err); setListState('error') })",
          'fetchBanks().catch(fail)',
          'fetchBanks().catch((err) => setError(err))',
          'try { body = await response.json() } catch { }',
          "try { return new URL(raw).origin } catch { return '' }",
          'try { await memberService.save(member) } catch (err) { setNotice(err.message) }',
          'try { await bankService.getBankNames() } catch (err) { throw err }',
          'try { const s = `${a}}`; await fetch(s) } catch (err) { fail(err) }',
        ],
      },
      (sample) => swallowsIn(blankComments(sample)),
      'swallowed-rejection',
    )
  })

  it('no source under src/ swallows a failed request', () => {
    const sources = nonTestSources()
    assertScanIsHonest(sources, FLOORS.SRC_SOURCE_FILES, 'non-test sources under src/')

    const offenders = sources.flatMap((source) => swallowsIn(source.code).map((line) => source.at(line)))

    expect(
      offenders,
      'These turn a failed request into an empty answer, so the screen above renders its EMPTY state ' +
        'when the backend is down -- A27 one layer down. Let it reject, or return the failure alongside ' +
        'the data (see `bankService.getBankListingDetailed`) so the page can say what is missing.',
    ).toEqual([])
  })
})
