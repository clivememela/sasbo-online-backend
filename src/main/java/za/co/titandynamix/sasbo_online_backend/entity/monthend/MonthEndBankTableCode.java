package za.co.titandynamix.sasbo_online_backend.entity.monthend;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A table code a bank's monthly file is made from -- in one bank's file at most ({@code
 * month_end_bank_table_codes_one_bank}), so no member is sent to two banks. Written by {@code
 * MonthEndSendSettingsRepository}'s statements; this entity exists so that H2's schema has the table.
 */
@Entity
@Table(name = "month_end_bank_table_codes")
@Getter
@NoArgsConstructor
public class MonthEndBankTableCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bank_id", nullable = false)
    private Long bankId;

    @Column(name = "table_code", nullable = false, unique = true)
    private Integer tableCode;

    @Override
    public String toString() {
        return "MonthEndBankTableCode[" + bankId + ", " + tableCode + "]";
    }
}
