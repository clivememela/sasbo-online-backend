package za.co.titandynamix.sasbo_online_backend.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller of sign-in (October build): {@code /api/auth}, {@code /api/admin} and {@code /api/audit}. Its
 * refusals are answered by {@code SignInExceptionHandler}, scoped to these controllers, so every other route of the
 * application answers exactly as it did -- the pattern of {@link MonthEndApi}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SignInApi {
}
