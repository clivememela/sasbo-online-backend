package za.co.titandynamix.sasbo_online_backend.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleEntityNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        ApiErrorResponse response = new ApiErrorResponse(
                LocalDateTime.now(ZoneId.of("UTC")),
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationFailures(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ApiErrorResponse response = new ApiErrorResponse(
                LocalDateTime.now(ZoneId.of("UTC")),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Validation failed for the incoming request payload.",
                request.getRequestURI(),
                errors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * The catch-all. Everything the other handlers did not claim arrives here, which
     * makes it the one handler that must assume the exception is hostile to show.
     * <p>
     * 🔒 It does NOT return {@code ex.getMessage()}. For the exceptions that
     * actually reach it that string is the system's internals: a
     * {@code DataIntegrityViolationException} carries the failing SQL and its bound
     * parameters, which on {@code members} are an identity number, names and a bank
     * account number. Every member endpoint is {@code permitAll()}, so that was
     * readable by anyone who could provoke a 500.
     * <p>
     * The detail is not lost, only moved: it is logged in full beside a correlation
     * id, and the same id is the one thing returned. A caller reporting "error
     * a1b2c3d4" is enough to find the whole stack trace in the log, and it tells an
     * attacker nothing.
     * <p>
     * Handlers for expected failures ({@code IllegalArgumentException}, validation)
     * keep returning their messages -- those are written FOR the user, and 01.2's
     * whole point is that the user finally sees them.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneralException(Exception ex, HttpServletRequest request) {
        String reference = UUID.randomUUID().toString().substring(0, 8);
        log.error("Unhandled exception on {} [ref {}]", request.getRequestURI(), reference, ex);
        ApiErrorResponse response = new ApiErrorResponse(
                LocalDateTime.now(ZoneId.of("UTC")),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "Something went wrong on our side. Quote reference " + reference
                        + " if you report this.",
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * A duplicate member is a 409 the administrator can act on -- SR-5.
     *
     * <p>🔴 <b>WITHOUT THIS, V26's CONSTRAINT MAKES THE SCREEN WORSE, NOT BETTER.</b>
     * A {@code DataIntegrityViolationException} would fall through to the catch-all
     * above and come back as a 500 reading <i>"Something went wrong on our side.
     * Quote reference a1b2c3d4"</i> -- correct of that handler, and useless here.
     * Nothing went wrong on our side: the member is already captured, and the person
     * at the desk needs to be told that rather than handed a reference number to
     * report. A constraint that produces an unactionable error is a constraint that
     * gets dropped.
     *
     * <p>🔒 <b>The driver's message is still not returned.</b> That is the whole
     * lesson of {@code B4} and it does not weaken for a status we happen to like:
     * the message carries the failing SQL and its bound parameters, which on
     * {@code members} are an identity number and a bank account number, and
     * {@code /api/**} is {@code permitAll()}. So the text below is written HERE,
     * from scratch, and the exception is logged in full with a correlation id
     * exactly as the catch-all does.
     *
     * <p>⚠️ <b>It does not name which constraint fired, deliberately.</b> Only one
     * uniqueness rule exists on this API today, but the message will be read again
     * after somebody adds a second -- and a message that GUESSES which rule was
     * violated is worse than one that does not claim to know. It says what the
     * caller can do; the log says what happened.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        String reference = UUID.randomUUID().toString().substring(0, 8);
        log.warn("Constraint violation on {} [ref {}]", request.getRequestURI(), reference, ex);
        ApiErrorResponse response = new ApiErrorResponse(
                LocalDateTime.now(ZoneId.of("UTC")),
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                "This conflicts with a record that already exists -- most often a member "
                        + "with the same identity number. Search for them before capturing "
                        + "again. Quote reference " + reference + " if you need help.",
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        ApiErrorResponse response = new ApiErrorResponse(
                LocalDateTime.now(ZoneId.of("UTC")),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * A path no controller handles is a <b>404</b> -- whichever of the two
     * exceptions Spring raises for it. Register row A18.
     *
     * <h2>🔴 Which exception you get depends on configuration, and that is the
     * whole of A18</h2>
     * Spring has two ways to say "no handler", and this application's own settings
     * decide which one it uses:
     *
     * <ul>
     *   <li>{@code NoHandlerFoundException}, when
     *       {@code spring.mvc.throw-exception-if-no-handler-found} is true -- which
     *       {@code src/main/resources/application.yaml} sets, along with
     *       {@code spring.web.resources.add-mappings: false}. <b>This is what the
     *       deployed application raises</b>, and it has answered 404 all along.</li>
     *   <li>{@code NoResourceFoundException}, when the resource handler is mapped
     *       and takes the unmatched path. It is a {@code ServletException}, so the
     *       catch-all registered for {@code Exception} claimed it and answered
     *       <b>500</b>.</li>
     * </ul>
     *
     * <p>⚠️ <b>So A18's "every unknown URL answers 500" was true of the TEST SLICE,
     * not of production.</b> {@code src/test/resources/application.yaml} <b>shadows</b>
     * the production file of the same name and carries neither MVC key -- it is an H2
     * datasource and nothing else -- so a {@code @WebMvcTest} maps resources, raises
     * {@code NoResourceFoundException}, and gets the 500 that the deployed application
     * does not. Measured against the deployed API on 2026-09-18:
     * {@code GET /api/acb}, {@code /api/acb/no-such-path} and
     * {@code /api/definitely-not-a-route} each answered <b>404</b> with this
     * envelope. That shadowing is the same mechanism as register row <b>C12</b>,
     * and it is the second defect it has produced.
     *
     * <p>Both types are handled here anyway, and deliberately: Boot's default for
     * {@code add-mappings} is <i>true</i>, so the day anyone removes that line the
     * 500 returns. One handler for both means the two paths cannot answer
     * differently -- they were two byte-identical methods before this.
     *
     * <p>🔒 The body is written here rather than taken from {@code ex.getMessage()},
     * for the reason the catch-all gives at length: this API is {@code permitAll()},
     * so a 404 answers anyone. The path the caller asked for is already in the
     * envelope's {@code path} field, put there deliberately.
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound(HttpServletRequest request) {
        log.warn("No handler for {} {}", request.getMethod(), request.getRequestURI());
        ApiErrorResponse response = new ApiErrorResponse(
                LocalDateTime.now(ZoneId.of("UTC")),
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                "The requested resource was not found",
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
}
