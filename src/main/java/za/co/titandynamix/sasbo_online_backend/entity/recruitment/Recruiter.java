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

    /**
     * The seed's name for a recruiting post that currently has no incumbent.
     *
     * <p>🗣️ <b>The owner settled it on 2026-09-10: a vacancy is not selectable.</b>
     * {@code recruiters.json} carries three rows named {@code VACANT} — Cape Town,
     * and Durban twice — and a vacancy is not a person. Recruiter figures are what
     * these records get totalled into, so a member recorded as recruited by an
     * unfilled post is a number attributed to nobody.
     *
     * <p>🔴 <b>THE ROWS ARE NOT DELETED, AND MUST NOT BE.</b> Between them those
     * three carry <b>228 members recruited</b> (46 + 88 + 94) — the union's own
     * record of work done before the post fell empty. Hiding a vacancy from the
     * drop-down stops a NEW member being attributed to one; deleting the row would
     * destroy a count nothing else holds. Same distinction {@code 03.5} drew about
     * {@code membersRecruited} itself.
     */
    public static final String VACANCY = "VACANT";

    /**
     * Is this a post with no incumbent, rather than a person?
     *
     * <p>⚠️ {@code static}, so Jackson does not serialise it. The controller returns
     * this entity directly and an instance-shaped {@code isVacancy()} would appear in
     * every response as a new {@code "vacancy"} property — a widened API surface,
     * which {@code ApiSurfaceSnapshotTest} would (rightly) refuse.
     *
     * <p>Exact match, trimmed and case-insensitive — not "contains". A recruiter
     * legitimately named, say, {@code VACANTER} is a person, and a predicate loose
     * enough to catch a typo is loose enough to hide someone real.
     */
    public static boolean isVacancy(Recruiter recruiter) {
        return recruiter != null
                && recruiter.getName() != null
                && VACANCY.equalsIgnoreCase(recruiter.getName().trim());
    }
}
