package smartbuy.buyerprofile;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name="buyer_profiles")
@Getter
public class BuyerProfile {

    // Primary Key: Internal ID for the database
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long profileId;

    @Column(nullable = false, unique = true)
    @Setter
    private String sessionId;

    // --- Financial Constraints ---
    @Column(nullable = false)
    @Setter
    private BigDecimal maxPrice;

    // --- Space Requirements ---
    @Column(nullable = false)
    @Setter
    private Integer minBedrooms;

    @Column(nullable = false)
    @Setter
    private BigDecimal minBathrooms;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Setter
    private PriorityMode priorityMode;

    // Audit field: Tracks when the profile was last modified
    private LocalDateTime updatedAt;

    // Constructors
    public BuyerProfile() {}

    // Lifecycle Hook: Automatically updates the timestamp before saving.
    @PrePersist
    @PreUpdate
    public void setupTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }

    public double[] getWeights() {
        return priorityMode != null ? priorityMode.getWeights() : null;
    }
}
