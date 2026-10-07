package za.co.titandynamix.sasbo_online_backend.legacy.rules;

import za.co.titandynamix.sasbo_online_backend.legacy.mapping.MemberField;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A row after every rule: its outcome, its problems split by severity, the member fields as they would be written, and
 * what the move and the reconciliation need beside them. Its {@code toString} names the outcome and nothing it holds.
 *
 * @param values          the fields a move writes -- only those that passed: a telephone number that is not one, or an
 *                        identity number SASBO chose to move without, is not here
 * @param memberStatus    the member's status as the record spells it (Active, Suspended, or a leaving status)
 * @param statusKind      {@code N}, {@code S} or {@code L} -- what the figures read of a status (PA-4)
 * @param identityType    {@code I.D. Number} or {@code Passport Number}, as capture stores them; {@code null} for none
 * @param identityKey     the identity number, where it is valid: what duplicates are found by
 * @param membershipKey   the membership number as it will be written ({@code M-123456})
 * @param membershipSeq   its number alone, where it is digits: the capture sequence is moved past the highest
 * @param statusChangedOn the day the member left or was suspended, for the status change a move writes
 */
public record CleanRow(Outcome outcome, List<Problem> errors, List<Problem> warnings, Map<MemberField, String> values,
                       String memberStatus, String statusKind, String identityType, String identityKey,
                       String membershipKey, Long membershipSeq, Long workplaceId, String areaLetter, Integer tableCode,
                       Integer deletionCode, Long deletionCodeId, Integer suspensionCode, Long suspensionCodeId,
                       LocalDate joinDate, LocalDate leftOn, LocalDate statusChangedOn) {

    public CleanRow {
        errors = List.copyOf(errors);
        warnings = List.copyOf(warnings);
        EnumMap<MemberField, String> copied = new EnumMap<>(MemberField.class);
        copied.putAll(values);
        values = Collections.unmodifiableMap(copied);
    }

    @Override
    public String toString() {
        return "CleanRow[" + outcome + ", " + errors.size() + " errors, " + warnings.size() + " warnings]";
    }
}
