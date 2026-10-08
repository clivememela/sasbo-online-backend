import { Download, FileText } from 'lucide-react';

import { Button, TextInput } from '../form';

/**
 * The pieces the three archive pages each drew by hand, identically (RF). The folder rows are NOT here: Research's
 * sub-folder button differs from Finance Houses' (no `type`, no `aria-expanded`, no focus ring, an `mb-2` wrapper), and
 * sharing it would change what it does and what it paints -- a note for that page, not a refactor.
 */

/**
 * The search, its Clear, and what it found. `TextInput kind="search"` brings the search keyboard, the "Search" return
 * key on a phone and the 16px rule; the Clear is a named button, where an unnamed `<X>` icon used to be.
 */
export function ArchiveSearchBar({ query, onChange, found }) {
  return (
    <div className="mb-6 max-w-xl">
      <div className="flex items-end gap-2">
        <TextInput
          label="Search"
          kind="search"
          value={query}
          onChange={(e) => onChange(e.target.value)}
          placeholder="Search documents and folders..."
          className="flex-1"
        />
        {query && (
          <Button variant="ghost" onClick={() => onChange('')}>Clear</Button>
        )}
      </div>
      {query && found && (
        <p className="text-sm text-ink-muted mt-2">{found}</p>
      )}
    </div>
  );
}

/** One document and its Download, at the top of a folder or `nested` one level down, where it is set smaller. */
export function ArchiveFile({ name, nested = false }) {
  const size = nested ? 14 : 16;
  return (
    <div className="flex items-center justify-between rounded-md px-3 py-1.5 transition-colors hover-hover:hover:bg-surface">
      <div className="flex items-center gap-2 flex-1 min-w-0">
        <FileText size={size} className="text-brand-ink flex-shrink-0" />
        <span className={nested ? 'text-xs truncate' : 'text-sm truncate'}>{name}</span>
      </div>
      <Button size="icon" variant="ghost" aria-label={`Download ${name}`} className="ml-2 shrink-0">
        <Download size={size} aria-hidden="true" className="text-brand-ink" />
      </Button>
    </div>
  );
}
