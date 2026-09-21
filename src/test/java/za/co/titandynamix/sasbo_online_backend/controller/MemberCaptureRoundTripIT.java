package za.co.titandynamix.sasbo_online_backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 🔴 THE ARC'S GATE. Capture a member through the real HTTP endpoint, against a
 * real PostgreSQL, then read the member back and check what actually survived.
 *
 * <h2>Why this test and not the ones that already exist</h2>
 * There is a client-side round-trip already ({@code CaptureMember.roundtrip.test.jsx}),
 * and it is a good test of a different thing: it asserts the <b>request body</b>
 * under MSW. It therefore <b>cannot see a field that is submitted, accepted with a
 * 200, and silently dropped</b> — which is register A31, and 24 of the 64 fields
 * this form submits. Only entity-to-column, through a real schema, can see that.
 *
 * <p>And a green unit suite is not the gate either: the two blockers below were
 * both invisible to it, because neither the entity binding nor Jackson's array
 * handling is exercised without an actual request.
 *
 * <h2>The payload is the REAL one</h2>
 * {@link #captureFormPayload()} is what {@code CaptureMember.jsx} actually sends,
 * including the fields with no column and the two empty arrays. That is
 * deliberate and load-bearing: a tidied-up synthetic payload omits
 * {@code cosatuPositions: []}, and omitting it is exactly what would have hidden
 * the blocker this arc exists to fix.
 *
 * <h2>🔑 The date shape, stated rather than discovered (BR-B)</h2>
 * Jackson serialises {@code LocalDate} as a <b>three-element array</b>
 * {@code [2026,9,9]}, not {@code "2026-09-09"}, because this application replaces
 * Boot's auto-configured {@code ObjectMapper} and loses
 * {@code WRITE_DATES_AS_TIMESTAMPS=false} with it. So a CORRECT round-trip reads
 * as a failure to anyone expecting a string. This test asserts the array shape
 * explicitly and says why, so the next reader does not "fix" a passing test.
 */
@AutoConfigureMockMvc
@DisplayName("Capturing a member, end to end, against a real database")
class MemberCaptureRoundTripIT extends AbstractWritingIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * The capture form's payload, as the browser sends it.
     *
     * <p>⚠️ Do not "clean this up". The empty arrays and the columnless fields are
     * the point: {@code cosatuPositions: []} is what made every save a 500, and it
     * did so only because the property EXISTS on the entity. Before that column
     * was added the same array was an unknown property and Spring ignored it
     * harmlessly.
     */
    private String captureFormPayload() {
        return """
                {
                  "title": "Mr",
                  "initials": "S",
                  "firstNames": "Synthetic",
                  "surname": "Testperson",
                  "identityType": "I.D. Number",
                  "identityNumber": "9001010000080",
                  "gender": "Male",
                  "race": "African",
                  "personalEmail": "synthetic@example.test",
                  "workEmail": "",
                  "preferredEmail": "personal",
                  "phone": "0115550000",
                  "workTel": "",
                  "homeTel": "",
                  "address": "1 Test Street",
                  "joinDate": "2026-09-09",
                  "status": "Active",
                  "subscriptionFrequency": "Monthly",
                  "employeeNumber": "EMP-0001",
                  "bankName": "Nedbank",
                  "bankAccountNumber": "1234567890",
                  "accountType": "Cheque",
                  "branch": "Head Office",
                  "employmentType": "Permanent",
                  "paymentType": "Debit Order",
                  "debitDay": 20,
                  "isRepresentative": false,
                  "isCosatu": "N",
                  "cosatuPositions": [],
                  "selectedBecStructures": [],
                  "remarks": "captured by the round-trip gate"
                }
                """;
    }

    @Test
    @DisplayName("🔴 the two blockers: a capture with the real payload answers 200, not 500")
    void captureSucceedsWithTheRealPayload() throws Exception {
        // A30 was `joinDate` submitted as a two-element ARRAY (`.split("T")` with
        // no `[0]`) into a LocalDate. B-1 was `cosatuPositions: []` binding an
        // array into a bare String on the ENTITY, because POST binds
        // @RequestBody Member while the deserializer sat on MemberDto.
        //
        // Either one alone answered 500. Fixing one alone left capture dead.
        JsonNode created = capture();

        assertThat(created.hasNonNull("id"))
                .as("the capture did not come back with an id. Response body: %s", created)
                .isTrue();
    }

    @Test
    @DisplayName("a captured member reads back with the fields that have columns")
    void theCapturedMemberRoundTrips() throws Exception {
        JsonNode read = readBack(capture());

        // Every one of these has a column, so every one of them must survive.
        assertThat(read.get("firstNames").asText()).isEqualTo("Synthetic");
        assertThat(read.get("surname").asText()).isEqualTo("Testperson");
        assertThat(read.get("identityNumber").asText()).isEqualTo("9001010000080");
        assertThat(read.get("employeeNumber").asText()).isEqualTo("EMP-0001");
        assertThat(read.get("bankAccountNumber").asText()).isEqualTo("1234567890");
        assertThat(read.get("paymentType").asText()).isEqualTo("Debit Order");
        assertThat(read.get("accountType").asText()).as("00.5, V49").isEqualTo("Cheque");
        assertThat(read.get("address").asText()).isEqualTo("1 Test Street");
        assertThat(read.get("remarks").asText()).isEqualTo("captured by the round-trip gate");
    }

    @Test
    @DisplayName("🔑 BR-B — joinDate reads back as [year, month, day], NOT as a string")
    void theDateShapeIsAnArrayAndThatIsAsserted() throws Exception {
        JsonNode read = readBack(capture());
        JsonNode joinDate = read.get("joinDate");

        assertThat(joinDate.isArray())
                .as("""
                        joinDate came back as %s rather than a JSON array.

                        If it is now an ISO STRING, that is not a regression -- it is
                        JacksonConfig having been fixed to keep Boot's
                        WRITE_DATES_AS_TIMESTAMPS=false. That is a deliberate,
                        BREAKING wire change that must ship with the client's
                        apiDates.fromApi(). Update this assertion in the same commit,
                        do not delete it.""", joinDate)
                .isTrue();
        assertThat(joinDate.get(0).asInt()).isEqualTo(2026);
        assertThat(joinDate.get(1).asInt()).isEqualTo(9);
        assertThat(joinDate.get(2).asInt()).isEqualTo(9);
    }

    @Test
    @DisplayName("00.5 a debit order captured without its account type is a 400 that says so, and nothing is stored")
    void aDebitOrderWithoutItsAccountTypeIsRefused() throws Exception {
        ObjectNode payload = formPayload();
        payload.remove("accountType");
        String sameForm = "SELECT count(*) FROM members WHERE employee_number = 'EMP-0001'";
        Integer before = this.jdbc.queryForObject(sameForm, Integer.class);

        MockHttpServletResponse response = this.mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.toString()))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString())
                .contains("Account type is required when payment type is 'Debit Order'");
        assertThat(this.jdbc.queryForObject(sameForm, Integer.class))
                .as("the refused capture left no member behind").isEqualTo(before);
    }

    @Test
    @DisplayName("🔒 SR-1 — a request body cannot write the users table or choose its own id")
    void massAssignmentIsRefused() throws Exception {
        // The member endpoints are permitAll() and bind the entity, and
        // Member.user cascades ALL. Before the READ_ONLY annotations this payload
        // created a user row -- username, enabled and userRoles included -- for an
        // anonymous caller.
        String hostile = """
                {
                  "firstNames": "Mallory",
                  "surname": "Testperson",
                  "id": 4242,
                  "membershipNumber": "M-000001",
                  "user": { "username": "mallory", "enabled": true }
                }
                """;

        JsonNode created = capture(hostile);

        assertThat(created.get("id").asInt())
                .as("the caller chose its own primary key")
                .isNotEqualTo(4242);

        Integer users = this.jdbc.queryForObject("SELECT count(*) FROM users", Integer.class);
        assertThat(users)
                .as("an anonymous POST to /api/members created a row in the USERS table")
                .isZero();
    }

    @Test
    @DisplayName("🔴 the same payload can be saved again from the register — PUT is not a 500")
    void editingACapturedMemberIsNotA500() throws Exception {
        // The second blocker broke EDITING as well: PUT binds the entity too, and
        // the register splits the comma-separated value back into an array to edit
        // it. So correcting a phone number was the same 500 as capturing.
        JsonNode created = capture();
        int id = created.get("id").asInt();

        ObjectNode edit = formPayload();
        edit.put("phone", "0115559999");
        edit.putArray("cosatuPositions").add("PEC").add("CEC");

        this.mockMvc.perform(put("/api/members/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(edit.toString()))
                .andReturn();

        JsonNode read = readBack(created);
        assertThat(read.get("phone").asText())
                .as("the edit did not persist")
                .isEqualTo("0115559999");
        assertThat(read.get("cosatuPositions").asText())
                .as("""
                        An array of positions was submitted and the stored value is not the
                        folded string. StringArrayToCommaSeparatedDeserializer is what makes
                        the array acceptable at all.""")
                .isEqualTo("PEC,CEC");
    }

    @Test
    @DisplayName("a PUT cannot retarget the write at a different member (SR-4)")
    void aPutCannotRetargetAnotherMember() throws Exception {
        JsonNode first = capture();
        // Two different people, per SR-5 -- and the test is stronger for it: a PUT
        // retargeted at "the other member" now means something, where two records of
        // one person made "different member" ambiguous.
        JsonNode second = captureAnotherPerson(2);
        int firstId = first.get("id").asInt();
        int secondId = second.get("id").asInt();

        // The mapper used to emit `target.setId(source.getId())`, so the id in the
        // BODY won over the id in the PATH.
        //
        // 🔑 The id is set on a PARSED node, not spliced into the payload's text.
        // The first version matched the text block's opening brace, which meant a
        // reindent or a leading comment would have turned the injection into a
        // NO-OP -- and a no-op here is this test passing green while asserting
        // nothing at all about retargeting.
        ObjectNode retarget = formPayload();
        retarget.put("surname", "Retargeted");
        retarget.put("id", secondId);

        this.mockMvc.perform(put("/api/members/{id}", firstId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(retarget.toString()))
                .andReturn();

        assertThat(readBack(second).get("surname").asText())
                .as("a PUT aimed at member %d rewrote member %d", firstId, secondId)
                .isEqualTo("Testperson");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** The capture payload as a mutable node, so an edit is a field set and not
     *  a string match against the text block's formatting. */
    private ObjectNode formPayload() throws Exception {
        return (ObjectNode) this.objectMapper.readTree(captureFormPayload());
    }

    private JsonNode capture() throws Exception {
        return capture(captureFormPayload());
    }

    /**
     * Capture a DIFFERENT person -- same form, its own identity number.
     *
     * <p>🔴 SR-5 IS WHY THIS EXISTS, and the reason is worth keeping. Every payload
     * in this class carries the one identity number {@code 9001010000080}, which was
     * harmless while {@code members.identity_number} had no unique constraint: a test
     * that captured twice quietly created the same person twice. {@code V26} makes
     * that a 409, and two tests here went red -- correctly. Their subject is the
     * membership-number SEQUENCE and PUT targeting; capturing one person twice was
     * incidental to both, and a coincidence of the fixture rather than a claim.
     *
     * <p>🔑 THE PLAN MISSED THIS FILE, and the reason it did is the useful part. SR-5's
     * blast radius was recorded as "nine test files, 23 save sites in
     * {@code MemberRepositoryIT} alone" -- all of them counted because they go through
     * {@code MemberFixture}. This class builds its payloads as raw JSON text, so it
     * never touched the fixture and never appeared in the sweep. **A survey of the
     * callers of one builder cannot see the callers that hand-roll their own.** Only
     * running it against a real database found these two.
     */
    private JsonNode captureAnotherPerson(int sequence) throws Exception {
        ObjectNode payload = formPayload();
        payload.put("identityNumber", MemberFixture.syntheticIdFor(sequence));
        return capture(payload.toString());
    }

    // ─── Edit Member · SASBO structures: what the three questions store ───

    /**
     * 🔴 CM-05. Edit Member asks Shop Steward?, BEC Member? and COSATU Member?, and
     * until V29 only the COSATU answer had a column: {@code isShopSteward} and
     * {@code isBec} were unknown properties, ignored by Jackson, and the save still
     * said success. Stakeholder, 2026-09-17: COSATU participation requires BEC
     * membership, and a COSATU member is recorded as sitting on a Local and/or a
     * Regional structure (yes/no each).
     */
    @Test
    @DisplayName("🔴 CM-05 — Shop Steward, BEC, and COSATU Local / Regional persist through an edit")
    void theStructureAnswersPersist() throws Exception {
        JsonNode created = capture();

        ObjectNode edit = formPayload();
        edit.put("isShopSteward", true);
        edit.put("isBec", true);
        edit.put("isCosatu", "Y");
        edit.put("isCosatuLocal", true);
        edit.put("isCosatuRegional", false);

        int status = this.mockMvc.perform(put("/api/members/{id}", created.get("id").asInt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(edit.toString()))
                .andReturn().getResponse().getStatus();
        assertThat(status).as("a BEC member may be COSATU; the edit is accepted").isEqualTo(200);

        JsonNode read = readBack(created);
        assertThat(read.path("isShopSteward").asText()).as("Shop Steward?").isEqualTo("true");
        assertThat(read.path("isBec").asText()).as("BEC Member?").isEqualTo("true");
        assertThat(read.path("isCosatu").asText()).as("COSATU Member?").isEqualTo("Y");
        assertThat(read.path("isCosatuLocal").asText()).as("sits on a COSATU Local structure").isEqualTo("true");
        // The must-pass half: `false` is an ANSWER and must come back as false, not
        // vanish into null the way an unmapped field does.
        assertThat(read.path("isCosatuRegional").asText()).as("does not sit on a Regional structure").isEqualTo("false");
    }

    @Test
    @DisplayName("🔴 COSATU without BEC membership is refused with a 400 that says why")
    void cosatuWithoutBecIsRefused() throws Exception {
        JsonNode created = capture();

        ObjectNode edit = formPayload();
        edit.put("isBec", false);
        edit.put("isCosatu", "Y");

        MockHttpServletResponse response = this.mockMvc.perform(put("/api/members/{id}", created.get("id").asInt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(edit.toString()))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).containsIgnoringCase("BEC");
        assertThat(readBack(created).path("isCosatu").asText())
                .as("and nothing was written").isNotEqualTo("Y");
    }

    // ─── 03.3: the two Recruitment Channel fields that STAY ───

    @Test
    @DisplayName("🔴 R5 — the Recruiter persists, where before it was dropped in silence")
    void theRecruiterPersists() throws Exception {
        // Before 03.3 the form sent `recruitmentRep` and NOTHING on the entity was
        // called that, so Jackson ignored it as an unknown property, the recruiter was
        // dropped, and the save reported success. A31's class, in the field the whole
        // Recruitment Channel tab exists for.
        //
        // The fixture is created HERE rather than read from the API. AbstractWritingIT
        // truncates every table around each test, so this lane has no seed data at all
        // -- and a test that quietly skipped on an empty list would assert nothing while
        // looking green, which is how the unseeded `areas` table survived since V1.
        String recruiter = seedRecruiter("Thandiwe Mokoena Testrecruiter");

        JsonNode read = readBack(capture(payloadWith(
                "\"recruitmentRep\": \"" + recruiter + "\"")));

        assertThat(read.get("recruitmentRepresentativeName").asText())
                .as("the recruiter was submitted and must come back resolved, not dropped")
                .isEqualTo(recruiter);
    }

    @Test
    @DisplayName("🔑 R2 / RE-3 — Area Recruited stores an AREA, and it reaches areas.id")
    void theAreaRecruitedPersistsAsAnArea() throws Exception {
        // The change of MEANING, asserted where it is visible. The old field captured a
        // PERSON under an area-shaped name; this one resolves to `members.area_id`, a
        // foreign key that had existed since V1 with nothing ever writing to it.
        String area = seedArea("Umhlanga Ridge");

        JsonNode read = readBack(capture(payloadWith(
                "\"areaRecruited\": \"" + area + "\"")));

        assertThat(read.get("areaRecruited").asText())
                .as("Area Recruited must resolve to a row in `areas`, not be stored as loose text")
                .isEqualTo(area);

        // 🔴 AND IT MUST NOT TOUCH THE MEMBER'S OWN AREA. `area_id` means "the area this
        // member BELONGS to" -- organisational, ongoing, and read by
        // RepresentativeServiceImpl as exactly that. 03.3 wrote the recruitment area
        // there until V24 separated them, so this half is the correction, asserted.
        // Without it the two facts silently overwrite one another and nothing fails.
        assertThat(read.hasNonNull("areaId"))
                .as("capture records where a member JOINED, not where they BELONG")
                .isFalse();
    }

    @Test
    @DisplayName("an area the union does not have resolves to nothing, rather than to loose text")
    void anUnknownAreaIsNotInvented() throws Exception {
        JsonNode read = readBack(capture(payloadWith(
                "\"areaRecruited\": \"Atlantis-On-Sea\"")));

        assertThat(read.hasNonNull("areaRecruited"))
                .as("an unmatched area must not create or imply a row")
                .isFalse();
    }

    // ─── 03.8: the Recruitment Channel tab, as the stakeholder defined it ───

    @Test
    @DisplayName("🗣️ all THREE things the stakeholder asked this tab to capture round-trip")
    void theRecruitmentChannelTripleRoundTrips() throws Exception {
        // The stakeholder, 2026-09-09: "Under Recruitment Channel we capture:
        //   1. How the member was recruited.
        //   2. The area where the member was recruited.
        //   3. Who recruited the member, where applicable."
        //
        // Asserted TOGETHER, in one capture, because the requirement is the tab and not
        // three separate fields. Each of the three has its own failure history: (1) was
        // never captured at all, (2) captured a person under an area-shaped name, and
        // (3) was sent on every capture and silently dropped.
        String channel = seedChannel("Website");
        String area = seedArea("Umhlanga Ridge");
        String recruiter = seedRecruiter("Thandiwe Mokoena Testrecruiter");

        JsonNode read = readBack(capture(payloadWith(
                "\"recruitedHow\": \"" + channel + "\", "
                        + "\"areaRecruited\": \"" + area + "\", "
                        + "\"recruitmentRep\": \"" + recruiter + "\"")));

        assertThat(read.get("recruitedHow").asText()).isEqualTo(channel);
        assertThat(read.get("areaRecruited").asText()).isEqualTo(area);
        assertThat(read.get("recruitmentRepresentativeName").asText()).isEqualTo(recruiter);
    }

    @Test
    @DisplayName("the recruiter is optional -- 'where applicable' is the stakeholder's own word")
    void theRecruiterIsOptional() throws Exception {
        // How and where, with no who. This must be an ordinary successful capture, not
        // a validation failure: a member who joined through the website was recruited
        // by nobody, and the form must be able to say so.
        String channel = seedChannel("Website");

        JsonNode read = readBack(capture(payloadWith(
                "\"recruitedHow\": \"" + channel + "\"")));

        assertThat(read.get("recruitedHow").asText()).isEqualTo(channel);
        assertThat(read.hasNonNull("recruitmentRepresentativeName")).isFalse();
    }

    @Test
    @DisplayName("a channel the union does not have resolves to nothing, rather than to loose text")
    void anUnknownChannelIsNotInvented() throws Exception {
        JsonNode read = readBack(capture(payloadWith("\"recruitedHow\": \"Carrier Pigeon\"")));

        assertThat(read.hasNonNull("recruitedHow"))
                .as("an unmatched channel must not create or imply a row")
                .isFalse();
    }

    /** One recruitment channel, and its name back. */
    private String seedChannel(String name) {
        this.jdbc.update("INSERT INTO recruitment_channel (name, is_enabled) VALUES (?, TRUE)", name);
        return name;
    }

    // ─── 03.4: the membership number ───

    @Test
    @DisplayName("🔴 a captured member GETS a membership number -- nothing assigned one before")
    void aCapturedMemberGetsAMembershipNumber() throws Exception {
        // `membershipNumber` was on the entity, `existsByMembershipNumber` was on the
        // repository, and nothing ever called it -- so every member captured through
        // the API was stored with a NULL number while all 160 seeded members have one.
        // The field looked implemented from every angle except the one that mattered.
        JsonNode read = readBack(capture());

        assertThat(read.get("membershipNumber").asText())
                .as("the shape the union's own 160 members use")
                .matches("M-\\d{6}");
    }

    @Test
    @DisplayName("🔴 two captures never share a number -- the reason it is a sequence")
    void twoCapturesNeverShareANumber() throws Exception {
        // The failure this design prevents. "Find the highest, add one, check it is
        // free" is a check-then-act race: two captures both read 100160, both find
        // 100161 free, and both take it. There is no DELETE endpoint (register D9), so
        // the duplicate is permanent and somebody edits it by hand forever.
        //
        // This asserts the PROPERTY -- distinct numbers, strictly increasing -- rather
        // than specific values, because the values depend on how many members this test
        // class captured before it and asserting those would be asserting the fixture.
        // Two DIFFERENT people. Before SR-5 both captures carried one identity number
        // and this test was quietly capturing the same person twice -- which is not
        // what "two captures" means, and is now a 409.
        String first = readBack(capture()).get("membershipNumber").asText();
        String second = readBack(captureAnotherPerson(1)).get("membershipNumber").asText();

        assertThat(second).isNotEqualTo(first);
        assertThat(numberIn(second)).isGreaterThan(numberIn(first));
    }

    @Test
    @DisplayName("the sequence starts past the seeded block, so it cannot collide with it")
    void theSequenceStartsPastTheSeededBlock() throws Exception {
        // The seed occupies M-100001..M-100160 contiguously. Starting anywhere at or
        // below 100160 would collide with a real member on the very first capture --
        // and this lane truncates the members table, so nothing else would notice.
        assertThat(numberIn(readBack(capture()).get("membershipNumber").asText()))
                .isGreaterThanOrEqualTo(100161L);
    }

    private static long numberIn(String membershipNumber) {
        return Long.parseLong(membershipNumber.substring(2));
    }

    /** One area, and its name back. */
    private String seedArea(String name) {
        this.jdbc.update("INSERT INTO areas (name) VALUES (?)", name);
        return name;
    }

    /**
     * One representative, and the display name {@code findByFullName} will match.
     *
     * <p>{@code representatives.member_id} is NOT NULL, so a representative cannot
     * exist without a member. This captures one through the API first — which also
     * means the fixture is built the way the application builds it, rather than by
     * writing a row shape a migration might later change underneath us.
     */
    /**
     * A RECRUITER the capture form can name — 03.10.
     *
     * <p>🔴 This used to insert into {@code representatives}, because that is where
     * {@code resolveRecruiter} looked names up. Measuring 03.10 showed why that never
     * worked outside a test: {@code representatives} is seeded by no migration and no
     * seeder, so in the running application the table was <b>empty</b> and every
     * capture stored a null recruiter — while the union's 33 real recruiter names sat
     * in {@code recruiters}, referenced by no foreign key and served by no controller.
     *
     * <p>🔑 The old helper was therefore <b>manufacturing the only condition under
     * which the feature worked.</b> It inserted the row the production code needed and
     * nothing else ever did, so the test proved the wiring while the screen stayed
     * broken. That is worth naming: a fixture that creates the data the application
     * cannot create is a test that passes for the wrong reason.
     *
     * <p>The name is synthetic and deliberately unlike anything in
     * {@code recruiters.json} — the 33 seeded rows are present in this lane, and a
     * case-insensitive collision with one of them is how the seed's three "Vacant"
     * rows were found.
     */
    private String seedRecruiter(String name) {
        this.jdbc.update("INSERT INTO recruiters (name, members_recruited) VALUES (?, 0)", name);
        return name;
    }

    /** The real payload with one extra field, so each test states only what it is about. */
    private String payloadWith(String extraJsonField) {
        String base = captureFormPayload().trim();
        return base.substring(0, base.lastIndexOf('}')) + ",\n  " + extraJsonField + "\n}";
    }

    /**
     * POST the payload and return the created member.
     *
     * <p>🔴 THE STATUS IS ASSERTED, AND IT WAS NOT BEFORE. This discarded the status
     * and returned whatever body came back, so a REJECTED capture flowed on as an
     * error envelope and died later in {@code readBack} as
     * {@code NullPointerException: ... "JsonNode.get(String)" is null} -- three frames
     * from the cause, naming neither the rejection nor its reason.
     *
     * <p>That is exactly what happened when {@code V26} landed: two tests here began
     * capturing a duplicate, the server correctly answered 409 with a sentence saying
     * so, and the lane reported a null-pointer in a helper. **01.2's whole subject is
     * that a failure should say what went wrong**, and this file was quietly doing the
     * opposite to itself. The server's own message is put in the assertion, so a
     * rejected capture now reads as the rejection it is.
     */
    private JsonNode capture(String payload) throws Exception {
        MockHttpServletResponse response = this.mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus())
                .as("the capture was rejected, and the server said: %s", response.getContentAsString())
                .isEqualTo(200);

        return this.objectMapper.readTree(response.getContentAsString());
    }

    // ═══ 🗣️ DECISION ⑨(b) — PUT SEMANTICS, BOTH HALVES ═══════════════════════
    //
    // The mapper used to copy NULLS, so a field a PUT never mentioned was ERASED.
    // Safe only by convention, stated in no contract, and the exact opposite of the
    // `absent = keep` rule the resolvers apply on the same request. The owner took
    // (b) on 2026-09-10: absent keeps, blank clears, everywhere.
    //
    // 🔑 BOTH HALVES, IN ONE PLACE, ON PURPOSE. Each is satisfied by a broken
    // implementation of the other: "absent keeps" alone passes against a mapper that
    // ignores the field entirely and can never clear it; "blank clears" alone passes
    // against the old copy-everything mapper. Only the pair pins the rule, and the
    // pair is what the decision actually says.

    @Test
    @DisplayName("🗣️ ⑨ a PUT that omits a field KEEPS the stored value")
    void aPutThatOmitsAFieldKeepsIt() throws Exception {
        JsonNode created = captureAnotherPerson(91);
        int id = created.get("id").asInt();

        ObjectNode edit = formPayload();
        edit.put("identityNumber", MemberFixture.syntheticIdFor(91));
        edit.remove("phone");                      // the field this PUT never mentions
        edit.put("surname", "Absent");             // and one it does, so the PUT is real

        this.mockMvc.perform(put("/api/members/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(edit.toString()))
                .andReturn();

        JsonNode read = readBack(created);
        assertThat(read.get("surname").asText())
                .as("the field the PUT DID send must change, or this test is asserting "
                        + "that a request which did nothing changed nothing")
                .isEqualTo("Absent");
        assertThat(read.get("phone").asText())
                .as("the request said nothing about the phone number, so there is "
                        + "nothing to change -- a caller that does not hold a field must "
                        + "not be able to erase it by omission")
                .isEqualTo(created.get("phone").asText());
    }

    @Test
    @DisplayName("🗣️ ⑨ ...and a PUT that sends a field BLANK clears it")
    void aPutThatSendsAFieldBlankClearsIt() throws Exception {
        // The other half. Without it the rule above is satisfied by a mapper that has
        // stopped copying the field at all, and a user could never clear a value they
        // entered by mistake -- which would be a worse defect than the one ⑨ fixes.
        JsonNode created = captureAnotherPerson(92);
        int id = created.get("id").asInt();
        assertThat(created.get("phone").asText())
                .as("the fixture must START with a phone number, or clearing it proves nothing")
                .isNotEmpty();

        ObjectNode edit = formPayload();
        edit.put("identityNumber", MemberFixture.syntheticIdFor(92));
        edit.put("phone", "");                     // an explicit, deliberate clear

        this.mockMvc.perform(put("/api/members/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(edit.toString()))
                .andReturn();

        assertThat(readBack(created).get("phone").asText())
                .as("an explicit empty string is a request to clear, and stays one")
                .isEmpty();
    }

    @Test
    @DisplayName("🔒 H8 — a PUT cannot set the money fields, even when it sends them")
    void aPutCannotSetTheMoneyFields() throws Exception {
        // The Recruiters screen's builder sends `currentBalance: member.currentBalance
        // || 0` on every PUT, so a member whose balance was empty was written as `0` by
        // a bulk assign action. `monthlySubscription` was only ever harmless because
        // `update()` overwrote it from the bank -- the derivation R1-F4 removes, which
        // is why H8 is sequenced BEFORE it.
        //
        // PATCH /subscription stays the one door for the amount.
        JsonNode created = captureAnotherPerson(93);
        int id = created.get("id").asInt();
        String storedSubscription = created.get("monthlySubscription").asText();

        ObjectNode edit = formPayload();
        edit.put("identityNumber", MemberFixture.syntheticIdFor(93));
        edit.put("currentBalance", "999999");
        edit.put("arrearsBalance", "888888");
        edit.put("lastMonthBalance", "777777");
        edit.put("monthlySubscription", "0.01");
        edit.put("status", "Inactive");            // NOT ignored -- two screens set it

        this.mockMvc.perform(put("/api/members/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(edit.toString()))
                .andReturn();

        JsonNode read = readBack(created);
        assertThat(read.get("currentBalance").asText())
                .as("a client-sent balance is not authoritative").isNotEqualTo("999999");
        assertThat(read.get("arrearsBalance").asText())
                .as("nor an arrears balance").isNotEqualTo("888888");
        assertThat(read.get("lastMonthBalance").asText())
                .as("nor last month's").isNotEqualTo("777777");
        assertThat(read.get("monthlySubscription").asText())
                .as("the subscription amount changes through PATCH /subscription and "
                        + "nowhere else")
                .isEqualTo(storedSubscription);
        assertThat(read.get("status").asText())
                .as("🔑 the non-vacuity half: `status` is DELIBERATELY still writable, "
                        + "because Edit Member edits it and the Recruiters screen sets "
                        + "Inactive through PUT. Without this the test above passes "
                        + "against a mapper that has stopped copying anything at all")
                .isEqualTo("Inactive");
    }

    private JsonNode readBack(JsonNode created) throws Exception {
        String body = this.mockMvc.perform(get("/api/members/{id}", created.get("id").asInt()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return this.objectMapper.readTree(body);
    }
}
