package za.co.titandynamix.sasbo_online_backend.support;

import org.springframework.jdbc.core.JdbcTemplate;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.utils.ReferenceListSeeder;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * What the data migration's integration tests stand on: SASBO's lists (their codes, areas and table codes), the four
 * INVENTED workplaces the fixtures name -- R/A numbers 990001 to 990004, never SASBO's own -- and the member already on
 * record whose identity number line 51 of the CSV fixture carries and whose membership number line 52 does. Every
 * person invented.
 */
public final class MigrationFixture {

    /** The CSV fixture's outcomes once checked against all of this: 4 valid, 17 with warnings, 32 held back. */
    public static final int CSV_ROWS = 53;
    public static final int CSV_VALID = 4;
    public static final int CSV_WARNED = 17;
    public static final int CSV_INVALID = 32;

    private MigrationFixture() {
    }

    /** The lists, the workplaces and the member on record. The member's id. */
    public static Integer prepare(JdbcTemplate jdbc, ReferenceListSeeder lists, MemberRepository members) {
        lists.seed();
        workplace(jdbc, 990001, "INVENTED BRANCH ONE", 13, "D");
        workplace(jdbc, 990002, "INVENTED BRANCH TWO", 24, "L");
        workplace(jdbc, 990003, "INVENTED BRANCH THREE", 43, "W");
        workplace(jdbc, 990004, "INVENTED DEPARTMENT", null, "D");
        Member onRecord = MemberFixture.withSurname("Onrecord");
        onRecord.setIdentityNumber(LegacyFixtures.ON_RECORD_ID);
        onRecord.setIdentityType("I.D. Number");
        onRecord.setMembershipNumber(LegacyFixtures.ON_RECORD_NUMBER);
        onRecord.setStatus("Active");
        return members.save(onRecord).getId();
    }

    private static void workplace(JdbcTemplate jdbc, int ra, String name, Integer tableCode, String area) {
        jdbc.update("INSERT INTO workplaces (ra_number, name, employer_printed, table_code_id, table_code_by, area_id, "
                        + "committee_code, staff_complement, cl_code) VALUES (?, ?, 'INVENTED EMPLOYER', "
                        + "(SELECT id FROM table_codes WHERE code = ?), ?, (SELECT id FROM sasbo_areas WHERE letter = ?), "
                        + "'Q99', 10, '00000000')",
                ra, name, tableCode, tableCode == null ? null : "NAME", area);
    }

    public static InputStream csv() {
        return new ByteArrayInputStream(LegacyFixtures.resource(LegacyFixtures.CSV_FIXTURE));
    }

    public static InputStream fixedWidth() {
        return new ByteArrayInputStream(LegacyFixtures.resource(LegacyFixtures.FIXED_WIDTH_FIXTURE));
    }
}
