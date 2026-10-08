import { DERIVATION, derivationNote, derivationStateOf } from './derivation.js'
import { nameKey } from './nameKey.js'

/**
 * WHO RECRUITED A MEMBER, AND THE AREA THAT FOLLOWS FROM THEM -- SASBO, 18 Sep 2026.
 *
 * 🗣️ Their words, in the meeting and in their notes. The recruiter TYPE comes first and decides the rest: "if I
 * choose here by recruiter type and I say website, everything else must fall away"; "if I say RO, then RO recruiter
 * names must come here"; and "area should be linked to the recruiter. We don't need to put in area recruited ... if
 * we say that the RO is Andries, his area must then link." Their notes: "Recruiter name must link to area
 * (prepopulated). Area recruited should not be selection based."
 *
 * 🔑 WHAT SASBO MEANS BY "AREA" IS A DEMARCATION LETTER -- Area A, B, C -- and it belongs to the organiser. The
 * recruiter table has carried it all along (`areaCode`, with the organiser's `region`): Andries Mompati is area X,
 * Johannesburg. Until now the capture screen offered "Area Recruited" from `/api/areas`, which is the 26 suburbs read
 * off the bank branches (Sandton, Rosebank, ...) -- where a member WORKS, a different kind of area altogether. So the
 * area is no longer chosen: it is read off the recruiter, and shown with where it came from.
 *
 * ⚠️ WHAT THIS DOES NOT DO YET, AND WHY. The recruiter table has no TYPE column, so only an organiser can be told
 * apart from the rest -- an organiser row carries a region, and the channel rows the same table holds (WEBSITE, EORO,
 * YOUTH, ADMINISTRATION) carry none. So RO lists the organisers only; MSO, Youth and BEC still list everyone, until
 * SASBO's list of MSO names reaches the table. And six organisers (Pretoria and Bloemfontein) have no area letter
 * yet: SASBO's demarcation rules will supply them, and until then the screen says so rather than guessing.
 */

/** The channels a member joins through BY THEMSELVES: there is no recruiter and no area to record. */
const NO_RECRUITER = new Set(['website', 'eoro'])

/** The organisers' channel -- the one whose names are the organisers, each with an area. */
const ORGANISERS = 'ro'

/**
 * A channel's key: its first word, lower-cased. The maintenance screen lets SASBO rename a channel, and "EORO (Each One
 * Reach One)" -- the recruiter table's own spelling -- must still be EORO.
 */
const channelKey = (channel) => nameKey(channel).split(/[\s(]/)[0]

/** Website and EORO: the member came in by themselves, so the recruiter and the area "fall away". */
export const channelHasNoRecruiter = (channel) => NO_RECRUITER.has(channelKey(channel))

/** An organiser: a recruiter row with a region. The channel rows in the same table carry none. */
export const isOrganiser = (recruiter) => Boolean(String(recruiter?.region ?? '').trim())

/**
 * The recruiters a channel offers. None until a channel is chosen (the type comes first) and none for a channel with
 * no recruiter; the organisers for RO; everybody for the rest, until the table can tell them apart.
 */
export function recruitersFor(channel, recruiters) {
  const rows = Array.isArray(recruiters) ? recruiters : []
  if (!channelKey(channel) || channelHasNoRecruiter(channel)) return []
  return channelKey(channel) === ORGANISERS ? rows.filter(isOrganiser) : rows
}

/** The recruiter row with this name, compared the way every other name on these screens is. */
export const findRecruiter = (recruiters, name) =>
  (Array.isArray(recruiters) ? recruiters : []).find((row) => nameKey(row?.name) === nameKey(name)) ?? null

/** `PORT ELIZABETH` → `Port Elizabeth`. The table stores regions in capitals; this is display only. */
const regionName = (region) =>
  String(region ?? '').trim().toLowerCase().replace(/(^|[\s-])(\p{L})/gu, (_, gap, letter) => gap + letter.toUpperCase())

/** `Area X · Johannesburg`, or '' when the recruiter has no area letter on file. */
export function recruiterAreaText(recruiter) {
  const letter = String(recruiter?.areaCode ?? '').trim()
  if (!letter) return ''
  const region = regionName(recruiter?.region)
  return region ? `Area ${letter} · ${region}` : `Area ${letter}`
}

/**
 * The area a member was recruited in, read off the recruiter -- and WHY it reads as it does, in 04.1's closed set.
 *
 * `listState` is the recruiter list's own: 'loading' | 'ready' | 'failed'. The sentences avoid "could not be loaded"
 * on purpose: that is the Recruiter field's own message for the same failure, and one failed list says so once.
 *
 * `noRecruiterNote` is what to say while no recruiter is named. It is the CALLER's, because the two screens differ:
 * capture can ask for one ("choose one and their area fills in"); a read-only record cannot, and says nothing.
 *
 * @returns {{ state: string, text: string, note: string | undefined }}
 */
export function recruitedArea({ channel, recruiterName, recruiters, listState = 'ready', noRecruiterNote }) {
  if (channelHasNoRecruiter(channel)) {
    return {
      state: DERIVATION.NOT_APPLICABLE,
      text: '',
      note: `No area is recorded for a member who joined through ${String(channel).trim()}.`,
    }
  }

  const name = String(recruiterName ?? '').trim()
  const recruiter = listState === 'ready' ? findRecruiter(recruiters, name) : null
  const text = recruiterAreaText(recruiter)
  const state = derivationStateOf({ userEdited: false, hasInput: Boolean(name), sourceLoaded: listState === 'ready', match: Boolean(text) })

  switch (state) {
    case DERIVATION.DERIVED:
      return { state, text, note: derivationNote(state, { source: `${recruiter.name}'s area` }) }
    case DERIVATION.NOT_APPLICABLE:
      return { state, text: '', note: noRecruiterNote }
    case DERIVATION.SOURCE_UNAVAILABLE:
      return {
        state,
        text: '',
        note: listState === 'loading'
          ? 'Waiting for the recruiter list.'
          : 'The recruiter list is unavailable, so the area cannot be shown yet.',
      }
    default:
      return { state, text: '', note: derivationNote(state, { detail: `No area is on file for ${name} yet.` }) }
  }
}
