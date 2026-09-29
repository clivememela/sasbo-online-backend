package za.co.titandynamix.sasbo_online_backend.entity.codes;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "deletion_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeletionCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    /**
     * SASBO's own number for the code -- 1 to 10, as its reports print them (month end 01.1, V53). The id is never
     * SASBO's number (RC7): a request, a file and a screen name this. Empty on the reasons for resigning, which carry
     * no number of SASBO's, and on any code an administrator added by hand.
     */
    @Column(name = "sasbo_code")
    private Integer sasboCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private DeletionCode parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DeletionCode> subReasons = new ArrayList<>(); // Used for "Resigned Society Reasons"
}
