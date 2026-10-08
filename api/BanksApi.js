import { ApiClient } from './ApiClient.js'

/**
 * The banks and branches REST surface, and the first client written on top of
 * `ApiClient`. Every later domain copies this shape.
 *
 * WHAT THE SERVER ACTUALLY EXPOSES -- read from the generated route artifact,
 * not from memory:
 *
 *   GET  /api/banks                 the banks, with their universal codes
 *   GET  /api/banks/branches        every branch
 *   POST /api/banks/branches        create one
 *   PUT  /api/banks/branches/{id}   update one
 *   GET  /api/branches?...          search, by bankName / branch / area / RAN
 *
 * 🔴 THERE IS NO DELETE. That single absence shapes the whole journey: this
 * suite may never create a branch it cannot remove, so the scenarios cover
 * create through the FORM VALIDATION path (which persists nothing) and cover
 * real persistence through UPDATE, which is reversible by writing the original
 * value back. Filed as a testability finding rather than worked around
 * silently.
 *
 * A NAMING ASYMMETRY THAT WILL CATCH THE NEXT PERSON
 * Reads return the entity, whose field is `branchName`. Writes take a DTO whose
 * field is `branch` (with `branchName` accepted as an alias). So the same
 * concept arrives under one name and leaves under another, and a payload built
 * by copying a read straight back into a write is not obviously wrong. This
 * client normalises it in one place so no caller has to remember.
 */
export class BanksApi extends ApiClient {
  /** Every bank. */
  async listBanks() {
    return this.json(await this.get('/api/banks'))
  }

  /**
   * Every branch, SORTED BY ID.
   *
   * 🔴 The sort is not cosmetic. The endpoint has no `ORDER BY`, so Postgres
   * returns rows in whatever order it likes -- observed starting at id 6, not 1
   * -- and it is free to differ between calls. A scenario that says "the first
   * branch" against an unsorted list is asserting against a different record
   * each run, which is a flake that looks like a data bug.
   *
   * Sorting here rather than in each caller means every scenario agrees on what
   * "first" means, and the suite never depends on server-side ordering it was
   * not promised.
   */
  async listBranches() {
    const branches = await this.json(await this.get('/api/banks/branches'))
    return branches.sort((a, b) => a.id - b.id)
  }

  /** One branch by id, found through the list -- there is no by-id read. */
  async findBranch(id) {
    const branches = await this.listBranches()
    return branches.find((branch) => branch.id === id)
  }


  /**
   * Update a branch, returning what the server now holds.
   *
   * The payload is sent under BOTH names because of the asymmetry described
   * above: `branch` is what the DTO declares, `branchName` is its alias and
   * what a read gave you. Sending both means a caller that passes a record
   * straight from `listBranches` gets the update it expected.
   */
  async updateBranch(id, changes) {
    const current = await this.findBranch(id)
    if (!current) throw new Error(`No branch with id ${id} to update.`)

    // 🔴 NOT THE ADDRESS THE READ GAVE, unless the change is to it. The read answers it as an OBJECT (street, suburb,
    // city, province); the write takes one line, flattening an object to its street alone and replacing the stored
    // address with that -- so writing a read straight back cost the branch its suburb, city and province on every
    // restore. A write that names no address keeps the stored one (`BankBranchServiceImpl.updateFromDto`).
    const { physicalAddress: _asRead, ...unchanged } = current
    const payload = {
      ...unchanged,
      ...changes,
      branch: changes.branch ?? changes.branchName ?? current.branchName,
      branchName: changes.branchName ?? changes.branch ?? current.branchName,
    }
    return this.json(await this.put(`/api/banks/branches/${id}`, payload))
  }

  /** Domain hooks for `ApiClient.changeFieldReversibly`. */
  get recordNoun() {
    return 'branch'
  }

  findOne(id) {
    return this.findBranch(id)
  }

  updateOne(id, changes) {
    return this.updateBranch(id, changes)
  }
}
