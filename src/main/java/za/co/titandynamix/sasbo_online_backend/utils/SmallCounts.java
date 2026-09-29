package za.co.titandynamix.sasbo_online_backend.utils;

/**
 * Rule C, for one sum -- the rows of a grouping and the total they add up to (02.2, MD27 as refined by MD37).
 *
 * <p>Until sign-in exists, a row whose membership is from one to four, where the row NAMES an employer or a table code,
 * is answered as FEWER THAN FIVE, with no number on it. And no answer gives its own row back: where the total and the
 * rows beside it would narrow such a row to fewer than {@value #KEEP} of its four values, the smallest row of five or
 * more is WITHHELD with it -- and again, until nothing is narrowed. A total is never suppressed. The arithmetic is
 * {@code planning/branches/monthly-menu/legacy/reference.py}'s {@code Ledger}, the one this folder's own documents are
 * held to, for a single sum.
 *
 * <p>A courtesy, not a control: two answers read together can find a row, and the control is sign-in.
 */
public final class SmallCounts {

    /** Of the four values a row of fewer than five can have, how many the sum must leave a reader unable to rule out. */
    public static final int KEEP = 3;

    /** What a row's count is answered as. */
    public enum State {
        SHOWN,
        FEWER_THAN_FIVE,
        WITHHELD
    }

    private SmallCounts() {
    }

    /**
     * The state each row is answered in.
     *
     * @param counts  each row's membership
     * @param guarded whether each row names an employer or a table code -- a row of its own kind ("no workplace on
     *                record") is not guarded, but it is a part of the sum like any other
     * @param total   what the rows add up to, printed in full
     */
    public static State[] settle(long[] counts, boolean[] guarded, long total) {
        State[] states = new State[counts.length];
        for (int i = 0; i < counts.length; i++) {
            states[i] = guarded[i] && counts[i] >= 1 && counts[i] <= 4 ? State.FEWER_THAN_FIVE : State.SHOWN;
        }
        while (exposed(counts, states, total)) {
            int spare = smallestShown(counts, states, 5);
            if (spare < 0) {
                // Nothing of five or more is left to withhold: the smallest row still printed goes, whatever it holds.
                spare = smallestShown(counts, states, 1);
            }
            if (spare < 0) {
                return states;
            }
            states[spare] = State.WITHHELD;
        }
        return states;
    }

    private static boolean exposed(long[] counts, State[] states, long total) {
        for (int i = 0; i < counts.length; i++) {
            if (states[i] == State.FEWER_THAN_FIVE && values(i, counts, states, total) < KEEP) {
                return true;
            }
        }
        return false;
    }

    /** How many values a reader could still give row {@code target}, from the total and the rows beside it. */
    static int values(int target, long[] counts, State[] states, long total) {
        long leastOfOthers = 0;
        long mostOfOthers = 0;
        boolean unbounded = false;
        for (int j = 0; j < counts.length; j++) {
            if (j == target) {
                continue;
            }
            switch (states[j]) {
                case SHOWN -> {
                    leastOfOthers += counts[j];
                    mostOfOthers += counts[j];
                }
                case FEWER_THAN_FIVE -> {
                    leastOfOthers += 1;
                    mostOfOthers += 4;
                }
                case WITHHELD -> {
                    leastOfOthers += 5;
                    unbounded = true;
                }
            }
        }
        long least = unbounded ? 1 : Math.max(1, total - mostOfOthers);
        long most = Math.min(4, total - leastOfOthers);
        return (int) Math.max(0, most - least + 1);
    }

    private static int smallestShown(long[] counts, State[] states, long atLeast) {
        int smallest = -1;
        for (int i = 0; i < counts.length; i++) {
            if (states[i] == State.SHOWN && counts[i] >= atLeast && (smallest < 0 || counts[i] < counts[smallest])) {
                smallest = i;
            }
        }
        return smallest;
    }
}
