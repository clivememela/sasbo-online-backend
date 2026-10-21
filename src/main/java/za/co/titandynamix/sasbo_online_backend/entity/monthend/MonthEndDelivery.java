package za.co.titandynamix.sasbo_online_backend.entity.monthend;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * What was sent to whom, and when (October build): every download, e-mail and SFTP upload of a region's pack or a
 * bank's or provider's file -- SENT, or REFUSED or FAILED with its code. Append-only on PostgreSQL by trigger. Written
 * by {@code MonthEndSentRepository}'s statements; this entity exists so that H2's schema has the table.
 */
@Entity
@Table(name = "month_end_deliveries")
@Getter
@NoArgsConstructor
public class MonthEndDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pack_id")
    private Long packId;

    @Column(name = "file_id")
    private Long fileId;

    @Column(name = "month_start", nullable = false)
    private LocalDate monthStart;

    @Column(name = "kind", nullable = false, length = 14)
    private String kind;

    @Column(name = "recipient_code", nullable = false, length = 20)
    private String recipientCode;

    @Column(name = "recipient_name", nullable = false, length = 60)
    private String recipientName;

    @Column(name = "channel", nullable = false, length = 10)
    private String channel;

    @Column(name = "formats", nullable = false, length = 20)
    private String formats;

    @Column(name = "address", length = 254)
    private String address;

    @Column(name = "outcome", nullable = false, length = 10)
    private String outcome;

    @Column(name = "code", length = 40)
    private String code;

    @Column(name = "file_name", nullable = false, length = 220)
    private String fileName;

    @Column(name = "delivered_by", nullable = false, length = 100)
    private String deliveredBy;

    @Column(name = "delivered_at", nullable = false)
    private Instant deliveredAt;

    @Override
    public String toString() {
        return "MonthEndDelivery[" + id + ", " + kind + ", " + channel + ", " + outcome + "]";
    }
}
