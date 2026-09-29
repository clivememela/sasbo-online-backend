package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.entity.member.Member;

import java.util.ArrayList;
import java.util.List;

public interface EmailService {
    byte[] buildWelcomeLetterPdf(Member member);

    void sendWelcomeLetter(Member member);

    /**
     * Who a welcome letter goes to: the address(es) the member's preference selects, or, when the
     * preference selects none, whichever address the member has -- personal first.
     *
     * <p>🔑 ONE rule, read in two places: by the sender, and by the capture deciding whether a letter
     * can be attempted at all (A64). It lives here, on the interface, because the capture must be able
     * to ask it where mail is NOT configured and no implementation exists. Two copies would drift, and
     * the symptom would be a capture reporting "no address" for a member the sender could have reached.
     */
    static List<String> recipientsOf(Member member) {
        String pref = member.getPreferredEmail();
        List<String> out = new ArrayList<>();
        boolean wantPersonal = "personal".equalsIgnoreCase(pref) || "both".equalsIgnoreCase(pref);
        boolean wantWork     = "work".equalsIgnoreCase(pref)     || "both".equalsIgnoreCase(pref);
        if (wantPersonal && hasValue(member.getPersonalEmail())) out.add(member.getPersonalEmail().trim());
        if (wantWork     && hasValue(member.getWorkEmail()))     out.add(member.getWorkEmail().trim());
        // Fallback when no preference is set
        if (out.isEmpty()) {
            if (hasValue(member.getPersonalEmail())) out.add(member.getPersonalEmail().trim());
            else if (hasValue(member.getWorkEmail())) out.add(member.getWorkEmail().trim());
        }
        return out;
    }

    private static boolean hasValue(String s) {
        return s != null && !s.isBlank();
    }
}
