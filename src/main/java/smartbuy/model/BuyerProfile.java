package smartbuy.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "buyer_profiles")
public class BuyerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private BigDecimal maxPrice;

    @Column(nullable = false)
    private Integer minBedrooms;

    @Column(nullable = false)
    private Integer minBathrooms;

    @Column(nullable = false)
    private Boolean needsSchool;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PriorityMode priorityMode;

    public enum PriorityMode {
        BALANCED,
        BUDGET_DRIVEN,
        SAFETY_FIRST,
        EDUCATION_FIRST
    }

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Constructors
    public BuyerProfile() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public BigDecimal getMaxPrice() { return maxPrice; }
    public void setMaxPrice(BigDecimal maxPrice) { this.maxPrice = maxPrice; }

    public Integer getMinBedrooms() { return minBedrooms; }
    public void setMinBedrooms(Integer minBedrooms) { this.minBedrooms = minBedrooms; }

    public Integer getMinBathrooms() { return minBathrooms; }
    public void setMinBathrooms(Integer minBathrooms) { this.minBathrooms = minBathrooms; }

    public Boolean getNeedsSchool() { return needsSchool; }
    public void setNeedsSchool(Boolean needsSchool) { this.needsSchool = needsSchool; }

    public PriorityMode getPriorityMode() { return priorityMode; }
    public void setPriorityMode(PriorityMode priorityMode) { this.priorityMode = priorityMode; }

    
    public double[] getWeights() {
        return switch (priorityMode) {
            case BALANCED -> new double[]{0.40, 0.25, 0.20, 0.15};
            case BUDGET_DRIVEN -> new double[]{0.50, 0.20, 0.20, 0.10};
            case SAFETY_FIRST -> new double[]{0.25, 0.15, 0.50, 0.10};
            case EDUCATION_FIRST -> new double[]{0.20, 0.15, 0.15, 0.50};
        };
    }
}
