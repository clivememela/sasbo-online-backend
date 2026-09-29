package za.co.titandynamix.sasbo_online_backend.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.support.TransactionOperations;

/**
 * The transaction a {@code @WebMvcTest} slice does not have.
 *
 * <p>A web slice loads no JPA and so no transaction manager, and {@code MemberController} runs a capture
 * through {@link TransactionOperations} (A64: the capture must commit before its welcome letter is
 * attempted). In a slice the callback simply runs, with nothing around it -- exactly what an
 * {@code @Transactional} annotation does in the same slice, which is to be inert.
 *
 * <p>So a slice proves what the controller DOES, never where its transaction ends. That is proven against
 * a real database, where the boundary exists: {@code WelcomeLetterAfterCaptureIT}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class NoTransactions {

    @Bean
    TransactionOperations transactionOperations() {
        return TransactionOperations.withoutTransaction();
    }
}
