/**
 * What a choice control shows before a choice is made -- loading, unavailable, empty, or the caller's own placeholder.
 * Once, for both select primitives, which spelt it out identically. `06.4`, F-P54: a list that failed is not an empty
 * one, so it reads "Unavailable", never the empty label.
 *
 * ⚠️ THE THREE BOOLEANS IT READS STAY INLINE IN EACH PRIMITIVE. Returned from a shared helper, they reach
 * `SearchableSelect`'s `useCallback` as fields of an object the React Compiler cannot prove unmutated, and it skips
 * the component (`preserve-manual-memoization`). Three lines each is the cheaper duplicate.
 */
export function choicePlaceholder({ loading, failed, isEmpty }, emptyLabel, placeholder) {
  return loading ? 'Loading…' : failed ? 'Unavailable' : isEmpty ? emptyLabel : placeholder;
}
