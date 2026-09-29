package za.co.titandynamix.sasbo_online_backend.services;

import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;

/**
 * At most {@value #AT_ONCE} figure queries at once (02.2, MD63): a third is answered {@code SUMMARY_UNAVAILABLE} at once,
 * and does not queue. The read is open and the hosted database is a tenth of a processor -- a queue of figure queries
 * behind a slow one is how an open read takes the member register down with it.
 */
@Component
public class SummaryGate {

    /** How many figure queries may run together. Published in the vocabulary. */
    public static final int AT_ONCE = 2;

    private final Semaphore permits = new Semaphore(AT_ONCE);

    /** Whether a figure query may start now. Never waits. */
    public boolean tryEnter() {
        return permits.tryAcquire();
    }

    /** A figure query has finished -- answered, refused or failed. */
    public void leave() {
        permits.release();
    }
}
