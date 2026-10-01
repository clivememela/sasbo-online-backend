package za.co.titandynamix.sasbo_online_backend.services;

import org.springframework.stereotype.Component;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.entity.member.MemberChange;
import za.co.titandynamix.sasbo_online_backend.repository.MemberChangeRepository;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * A member's history, as the saves write it (month end 03.5).
 *
 * <h2>Inside the save, never beside it</h2>
 * Each of the four write methods -- {@code create}, {@code update}, {@code updateBanking},
 * {@code updateMonthlySubscription} -- takes a {@link #snapshot} of the member BEFORE it changes anything, and hands it
 * to {@link #changed} after its save, in its own transaction: a save that fails leaves no row, and a row never
 * outlives the save it describes. A capture is ONE row, {@code captured}, and not a row a field.
 *
 * <h2>One row a field, one id a save</h2>
 * Every row of one save carries the same {@code saveId}, made here, so that three fields changed together are one
 * change on the History tab (ME-22): the server has no request id to borrow. A save that changes nothing writes
 * nothing. A change of workplace is a row like any other -- which is what lets a transfer be told from a join.
 *
 * <h2>Masked on the way in</h2>
 * Identity, passport and account numbers are written as their last four, never whole: the history is kept for ever
 * until SASBO names a period (PA-6, MM14), and it is read by anyone who can read the member.
 *
 * <p>The actor is {@code system} until sign-in exists (PA-12); the instant is the injected clock's (BR40).
 */
@Component
public class MemberTrail {

    /** Who changed it, until sign-in exists. */
    public static final String ACTOR = "system";

    /** The one row a capture writes. */
    public static final String CAPTURED = "captured";

    /** The fields a history row may name, each read off the member as a person reads it. */
    private static final Map<String, Function<Member, Object>> FIELDS = fields();

    /** The fields written as their last four, never whole. */
    static final List<String> MASKED = List.of("identityNumber", "passportNumber", "bankAccountNumber");

    private final MemberChangeRepository changes;
    private final Clock clock;

    public MemberTrail(MemberChangeRepository changes, Clock clock) {
        this.changes = changes;
        this.clock = clock;
    }

    private static Map<String, Function<Member, Object>> fields() {
        Map<String, Function<Member, Object>> f = new LinkedHashMap<>();
        f.put("title", Member::getTitle);
        f.put("initials", Member::getInitials);
        f.put("firstNames", Member::getFirstNames);
        f.put("surname", Member::getSurname);
        f.put("identityType", Member::getIdentityType);
        f.put("identityNumber", Member::getIdentityNumber);
        f.put("passportNumber", Member::getPassportNumber);
        f.put("dateOfBirth", Member::getDateOfBirth);
        f.put("gender", Member::getGender);
        f.put("race", Member::getRace);
        f.put("status", Member::getStatus);
        f.put("employeeNumber", Member::getEmployeeNumber);
        f.put("personalEmail", Member::getPersonalEmail);
        f.put("workEmail", Member::getWorkEmail);
        f.put("preferredEmail", Member::getPreferredEmail);
        f.put("phone", Member::getPhone);
        f.put("cellTel", Member::getCellTel);
        f.put("workTel", Member::getWorkTel);
        f.put("homeTel", Member::getHomeTel);
        f.put("address", Member::getAddress);
        f.put("bankName", Member::getBankName);
        f.put("bankAccountNumber", Member::getBankAccountNumber);
        f.put("accountType", Member::getAccountType);
        f.put("branch", Member::getBranch);
        f.put("ibtNo", Member::getIbtNo);
        f.put("bankAddress", Member::getBankAddress);
        f.put("workBankAddress", Member::getWorkBankAddress);
        f.put("employmentType", Member::getEmploymentType);
        f.put("paymentType", Member::getPaymentType);
        f.put("debitDay", Member::getDebitDay);
        f.put("monthlySubscription", Member::getMonthlySubscription);
        f.put("subscriptionFrequency", Member::getSubscriptionFrequency);
        f.put("jobGrade", m -> m.getJobGrade() == null ? null : m.getJobGrade().getGrade());
        f.put("workplace", m -> m.getWorkplace() == null ? null
                : m.getWorkplace().getRaNumber() + " " + m.getWorkplace().getName());
        f.put("isRepresentative", Member::getIsRepresentative);
        // The structures (V55; asked for by sasbo-online-4c, 2026-09-30): a seat taken or left is what a member's
        // history is asked about. The committee by its code, which is stable. A No that clears its follow-ups -- BEC
        // clearing the committee and the three roles -- is written here too: the trail compares the member before and
        // after the save, never the request.
        f.put("isShopSteward", Member::getIsShopSteward);
        f.put("shopStewardElectionDate", Member::getShopStewardElectionDate);
        f.put("isBec", Member::getIsBec);
        // Month end 04.8 (MM80): the Memnet mark, set from the nomination form.
        f.put("isMemnet", Member::getIsMemnet);
        f.put("becCommitteeCode", m -> m.getBecCommittee() == null ? null : m.getBecCommittee().getCode());
        f.put("isBecRecruiter", Member::getIsBecRecruiter);
        f.put("isBecStructureRep", Member::getIsBecStructureRep);
        f.put("becStructures", Member::getBecStructures);
        f.put("isBecExecutive", Member::getIsBecExecutive);
        f.put("becExecutivePosition", Member::getBecExecutivePosition);
        f.put("isCosatu", Member::getIsCosatu);
        f.put("isCosatuLocal", Member::getIsCosatuLocal);
        f.put("isCosatuRegional", Member::getIsCosatuRegional);
        f.put("cosatuPositions", Member::getCosatuPositions);
        f.put("remarks", Member::getRemarks);
        return f;
    }

    /** The member as the history reads them -- every field it names, masked where it must be. Take it FIRST. */
    public Map<String, String> snapshot(Member member) {
        Map<String, String> values = new LinkedHashMap<>();
        FIELDS.forEach((field, read) -> values.put(field, shown(field, read.apply(member))));
        return values;
    }

    /** A capture: one row, {@code captured}. */
    public void captured(Member saved) {
        changes.save(new MemberChange(saved.getId(), UUID.randomUUID().toString(), CAPTURED, null,
                saved.getMembershipNumber(), ACTOR, clock.instant()));
    }

    /**
     * A save: one row a field that differs between {@code before} and {@code after}, all under one save's id. Nothing
     * is written where nothing changed. {@code before} is {@code null} only where no snapshot could be taken -- a
     * collaborator standing in for this one -- and then nothing is written either.
     */
    public void changed(Integer memberId, Map<String, String> before, Map<String, String> after) {
        if (before == null || after == null) {
            return;
        }
        String saveId = UUID.randomUUID().toString();
        List<MemberChange> rows = new ArrayList<>();
        for (Map.Entry<String, String> field : after.entrySet()) {
            String was = before.get(field.getKey());
            if (!Objects.equals(was, field.getValue())) {
                rows.add(new MemberChange(memberId, saveId, field.getKey(), was, field.getValue(), ACTOR,
                        clock.instant()));
            }
        }
        if (!rows.isEmpty()) {
            changes.saveAll(rows);
        }
    }

    /** A value as a person reads it: its text, blank as none, a masked number as its last four, capped at 500. */
    static String shown(String field, Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        if (MASKED.contains(field)) {
            return text.length() <= 4 ? "****" : "****" + text.substring(text.length() - 4);
        }
        return text.length() > 500 ? text.substring(0, 500) : text;
    }
}
