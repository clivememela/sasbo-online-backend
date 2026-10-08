// @vitest-environment jsdom -- the address search when Google answers, and when it does not
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, screen } from '@testing-library/react'
import { useState } from 'react'

import AddressAutocomplete from './AddressAutocomplete'
import { renderWithProviders } from '../../test/renderWithProviders'
import { setupUser } from '../../test/user'
import { deferred } from '../../test/deferred'

/**
 * THE SUGGESTION LIST, DRIVEN. `06.4`.
 *
 * `src/test/setup.js` mocks `useMapsLibrary` to `null` for the whole suite, so no suggestion ever arrives in
 * `AddressAutocomplete.test.jsx` -- and that file says so. This one replaces the mock with a library that is
 * present, and a `google.maps.places` whose lookup this file controls, so both outcomes can be driven.
 *
 * 🔴 A lookup that FAILED looked exactly like an address Google did not know: the list never opened, and the
 * person typing waited for suggestions that were not coming. The case below is paired with the lookup that
 * works -- without it, a field that always said the suggestions failed would pass, and so would a mock that
 * never reached the component at all.
 */

// 🔑 ONE LIBRARY OBJECT, NOT ONE PER RENDER. The component's lookup is a `useCallback` keyed on the library, and
// the debounce effect is keyed on the lookup: a mock returning `({})` hands it a new library on every render, so
// every render re-arms the lookup, whose own state update renders again -- the test never settles. The real hook
// returns the same library once it has loaded, and so does this.
vi.mock('@vis.gl/react-google-maps', () => {
  const places = {}
  return {
    APIProvider: ({ children }) => children,
    useMapsLibrary: () => places,
    useMap: () => null,
    Map: () => null,
    Marker: () => null,
    AdvancedMarker: () => null,
  }
})

const fetchAutocompleteSuggestions = vi.fn()

function Harness() {
  const [value, setValue] = useState('')
  return <AddressAutocomplete label="Residential Address" value={value} onChange={(event) => setValue(event.target.value)} />
}

describe('AddressAutocomplete — the suggestion lookup', () => {
  beforeEach(() => {
    /*
     * 🔴 THE 300ms DEBOUNCE IS DRIVEN, NOT WAITED FOR (P10's refactor pass). These tests used to let the
     * component's REAL `setTimeout` run out inside a find-query's 1000ms window -- about 700ms of margin on a
     * box that has been seen at 200 MB free, which is a flake with a long fuse. Only the timer functions are
     * faked, so promises and MutationObservers stay real; each test advances the debounce itself and then
     * reads the screen SYNCHRONOUSLY, so nothing in this file depends on how fast the machine is.
     */
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    // ⚠️ AND TESTING LIBRARY HAS TO BE TOLD. Its async wrapper drains with a `setTimeout(0)` and advances it only when
    // it finds a global `jest`; under vitest there is none, so that timer is faked and never fires, and every test
    // HANGS to the 20s timeout -- which is what the first version of this did. The shim points it at vitest's clock.
    globalThis.jest = { advanceTimersByTime: (ms) => vi.advanceTimersByTime(ms) }
    // The component reports the failure through the sink as well as showing it.
    vi.spyOn(console, 'error').mockImplementation(() => {})
    globalThis.google = {
      maps: { places: { AutocompleteSessionToken: class {}, AutocompleteSuggestion: { fetchAutocompleteSuggestions } } },
    }
  })

  afterEach(() => {
    vi.useRealTimers()
    delete globalThis.jest
    delete globalThis.google
    fetchAutocompleteSuggestions.mockReset()
  })

  it('that failed says so, and keeps what was typed', async () => {
    fetchAutocompleteSuggestions.mockRejectedValue(new Error('REQUEST_DENIED'))
    const user = setupUser({ advanceTimers: vi.advanceTimersByTime })
    renderWithProviders(<Harness />)

    await user.type(screen.getByLabelText('Residential Address'), '12 Synthetic Street')
    await act(() => vi.advanceTimersByTimeAsync(300))

    expect(screen.getByText(/Address suggestions could not be loaded/i)).toBeInTheDocument()
    expect(screen.getByLabelText('Residential Address')).toHaveValue('12 Synthetic Street')
  })

  it('the must-pass: a lookup that works offers its suggestions and says nothing of the kind', async () => {
    fetchAutocompleteSuggestions.mockResolvedValue({
      suggestions: [
        {
          placePrediction: {
            placeId: 'synthetic-1',
            text: { text: '12 Synthetic Street, Johannesburg' },
            structuredFormat: { mainText: { text: '12 Synthetic Street' }, secondaryText: { text: 'Johannesburg' } },
          },
        },
      ],
    })
    const user = setupUser({ advanceTimers: vi.advanceTimersByTime })
    renderWithProviders(<Harness />)

    await user.type(screen.getByLabelText('Residential Address'), '12 Synthetic Street')
    await act(() => vi.advanceTimersByTimeAsync(300))

    expect(screen.getByRole('option', { name: /12 Synthetic Street/ })).toBeInTheDocument()
    expect(screen.queryByText(/could not be loaded/i)).not.toBeInTheDocument()
  })

  it('suggestions for a query you backed out of do not come back when you type again', async () => {
    /*
     * 🔴 THE SEMANTIC `10.4` HAD TO KEEP, AND THE ONE THE OBVIOUS FIX WOULD HAVE LOST.
     *
     * Below three characters there are no suggestions: the list was CLEARED by an effect, which
     * `react-hooks/set-state-in-effect` makes an error this phase. The natural replacement -- hide
     * the list while the query is short, i.e. derive it -- does not clear anything. It only stops
     * SHOWING the old list, so the moment the query is long enough again the suggestions for the
     * query you abandoned reappear under the one you are now typing, until the new lookup answers.
     * Today they are gone; they must stay gone.
     *
     * The second lookup is HELD until this test releases it. Without that, on a slow box the
     * debounce could fire and the fresh answer replace the stale one before the assertion ran --
     * and the test would pass against the very bug it is here to catch.
     */
    const first = {
      suggestions: [
        {
          placePrediction: {
            placeId: 'old',
            text: { text: '12 Synthetic Street, Johannesburg' },
            structuredFormat: { mainText: { text: '12 Synthetic Street' }, secondaryText: { text: 'Johannesburg' } },
          },
        },
      ],
    }
    const second = deferred()
    const releaseSecond = () =>
      second.resolve({
        suggestions: [
          {
            placePrediction: {
              placeId: 'new',
              text: { text: '12 Sandton Drive, Sandton' },
              structuredFormat: { mainText: { text: '12 Sandton Drive' }, secondaryText: { text: 'Sandton' } },
            },
          },
        ],
      })
    fetchAutocompleteSuggestions.mockResolvedValueOnce(first).mockImplementationOnce(() => second.promise)

    const user = setupUser({ advanceTimers: vi.advanceTimersByTime })
    renderWithProviders(<Harness />)
    const input = screen.getByLabelText('Residential Address')

    await user.type(input, '12 Synthetic')
    await act(() => vi.advanceTimersByTimeAsync(300))
    expect(screen.getByRole('option', { name: /12 Synthetic Street/ })).toBeInTheDocument()

    // Back out below three characters: the old list goes.
    await user.clear(input)
    await user.type(input, '12')
    expect(screen.queryByRole('option')).not.toBeInTheDocument()

    try {
      // Long enough again -- and the second lookup is not allowed to answer yet.
      await user.type(input, ' S')
      // The debounce DOES fire here and the second lookup IS issued -- held, not skipped -- so the
      // assertion below is about the stale list, not about a lookup that never started.
      await act(() => vi.advanceTimersByTimeAsync(300))

      expect(
        screen.queryByRole('option', { name: /12 Synthetic Street/ }),
        'the suggestions for the abandoned query came back under the new one',
      ).toBeNull()
    } finally {
      releaseSecond()
    }
    await act(() => vi.advanceTimersByTimeAsync(0))

    // The must-pass: the NEW query's suggestion does arrive, so "nothing shown" above was the
    // stale list staying gone, not the lookup having stopped working.
    expect(screen.getByRole('option', { name: /12 Sandton Drive/ })).toBeInTheDocument()
  })
})
