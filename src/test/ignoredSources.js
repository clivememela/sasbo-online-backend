import { Buffer } from 'node:buffer'
import { execFileSync } from 'node:child_process'

import { trackedFiles } from './committedSecrets.js'

/**
 * Which TRACKED SOURCE FILES are shadowed by a .gitignore rule.
 *
 * WHY A GUARD EXISTS FOR THIS AT ALL
 * Tailwind v4's automatic source detection SKIPS anything .gitignore matches. A
 * bare pattern in .gitignore matches a directory of that name AT ANY DEPTH, so
 * a rule written for one directory at the repository root silently deletes
 * another directory's styling. That is not hypothetical here: `.gitignore` had
 * a bare `reports`, added for the test output `client/reports/`, which also matched
 * `src/pages/reports/`. Every tracked file under it became invisible to the scanner,
 * every class unique to those files was absent from the built CSS, and three headings
 * on /reports/organizer-report rendered `text-white` on a white card -- invisible.
 * The count is deliberately not written here: four at the trunk `01819d2`, three after
 * `9f2c233` deleted the dead `OrganizerReport.css`. Register row A42 carries it.
 *
 * 🔴 NOTHING IN THE ORDINARY GATE CAN SEE THIS. `npm run build` is green either
 * way; the CSS is just quietly thinner. `npm test` passes; jsdom has no CSS.
 * The failure is invisible to every check the repository already had, which is
 * why it survived from the commit that introduced it until a contrast pass
 * measured rendered pixels and found a 1.00:1 pair.
 *
 * 🔴 THE TRAP THIS ENCODES, and the reason it is `--no-index`:
 * `git check-ignore <path>` reports NOTHING for a TRACKED file. Every one of
 * these files is tracked, so the obvious command answers "not ignored" while
 * Tailwind is ignoring them. A guard written the obvious way would be green and
 * worthless.
 *
 * Asking git rather than re-implementing gitignore matching is deliberate:
 * precedence, negation and depth are exactly the parts that are easy to get
 * subtly wrong, and a sweep that gets them wrong reports CLEAN. It also means
 * every ignore SOURCE is covered -- `.git/info/exclude` and `core.excludesFile`
 * as well as the committed `.gitignore` -- which matters here specifically,
 * because this workspace's standing rule is that tooling is excluded via
 * `.git/info/exclude`, where no committed file would show the shadowing.
 *
 * 🔁 THERE IS A SIBLING, and it is not the gate. `mockups/design/tools/ignore_sweep.py`
 * at the workspace root asks the same question of ANY worktree at whatever SHA is
 * checked out -- which this cannot do, since it only ever sees its own repository.
 * That one is a diagnostic run by hand; THIS one runs in `npm test` and is what holds
 * the line. Neither is generated from the other, so a change to the parsing or the
 * byte-handling belongs in both.
 */

/**
 * Every tracked file Tailwind could need to scan for class candidates.
 *
 * `index.html` is in the set deliberately: Tailwind scans it too, and it is the one
 * such file that lives outside `src/`.
 */
export function trackedSources(repoRoot) {
  return trackedFiles(repoRoot, 'src', 'index.html')
}

/**
 * The subset of `paths` that .gitignore matches, as `{ path, rule }`.
 *
 * Paths go to git as BYTES on stdin. Handing it a string through a text stream
 * on Windows appends a CR to every line, and git then reports paths that do not
 * exist -- which reads as a pile of findings rather than as an encoding bug.
 */
export function shadowedSources(repoRoot, paths) {
  // 🔴 check-ignore exits 1 when NOTHING matches -- the GOOD case -- and execFileSync
  // throws on a non-zero exit. Catching it is not defensive noise: without this the
  // guard throws precisely when the repository is clean, and the obvious "fix" for a
  // guard that only fails is to delete it.
  let result = ''
  try {
    result = execFileSync('git', ['check-ignore', '-v', '--no-index', '--stdin'], {
      cwd: repoRoot,
      input: Buffer.from(paths.join('\n'), 'utf8'),
      encoding: 'utf8',
    })
  } catch (error) {
    // Exit 1 means "no path matched" and carries no output. Anything else is a real
    // failure -- a missing git, a bad repo -- and must NOT be read as "clean".
    if (error.status !== 1) throw error
    result = error.stdout ?? ''
  }

  return result
    .split('\n')
    .filter(Boolean)
    .map((line) => {
      const tab = line.lastIndexOf('\t')
      return { rule: line.slice(0, tab), path: line.slice(tab + 1) }
    })
}
