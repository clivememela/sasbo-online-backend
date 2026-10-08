package za.co.titandynamix.sasbo_online_backend.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller of the data migration. {@code MigrationExceptionHandler} is scoped to the controllers that carry
 * it, so the migration's refusals and its unreadable requests answer with its own codes -- and every other route of the
 * application answers exactly as it always has.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MigrationApi {
}
