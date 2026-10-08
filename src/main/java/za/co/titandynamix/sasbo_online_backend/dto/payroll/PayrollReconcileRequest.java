package za.co.titandynamix.sasbo_online_backend.dto.payroll;

import java.util.Map;

/**
 * Compare a file again: against the members as they are now, and -- where {@code columnMapping} is given -- reading the
 * file again with that mapping (field to heading as printed). {@code version} is the file's, as the screen read it.
 */
public record PayrollReconcileRequest(Integer version, Map<String, String> columnMapping) {
}
