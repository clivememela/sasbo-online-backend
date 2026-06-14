package za.co.titandynamix.sasbo_online_backend.entity.location;


import jakarta.persistence.*;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhysicalAddress {
    @Column(name = "complex_or_building", length = 100)
    private String complexOrBuilding;

    @Column(name = "street_number", length = 20)
    private String streetNumber;

    @Column(name = "street_name", length = 100)
    private String streetName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suburb_id", nullable = false)
    private Suburb suburb;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id", nullable = false)
    private City city;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "province_id", nullable = false)
    private Province province;

    @Column(name = "postal_code", nullable = false, length = 10)
    private String postalCode;
}
