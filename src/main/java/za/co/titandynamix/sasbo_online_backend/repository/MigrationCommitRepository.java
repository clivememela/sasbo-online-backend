package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a move writes and an undo removes: members, their status changes and the links from each member to its row --
 * in JDBC batches, a chunk at a time, inside the transaction the caller holds. 30 of the application's 31 entities take
 * their ids from the database, which switches Hibernate's batching off; a half-million-row move is statements, not
 * entities.
 *
 * <p>A member is written through the columns capture writes, and nothing else: no history row ({@code member_changes} is
 * append-only, and an undo must be able to remove what a move wrote -- the member's trail starts with the first change
 * made here), and its status change is written as month end's status change is, so a figure reads a moved member as it
 * reads any other. The commit runs on PostgreSQL: the capture sequence it moves is V22's.
 */
@Repository
public class MigrationCommitRepository {

    /** A member to write: every column a move fills. */
    public record MemberInsert(String membershipNumber, String title, String initials, String firstNames, String surname,
                               String identityType, String identityNumber, String passportNumber, LocalDate dateOfBirth,
                               String gender, String race, String status, LocalDate joinDate, String personalEmail,
                               String workEmail, String preferredEmail, String cellTel, String workTel, String homeTel,
                               String address, String bankName, String bankAccountNumber, String accountType,
                               String paymentType, Integer debitDay, BigDecimal monthlySubscription,
                               String employeeNumber, Long workplaceId, String remarks) {

        @Override
        public String toString() {
            return "MemberInsert[" + membershipNumber + "]";
        }
    }

    /** A status change to write: the one a deleted or suspended member is given, as month end writes one. */
    public record StatusInsert(int memberId, String toStatus, String reason, Long deletionCodeId, Long suspensionCodeId,
                               String note, String actor, OffsetDateTime at) {
    }

    /** The link from a member to its batch and row. */
    public record LinkInsert(int memberId, long batchId, long rowId, Long statusChangeId) {
    }

    /** A link as the undo reads it. */
    public record Link(long id, int memberId, Long statusChangeId) {
    }

    private final NamedParameterJdbcTemplate named;
    private final JdbcTemplate jdbc;

    public MigrationCommitRepository(NamedParameterJdbcTemplate named) {
        this.named = named;
        this.jdbc = named.getJdbcTemplate();
    }

    // ------------------------------------------------------------------------------------------------ the move

    /**
     * The members of a chunk, in one JDBC batch. Created and updated at the same instant: an edit made here afterwards
     * moves {@code updated_at} on its own, and that is how an undo knows a member was touched.
     */
    public void insertMembers(List<MemberInsert> members, LocalDateTime now) {
        Timestamp at = Timestamp.valueOf(now);
        List<Object[]> args = new ArrayList<>(members.size());
        for (MemberInsert m : members) {
            args.add(new Object[] {m.membershipNumber(), m.title(), m.initials(), m.firstNames(), m.surname(),
                    m.identityType(), m.identityNumber(), m.passportNumber(),
                    m.dateOfBirth() == null ? null : Date.valueOf(m.dateOfBirth()), m.gender(), m.race(), m.status(),
                    m.joinDate() == null ? null : Date.valueOf(m.joinDate()), m.personalEmail(), m.workEmail(),
                    m.preferredEmail(), m.cellTel(), m.workTel(), m.homeTel(), m.address(), m.bankName(),
                    m.bankAccountNumber(), m.accountType(), m.paymentType(), m.debitDay(), m.monthlySubscription(),
                    m.employeeNumber(), m.workplaceId(), m.remarks(), at, at});
        }
        jdbc.batchUpdate("""
                INSERT INTO members (membership_number, title, initials, first_names, surname, identity_type,
                  identity_number, passport_number, date_of_birth, gender, race, status, join_date, personal_email,
                  work_email, preferred_email, cell_tel, work_tel, home_tel, address, bank_name, bank_account_number,
                  account_type, payment_type, debit_day, monthly_subscription_numeric, employee_number, workplace_id,
                  remarks, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, args, new int[] {Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.DATE, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                Types.DATE, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.INTEGER, Types.NUMERIC,
                Types.VARCHAR, Types.BIGINT, Types.VARCHAR, Types.TIMESTAMP, Types.TIMESTAMP});
    }

    /**
     * The ids of members by their membership numbers -- every number of a chunk is the batch's own and unique, so each
     * comes back once. A number on two members comes back twice, and the caller refuses the chunk.
     */
    public Map<String, List<Integer>> memberIdsByNumber(Collection<String> numbers) {
        Map<String, List<Integer>> ids = new HashMap<>();
        if (numbers.isEmpty()) {
            return ids;
        }
        named.query("SELECT id, membership_number FROM members WHERE membership_number IN (:numbers)",
                new MapSqlParameterSource("numbers", numbers), rs -> {
                    ids.computeIfAbsent(rs.getString("membership_number"), n -> new ArrayList<>()).add(rs.getInt("id"));
                });
        return ids;
    }

    /** The status changes of a chunk, in one JDBC batch, each from Active -- the member's status before they left. */
    public void insertStatusChanges(List<StatusInsert> changes) {
        if (changes.isEmpty()) {
            return;
        }
        List<Object[]> args = new ArrayList<>(changes.size());
        for (StatusInsert c : changes) {
            args.add(new Object[] {c.memberId(), "Active", c.toStatus(), c.reason(), c.deletionCodeId(),
                    c.suspensionCodeId(), c.note(), c.actor(), c.at()});
        }
        jdbc.batchUpdate("""
                INSERT INTO member_status_changes (member_id, from_status, to_status, reason, deletion_code_id,
                  suspension_code_id, note, changed_by, changed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, args, new int[] {Types.INTEGER, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.BIGINT,
                Types.BIGINT, Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP_WITH_TIMEZONE});
    }

    /** The status change each of these new members was given -- a member just written has no other. */
    public Map<Integer, Long> statusChangeIds(Collection<Integer> memberIds) {
        Map<Integer, Long> ids = new HashMap<>();
        if (memberIds.isEmpty()) {
            return ids;
        }
        named.query("SELECT id, member_id FROM member_status_changes WHERE member_id IN (:members)",
                new MapSqlParameterSource("members", memberIds), rs -> {
                    ids.put(rs.getInt("member_id"), rs.getLong("id"));
                });
        return ids;
    }

    public void insertLinks(List<LinkInsert> links, Instant now) {
        OffsetDateTime at = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        List<Object[]> args = new ArrayList<>(links.size());
        for (LinkInsert link : links) {
            args.add(new Object[] {link.memberId(), link.batchId(), link.rowId(), link.statusChangeId(), at});
        }
        jdbc.batchUpdate("INSERT INTO migration_member_links (member_id, batch_id, row_id, status_change_id, committed_at)"
                + " VALUES (?, ?, ?, ?, ?)", args, new int[] {Types.INTEGER, Types.BIGINT, Types.BIGINT, Types.BIGINT,
                Types.TIMESTAMP_WITH_TIMEZONE});
    }

    /** The next membership number of ours (V22's sequence), for a file that carries none. */
    public long nextMembershipNumber() {
        Long next = jdbc.queryForObject("SELECT nextval('membership_number_seq')", Long.class);
        if (next == null) {
            throw new IllegalStateException("the membership number sequence gave nothing");
        }
        return next;
    }

    /**
     * The capture sequence moved past the highest number a move writes -- BEFORE the move, so a capture made while it
     * runs never takes a number the move is about to write. Never backwards: a sequence already past it stays put.
     */
    public void moveMembershipSequencePast(long highest) {
        jdbc.queryForObject("SELECT setval('membership_number_seq', GREATEST(nextval('membership_number_seq'), ?), false)",
                Long.class, highest + 1);
    }

    // ------------------------------------------------------------------------------------------------ the undo

    /** The next links of a batch, oldest first -- an undo works through them a chunk at a time. */
    public List<Link> linksPage(long batchId, int size) {
        return named.query("SELECT id, member_id, status_change_id FROM migration_member_links WHERE batch_id = :batch"
                        + " ORDER BY id LIMIT :size", new MapSqlParameterSource("batch", batchId).addValue("size", size),
                (rs, n) -> {
                    long change = rs.getLong("status_change_id");
                    return new Link(rs.getLong("id"), rs.getInt("member_id"), rs.wasNull() ? null : change);
                });
    }

    /**
     * How many of a batch's members have been changed or used since the move: edited (their record's {@code updated_at}
     * moved, or a history row written), given another status change, or taken into a debit order run, the opening
     * position or a representative's record. An undo deletes none of them while any has been.
     */
    public long changedSince(long batchId) {
        Long changed = named.queryForObject("SELECT COUNT(*) FROM migration_member_links l JOIN members m ON m.id = l.member_id"
                + " WHERE l.batch_id = :batch AND " + CHANGED, new MapSqlParameterSource("batch", batchId), Long.class);
        return changed == null ? 0 : changed;
    }

    /** The same question for one chunk's members, asked again inside the undo's transaction, the members locked. */
    public long changedAmong(Collection<Long> linkIds) {
        Long changed = named.queryForObject("SELECT COUNT(*) FROM migration_member_links l JOIN members m ON m.id = l.member_id"
                + " WHERE l.id IN (:links) AND " + CHANGED, new MapSqlParameterSource("links", linkIds), Long.class);
        return changed == null ? 0 : changed;
    }

    private static final String CHANGED = """
            (m.updated_at <> m.created_at
              OR EXISTS (SELECT 1 FROM member_changes c WHERE c.member_id = l.member_id)
              OR EXISTS (SELECT 1 FROM member_status_changes s WHERE s.member_id = l.member_id
                         AND (l.status_change_id IS NULL OR s.id <> l.status_change_id))
              OR EXISTS (SELECT 1 FROM acb_run_lines x WHERE x.member_id = l.member_id)
              OR EXISTS (SELECT 1 FROM acb_run_exclusions x WHERE x.member_id = l.member_id)
              OR EXISTS (SELECT 1 FROM acb_opening_position x WHERE x.member_id = l.member_id)
              OR EXISTS (SELECT 1 FROM representatives x WHERE x.member_id = l.member_id))""";

    /** The members of a chunk, locked until the undo's transaction ends -- an edit begun now waits, then finds them gone. */
    public void lock(Collection<Integer> memberIds) {
        named.query("SELECT id FROM members WHERE id IN (:members) FOR UPDATE",
                new MapSqlParameterSource("members", memberIds), rs -> { });
    }

    /** A chunk undone: its links, then the status changes they name, then the members -- the order the keys allow. */
    public int remove(Collection<Long> linkIds, Collection<Long> statusChangeIds, Collection<Integer> memberIds) {
        named.update("DELETE FROM migration_member_links WHERE id IN (:links)", new MapSqlParameterSource("links", linkIds));
        if (!statusChangeIds.isEmpty()) {
            named.update("DELETE FROM member_status_changes WHERE id IN (:changes)",
                    new MapSqlParameterSource("changes", statusChangeIds));
        }
        return named.update("DELETE FROM members WHERE id IN (:members)", new MapSqlParameterSource("members", memberIds));
    }
}
