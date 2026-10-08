// @vitest-environment jsdom -- renders a real button and drives it with a keyboard
import { describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'

import Button, { BUTTON_SIZES, BUTTON_VARIANTS } from './Button'
import { renderWithProviders } from '../../test/renderWithProviders'
import { expectNoA11yViolations } from '../../test/assertions'
import { setupUser } from '../../test/user'

/**
 * THE FOUR TESTS EVERY PRIMITIVE GETS (testingAndErrorHandling.md §2a):
 * the eight states, the keyboard, axe in both themes, and one mutation-checked
 * convention.
 *
 * ⚠️ TWO OF THE EIGHT STATES DO NOT APPLY TO A BUTTON, and this file says so rather
 * than asserting something vacuous to make a count. `invalid` belongs to a control
 * that holds a value; `empty` to one that renders a list. A button holds neither, so
 * six states are asserted and the two absences are recorded here -- which is what
 * contract ⑬ asks of a box that diverges from the plan.
 */

describe('Button — the states', () => {
  it('renders every variant and size without a raw colour utility or a dark: twin', () => {
    const { container } = renderWithProviders(
      <div>
        {BUTTON_VARIANTS.map((variant) => (
          <Button key={variant} variant={variant}>
            {variant}
          </Button>
        ))}
        {BUTTON_SIZES.map((size) => (
          <Button key={size} size={size} aria-label={size}>
            {size}
          </Button>
        ))}
      </div>,
    )

    // 🔴 The token rule, asserted on the RENDERED class list rather than on the source.
    // A component that reintroduces `bg-cyan-600` or a `dark:` twin has left the token
    // layer behind, and the dark theme then stops following the direction silently.
    const classes = [...container.querySelectorAll('button')]
      .flatMap((button) => [...button.classList])
      .join(' ')

    expect(
      classes,
      'A button is carrying a `dark:` twin. Every colour here comes from a token that ' +
        'is already redefined under `.dark`, so a hand-written twin is a second, ' +
        'competing answer that the token layer cannot reach.',
    ).not.toMatch(/\bdark:/)

    expect(
      classes,
      'A button is carrying a literal Tailwind palette colour. After P1 the only ' +
        'source of colour is the token layer (contract ①).',
    ).not.toMatch(/\b(bg|text|border|ring)-(slate|gray|zinc|cyan|red|blue|green|amber)-\d{2,3}\b/)
  })

  it('disabled: refuses the click and is not in the tab order', async () => {
    const user = setupUser()
    const onClick = vi.fn()
    renderWithProviders(
      <Button disabled onClick={onClick}>
        Save member
      </Button>,
    )

    const button = screen.getByRole('button', { name: /save member/i })
    expect(button).toBeDisabled()
    await user.click(button)
    expect(
      onClick,
      'A disabled button fired its handler. Disabled is the state that stops a second ' +
        'submit; a disabled button that still calls back is decoration.',
    ).not.toHaveBeenCalled()
  })

  it('loading: announces itself busy, refuses the click, and KEEPS its accessible name', async () => {
    const user = setupUser()
    const onClick = vi.fn()
    renderWithProviders(
      <Button loading onClick={onClick}>
        Save member
      </Button>,
    )

    // 🔑 Found by name, which is the assertion: a spinner that REPLACES the label
    // leaves a screen-reader user with an unnamed busy control exactly when the page
    // is doing something on their behalf.
    const button = screen.getByRole('button', { name: /save member/i })
    expect(button).toHaveAttribute('aria-busy', 'true')
    expect(button).toBeDisabled()

    await user.click(button)
    expect(onClick).not.toHaveBeenCalled()
  })

  // Named for what it asserts and no more: focus-visible is the mutation check below,
  // and a title claiming coverage this test does not have is how a suite starts lying.
  it('hover, active and disabled are declared, not left to the caller', () => {
    renderWithProviders(<Button variant="primary">Go</Button>)
    const classes = screen.getByRole('button', { name: 'Go' }).className

    // `hover-hover:` and not bare `hover:` — R-RESP-H. On a touch screen a bare hover
    // latches after a tap and stays lit, which reads as a rendering fault.
    expect(classes).toMatch(/hover-hover:hover:/)
    expect(classes).toMatch(/active:/)
    expect(classes).toMatch(/disabled:/)
  })
})

describe('Button — the keyboard', () => {
  it('is reachable by Tab and fires on both Enter and Space', async () => {
    const user = setupUser()
    const onClick = vi.fn()
    renderWithProviders(<Button onClick={onClick}>Print</Button>)

    await user.tab()
    const button = screen.getByRole('button', { name: 'Print' })
    expect(
      button,
      'Tab did not reach the button. A control the keyboard cannot reach is a control ' +
        'a keyboard-only administrator cannot use, and axe cannot see this (T17).',
    ).toHaveFocus()

    await user.keyboard('{Enter}')
    await user.keyboard(' ')
    expect(onClick).toHaveBeenCalledTimes(2)
  })
})

describe('Button — accessibility, both themes', () => {
  it('has no violations in light or dark', async () => {
    const { container } = renderWithProviders(
      <div>
        <Button variant="primary">Save</Button>
        <Button variant="danger">Delete</Button>
        <Button size="icon" aria-label="Close" />
        <Button loading>Saving</Button>
        <Button disabled>Unavailable</Button>
      </div>,
    )

    await expectNoA11yViolations(container)

    document.documentElement.classList.add('dark')
    try {
      await expectNoA11yViolations(container)
    } finally {
      document.documentElement.classList.remove('dark')
    }
  })
})

describe('Button — the convention, mutation-checked', () => {
  /**
   * 🔴 THE MUTATION CHECK. A test that only asserts the ring is present passes for a
   * component that never had one removed; the check is that REMOVING it goes red.
   * Proved by deleting `focus-visible:ring-2` from `Button.jsx` and running this file:
   * this assertion, and only this assertion, fails.
   *
   * conventions §7: *"outline-none without a focus-visible: replacement is a defect"*,
   * and this button sets `focus:outline-none`, so the ring is the only thing between a
   * keyboard user and an invisible focus position.
   */
  it('a focus ring survives the outline being removed', () => {
    renderWithProviders(<Button>Focus me</Button>)
    const classes = screen.getByRole('button', { name: 'Focus me' }).className

    expect(
      classes,
      'This button removes the browser outline (`focus:outline-none`) and must replace ' +
        'it. Without `focus-visible:ring-*` a keyboard user has no idea where they are ' +
        'on the page — and nothing errors, so only this assertion can catch it.',
    ).toMatch(/focus-visible:ring-2/)

    expect(classes).toMatch(/focus-visible:ring-brand-ring/)
  })

  it('an unknown variant THROWS rather than quietly rendering the default one', () => {
    // 🔴 THE REFACTOR PASS FOUND A REAL ONE WITH THIS. `VARIANTS[variant] ?? VARIANTS.default`
    // meant `<Button variant="primry">` rendered a neutral button and nobody found out --
    // the same silent degrade `fieldKinds.js` spends a paragraph refusing and `Table` throws
    // over. Turning it on immediately surfaced `variant="secondary"` in `BankBranchModal`,
    // a variant this component has never had, rendering as `default` since it was written.
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    try {
      expect(() => renderWithProviders(<Button variant="secondry">Oops</Button>)).toThrow(
        /Unknown Button variant/,
      )
      expect(() => renderWithProviders(<Button size="enormous">Oops</Button>)).toThrow(
        /Unknown Button size/,
      )
    } finally {
      consoleError.mockRestore()
    }
  })

  it('a coarse pointer gets a 44px hit area without the control growing', () => {
    renderWithProviders(<Button size="sm">Tiny</Button>)
    // `touch-target` is the utility (index.css) that applies a 44px ::after only under
    // `@media (pointer: coarse)`, so desktop density is provably untouched — T6.
    expect(screen.getByRole('button', { name: 'Tiny' }).className).toMatch(/\btouch-target\b/)
  })
})
