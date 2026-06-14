package za.co.titandynamix.sasbo_online_backend.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.Hibernate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "roles")
public class User {

    /**
     * Unique identifier for the user.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "uuid")
    private String id;

    /**
     * Title of the user.
     */
    private String title;

    /**
     * First name of the user.
     */
    private String firstname;

    /**
     * Last name of the user.
     */
    private String lastname;

    /**
     * Unique username for the user.
     */
    @Column(nullable = false, unique = true)
    private String username;

    /**
     * Date of birth of the user.
     */
    private LocalDate dob;

    /**
     * Telephone number of the user.
     */
    private long tel;

    /**
     * Additional tag for the user.
     */
    private String tag;

    /**
     * Encrypted password for the user.
     */
    @JsonIgnore
    private String password;

    /**
     * Gender of the user.
     */
    private String gender;

    /**
     * Timestamp of when the user was created.
     */
    @CreationTimestamp
    private LocalDateTime createdAt;

    /**
     * Timestamp of when the user was last updated.
     */
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Embedded
    private TwoFactorAuthentication twoFactorAuthentication = new TwoFactorAuthentication();

    @Builder.Default
    @Column(name = "is_enabled")
    private boolean enabled = true;

    /**
     * List of roles assigned to the user.
     */
    @Builder.Default
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();

    // Helper Methods to Synchronize Bidirectional Relationship
    public void addRole(Role role) {
        this.roles.add(role);
        role.getUsers().add(this);
    }

    public void removeRole(Role role) {
        this.roles.remove(role);
        role.getUsers().remove(this);
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        User user = (User) o;
        return getUsername() != null && Objects.equals(getUsername(), user.getUsername());
    }

    @Override
    public final int hashCode() {
        return Objects.hash(Hibernate.getClass(this), getUsername());
    }

}
