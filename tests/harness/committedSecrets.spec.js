import { expect, test } from '@playwright/test'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

import {
  ALLOWLIST,
  PATH_EXCLUSIONS,
  gitMetadata,
  isExcluded,
  scanForSecrets,
  trackedFiles,
} from '../../support/committedSecrets.js'
import { assertNonVacuousScan } from '../../support/assertNonVacuousScan.js'

/**
 * NO SECRET GETS COMMITTED TO THIS REPOSITORY.
 *
 * WHY IT IS HERE AND NOT ONLY IN THE OTHER TWO
 * A credential guard that exists in one repository and not the others protects
 * the repository that happened to get it. This is the third of three: the
 * browser's found a live Google API key in its `.env` and carries it on a named
 * allowlist; the server's allowlist is empty; so is this one.
 *
 * WHY THIS REPOSITORY MATTERS MOST FOR THIS GUARD, not least
 * It is the one WE are writing, and it is the one that will grow credentials:
 * an end-to-end suite wants a login, a seeded user, a service token. This guard
 * lands BEFORE those arrive, which is the only time a guard like this is cheap.
 *
 * WHY IT NEEDS NO BROWSER AND NO STACK
 * It shells out to `git ls-files` and reads text, so it belongs to the harness
 * lane and runs with nothing brought up.
 *
 * AND WHY IT RUNS IN THE CONTAINER TOO
 * The harness lane of the merged gate runs inside the suite container, where a
 * worktree's git metadata is a host path that does not exist. There the scan
 * reads the working tree instead, and says so in this report -- see
 * `gitMetadata`. It is never skipped.
 */
const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..')

test.describe('committed secrets', () => {
  test('the scan found this repository, and says where its file list came from', () => {
    const tracked = trackedFiles(REPO_ROOT)
    test.info().annotations.push({ type: 'files scanned, from', description: tracked.from })

    // What this catches is the list coming back empty. That reports no secrets
    // and passes -- the failure mode of every discovery guard, which is to be
    // green because it looked at nothing.
    assertNonVacuousScan(tracked.files, 10, `files to scan, from ${tracked.from}`)
  })

  test('wherever git can be asked, git is what answered', () => {
    // 🔴 The fallback is for a checkout whose metadata is not on this filesystem,
    // and ONLY that. A guard that quietly walked the disk while git sat right
    // there would lose the index fallback below and never say so. Asserted in
    // both places this runs -- natively, and in the container -- not skipped in
    // either.
    const metadata = gitMetadata(REPO_ROOT)
    expect(trackedFiles(REPO_ROOT).from).toBe(
      metadata.reachable ? 'git ls-files' : `the working tree, because ${metadata.why}`,
    )
  })

  test('only a checkout without reachable git metadata is read from the working tree', () => {
    const root = mkdtempSync(join(tmpdir(), 'sasbo-git-metadata-'))
    try {
      const checkout = (name) => {
        const dir = join(root, name)
        mkdirSync(dir)
        return dir
      }

      const withDirectory = checkout('clone')
      mkdirSync(join(withDirectory, '.git'))
      expect(gitMetadata(withDirectory)).toEqual({ reachable: true })

      const metadata = checkout('metadata')
      const worktree = checkout('worktree')
      writeFileSync(join(worktree, '.git'), `gitdir: ${metadata}\n`)
      expect(gitMetadata(worktree), 'a worktree whose metadata resolves').toEqual({ reachable: true })

      // The host path a Windows worktree names, as a Linux container sees it.
      const mounted = checkout('mounted')
      const hostPath = ['C:', 'host', 'checkout', '.git', 'worktrees', 'mounted'].join('/')
      writeFileSync(join(mounted, '.git'), `gitdir: ${hostPath}\n`)
      expect(gitMetadata(mounted).reachable).toBe(false)
      expect(gitMetadata(mounted).why).toContain(hostPath)

      expect(gitMetadata(checkout('bare')).reachable, 'no .git at all').toBe(false)

      const garbled = checkout('garbled')
      writeFileSync(join(garbled, '.git'), 'not a pointer\n')
      expect(gitMetadata(garbled).reachable, 'a .git file with no gitdir line').toBe(false)
    } finally {
      rmSync(root, { recursive: true, force: true })
    }
  })

  test('it scans real files rather than excluding everything', () => {
    const scanned = trackedFiles(REPO_ROOT).files.filter((path) => !isExcluded(path))

    // The mirror image of the exclusion list. An over-broad exclusion silences
    // this guard exactly as effectively as a raised threshold would, and the
    // finding count alone would never show it.
    assertNonVacuousScan(scanned, 10, 'files actually scanned after path exclusions')
    expect(scanned.some((path) => path.endsWith('.js'))).toBe(true)
  })

  test('every path exclusion carries its justification', () => {
    // An exclusion nobody can see is where the real key hides next time.
    expect(PATH_EXCLUSIONS.length).toBeGreaterThan(0)
    for (const exclusion of PATH_EXCLUSIONS) {
      expect(exclusion.why, `${exclusion.match} has no justification`).toBeTruthy()
      expect(exclusion.why.length).toBeGreaterThan(30)
    }
  })

  test('the lockfile is excluded BEFORE anything is scanned', () => {
    // 🔴 The order is the design. package-lock.json holds hundreds of integrity
    // hashes, each a high-entropy base64 string indistinguishable from a key.
    // Scanning first and filtering after gives a first run with hundreds of
    // findings -- and a run like that is not triaged, it is silenced, usually
    // by loosening the very heuristic that would have found the real one.
    expect(isExcluded('package-lock.json')).toBe(true)
    expect(isExcluded('support/env.js')).toBe(false)
  })

  test('the allowlist is empty, and that is asserted rather than assumed', () => {
    // We own every file in this repository, so there is no incumbent's
    // credential to work around. An entry appearing later is a decision about a
    // real credential and deserves a reviewer, not a commit.
    expect(
      ALLOWLIST,
      'Someone has allowlisted a file here. This repository is ours and should never ' +
        'need one -- an end-to-end suite that wants a credential should take it from the ' +
        'environment, not from a tracked file.',
    ).toEqual([])
  })

  test('no secret is committed anywhere in this repository', () => {
    const findings = scanForSecrets(REPO_ROOT).map(
      (finding) => `${finding.at}  [${finding.signature}]`,
    )

    expect(
      findings,
      'A secret has been COMMITTED. It is now on every clone, every fork and every ' +
        'backup of this repository, and deleting the file does not remove it from ' +
        'history. Rotate the credential first -- that is the only step that actually ' +
        'revokes it -- then remove it from the working tree.',
    ).toEqual([])
  })
})
