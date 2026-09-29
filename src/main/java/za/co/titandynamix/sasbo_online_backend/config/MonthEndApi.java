package za.co.titandynamix.sasbo_online_backend.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller of the month-end arc (MD49). Every route of the arc lives in a controller that carries this --
 * the lists, the workplaces, the figures, a member's status and history, the month taken -- beside
 * {@code MemberController} and never inside it.
 *
 * <p>What it buys: {@code MonthEndExceptionHandler} is scoped to the controllers that carry it, so a request that
 * cannot be read answers {@code REQUEST_MALFORMED}, naming the field and nothing of what was sent, on this arc's routes
 * -- while every other route of the application answers exactly as it always has (register A63 stays as it is
 * elsewhere, and is proved unmoved). {@code MonthEndGuardsTest} holds every route of the arc to a controller that
 * carries it.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MonthEndApi {
}
