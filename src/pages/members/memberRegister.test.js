import { describe, expect, it } from 'vitest'

import { committedVocabulary } from '../../test/apiContractScan'
import {
  PRIVATE_QUERY, REGISTER_DEFAULT_PAGE_SIZE, REGISTER_DEFAULT_SORT, REGISTER_PAGE_SIZES, REGISTER_SORTS, SEARCH_MIN,
  TERM_MAX, historyState, isRegisterPage, isWholeIdentityNumber, nextSort, overviewFigures, readSort, readView,
  registerQuery, searchTerm, writeView,
} from './memberRegister'

/**
 * THE MEMBER REGISTER'S RULES, A PAGE AT A TIME (register row A10) -- held to the server's vocabulary where the server
 * publishes them, and each at its edge.
 *
 * The address bar is the register's view: a refresh, a link or Back must open the register where it was, an address
 * kept from last month must still open it, and a whole identity number must never be written into it. Every member,
 * name and number here is invented; the identity number is thirteen digits of no one.
 */
const vocabulary = committedVocabulary().monthEnd
const STATUSES = ['Active', 'Pending', 'Suspended']
const WHOLE_ID = '0000000000000'
const view = (search, state, statuses = STATUSES) => readView(new URLSearchParams(search), state, statuses)

describe('the register asks what the server answers', () => {
  it('its orders are the server\'s, by their names on the wire (`registerSorts`)', () => {
    expect([...REGISTER_SORTS]).toEqual(vocabulary.registerSorts)
  })

  it('every page size it offers is one the server serves, the default among them (`registerPageMaxSize`)', () => {
    expect(REGISTER_PAGE_SIZES.length).toBeGreaterThan(1)
    for (const size of REGISTER_PAGE_SIZES) {
      expect(size).toBeGreaterThanOrEqual(1)
      expect(size).toBeLessThanOrEqual(vocabulary.registerPageMaxSize)
    }
    expect(REGISTER_PAGE_SIZES).toContain(REGISTER_DEFAULT_PAGE_SIZE)
    expect(REGISTER_SORTS).toContain(REGISTER_DEFAULT_SORT.key)
  })
})

describe('the view, read from the address bar', () => {
  it('an address with nothing in it is the register\'s own view: page 1, the default size and order', () => {
    expect(view('')).toEqual({
      q: '', page: 0, size: REGISTER_DEFAULT_PAGE_SIZE, sort: REGISTER_DEFAULT_SORT, status: '', bank: '', branch: '',
    })
  })

  it('the page counts from 1 in the address bar and from 0 on the wire', () => {
    expect(view('page=3').page).toBe(2)
    expect(view('page=1').page).toBe(0)
  })

  it.each([
    ['a page that is a word', 'page=three', 'page', 0],
    ['page nought', 'page=0', 'page', 0],
    ['a page below nought', 'page=-2', 'page', 0],
    ['a size the screen does not offer', 'size=37', 'size', REGISTER_DEFAULT_PAGE_SIZE],
    ['a size past the server\'s ceiling', 'size=5000', 'size', REGISTER_DEFAULT_PAGE_SIZE],
    ['an order the register is not read in', 'sort=identityNumber', 'sort', REGISTER_DEFAULT_SORT],
    ['an order the right way round but no column', 'sort=,desc', 'sort', REGISTER_DEFAULT_SORT],
    ['a direction that is neither', 'sort=surname,sideways', 'sort', REGISTER_DEFAULT_SORT],
    ['a status there is not', 'status=Retired-ish', 'status', ''],
  ])('%s opens the register as its default -- never a refusal', (_, search, field, expected) => {
    expect(view(search)[field]).toEqual(expected)
  })

  it('the must-pass: what the screen offers is read as written', () => {
    expect(view('size=25&sort=membershipNumber,desc&status=Pending&bank=Invented%20Bank&branch=Rosebank&q=Synth'))
      .toEqual({
        q: 'Synth', page: 0, size: 25, sort: { key: 'membershipNumber', direction: 'desc' },
        status: 'Pending', bank: 'Invented Bank', branch: 'Rosebank',
      })
  })

  it('a search of one character asks nothing, and a long one is cut at the server\'s ceiling', () => {
    expect(searchTerm('S')).toBe('')
    expect(searchTerm(' Sy ')).toBe('Sy')
    expect(SEARCH_MIN).toBe(2)
    expect(searchTerm('x'.repeat(TERM_MAX + 20))).toHaveLength(TERM_MAX)
  })

  it('another screen\'s bank and branch arrive in the history entry\'s state, the address bar\'s winning', () => {
    expect(view('', { filters: { bankName: 'Invented Bank', branch: 'Rosebank' } }))
      .toMatchObject({ bank: 'Invented Bank', branch: 'Rosebank' })
    expect(view('bank=Other%20Bank', { filters: { bankName: 'Invented Bank' } }).bank).toBe('Other Bank')
  })
})

describe('the view, written to the address bar', () => {
  it('the defaults are left out, so the register\'s own address is bare', () => {
    expect(writeView(view('')).toString()).toBe('')
  })

  it('what is not the default is written, the page counted from 1', () => {
    const written = writeView({ ...view(''), q: 'Synth', page: 2, size: 25, sort: { key: 'bank', direction: 'desc' } })
    expect(Object.fromEntries(written)).toEqual({ q: 'Synth', page: '3', size: '25', sort: 'bank,desc' })
  })

  it('a view written and read back is the same view (a refresh opens the register where it was)', () => {
    const shown = {
      q: 'Synth', page: 4, size: 100, sort: { key: 'status', direction: 'asc' }, status: 'Active', bank: 'Invented Bank',
      branch: 'Rosebank',
    }
    expect(view(writeView(shown).toString())).toEqual(shown)
  })

  it('🔒 a whole identity number never goes in the address bar -- it is kept in the history entry\'s own state', () => {
    const asked = { ...view(''), q: WHOLE_ID }
    expect(isWholeIdentityNumber(WHOLE_ID)).toBe(true)
    expect(writeView(asked).toString()).not.toContain(WHOLE_ID)
    expect(historyState(asked)).toEqual({ [PRIVATE_QUERY]: WHOLE_ID })
    // ...and Back or a refresh still finds it there.
    expect(view('', historyState(asked)).q).toBe(WHOLE_ID)
  })

  it('the must-pass: the last four digits of one, or a name, ARE written -- they are a search, not a member\'s number', () => {
    for (const q of ['0000', 'Synthetic']) {
      expect(writeView({ ...view(''), q }).get('q')).toBe(q)
      expect(historyState({ ...view(''), q })).toBeNull()
    }
  })
})

describe('the order a heading asks for', () => {
  it('another column starts A to Z; the same column turns round', () => {
    const bySurname = { key: 'surname', direction: 'asc' }
    expect(nextSort(bySurname, 'bank')).toEqual({ key: 'bank', direction: 'asc' })
    expect(nextSort(bySurname, 'surname')).toEqual({ key: 'surname', direction: 'desc' })
    expect(nextSort({ key: 'surname', direction: 'desc' }, 'surname')).toEqual(bySurname)
  })

  it('a bare column is read A to Z', () => {
    expect(readSort('branch')).toEqual({ key: 'branch', direction: 'asc' })
  })
})

describe('the question the server is asked', () => {
  it('the view and the criteria behind "More criteria", the empty ones left out', () => {
    const asked = registerQuery({ ...view(''), q: 'Synth', page: 1, status: 'Active' }, { phone: '0009' })
    expect(asked).toEqual({
      phone: '0009', q: 'Synth', status: 'Active', bank: undefined, branch: undefined, page: 1,
      size: REGISTER_DEFAULT_PAGE_SIZE, sort: 'surname,asc',
    })
    // Sent as JSON, the empty ones are not sent at all.
    expect(Object.keys(JSON.parse(JSON.stringify(asked)))).toEqual(['phone', 'q', 'status', 'page', 'size', 'sort'])
  })
})

describe('the answers, read for what they are', () => {
  const PAGE = { content: [], page: 0, size: 50, totalElements: 0, totalPages: 0, sort: 'surname,asc' }

  it('a page is rows and a total -- never a bare list, nor a proxy\'s page of HTML (MV-36)', () => {
    expect(isRegisterPage(PAGE)).toBe(true)
    expect(isRegisterPage([])).toBe(false)
    expect(isRegisterPage('<!doctype html><title>Gateway</title>')).toBe(false)
    expect(isRegisterPage({ ...PAGE, totalElements: undefined })).toBe(false)
    expect(isRegisterPage(null)).toBe(false)
  })

  it('the figures are read only from an answer that is them; anything else is not available, never a zero', () => {
    const figures = { total: 6, active: 5, pending: 1, suspended: 0, newThisMonth: 2, newDebitOrders: 1, updatedToday: 3 }
    expect(overviewFigures({ ...figures, branches: ['Rosebank'], asOf: '2026-10-07' })).toEqual(figures)
    expect(overviewFigures([])).toBeNull()
    expect(overviewFigures({ status: 500 })).toBeNull()
    expect(overviewFigures('<!doctype html>')).toBeNull()
  })
})
