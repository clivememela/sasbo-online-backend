/**
 * The archives' document trees, searched and counted (RF). Finance Houses and Research Information each held this
 * algorithm under their own names -- bank/category there, category/subcategory here -- line for line the same.
 *
 * A tree is `{ top: files[] | { middle: files[] } }`. A search keeps a whole branch whose NAME matches, and otherwise
 * only the files that do; a branch with nothing left is dropped. An empty or blank query is the whole tree.
 */

/** @param {Record<string, string[] | Record<string, string[]>>} tree @param {string} searchQuery */
export function filterArchive(tree, searchQuery) {
  if (!searchQuery.trim()) return tree;

  const query = searchQuery.toLowerCase();
  const filtered = {};

  Object.entries(tree).forEach(([top, branch]) => {
    const topMatches = top.toLowerCase().includes(query);

    if (Array.isArray(branch)) {
      const matchedFiles = branch.filter((file) => file.toLowerCase().includes(query));
      if (topMatches || matchedFiles.length > 0) filtered[top] = topMatches ? branch : matchedFiles;
      return;
    }

    const matched = {};
    Object.entries(branch).forEach(([middle, files]) => {
      const middleMatches = middle.toLowerCase().includes(query);
      const matchedFiles = files.filter((file) => file.toLowerCase().includes(query));
      if (middleMatches || matchedFiles.length > 0) matched[middle] = middleMatches ? files : matchedFiles;
    });
    if (topMatches || Object.keys(matched).length > 0) filtered[top] = topMatches ? branch : matched;
  });

  return filtered;
}

/** Every file in a tree, at either depth. */
export function countArchive(tree) {
  return Object.values(tree).reduce((sum, branch) => {
    if (Array.isArray(branch)) return sum + branch.length;
    return sum + Object.values(branch).reduce((s, files) => s + files.length, 0);
  }, 0);
}
