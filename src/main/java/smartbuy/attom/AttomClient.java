package smartbuy.attom;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.lang.NonNull;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Service
public class AttomClient {

    private final RestClient attomRestClient;

  
    public AttomClient(
            @NonNull @Value("${attom.base-url}") String baseUrl,
            @Value("${attom.api-key}") String apiKey
    ) {
        String cleanUrl = baseUrl.split(",")[0].trim();
        String cleanKey = apiKey.trim();

        RestClient.Builder builder = RestClient.builder().baseUrl(cleanUrl);
        
        builder.defaultHeader("apikey", apiKey);
        this.attomRestClient = builder.build();
    }

    /**
     * Calls ATTOM AVM detail by address.
     * /propertyapi/v1.0.0/attomavm/detail?address1=4529%20Winona%20Court&address2=Denver%2C%20CO
     */
    public JsonNode avmDetail(String address1, String address2) {
        try {
            return attomRestClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/propertyapi/v1.0.0/attomavm/detail")
                            .queryParam("address1", address1)
                            .queryParam("address2", address2)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("ATTOM avm detail failed: " + e.getResponseBodyAsString(), e);
        }
    }

    /**
     * Calls ATTOM schools endpoint by attomId (v4).
     * /propertyapi/v4/property/detailwithschools?attomid=184713191
     */
    public JsonNode detailWithSchools(long attomId) {
        try {
            return attomRestClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/propertyapi/v4/property/detailwithschools")
                            .queryParam("attomid", attomId)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("ATTOM detailwithschools failed: " + e.getResponseBodyAsString(), e);
        }
    }

    public SchoolsData schoolsData(long attomId) {
        JsonNode resp = detailWithSchools(attomId);
        JsonNode school = resp.path("property").path(0).path("school");
        // if (school.isMissingNode() || school.isNull()) {
        //     school = resp.path("school");
        // }
        // we can double check if the current api datastructure response keep the same 
        String schoolsJson = (school.isMissingNode() || school.isNull()) ? "[]" : school.toString();
        return new SchoolsData(schoolsJson);
    }

    /**
     * /v4/neighborhood/community?geoIdv4=<N2>
     * Note: In your usage, geoIdv4 is actually geoIdV4.N2. renamed as crimeId
     */
    public JsonNode neighborhoodCommunity(String geoIdv4) {
        try {
            return attomRestClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v4/neighborhood/community")
                            .queryParam("geoIdv4", geoIdv4)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("ATTOM neighborhood/community failed: " + e.getResponseBodyAsString(), e);
        }
    }

    /**
     * Extract crime.crime_Index from neighborhood community response.
     */
    public Integer crimeIndex(String crimeId) {
        if (crimeId == null || crimeId.isBlank()) {
            return null;
        }
        JsonNode resp = neighborhoodCommunity(crimeId);
    
        JsonNode idx = resp.path("community").path("crime").path("crime_Index");
        if (!idx.isMissingNode() && !idx.isNull()) {
            return idx.asInt();
        }
        // idx = resp.path("crime").path("crime_Index");
        // if (!idx.isMissingNode() && !idx.isNull()) {
        //     return idx.asInt();
        // }    // we might not need this fallback check, assume only use this same api call . 
        return null;
    }

    /**
     * we need the following data from Attom api 
     * - attomId
     * - geoIdV4 (full JSON)
     * - crimeId (geoIdV4.N2; null if missing)
     * - beds, bathstotal
     * - avm amount.value
     */
    public AvmDetailData avmDetailData(String address1, String address2) {
        JsonNode resp = avmDetail(address1, address2);

        long attomId = resp.path("status").path("attomId").asLong();
        JsonNode property0 = resp.path("property").path(0);
        JsonNode geoIdV4 = property0.path("location").path("geoIdV4");
        if (attomId == 0 || geoIdV4.isMissingNode() || geoIdV4.isNull()) {
            throw new IllegalStateException("ATTOM avm detail missing attomId/geoIdV4");
        }

        String n2 = geoIdV4.path("N2").asText(null);

        String crimeId = (n2 == null || n2.isBlank()) ? null : n2;

        JsonNode rooms = property0.path("building").path("rooms");
        if (rooms.isMissingNode() || rooms.isNull()) {
            rooms = property0.path("rooms");
        }

        Integer beds = null;
        BigDecimal bathsTotal = null;

        JsonNode bedsNode = rooms.path("beds");
        if (!bedsNode.isMissingNode() && !bedsNode.isNull()) {
            beds = bedsNode.asInt();
        }

        JsonNode bathsTotalNode = rooms.path("bathstotal");
        if (!bathsTotalNode.isMissingNode() && !bathsTotalNode.isNull()) {
            try {
                bathsTotal = bathsTotalNode.decimalValue();
            } catch (Exception ignored) {
            }
        }

        JsonNode avm = property0.path("avm");
        JsonNode avmValueNode = avm.path("amount").path("value");
        Long avmValue = (!avmValueNode.isMissingNode() && !avmValueNode.isNull()) ? avmValueNode.asLong() : null;

        return new AvmDetailData(attomId, geoIdV4.toString(), crimeId, beds, bathsTotal, avmValue);
    }

    public record SchoolsData(String schoolsJson) {}

    public record AvmDetailData(
            long attomId,
            String geoIdV4,
            String crimeId,
            Integer beds,
            BigDecimal bathsTotal,
            Long avmValue
    ) {}
}

