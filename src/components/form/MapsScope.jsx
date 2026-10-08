import { APIProvider } from '@vis.gl/react-google-maps';

/**
 * GOOGLE MAPS, LOADED ON THE THREE ROUTES THAT USE IT. `03.2b`, R-SEC-D / R-PERF-G.
 *
 * 🔴 **IT USED TO BE IN `main.jsx`, WRAPPED AROUND THE WHOLE APPLICATION.** So every one of
 * the 27 routes — the login screen, the archives, the reports menu, every page with no
 * address field anywhere on it — loaded the Google Maps JavaScript API and shipped the API
 * key to the browser. `03.1` split every route so a user downloads only the page they
 * asked for; leaving a third-party script at the root undoes that for a third-party script,
 * which is the worst place to leave it.
 *
 * **Three routes need it**, and they are the three that render `AddressAutocomplete`:
 * `/members` *(Residential, Bank Work)*, `/members/capture` *(Residential, Bank)* and
 * `/banks` *(the branch modal's Physical and Postal)*.
 *
 * 🔒 **`VITE_GOOGLE_API_KEY` SHIPS IN THE CLIENT BUNDLE BY DESIGN** — it is a browser key
 * and there is no way around that. What follows is that it **must** be referrer- and
 * API-restricted in the Google Cloud console, or anyone who reads the bundle can bill it.
 * That is R-SEC-A, it is a console setting rather than a code change, and its status is
 * recorded in `findings.md` rather than assumed here. Narrowing where the key is *loaded*
 * does not make it secret; it reduces the number of pages that expose it and the number of
 * users who ever fetch it.
 *
 * 🔑 **WHY IT WRAPS THE ROUTE AND NOT THE COMPONENT.** Putting the provider inside
 * `AddressAutocomplete` would be the structural answer, and it does not work: `Members.jsx`
 * renders two of them as **siblings**, so each would mount its own provider and race to load
 * the same script. Wrapping at the route in `App.jsx` gives exactly one provider per page —
 * and, importantly, requires no edit to `CaptureMember.jsx`, which belongs to the Capture
 * Member arc until `04.2`.
 *
 * ⚠️ **A missing key is not a crash.** With no key the provider mounts, `useMapsLibrary`
 * stays `null`, and `AddressAutocomplete` degrades to a plain text field that still accepts
 * and submits a typed address. That is the honest behaviour for a back office whose network
 * may not reach Google, and it is why there is no throw here.
 */
export default function MapsScope({ children }) {
  return (
    <APIProvider apiKey={import.meta.env.VITE_GOOGLE_API_KEY}>{children}</APIProvider>
  );
}
