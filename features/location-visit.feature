Feature: The Location Visit form — a visit saved as a draft, and a draft discarded
  Since the 5 Oct Show & Tell an organiser who may capture visits saves the
  Location Visit form as a draft, comes back to it, and submits it; a draft that
  is not wanted is discarded. A submitted visit is the union's record, so no
  journey here submits one: saving and discarding leave nothing behind.

  No workplace is named here: the scenario reads, from the server when it runs,
  a workplace of the Organiser's area.

  Scenario: An organiser saves a visit as a draft, then discards it, and it is gone
    Given the Organiser is signed in
    And a workplace in the Organiser's area
    When the Organiser saves a visit to that workplace as a draft
    Then the form says the draft is saved, and the server holds it as the Organiser's draft
    When the Organiser discards that draft
    Then the form says the draft is discarded, and the server holds it no more
