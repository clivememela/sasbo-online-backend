package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.entity.member.Member;

public interface EmailService {
    byte[] buildWelcomeLetterPdf(Member member);

    void sendWelcomeLetter(Member member);
}
