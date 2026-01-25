package smartbuy.house;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "houses")
public class House {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long attomId;

    @Column(columnDefinition = "text")
    private String address1;

    /**
     * City, State ZIP (ATTOM basicprofile requires address2).
     * Keep nullable for now because we already have existing rows created before this field existed.
     */
    @Column(columnDefinition = "text")
    private String address2;

    /**
     * Raw geoIdV4 object stored as JSON string (minimal + DB-portable).
     */
    @Column(name = "geo_idv4", nullable = false, columnDefinition = "text")
    private String geoIdV4;

    /**
     * Frequently-used geoIdV4.N2 extracted into its own column for convenience.
     * Keep nullable for now because we already have existing rows created before this field existed.
     */
    @Column(name = "crime_id", length = 64)
    private String crimeId;

    @Column(name = "beds")
    private Integer beds;

    @Column(name = "baths_total", precision = 6, scale = 2)
    private BigDecimal bathsTotal;

    @Column(name = "rooms_total")
    private Integer roomsTotal;

    @Column(name = "avm_value")
    private Long avmValue;

    @Column(name = "schools_json", columnDefinition = "text")
    private String schoolsJson;

    @Column(name = "crime_index")
    private Integer crimeIndex;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected House() {
    }

    public House(
            String address1,
            String address2,
            Long attomId,
            String geoIdV4,
            String crimeId,
            Integer beds,
            BigDecimal bathsTotal,
            Integer roomsTotal,
            Long avmValue,
            String schoolsJson,
            Integer crimeIndex
    ) {
        this.address1 = address1;
        this.address2 = address2;
        this.attomId = attomId;
        this.geoIdV4 = geoIdV4;
        this.crimeId = crimeId;
        this.beds = beds;
        this.bathsTotal = bathsTotal;
        this.roomsTotal = roomsTotal;
        this.avmValue = avmValue;
        this.schoolsJson = schoolsJson;
        this.crimeIndex = crimeIndex;
    }

    public Long getId() {
        return id;
    }

    public Long getAttomId() {
        return attomId;
    }

    public String getAddress1() {
        return address1;
    }

    public String getAddress2() {
        return address2;
    }

    public String getGeoIdV4() {
        return geoIdV4;
    }

    public String getCrimeId() {
        return crimeId;
    }

    public Integer getBeds() {
        return beds;
    }

    public BigDecimal getBathsTotal() {
        return bathsTotal;
    }

    public Integer getRoomsTotal() {
        return roomsTotal;
    }

    public Long getAvmValue() {
        return avmValue;
    }

    public String getSchoolsJson() {
        return schoolsJson;
    }

    public Integer getCrimeIndex() {
        return crimeIndex;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
