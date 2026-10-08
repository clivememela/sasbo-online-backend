# `features/`

**Every `.feature` file in the system lives here** — not in the client repository, not in the server
repository. Neither of those carries a Cucumber dependency, and neither should.

A scenario in this directory describes a **journey, never a mechanism**:

```gherkin
When the branch is saved          # yes
When a POST is sent to /api/...   # no
```

A scenario a stakeholder cannot read has paid Cucumber's whole cost and taken none of its benefit —
and the second form also welds the test to an endpoint path, so a rename breaks a file that should
not have known the path existed.

**A scenario that cannot be written honestly is not written.** Skipped, with the reason stated, beats
one that passes while asserting nothing. In particular: a scenario claims only an identity the
server checks. Until the October build the application's sign-in validated nothing, so `Given I am
logged in` would have passed while asserting precisely nothing — worse than not having it — and no
scenario claimed one. Now a scenario signs in as a demonstration user by POSITION ("Given the Chief
Financial Officer is signed in"), and the server decides what that position may do.

Steps are defined in [`../steps/`](../steps), and every one of them is a one-liner that delegates to
a page object or an API client.
