import { describe, expect, it } from 'vitest'

import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

import { bankNameOf, branchNameOf, branchNamesForBank, findBranchRecord, formatBranchAddress } from './bankBranches'

/**
 * E3 / E6 -- the branch list, and the address derivation that depended on it.
 *
 * THE DEFECT THIS PINS
 * The old list was a hand-assembled constant. The server resolves the bank address with
 * `findByBank_NameIgnoreCaseAndBranchNameIgnoreCase`, i.e. on `branchName` ALONE, so
 * every option that was actually an `area` was unresolvable -- the save succeeded, the
 * address came back empty, and no error was raised anywhere. Ten of fourteen options
 * were in that state.
 *
 * WHAT THESE TESTS ARE FOR
 * The derivation must agree with the server's lookup rule, so the cases below are
 * written as the SERVER's contract, not as this function's implementation: exact match
 * on the bank, `branchName` used verbatim, and nothing offered that the server could
 * not resolve.
 */

// The shape the API actually returns. `GET /api/banks/branches` serialises the
// BankBranch ENTITY (not the DTO), so the branch is `branchName` and the bank name
// arrives via the `getBankName()` helper as `bankName`. The DTO spelling `branch`
// is accepted too because the create/update path speaks it.
const LISTING = [
  { id: 1, bankName: 'Absa Bank Limited', branchName: 'Head Office', area: 'Johannesburg' },
  { id: 2, bankName: 'Absa Bank Limited', branchName: 'Rosebank', area: 'Johannesburg' },
  { id: 3, bankName: 'Standard Bank South Africa', branchName: 'Rosebank', area: 'Johannesburg' },
  { id: 4, bankName: 'Nedbank Limited', branchName: 'South Africa Branch', area: 'Sandton' },
  { id: 5, bankName: 'Nedbank Limited', branchName: '', area: 'Sandton' },
]

describe('branchNamesForBank', () => {
  it('offers only the branches of the selected bank', () => {
    expect(branchNamesForBank(LISTING, 'Absa Bank Limited')).toEqual(['Head Office', 'Rosebank'])
  })

  it('does not leak a neighbouring bank\'s branch that shares a name', () => {
    // "Rosebank" exists under two banks. Selecting Nedbank must not offer it: the
    // server would look for (Nedbank, Rosebank) and find nothing.
    expect(branchNamesForBank(LISTING, 'Nedbank Limited')).toEqual(['South Africa Branch'])
  })

  it('matches the bank case-insensitively and ignores surrounding space, as the server does', () => {
    expect(branchNamesForBank(LISTING, '  absa bank limited ')).toEqual(['Head Office', 'Rosebank'])
  })

  it('is EXACT, not a substring match -- a prefix of a real bank name offers nothing', () => {
    // This is the load-bearing difference from the fuzzy `includes()` match used to
    // resolve a bank CODE for job grades. A substring match here would offer branches
    // of a bank the user did not choose, and the server's exact lookup would then fail
    // -- rebuilding the defect while appearing to fix it.
    expect(branchNamesForBank(LISTING, 'Absa')).toEqual([])
    expect(branchNamesForBank(LISTING, 'Bank')).toEqual([])
  })

  it('offers nothing until a bank is chosen', () => {
    expect(branchNamesForBank(LISTING, '')).toEqual([])
    expect(branchNamesForBank(LISTING, null)).toEqual([])
    expect(branchNamesForBank(LISTING, undefined)).toEqual([])
  })

  it('drops blank branch names rather than offering an unselectable option', () => {
    expect(branchNamesForBank(LISTING, 'Nedbank Limited')).not.toContain('')
  })

  it('survives a listing that failed to load', () => {
    expect(branchNamesForBank([], 'Absa Bank Limited')).toEqual([])
    expect(branchNamesForBank(null, 'Absa Bank Limited')).toEqual([])
    expect(branchNamesForBank(undefined, 'Absa Bank Limited')).toEqual([])
  })

  it('reads the DTO spelling `branch` as well as the entity spelling `branchName`', () => {
    const dtoShaped = [{ bankName: 'Absa Bank Limited', branch: 'Sandton' }]
    expect(branchNamesForBank(dtoShaped, 'Absa Bank Limited')).toEqual(['Sandton'])
  })

  it('de-duplicates and sorts', () => {
    const dupes = [
      { bankName: 'X', branchName: 'Zulu' },
      { bankName: 'X', branchName: 'Alpha' },
      { bankName: 'X', branchName: 'Zulu' },
    ]
    expect(branchNamesForBank(dupes, 'X')).toEqual(['Alpha', 'Zulu'])
  })
})

describe('findBranchRecord', () => {
  it('returns the record for an exact bank+branch pair', () => {
    expect(findBranchRecord(LISTING, 'Absa Bank Limited', 'Rosebank')?.id).toBe(2)
  })

  it('disambiguates a branch name shared by two banks', () => {
    expect(findBranchRecord(LISTING, 'Standard Bank South Africa', 'Rosebank')?.id).toBe(3)
  })

  it('returns null for a pair the server could not resolve either', () => {
    expect(findBranchRecord(LISTING, 'Nedbank Limited', 'Rosebank')).toBeNull()
    expect(findBranchRecord(LISTING, 'Absa Bank Limited', '')).toBeNull()
  })
})

describe('field readers', () => {
  it('read either spelling and normalise to a trimmed string', () => {
    expect(bankNameOf({ name: ' Absa ' })).toBe('Absa')
    expect(bankNameOf({ bankName: 'Absa' })).toBe('Absa')
    expect(bankNameOf(null)).toBe('')
    expect(branchNameOf({ branch: ' Head Office ' })).toBe('Head Office')
    expect(branchNameOf({ branchName: 'Head Office' })).toBe('Head Office')
    expect(branchNameOf(undefined)).toBe('')
  })
})

/**
 * THE CANARY -- the arc's second rule: every guard gets one.
 *
 * The tests above all run against a hand-written LISTING. If the shipped bank data ever
 * stopped carrying `branchName`, every assertion here would keep passing while the real
 * dropdown went permanently empty -- a guard that passes while looking at nothing, which
 * this arc has already found three times.
 *
 * So this reads the actual seed file and asserts the derivation is non-vacuous over it.
 */
describe('canary -- the derivation is non-vacuous over the real seed data', () => {
  const banks = JSON.parse(
    readFileSync(fileURLToPath(new URL('../data/banks.json', import.meta.url)), 'utf8'),
  )

  it('every seeded bank offers at least one branch', () => {
    const bankNames = [...new Set(banks.map((b) => b.bankName))].filter(Boolean)
    expect(bankNames.length).toBeGreaterThan(20)

    const empty = bankNames.filter((name) => branchNamesForBank(banks, name).length === 0)
    expect(empty).toEqual([])
  })

  it('every offered branch resolves back to its own record', () => {
    // The round trip the server performs. If this fails, the dropdown is offering a
    // pair `autoPopulateBankAddress` cannot look up -- exactly the old defect.
    const unresolvable = []
    for (const name of new Set(banks.map((b) => b.bankName))) {
      for (const branch of branchNamesForBank(banks, name)) {
        if (!findBranchRecord(banks, name, branch)) unresolvable.push(`${name} / ${branch}`)
      }
    }
    expect(unresolvable).toEqual([])
  })
})

/**
 * 04.1 -- the address formatter, which must produce the SAME string the server
 * writes on save.
 *
 * `MemberServiceImpl.formatPhysicalAddress` joins six parts, in one order, with
 * ", ", dropping the blanks. If the browser formats it differently, the address
 * the user saw and approved is silently replaced on save by a
 * differently-punctuated one -- a diff nobody reads and everybody distrusts once
 * they notice it. The order below IS the contract, restated here so a change to
 * either side has to change a test.
 */
describe('formatBranchAddress mirrors the server formatter', () => {
  it('the ENTITY shape -- what GET /api/banks/branches actually returns', () => {
    // `BankController.getAllBranches` returns `List<BankBranch>`, the entity, so
    // suburb/city/province arrive as nested {id, name} objects. This is the shape
    // the running browser sees, and therefore the one that matters most.
    expect(formatBranchAddress({
      physicalAddress: {
        streetNumber: '170',
        streetName: 'Main St',
        suburb: { id: 1, name: 'Marshalltown' },
        city: { id: 2, name: 'Johannesburg' },
        province: { id: 3, name: 'Gauteng' },
        postalCode: '2001',
      },
    })).toBe('170, Main St, Marshalltown, Johannesburg, Gauteng, 2001')
  })

  it('the flat …Name shape the entity also declares as @Transient fields', () => {
    expect(formatBranchAddress({
      physicalAddress: {
        streetNumber: '170',
        streetName: 'Main St',
        cityName: 'Johannesburg',
        provinceName: 'Gauteng',
      },
    })).toBe('170, Main St, Johannesburg, Gauteng')
  })

  it('the STRING shape -- BankBranchDto collapses it, and so does this repo fixture', () => {
    expect(formatBranchAddress({ physicalAddress: '170 Main St, Johannesburg' }))
      .toBe('170 Main St, Johannesburg')
  })

  it('drops blank parts rather than leaving ", ," gaps -- as the server does', () => {
    expect(formatBranchAddress({
      physicalAddress: { streetNumber: '170', streetName: 'Main St', city: null, province: {} },
    })).toBe('170, Main St')
  })

  it('answers an empty string when there is no address, rather than "undefined"', () => {
    for (const record of [null, undefined, {}, { physicalAddress: null }]) {
      expect(formatBranchAddress(record)).toBe('')
    }
  })

  it(`canary -- every row of this repo's own bank fixture formats to something`, () => {
    // Without this the cases above pass just as well against a formatter that has
    // stopped reading the real field names. 10 of 14 branch options used to resolve
    // to nothing and no assertion anywhere noticed.
    //
    // ⚠️ AND IT PROVES LESS THAN IT LOOKS, which is worth writing down. This fixture
    // is `src/data/banks.json` -- 66 rows, `branch` rather than `branchName`, and
    // `physicalAddress` as a STRING. The server ships a file of the same name with
    // 68 rows, `branchName`, `branchCode`, and `physicalAddress` as an OBJECT. The
    // browser is served the server's. So this canary covers the string branch only;
    // the entity branch is covered by the first case above, by hand.
    const blank = banksFixture()
      .filter((record) => formatBranchAddress(record) === '')
      .map((record) => `${record.bankName} / ${record.branch}`)
    expect(blank).toEqual([])
  })
})

function banksFixture() {
  return JSON.parse(
    readFileSync(fileURLToPath(new URL('../data/banks.json', import.meta.url)), 'utf8'),
  )
}
