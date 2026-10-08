import { describe, expect, it } from 'vitest'
import { join } from 'node:path'

import * as memberConstants from '../config/memberConstants'
import {
  REPO_ROOT,
  committedVocabulary,
  discoverServiceModules,
} from './apiContractScan.js'
import {
  FLOORS,
  assertBlankingPreservedCode,
  assertNonVacuousScan,
  assertNothingWasSkipped,
} from './assertNonVacuousScan'
import { blankingDestroyedCode, readSources, unreadableDirectories } from './sourceScan.js'

/**
 * VOCABULARY DRIFT: a constant copied instead of referenced.
 *
 * The endpoint guard next door catches a PATH that does not exist. It cannot
 * catch a string CONSTANT that has drifted -- and drift is the quieter defect,
 * because the request succeeds, the screen renders, and a branch that was
 * supposed to run simply never does. Nothing is red. Nothing is logged. The
 * rule just silently stops applying.
 *
 * WRITTEN AGAINST THE CLASS, NOT THE INSTANCE
 * The field/vocabulary pairs are DERIVED from `memberConstants` -- every
 * `X_Y_OPTIONS` export is the vocabulary for field `xY`, and a field whose name
 * ENDS with a discovered vocabulary name shares it. This guard does not name
 * `paymentType` and `identityType`, the two fields that prompted it. Add a
 * `TENURE_OPTIONS` tomorrow and comparisons against `tenure` are checked the
 * same day, because it exists rather than because somebody remembered.
 *
 * COMMENTS ARE BLANKED FIRST
 * The comment explaining a known-broken constant contains the very string being
 * searched for -- `usePaymentTypeValidation.test.jsx` says `'Payroll Deduction'`
 * in prose. A guard its own subject's docs can falsify is not a guard.
 *
 * Runs under `node`, which is now the suite's DEFAULT rather than an opt-out
 * this file carries -- see `vite.config.js`, where this file's own measurement
 * is recorded as part of the argument for inverting it. Measured 2026-09-05:
 * 5.39s with jsdom against 1.77s without, for identical results. jsdom was ~47%
 * of this file's wall clock and nothing in it reads a DOM.
 */

const camelCase = (snake) =>
  snake
    .toLowerCase()
    .split('_')
    .map((word, index) => (index === 0 ? word : word[0].toUpperCase() + word.slice(1)))
    .join('')

/**
 * Fields whose vocabulary constant is not named after them.
 *
 * ⚠️ A SUFFIX RULE WAS TRIED HERE FIRST, AND RUNNING IT KILLED IT. The idea was
 * that a field whose name ends with a vocabulary's base name inherits it, so
 * `subscriptionFrequency` and `endowmentFrequency` would both find
 * `FREQUENCY_OPTIONS` without being listed. It generalises cleanly and it is
 * WRONG: `electedRep` ends in `Rep`, so it inherited `REP_OPTIONS` -- 64 people
 * names -- and the guard immediately convicted `form.electedRep === "Y"`, which
 * is correct code. `electedRep` is a Y/N flag; its name merely ends in the same
 * three letters.
 *
 * That is the "cries wolf" failure this arc keeps naming, reached by a rule
 * that looked more principled than the list it replaced. A false positive on
 * the first run is worse than a list, because the list is honest about being a
 * list. So the aliases are named, and every one is asserted to carry a reason.
 */
const FIELD_ALIASES = {
  subscriptionFrequency: {
    constant: 'FREQUENCY_OPTIONS',
    why: 'the frequency of the Sasbo subscription, drawn from the same four values as any other frequency. The constant is named for the concept, the field for the thing it belongs to.',
  },
  endowmentFrequency: {
    constant: 'FREQUENCY_OPTIONS',
    why: 'the same four values, for the endowment fund rather than the subscription.',
  },
}

/**
 * Y/N flags, DISCOVERED rather than listed.
 *
 * This one generalises safely, because it keys on what the repository itself
 * says a field IS rather than on how its name is spelled: any member field
 * whose own label ends in `(Y/N)` takes `YES_NO_OPTIONS`. Add a flag tomorrow
 * and it is covered the same day. It is also what makes `electedRep` resolve
 * correctly -- its label is `Elected Sasbo Recruiter (Y/N)`.
 */
function yesNoFields() {
  return Object.entries(memberConstants.MEMBER_FIELD_LABEL_MAP ?? {})
    .filter(([, label]) => /\(Y\/N\)\s*$/.test(label))
    .map(([field]) => field)
}

/** field name -> { constant, values }, discovered from the constants module. */
function vocabularies() {
  const found = {}

  for (const [name, value] of Object.entries(memberConstants)) {
    if (!name.endsWith('_OPTIONS') || !Array.isArray(value) || value.length === 0) continue
    found[camelCase(name.replace(/_OPTIONS$/, ''))] = { constant: name, values: value }
  }

  for (const field of yesNoFields()) {
    found[field] = { constant: 'YES_NO_OPTIONS', values: memberConstants.YES_NO_OPTIONS }
  }

  for (const [field, { constant }] of Object.entries(FIELD_ALIASES)) {
    if (Array.isArray(memberConstants[constant]) && memberConstants[constant].length > 0) {
      found[field] = { constant, values: memberConstants[constant] }
    }
  }

  return found
}

/** Every `x.field === 'literal'` comparison in the screens and the hooks. */
function comparisons() {
  const sources = [
    ...readSources(join(REPO_ROOT, 'src', 'pages'), { root: REPO_ROOT, pattern: /\.jsx?$/ }),
    ...readSources(join(REPO_ROOT, 'src', 'hooks'), { root: REPO_ROOT, pattern: /\.jsx?$/ }),
  ].filter((source) => !source.path.includes('.test.'))

  const vocab = vocabularies()

  // Compiled ONCE per field, not once per field per line. The pattern depends
  // only on the field name, and building it inside the loop meant 119,070
  // RegExp constructions per run to find 47 matches -- measured 2026-09-05 at
  // ~450ms cold, against ~330ms hoisted.
  const compiled = Object.entries(vocab).map(([field, entry]) => [
    field,
    entry,
    new RegExp(
      `([A-Za-z_$][\\w$]*\\s*\\.\\s*)?\\b${field}\\b\\s*[=!]==\\s*(['"])([^'"]*)\\2`,
      'g',
    ),
  ])

  const found = []

  for (const source of sources) {
    source.code.split('\n').forEach((line, index) => {
      for (const [field, entry, pattern] of compiled) {
        pattern.lastIndex = 0
        let match
        while ((match = pattern.exec(line)) !== null) {
          const receiver = (match[1] || '').replace(/[\s.]/g, '') || '(param)'
          found.push({
            field,
            receiver,
            literal: match[3],
            at: source.at(index + 1),
            inVocabulary: entry.values.includes(match[3]),
            key: `${source.path} :: ${receiver}.${field} === ${JSON.stringify(match[3])}`,
          })
        }
      }
    })
  }

  return { sources, found }
}

/**
 * Every comparison against a value its own options array cannot produce.
 *
 * 🔴 THE SAME LITERAL IS A DEFECT IN ONE FILE AND CORRECT IN ANOTHER, which is
 * why this is keyed by SITE and not by string. `'Payroll Deduction'` in
 * `Members.jsx` normalises a legacy value that really can arrive -- `MemberDto`
 * declares `paymentType` as a String, so whatever is stored is passed straight
 * through. The identical comparison in `usePaymentTypeValidation` branches on
 * the value the FORM holds, which comes from `PAYMENT_TYPE_OPTIONS` and can
 * never be that string. One is defensive; the other is dead code.
 *
 * Keyed by file rather than by file:line on purpose: a line number moves when
 * anyone edits anything above it, and a guard that reddens for an unrelated
 * edit is a guard that gets switched off.
 *
 * `defect` is the REGISTER ROW itself, never a copy of its prose -- an earlier
 * draft restated A6 and A7 here and had already drifted from the register by
 * the time it was reviewed.
 */
const EXPECTED = {
  // A7's two sites (CaptureMember.jsx and Members.jsx comparing against "Passport") are GONE, 2026-09-17:
  // both compare against IDENTITY_TYPE_PASSPORT now, so there is nothing left here to accept or to flag.
  //
  // A6's two sites are gone the same way, 2026-09-17, and by the same repair rather than by
  // deletion. `usePaymentTypeValidation.js` compared against "Payroll Deduction" while the options
  // offered "Payroll"; the OPTION took the server's name, so that comparison is now in-vocabulary
  // and the scan does not flag it. `Members.jsx`'s legacy fold is still live and still necessary --
  // stored members really do arrive spelled "Payroll" -- but it compares against
  // PAYMENT_TYPE_PAYROLL_LEGACY, a named constant, so there is no drifted literal left to accept.
  // That is the shape this guard is for: the exception disappears because the code stopped
  // spelling a vocabulary value twice, not because the guard stopped looking.
  // ⚖️ THE ENTRY BELOW WAS SPECIFIED BY THE AUTOMATION ARC, NOT BY THE DESIGN ARC, and the
  // attribution is part of the record. This file is a guard and guards belong to that arc;
  // `06.1b` raised the match, proposed an entry, and asked. They did the reasoning, declined
  // the alternative and wrote the text. Transcribed here, unchanged.
  'src/hooks/useReferenceData.js :: result.status === "fulfilled"': {
    verdict: 'ACCEPTED',
    why:
      'not a vocabulary comparison at all. `result` is a PromiseSettledResult from ' +
      'Promise.allSettled and `status` is the language\'s own field on it, whose only ' +
      'values are "fulfilled" and "rejected". Same shape as the removed Subscriptions ' +
      'entries: a field name shared with the MEMBER vocabulary, not a member field. ' +
      'NOT narrowed away in the predicate: this scan sees a receiver, a field and a ' +
      'literal, never a type, so excluding it would mean excluding the literals ' +
      '"fulfilled"/"rejected" -- and a literal-keyed blind spot is how this walker went ' +
      'blind once already, when literals became constants.',
  },
  // 🔑 TWO ACCEPTED SITES WERE HERE AND `04.3` REMOVED THEM -- not because they were
  // wrong, but because the code that held them went. They were
  // `Subscriptions.jsx :: sub.status === "ACTIVE"` and `=== "PENDING"`, a presentational
  // ternary picking a chip colour, and both were recorded ACCEPTED: a SUBSCRIPTION status
  // is upper case because the server declares `SubscriptionsStatus {PENDING, ACTIVE,
  // CANCELLED, INACTIVE}` with no display names, so Jackson puts the enum NAME on the
  // wire. `STATUS_OPTIONS` is the MEMBER vocabulary -- a different list that happens to
  // share a field name.
  //
  // The ternary is now `<StatusChip status={sub.status} />`, which looks its colour up
  // case-insensitively and renders the value unchanged. So there are two fewer places
  // hard-coding a vocabulary string, and no register row to close: nothing was fixed
  // because nothing was broken.
  //
  // ⚠️ THIS GUARD IS WHAT CAUGHT THE FIRST ATTEMPT AT THAT CHANGE. It title-cased the
  // subscription status so the shared chip's map would match -- which would have changed
  // what a subscription reads as on screen and merged two vocabularies the server keeps
  // apart. The equality below is what refused it.
}

describe('vocabulary parity between this browser and its server', () => {
  const { sources, found } = comparisons()
  const vocab = vocabularies()
  const offenders = found.filter((comparison) => !comparison.inVocabulary)
  const offendingKeys = [...new Set(offenders.map((offender) => offender.key))].sort()

  describe('the scan actually scanned something', () => {
    it('discovered the option-backed fields from the constants themselves', () => {
      assertNonVacuousScan(
        Object.keys(vocab),
        FLOORS.OPTION_BACKED_FIELDS,
        'option-backed fields in memberConstants',
      )

      // The five the plan named must all be reachable, or the convention that
      // derives them has broken and the guard is checking the wrong fields.
      expect(Object.keys(vocab)).toEqual(
        expect.arrayContaining([
          'identityType',
          'paymentType',
          'status',
          'employmentType',
          'subscriptionFrequency',
        ]),
      )
    })

    it('found comparisons to look at', () => {
      assertNonVacuousScan(
        found,
        FLOORS.VOCABULARY_COMPARISONS,
        'comparisons against option-backed fields',
      )
    })

    it('read every directory it walked, rather than skipping one in silence', () => {
      // 🔴 THE HALF THIS GUARD DID NOT HAVE, and the guard it is missing from
      // matters more than its neighbour -- E2.
      //
      // `apiContract.test.js` next door asserts it "scanned EVERY service module
      // on disk" against an independent `readdirSync`, so a lost directory
      // reddens it. This file stands on a floor of 20 against an actual of 47.
      // Losing `src/hooks/` -- which holds one of the two sites the register
      // pins -- drops it to roughly 40, clears the floor, and reports success
      // while no longer looking at the code the pin is about.
      //
      // 🔑 So the pinned defects below would go GREEN by not being looked at,
      // which is this arc's defining failure mode reached through a directory
      // permission rather than through a test.
      assertNothingWasSkipped(unreadableDirectories(), 'the vocabulary scan')
    })

    it('did not destroy any code while blanking comments', () => {
      assertBlankingPreservedCode(blankingDestroyedCode(sources))
    })
  })

  describe('every comparison against an option-backed field', () => {
    it('is either in that field options, or a known and explained site', () => {
      const unexplained = offenders
        .filter((offender) => !Object.hasOwn(EXPECTED, offender.key))
        .map((offender) => `${offender.at}  ${offender.key}`)

      expect(
        unexplained,
        'A comparison against a value the field options cannot produce. If the receiver ' +
          'holds a value chosen from those options, this branch can NEVER be true and the ' +
          'rule it guards has never run. If it legitimately holds a value from somewhere ' +
          'else -- a different enum, or legacy storage -- add it to EXPECTED as ACCEPTED ' +
          'with the reason.',
      ).toEqual([])
    })

    it('EXPECTED names every drifted site and no others', () => {
      // Deduplicated, because ONE site legitimately appears on several lines --
      // `identityType === "Passport"` is at CaptureMember.jsx:270 and :446, one
      // defect in two places. EXPECTED is keyed by site, so the comparison must
      // be too, or the count would drift every time a file gained a line.
      expect(
        offendingKeys,
        'EXPECTED is out of date. A key here that the scan no longer finds means the site ' +
          'was fixed or moved: close its register row. A key the scan finds that is not ' +
          'here is new drift.',
      ).toEqual([...new Set(Object.keys(EXPECTED))].sort())
    })

    it('every ACCEPTED site carries a reason somebody can check', () => {
      // An exception nobody can see is where the real drift hides next time.
      for (const [key, entry] of Object.entries(EXPECTED)) {
        if (entry.verdict !== 'ACCEPTED') continue
        expect(entry.why, `${key} is ACCEPTED with no reason`).toBeTruthy()
        expect(entry.why.length).toBeGreaterThan(40)
      }
    })
  })

  /**
   * The drifted constants, through the register rather than beside it.
   *
   * A6 and A7 already carry a skip and a pin in the files that own their
   * behaviour; these are the SCAN's half -- the assertion that the drift is
   * still discoverable by looking at the source, which is what would notice a
   * third site appearing.
   */
  describe('the drifted constants', () => {
    it('A6 (fixed): no dead payment-type comparison survives', () => {
      expect(offendingKeys.filter((key) => key.includes('paymentType'))).toEqual([])
    })

    it('A7 (fixed): no screen compares identityType to "Passport"', () => {
      expect(offendingKeys.filter((key) => key.includes('identityType'))).toEqual([])
    })

  })

  describe('the vocabulary the server publishes', () => {
    const vocabulary = committedVocabulary()

    it('contains every payment type this browser offers', () => {
      // SUBSET, not equality, and deliberately so: the server may legitimately
      // hold values the browser does not offer. Equality would redden the day
      // the server grows, which is a normal thing for it to do.
      expect(
        vocabulary.paymentTypes.map((type) => type.displayName),
        'PAYMENT_TYPE_OPTIONS offers a payment type this server does not accept. A member ' +
          'choosing it reaches a real person as a failed capture, not as a warning.',
      ).toEqual(expect.arrayContaining(memberConstants.PAYMENT_TYPE_OPTIONS))
    })

    it('00.5 offers exactly the account types the server stores', () => {
      // EQUALITY, unlike the payment types: the bureau file has exactly these digits, and a type the browser
      // cannot offer is a member who cannot be captured with the account they have.
      expect(
        memberConstants.ACCOUNT_TYPE_OPTIONS,
        'ACCOUNT_TYPE_OPTIONS is not the list the server publishes from AccountType.',
      ).toEqual(vocabulary.accountTypes)
    })

    it('V55 offers exactly the SASBO-structure lists the server holds a member to', () => {
      // EQUALITY, in order: the server refuses any structure or position outside its list
      // (MemberValidator.validateBecRoles / validateCosatuPositions), and writes the rest back in
      // this spelling and order -- so a box the browser offers and the server lacks is a save that
      // fails, and one the server has and the browser lacks is a role nobody can record.
      expect(
        memberConstants.BEC_STRUCTURES_REPRESENTED,
        'BEC_STRUCTURES_REPRESENTED is not the list the server publishes from BecStructure.',
      ).toEqual(vocabulary.memberStructures.becStructures)
      expect(
        memberConstants.BEC_EXECUTIVE_POSITIONS,
        'BEC_EXECUTIVE_POSITIONS is not the list the server publishes from BecExecutivePosition.',
      ).toEqual(vocabulary.memberStructures.becExecutivePositions)
      expect(
        memberConstants.COSATU_POSITIONS,
        'COSATU_POSITIONS is not the list the server publishes from CosatuPosition.',
      ).toEqual(vocabulary.memberStructures.cosatuPositions)
    })
  })

  describe('the failure envelope', () => {
    const vocabulary = committedVocabulary()

    it('publishes the keys an error response can carry', () => {
      assertNonVacuousScan(
        vocabulary.errorEnvelopeKeys,
        FLOORS.ENVELOPE_KEYS,
        'error envelope keys in the artifact',
      )
      expect(vocabulary.errorEnvelopeKeys).toEqual(
        expect.arrayContaining(['status', 'message', 'validationErrors']),
      )
    })

    it('the service layer READS the error envelope, which is A24 closed', () => {
      /*
       * ✅ A24's PIN, FLIPPED BY 06.1a AND INVERTED RATHER THAN DELETED.
       *
       * It asserted that NO service read a single key of the envelope: every service threw
       * `HTTP ${r.status}: ${r.statusText}` — the HTTP Response's own fields, never the server's
       * JSON body — so `message`, `validationErrors` and the rest were published, sent, and
       * discarded. A 400 listing exactly which fields were invalid reached the user as
       * "HTTP 400: Bad Request" and the form highlighted nothing.
       *
       * `parseApiError.js` reads them now, so the same scan asserts the opposite. Kept rather than
       * deleted because it is the only check that the envelope is read AT ALL — a refactor that
       * reverted to `statusText` would otherwise be invisible.
       *
       * 🔴 AND THE PATTERN NEEDED ONE FIX TO SURVIVE THE FLIP, which is the interesting part. It
       * matched a PLAIN DOT. `parseApiError` is written defensively against a body that may be
       * `null`, so every read is OPTIONAL CHAINING — `body?.message`, `body?.validationErrors` —
       * and a `?` is not whitespace, so the original pattern matched NEITHER. Inverting the
       * assertion without fixing it would have reported "the envelope is still not read" against
       * code that reads it on the line above. A guard aimed at a notation rather than at a fact
       * catches whichever spelling it happened to be written against, which is the species this
       * suite keeps recording, so the `?` is explicit now.
       */
      //
      // Matched in a MEMBER position or a destructuring, not only as
      // `body.<key>` -- `const { validationErrors } = await r.json()` is the
      // more idiomatic repair, and a pin watching one syntax would stay green
      // while the defect was being fixed.
      //
      // ⚠️ THE RECEIVER IS NAMED POSITIVELY, AND IT HAS TO BE. Three of the
      // envelope's six keys collide with names this code already uses for
      // something else: `r.status` is the HTTP status line, `console.error` is
      // not the envelope's `error`, and `err.message` is a JavaScript Error.
      // A detector matching any `.status` reported A24 as FIXED on its first
      // run while nothing had changed -- found by running it, twice: a
      // blacklist of receiver names then missed `branchesResponse.status`,
      // because a blacklist has to predict every name anyone will ever choose.
      //
      // So this matches only a receiver that IS the parsed body. The envelope
      // arrives from `.json()`, and a repair binds it to something body-shaped
      // or destructures it. Both are covered; nothing else is.
      const BODY = 'body|payload|json|data|envelope|problem|errorBody|responseBody'
      const sources = discoverServiceModules()

      // Built once per KEY, not once per (key, source) pair. The pattern
      // depends only on the key, and this file already paid for that lesson 260
      // lines above: "building it inside the loop meant 119,070 RegExp
      // constructions per run to find 47 matches -- measured 2026-09-05 at
      // ~450ms cold, against ~330ms hoisted." Same slip, smaller loop.
      const keysRead = vocabulary.errorEnvelopeKeys.filter((key) => {
        const reads = new RegExp(
          `\\b(?:${BODY})\\s*\\??\\.\\s*${key}\\b` + `|\\{[^}]*\\b${key}\\b[^}]*\\}\\s*=[^=]`,
          'i',
        )
        return sources.some((source) => reads.test(source.code))
      })

      // The two keys that carry meaning for a person and for a form, asserted BY NAME against
      // the committed vocabulary rather than as a count — so "the envelope is read" cannot be
      // satisfied by reading `timestamp`.
      expect(
        keysRead,
        'The service layer has stopped reading the error envelope, so a 400 that names the ' +
          'invalid fields reaches the user as a status code again and the form highlights ' +
          'nothing. That is A24, reopened.',
      ).toEqual(expect.arrayContaining(['message', 'validationErrors']))
    })
  })
})
