Feature: When the backend is unavailable, Month end says its figures could not be read
  The month's figures are read from the server. When the server cannot be
  reached they must say that they could not be read -- never that the union has
  no members -- and the rest of Month end must still stand. The first screen,
  where everybody starts, asks the server for nothing since its figures moved to
  Month end at the 5 Oct Show & Tell, so its modules are there whether the
  server answers or not.

  These scenarios stop the API container, assert what the screen says, and
  start it again. They run alone, after everything else has finished, because
  they take down the stack every other scenario is using.

  Scenario: The first screen shows its modules while the backend is unavailable
    Given the API is stopped
    When an administrator opens the first screen
    Then the first screen shows the modules "Admin Menu", "Members" and "Debit Order Deductions"

  # The register once told an administrator, confidently, that there were no
  # members, because a failed fetch left an empty list (A27). A figure strip
  # that reads nought would be the same defect on the page that counts them.
  Scenario: Month end shows its statistics and says its figures are not available
    Given the API is stopped
    When an administrator opens the Month end page
    Then the page shows its statistics
    And the month's figures say they could not be read
    And no figure on Month end reads nought
    And the month's figures offer a way to try again

  Scenario: The figures come back when the backend does
    Given the API is stopped
    And an administrator has Month end open, its figures not available
    When the API is started again
    And they ask the month's figures to try again
    Then the page shows the month's figures

  # A request that is never answered is not a failure the server reported: the
  # screen has to give up by itself. Month end asks for its months and then for
  # its figures, so two of the client's ten-second reads stand between it and
  # the failure -- asserted at forty seconds: twice that, so a slow host does
  # not decide it, and a third of the proxy's two sixty-second waits, so waiting
  # on the proxy still fails it.
  Scenario: Month end gives up on its figures rather than waiting for the proxy
    Given the API is stopped
    When an administrator opens the Month end page
    Then the month's figures say they could not be read before the proxy would give up
