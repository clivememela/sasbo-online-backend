/**
 * SASBO STRUCTURES -- the rules of the member record's Structures tab (Edit Member).
 *
 * 🗣️ SASBO, through the owner, 2026-09-30: "Shop Steward (Yes) > Select Election Date. BEC Member (Yes) > Select
 * Branch Committee. We need those functionalities back" -- and the tree they say the screen had: under BEC Member,
 * the committee, BEC Recruiter, BEC Structure Representative (the structures represented), BEC Executive (one
 * position) and the COSATU structure. The record asked all of it until 2026-09-17 and stored none of it -- no column
 * (CM-05) -- which is why it was cut to Shop Steward?, BEC Member? and COSATU. The server stores every answer since
 * V55, so the tree is back.
 *
 * 🔑 EDIT MEMBER ONLY. SASBO's spec took the structures off Capture Member "because a newly captured member cannot sit
 * on a structure" (2.4); the questions are asked of a member already on record.
 *
 * The stakeholder's shape of 2026-09-17 is kept inside it: COSATU only under BEC, and a COSATU Local or Regional
 * structure only for a COSATU member (the server's `validateStructures`).
 */
import { compareDates, parseDate, today } from '@/lib/format.js'

/** A stored yes/no as its answer on screen; `''` is "never answered", which is not No (V29). */
export const answerOf = (flag) => (flag === true ? 'Yes' : flag === false ? 'No' : '')

/** The answer chosen, as the server stores it. */
export const flagOf = (answer) => (answer === 'Yes' ? true : answer === 'No' ? false : null)

/** COSATU is stored as V19's "Y"/"N"; older records carry a boolean. */
export const isCosatuMember = (member) => member?.isCosatu === 'Y' || member?.isCosatu === true

/** A list field as the record holds it: `memberService.normalizeMember` unfolds the server's "PEC,CEC". */
const listOf = (value) => (Array.isArray(value) ? value : [])

/**
 * The structure answers after one of them changes. A No takes with it what its Yes needed: a steward's election
 * date; a BEC member's committee and roles -- and, since COSATU needs BEC, the COSATU answers too; a structure
 * representative's structures; an executive's position; a COSATU member's structures and positions. The server
 * clears the same on save; clearing here keeps the screen from showing, or sending, a follow-up to a question now
 * answered No.
 *
 * The date clears to `null`, never `''`: an empty string is not a date on the wire. A list clears to `[]`, which the
 * server stores as cleared (an empty list is an answer; V55).
 */
export function withStructureAnswer(form, field, value) {
  const next = { ...form, [field]: value }
  if (field === 'isShopSteward' && value !== true) next.shopStewardElectionDate = null
  if (field === 'isBec' && value !== true) {
    Object.assign(next, {
      becCommitteeCode: '',
      isBecRecruiter: false,
      isBecStructureRep: false,
      becStructures: [],
      isBecExecutive: false,
      becExecutivePosition: '',
      isCosatu: 'N',
    })
    // Display only -- the committee's name beside its code, so the save confirmation can say it.
    if ('becCommitteeName' in form) next.becCommitteeName = ''
  }
  if ((field === 'isBec' && value !== true) || (field === 'isCosatu' && value !== 'Y')) {
    Object.assign(next, { isCosatuLocal: false, isCosatuRegional: false, cosatuPositions: [] })
  }
  if (field === 'isBecStructureRep' && value !== true) next.becStructures = []
  if (field === 'isBecExecutive' && value !== true) next.becExecutivePosition = ''
  return next
}

/**
 * SASBO's committees as options -- from `GET /api/reference/committees`, as printed on their reports.
 *
 * Alphabetical, as SASBO asked for the BEC dropdown on 18 Sep 2026. The report's UNATTACHED lines are left out:
 * they are the workplaces no committee covers, and a BEC member by definition sits on a committee.
 */
export function committeeOptions(answer) {
  const rows = Array.isArray(answer?.rows) ? answer.rows : []
  return rows
    .filter((row) => row?.code && row?.name && row.name.trim().toUpperCase() !== 'UNATTACHED')
    .map((row) => ({ value: row.code, label: row.name }))
    .sort((a, b) => a.label.localeCompare(b.label))
}

/**
 * Ticked boxes in the list's own order, as the server stores them -- so ticking in another order, or unticking and
 * ticking again, is not a change. A value the list does not offer (free text the old record wrote into
 * `cosatuPositions`, BR-14) is kept, after the rest: the server keeps one already on record, and so does the screen.
 */
export const inOrder = (chosen, vocabulary) => [
  ...vocabulary.filter((value) => chosen.includes(value)),
  ...chosen.filter((value) => !vocabulary.includes(value)),
]

/** A Yes on record with nothing beside it -- every one stored before V55, when there was no column to hold it. */
const legacyYes = (previous, answer, followUp) => previous?.[answer] === true && !followUp(previous)

/**
 * What the Structures answers still need, by field -- the server's `MemberValidator.validateSeats` and
 * `validateBecRoles`, asked before the save rather than refused by it.
 *
 * A steward's date and a BEC member's committee are required while the answer is Yes, except for a Yes already on
 * record with nothing beside it: every steward and BEC member stored before V55 has neither, and must stay saveable
 * unchanged. Once on record, either can be changed but not removed. A representative's structures and an
 * executive's position have no such exception -- their columns are V55's own, so no member holds either Yes without.
 *
 * @param form     the answers as the screen holds them -- a stored date arrives as `[y, m, d]`, a chosen one as text
 * @param previous the answers as stored
 * @param now      the local calendar day (`lib/format`'s `today()`), injectable for tests
 */
export function structureErrors(form, previous, now = today()) {
  const errors = {}
  if (form.isShopSteward === true) {
    const elected = parseDate(form.shopStewardElectionDate)
    if (!elected && !legacyYes(previous, 'isShopSteward', (member) => parseDate(member.shopStewardElectionDate))) {
      errors.shopStewardElectionDate = 'Enter the date the shop steward was elected'
    } else if (elected && compareDates(elected, now) > 0) {
      errors.shopStewardElectionDate = 'The election date cannot be in the future'
    }
  }
  if (form.isBec === true) {
    if (!form.becCommitteeCode && !legacyYes(previous, 'isBec', (member) => member.becCommitteeCode)) {
      errors.becCommitteeCode = 'Choose the Branch Executive Committee this member sits on'
    }
    if (form.isBecStructureRep === true && listOf(form.becStructures).length === 0) {
      errors.becStructures = 'Choose the structures this member represents'
    }
    if (form.isBecExecutive === true && !form.becExecutivePosition) {
      errors.becExecutivePosition = 'Choose the position this member holds'
    }
  }
  return errors
}
