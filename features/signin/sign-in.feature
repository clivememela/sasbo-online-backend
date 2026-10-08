Feature: Signing in — who is working, and what their position lets them do
  SASBO Online is used by staff in positions -- head office, the Chief
  Financial Officer, the regions' secretaries, the organisers -- each seeing the
  part of SASBO their position covers. Until SASBO's own accounts exist the link
  stays open: anyone may carry on without signing in, and only what needs a
  permission asks for one. These journeys sign in as the demonstration users the
  stack seeds, one for each position, and as a user an administrator creates.

  Scenario: Anyone can carry on without signing in while sign-in is open
    When someone opens SASBO Online
    Then they are asked to sign in, and offered to continue without signing in
    When they continue without signing in
    Then the first screen is shown, with a way to sign in

  Scenario: A Head Office Administrator signs in and is shown by name and position
    When the Head Office Administrator signs in on the sign-in screen
    Then the first screen is shown
    And the header shows who is signed in, by name and position

  # Signing out ends the session on the server, not only on the screen: on a
  # shared office computer, a session left alive is the next person's.
  Scenario: Signing out ends the session on the server as well as on the screen
    Given the Head Office Administrator has signed in on the sign-in screen
    When they sign out
    Then the sign-in screen is shown
    And the session they signed out of is refused by the server

  # The 5 Oct Show & Tell: "an administrator creating users with a position and
  # an area". The password the dialog shows once is temporary: the new user
  # chooses their own the first time they sign in.
  Scenario: A System Administrator creates a user with a position and an area
    Given the System Administrator is signed in
    When they create a user who is an Organiser in area L
    Then the new user is listed as an Organiser who sees area L, waiting on a temporary password
    And the new user can sign in with the temporary password, and must choose their own

  Scenario: A user without the permission is told what is needed, and the server refuses them too
    Given the Organiser is signed in
    When they open the users
    Then they are told that managing users needs a permission they do not hold
    And the server refuses them the users, naming the permission
