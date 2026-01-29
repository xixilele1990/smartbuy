package smartbuy.house;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

// no data for house
@JsonIgnoreProperties(ignoreUnknown = true)
public class House {

    private Long attomId;

    private String address1;

    private String address2;

    private String geoIdV4;

    private String crimeId;

    private Integer beds;

    private BigDecimal bathsTotal;

    private Long avmValue;

    private String schoolsJson;

    private Integer crimeIndex;

    public House() {
    }

    public House(
            String address1,
            String address2,
            Long attomId,
            String geoIdV4,
            String crimeId,
            Integer beds,
            BigDecimal bathsTotal,
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

    public Long getAvmValue() {
        return avmValue;
    }

    public String getSchoolsJson() {
        return schoolsJson;
    }

    public Integer getCrimeIndex() {
        return crimeIndex;
    }

    public void setAttomId(Long attomId) {
        this.attomId = attomId;
    }

    public void setAddress1(String address1) {
        this.address1 = address1;
    }

    public void setAddress2(String address2) {
        this.address2 = address2;
    }

    public void setGeoIdV4(String geoIdV4) {
        this.geoIdV4 = geoIdV4;
    }

    public void setCrimeId(String crimeId) {
        this.crimeId = crimeId;
    }

    public void setBeds(Integer beds) {
        this.beds = beds;
    }

    public void setBathsTotal(BigDecimal bathsTotal) {
        this.bathsTotal = bathsTotal;
    }

    public void setAvmValue(Long avmValue) {
        this.avmValue = avmValue;
    }

    public void setSchoolsJson(String schoolsJson) {
        this.schoolsJson = schoolsJson;
    }

    public void setCrimeIndex(Integer crimeIndex) {
        this.crimeIndex = crimeIndex;
    }
}
