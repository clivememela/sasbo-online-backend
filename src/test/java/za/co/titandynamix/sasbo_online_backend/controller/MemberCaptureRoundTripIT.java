package za.co.titandynamix.sasbo_online_backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
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
        JsonNode second = capture();
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
        String recruiter = seedRepresentative("Thandiwe", "Mokoena");

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

        assertThat(read.get("areaName").asText())
                .as("Area Recruited must resolve to a row in `areas`, not be stored as loose text")
                .isEqualTo(area);
        assertThat(read.hasNonNull("areaId"))
                .as("the FK itself must be set -- a name that matched nothing would still "
                        + "round-trip as text and look correct")
                .isTrue();
    }

    @Test
    @DisplayName("an area the union does not have resolves to nothing, rather than to loose text")
    void anUnknownAreaIsNotInvented() throws Exception {
        JsonNode read = readBack(capture(payloadWith(
                "\"areaRecruited\": \"Atlantis-On-Sea\"")));

        assertThat(read.hasNonNull("areaId"))
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
        String recruiter = seedRepresentative("Thandiwe", "Mokoena");

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
        String first = readBack(capture()).get("membershipNumber").asText();
        String second = readBack(capture()).get("membershipNumber").asText();

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
    private String seedRepresentative(String firstNames, String surname) throws Exception {
        int memberId = capture().get("id").asInt();
        // `created_at` and `updated_at` are NOT NULL with no default -- the entity fills
        // them via @PrePersist, which a raw INSERT bypasses. Supplied explicitly rather
        // than made nullable: relaxing a production constraint to suit a fixture is the
        // wrong direction.
        this.jdbc.update(
                "INSERT INTO representatives (first_names, surname, member_id, created_at, updated_at) "
                        + "VALUES (?, ?, ?, now(), now())",
                firstNames, surname, memberId);
        return firstNames + " " + surname;
    }

    /** The real payload with one extra field, so each test states only what it is about. */
    private String payloadWith(String extraJsonField) {
        String base = captureFormPayload().trim();
        return base.substring(0, base.lastIndexOf('}')) + ",\n  " + extraJsonField + "\n}";
    }

    private JsonNode capture(String payload) throws Exception {
        String response = this.mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return this.objectMapper.readTree(response);
    }

    private JsonNode readBack(JsonNode created) throws Exception {
        String body = this.mockMvc.perform(get("/api/members/{id}", created.get("id").asInt()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return this.objectMapper.readTree(body);
    }
}
