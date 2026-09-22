package za.co.titandynamix.sasbo_online_backend.entity.acb;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import za.co.titandynamix.sasbo_online_backend.enums.LetterChannel;
import za.co.titandynamix.sasbo_online_backend.enums.LetterStatus;
import za.co.titandynamix.sasbo_online_backend.enums.LetterTemplate;

import java.time.Instant;

/**
 * The union's letter for one unpaid: its template, where it is on its way, and what it was sent to.
 * The body is not kept -- it is rendered from the unpaid whenever it is needed, and its hash kept.
 */
@Entity
@Table(name = "acb_letters")
@Getter
@Setter
@NoArgsConstructor
public class AcbLetter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "line_id", nullable = false)
    private Long lineId;

    @Enumerated(EnumType.STRING)
    @Column(name = "template", nullable = false, length = 20)
    private LetterTemplate template;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LetterStatus status;

    /** Why a letter is waiting -- {@code NO_EMAIL} -- or why it failed, as a word, never a message. */
    @Column(name = "status_reason", length = 40)
    private String statusReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 10)
    private LetterChannel channel;

    @Column(name = "attempts", nullable = false)
    private Integer attempts;

    @Column(name = "body_sha256", length = 64)
    private String bodySha256;

    /** Where it went: the email address it was rendered for, as it was at the time. */
    @Column(name = "address_snapshot", length = 500)
    private String addressSnapshot;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "rendered_at")
    private Instant renderedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "failure_class", length = 200)
    private String failureClass;

    /** How a held letter was dealt with by hand (Q46) -- never logged, never audited (SD3-D). */
    @Column(name = "handled_note", length = 300)
    private String handledNote;

    @Column(name = "handled_at")
    private Instant handledAt;

    /** Who queued it (V51): a letter is sent only if a named administrator's recording queued it (03.3, B1). */
    @Column(name = "queued_by", length = 100)
    private String queuedBy;
}
