package za.co.titandynamix.sasbo_online_backend.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller of the payroll module (Payroll Deductions and Reconciliation). {@code PayrollExceptionHandler} is
 * scoped to the controllers that carry it, so a payroll route that cannot read a request answers
 * {@code REQUEST_MALFORMED}, naming the field and nothing of what was sent, while every other route of the application
 * answers exactly as it did.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PayrollApi {
}
