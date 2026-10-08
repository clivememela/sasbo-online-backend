import Sheet from '../common/Sheet.jsx';
import DataState from '../common/DataState.jsx';
import { Button } from '../form';

/**
 * 🔴 A SEARCH THAT MATCHED NOTHING LEFT THE PAGE BLANK. The list simply stopped
 * rendering, and the only signal was the "Found 0 …" line above -- which itself only
 * appears while a search is typed, and which reads as a fact about the archive rather
 * than about the query. A blank page with a filled search box is the shape a user
 * reads as "broken", and there was no way out of it but to clear the field by hand.
 *
 * One component for the three archives, which carried this block character for character.
 */
export default function ArchiveEmpty({ searchQuery, onClear }) {
  return (
    <Sheet>
      <DataState
        state="empty"
        title={searchQuery ? "Nothing matches that search." : "Nothing here yet."}
        message={searchQuery ? "Try fewer words, or clear the search to see everything again." : null}
        action={searchQuery
          ? <Button variant="default" onClick={onClear}>Clear the search</Button>
          : null}
      />
    </Sheet>
  );
}
