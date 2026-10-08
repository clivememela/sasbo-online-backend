/** How long the object URL outlives the press: a browser that starts its download late must still find the bytes. */
export const REVOKE_AFTER_MS = 30_000;

/**
 * Hands a fetched file to the browser to save, under `filename`.
 *
 * A report that needs the signed-in user's token is FETCHED, not linked -- the token travels on a `fetch`, never on an
 * `<a href>` -- so the browser's own download never sees the request. This is the other half: the bytes become an
 * object URL, a link nobody sees is pressed, and the URL is let go once the browser has had time to take the bytes.
 * Nothing is stored anywhere.
 * @param {{ blob: Blob, filename?: string | null }} file
 * @param {string} fallbackName the name to use when the server gave none
 */
export default function saveFile({ blob, filename }, fallbackName) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename || fallbackName;
  link.rel = 'noopener';
  link.hidden = true;
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), REVOKE_AFTER_MS);
}
