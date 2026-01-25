package smartbuy.house;

import java.math.BigDecimal;

/**
 * No-DB branch DTO.
 * <p>
 * This class is intentionally NOT a JPA entity: in lexyno-db we fetch from ATTOM and feed data into
 * backend logic without persisting to a database.
 */
public class House {

    private Long attomId;

    private String address1;

    /**
     * City, State ZIP (ATTOM basicprofile requires address2).
     */
    private String address2;

    /**
     * Raw geoIdV4 object stored as JSON string.
     */
    private String geoIdV4;

    /**
     * Frequently-used geoIdV4.N2 extracted into its own field for convenience.
     */
    private String crimeId;

    private Integer beds;

    private BigDecimal bathsTotal;

    private Integer roomsTotal;

    private Long avmValue;

    private String schoolsJson;

    private Integer crimeIndex;

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
}
