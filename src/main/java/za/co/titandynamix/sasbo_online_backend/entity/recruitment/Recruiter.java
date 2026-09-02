package za.co.titandynamix.sasbo_online_backend.entity.recruitment;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "recruiters")
public class Recruiter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id")
    private Integer id;

    @Column(name = "name")
    private String name;

    /** Number of members this recruiter / recruitment channel has recruited. */
    @Column(name = "members_recruited")
    private Integer membersRecruited;

    @Column(name = "region")
    private String region;

    @Column(name = "area_code")
    private String areaCode;
}
