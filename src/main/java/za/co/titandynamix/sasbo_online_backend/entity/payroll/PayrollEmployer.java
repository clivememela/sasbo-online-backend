package za.co.titandynamix.sasbo_online_backend.entity.payroll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollDelivery;

import java.math.BigDecimal;

/**
 * An employer that sends SASBO a payroll file each month (Stephne, PAY-01), and how its file is read: its layout, the
 * fee it keeps (Absa 3%, PAY-27), and an administrator's override of which heading is which field. V73.
 */
@Entity
@Table(name = "payroll_employers")
@Getter
@Setter
@NoArgsConstructor
public class PayrollEmployer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "ordinal", nullable = false)
    private Integer ordinal;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery", nullable = false, length = 10)
    private PayrollDelivery delivery;

    /** {@code false} for an employer whose file cannot be read (a PDF, a picture): its rows are typed in. */
    @Column(name = "readable", nullable = false)
    private Boolean readable;

    @Column(name = "layout_key", nullable = false, length = 40)
    private String layoutKey;

    @Column(name = "layout_version", nullable = false)
    private Integer layoutVersion;

    /** The layout the reader is driven by, as JSON ({@code PayrollLayout}). */
    @Column(name = "layout", nullable = false, length = 8000)
    private String layout;

    /** An administrator's override: field to heading as printed, as JSON; {@code null} for none. */
    @Column(name = "column_mapping", length = 2000)
    private String columnMapping;

    @Column(name = "admin_fee_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal adminFeePercent;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;
}
