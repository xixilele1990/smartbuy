package smartbuy.scoring;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import smartbuy.buyerprofile.BuyerProfile;
import smartbuy.buyerprofile.PriorityMode;
import smartbuy.house.House;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ScoringServiceTest {

    private final ScoringService scoringService = new ScoringService();

    private static BuyerProfile profile(PriorityMode mode, BigDecimal maxPrice, int minBedrooms, BigDecimal minBathrooms) {
        BuyerProfile p = new BuyerProfile();
        p.setSessionId("test-session");
        p.setPriorityMode(mode);
        p.setMaxPrice(maxPrice);
        p.setMinBedrooms(minBedrooms);
        p.setMinBathrooms(minBathrooms);
        return p;
    }

    private static House house(Long avmValue, Integer beds, BigDecimal bathsTotal, Integer crimeIndex, String schoolsJson) {
        House h = new House();
        h.setAddress1("x");
        h.setAddress2("y");
        h.setAvmValue(avmValue);
        h.setBeds(beds);
        h.setBathsTotal(bathsTotal);
        h.setCrimeIndex(crimeIndex);
        h.setSchoolsJson(schoolsJson);
        return h;
    }

    @Test
    void score_returnsDimensionsAndSummary() {
        BuyerProfile p = profile(PriorityMode.BALANCED, new BigDecimal("2000000"), 3, new BigDecimal("2.0"));
        House h = house(1_900_000L, 3, new BigDecimal("2.0"), 33,
                "[{\"schoolRating\":\"A\"},{\"schoolRating\":\"B+\"}]");

        ScoreResponse resp = scoringService.score(p, h);

        assertNotNull(resp);
        assertNotNull(resp.totalScore());
        assertNotNull(resp.dimensions());
        assertEquals(4, resp.dimensions().size());
        assertEquals(List.of("Price", "Space", "Safety", "Schools"),
                resp.dimensions().stream().map(DimensionScore::getName).toList());
        assertNotNull(resp.summary());
        assertFalse(resp.summary().isBlank());
    }

    @Test
    void safetyScore_mapping_matchesIndexDefinition() {
        BuyerProfile p = profile(PriorityMode.BALANCED, new BigDecimal("2000000"), 3, new BigDecimal("2.0"));
        String schoolsJson = "[{\"schoolRating\":\"A\"}]";

        // C <= 80 -> 100
        ScoreResponse respLow = scoringService.score(p, house(1_900_000L, 3, new BigDecimal("2.0"), 33, schoolsJson));
        int safetyLow = respLow.dimensions().stream().filter(d -> d.getName().equals("Safety")).findFirst().orElseThrow().getScore();
        assertEquals(100, safetyLow);

        // 80 < C < 200 -> linear down (current impl uses denominator 120)
        ScoreResponse respMid = scoringService.score(p, house(1_900_000L, 3, new BigDecimal("2.0"), 113, schoolsJson));
        int safetyMid = respMid.dimensions().stream().filter(d -> d.getName().equals("Safety")).findFirst().orElseThrow().getScore();
        assertEquals(73, safetyMid);

        // C >= 200 -> 0 
        ScoreResponse respHigh = scoringService.score(p, house(1_900_000L, 3, new BigDecimal("2.0"), 200, schoolsJson));
        int safetyHigh = respHigh.dimensions().stream().filter(d -> d.getName().equals("Safety")).findFirst().orElseThrow().getScore();
        assertEquals(0, safetyHigh);
    }

    @Test
    void schoolsScore_averagesMappedRatings() {
        BuyerProfile p = profile(PriorityMode.BALANCED, new BigDecimal("2000000"), 3, new BigDecimal("2.0"));
        House h = house(1_900_000L, 3, new BigDecimal("2.0"), 33,
                "[{\"schoolRating\":\"A \"},{\"schoolRating\":\"B+\"},{\"schoolRating\":\"C-\"}]");

        ScoreResponse resp = scoringService.score(p, h);
        int school = resp.dimensions().stream().filter(d -> d.getName().equals("Schools")).findFirst().orElseThrow().getScore();
        // A -> 90, B -> 75, C -> 60 ， avg 75
        assertEquals(75, school);
    }

    @Test
    void priceFitScore_tiers_matchSpec() {
        BuyerProfile p = profile(PriorityMode.BALANCED, new BigDecimal("100"), 0, new BigDecimal("0.0"));
        String schoolsJson = "[{\"schoolRating\":\"A\"}]";

        assertEquals(100, priceScore(p, 80L, schoolsJson));  
        assertEquals(95, priceScore(p, 85L, schoolsJson));   
        assertEquals(90, priceScore(p, 100L, schoolsJson));  
        assertEquals(80, priceScore(p, 105L, schoolsJson)); 
        assertEquals(70, priceScore(p, 110L, schoolsJson));  
        assertEquals(50, priceScore(p, 120L, schoolsJson));  
        assertEquals(30, priceScore(p, 121L, schoolsJson));  
    }

    private int priceScore(BuyerProfile p, long avmValue, String schoolsJson) {
        House h = house(avmValue, 0, new BigDecimal("0.0"), 33, schoolsJson);
        ScoreResponse resp = scoringService.score(p, h);
        return resp.dimensions().stream().filter(d -> d.getName().equals("Price")).findFirst().orElseThrow().getScore();
    }

    @Test
    void failFast_missingCrimeIndex_returns400() {
        BuyerProfile p = profile(PriorityMode.BALANCED, new BigDecimal("2000000"), 3, new BigDecimal("2.0"));
        House h = house(1_900_000L, 3, new BigDecimal("2.0"), null,
                "[{\"schoolRating\":\"A\"}]");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> scoringService.score(p, h));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("house.crimeIndex is required", ex.getReason());
    }

    @Test
    void failFast_missingSchoolsJson_returns400() {
        BuyerProfile p = profile(PriorityMode.BALANCED, new BigDecimal("2000000"), 3, new BigDecimal("2.0"));
        House h = house(1_900_000L, 3, new BigDecimal("2.0"), 33, " ");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> scoringService.score(p, h));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("house.schoolsJson is required", ex.getReason());
    }
}

