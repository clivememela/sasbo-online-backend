package za.co.titandynamix.sasbo_online_backend.entity.location;


import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonCreator;

// how to model South African address structures and bank branches using Java JPA (Jakarta Persistence API).
// In South Africa, addresses typically fall into Urban/Street formats:
//   (Street Number, Street Name, Suburb, City, Postal Code) or
//   Informal/Rural/Box formats. Additionally, major South African banks (like Standard Bank, ABSA, FNB, and Nedbank)
//   utilise specific 6-digit branch codes,
//   though many are moving toward universal branch codes.

// Using @Embeddable allows you to reuse the address structure across different entities
// and ensures that the address details are stored in the same table as the parent entity.
// (e.g., Branches, Customers).
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhysicalAddress {
    @Column(name = "complex_or_building", length = 100)
    private String complexOrBuilding; // Optional: e.g., "Unit 4, Willowbrook Complex"

    @Column(name = "street_number", length = 20)
    private String streetNumber; // e.g., "135" or "Shop 12"

    @Column(name = "street_name", length = 100)
    private String streetName; // e.g., "Rivonia Road"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suburb_id")
    private Suburb suburb;  // e.g., "Sandton"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city; // e.g., "Johannesburg"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "province_id")
    private Province province; // e.g., "Gauteng"

    @Column(name = "postal_code", length = 10)
    private String postalCode; // e.g., "2196"

    @Transient
    private String cityName;
    @Transient
    private String suburbName;
    @Transient
    private String provinceName;

    @JsonCreator
    public PhysicalAddress(String streetName) {
        this.streetName = streetName;
    }
}
