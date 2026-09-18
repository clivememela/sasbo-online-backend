package za.co.titandynamix.sasbo_online_backend.services.impl;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EmailServiceImpl")
class EmailServiceImplTest {

    private static final String FROM_ADDRESS = "noreply@sasbo.org.za";

    @Mock
    private JavaMailSender mailSender;

    @Captor
    private ArgumentCaptor<MimeMessage> messageCaptor;

    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        this.emailService = new EmailServiceImpl(this.mailSender);
        ReflectionTestUtils.setField(this.emailService, "from", FROM_ADDRESS);
        when(this.mailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage((Session) null));
    }

    @Test
    @DisplayName("sends welcome letter to personal email when preference is personal")
    void sendsToPersonalEmailWhenPreferred() throws Exception {
        Member member = MemberFixture.aMember();
        member.setTitle("Ms");
        member.setFirstNames("Thandi");
        member.setSurname("Khumalo");
        member.setPersonalEmail("thandi@personal.example.com");
        member.setWorkEmail("thandi@work.example.com");
        member.setPreferredEmail("personal");
        member.setMembershipNumber("M12345");
        member.setAddress("123 Union St, Braamfontein");

        this.emailService.sendWelcomeLetter(member);

        verify(this.mailSender, times(1)).send(this.messageCaptor.capture());
        MimeMessage sent = this.messageCaptor.getValue();
        assertThat(sent.getSubject()).isEqualTo("Welcome to SASBO – Your Membership is Confirmed");
        assertThat(sent.getAllRecipients()).hasSize(1);
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("thandi@personal.example.com");
    }

    @Test
    @DisplayName("sends welcome letter to work email when preference is work")
    void sendsToWorkEmailWhenPreferred() throws Exception {
        Member member = MemberFixture.aMember();
        member.setPersonalEmail("thandi@personal.example.com");
        member.setWorkEmail("thandi@work.example.com");
        member.setPreferredEmail("work");

        this.emailService.sendWelcomeLetter(member);

        verify(this.mailSender, times(1)).send(this.messageCaptor.capture());
        MimeMessage sent = this.messageCaptor.getValue();
        assertThat(sent.getAllRecipients()).hasSize(1);
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("thandi@work.example.com");
    }

    @Test
    @DisplayName("sends welcome letter to both emails when preference is both")
    void sendsToBothEmailsWhenPreferred() throws Exception {
        Member member = MemberFixture.aMember();
        member.setPersonalEmail("thandi@personal.example.com");
        member.setWorkEmail("thandi@work.example.com");
        member.setPreferredEmail("both");

        this.emailService.sendWelcomeLetter(member);

        verify(this.mailSender, times(2)).send(this.messageCaptor.capture());
        var messages = this.messageCaptor.getAllValues();
        assertThat(messages.get(0).getAllRecipients()[0].toString()).isEqualTo("thandi@personal.example.com");
        assertThat(messages.get(1).getAllRecipients()[0].toString()).isEqualTo("thandi@work.example.com");
    }

    @Test
    @DisplayName("falls back to personal email when preference is not specified")
    void fallsBackToPersonalEmailWhenNoPreference() throws Exception {
        Member member = MemberFixture.aMember();
        member.setPersonalEmail("thandi@personal.example.com");
        member.setWorkEmail("thandi@work.example.com");
        member.setPreferredEmail(null);

        this.emailService.sendWelcomeLetter(member);

        verify(this.mailSender, times(1)).send(this.messageCaptor.capture());
        MimeMessage sent = this.messageCaptor.getValue();
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("thandi@personal.example.com");
    }

    @Test
    @DisplayName("falls back to work email when preference is not specified and personal email is blank")
    void fallsBackToWorkEmailWhenPersonalBlank() throws Exception {
        Member member = MemberFixture.aMember();
        member.setPersonalEmail(" ");
        member.setWorkEmail("thandi@work.example.com");
        member.setPreferredEmail(null);

        this.emailService.sendWelcomeLetter(member);

        verify(this.mailSender, times(1)).send(this.messageCaptor.capture());
        MimeMessage sent = this.messageCaptor.getValue();
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("thandi@work.example.com");
    }

    @Test
    @DisplayName("throws IllegalArgumentException when no recipient email address is available")
    void throwsWhenNoEmailAvailable() {
        Member member = MemberFixture.aMember();
        member.setPersonalEmail(null);
        member.setWorkEmail(" ");
        member.setPreferredEmail("personal");

        assertThatThrownBy(() -> this.emailService.sendWelcomeLetter(member))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No email address is available for this member.");

        verify(this.mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("handles member without title, membership number, or address gracefully")
    void handlesMemberWithMinimalFields() throws Exception {
        Member member = MemberFixture.aMember();
        member.setTitle(null);
        member.setMembershipNumber(null);
        member.setAddress(null);
        member.setPersonalEmail("thandi@example.com");

        this.emailService.sendWelcomeLetter(member);

        verify(this.mailSender, times(1)).send(this.messageCaptor.capture());
        MimeMessage sent = this.messageCaptor.getValue();
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("thandi@example.com");
    }
}
