package smartbuy.scoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import smartbuy.buyerprofile.BuyerProfile;
import smartbuy.buyerprofile.PriorityMode;
import smartbuy.house.House;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;


@Service
public class ScoringService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScoreResponse score(BuyerProfile profile, House house) {
        // fail-fast：any missing data, throw 400, no totalScore
        if (profile == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "buyerProfile is required");
        }
        if (profile.getPriorityMode() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "buyerProfile.priorityMode is required");
        }
        if (house == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "house is required");
        }

        PriorityMode mode = profile.getPriorityMode();

        int price = priceFitScore(house, profile);
        int space = spaceScore(profile, house);
        int safety = safetyScore(house);
        int schools = schoolsScore(house);

        List<DimensionScore> dimensions = List.of(
                new DimensionScore("Price", price),
                new DimensionScore("Space", space),
                new DimensionScore("Safety", safety),
                new DimensionScore("Schools", schools)
        );

        int total = weightedTotal(mode, price, space, safety, schools);

        // Top-Level Rule: Total Score Cap Penalty： If Space Fit or Crime Safety is 0, cap total at 40.
        if (space == 0 || safety == 0) {
            total = Math.min(total, 40);
        }

        String summary = generateSummary(total, dimensions, mode);

        return new ScoreResponse(house, total, dimensions, summary);
    }

    /**
     * ratio = (avm - maxPrice) / maxPrice
     *
     * Tiered:
     * <= 80%    -> 100
     * 80%-90%   -> 95
     * 100%-110%   -> 80
     * 110%-120%   -> 70
     * 120%-130%   -> 50
     * > 130%    -> 30
     */
    private int priceFitScore(House house, BuyerProfile profile) {
        if (house.getAvmValue() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "house.avmValue is required");
        }
        if (profile.getMaxPrice() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "buyerProfile.maxPrice is required");
        }

        BigDecimal avmI = BigDecimal.valueOf(house.getAvmValue());
        BigDecimal maxPrice = profile.getMaxPrice();
        if (maxPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "buyerProfile.maxPrice must be > 0");
        }

        BigDecimal ratio = avmI.divide(maxPrice, 4, java.math.RoundingMode.HALF_UP);
    
        if (ratio.compareTo(new BigDecimal("0.80")) <= 0) return 100; // Well Under Budget
        if (ratio.compareTo(new BigDecimal("0.90")) <= 0) return 95;  // Under Budget
        if (ratio.compareTo(new BigDecimal("1.00")) <= 0) return 90;  // At Budget

        // --- OVER BUDGET (Calculated by how much it exceeds 1.00) ---
        if (ratio.compareTo(new BigDecimal("1.05")) <= 0) return 80;  // Slightly Over (5%)
        if (ratio.compareTo(new BigDecimal("1.10")) <= 0) return 70;  // Over Budget (10%)
        if (ratio.compareTo(new BigDecimal("1.20")) <= 0) return 50;  // Significantly Over (20%)

        return 30; 
    }

    private int spaceScore(BuyerProfile profile, House house) {
        // bedroom penalty: -50 each missing bedroom
        // bathroom penalty: -15 each missing bathroom

        if (profile.getMinBedrooms() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "buyerProfile.minBedrooms is required");
        }
        if (profile.getMinBathrooms() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "buyerProfile.minBathrooms is required");
        }

        int bedroomPenalty = 0;
        BigDecimal bathroomPenalty = BigDecimal.ZERO;
     
        if (house.getBeds() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "house.beds is required");
        }
        int missingBedrooms = Math.max(0, profile.getMinBedrooms() - house.getBeds());
        bedroomPenalty = 50 * missingBedrooms;
        

        // bathrooms (supports decimal like 1.5)
        if (house.getBathsTotal() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "house.bathsTotal is required");
        }
        BigDecimal diffBath = profile.getMinBathrooms().subtract(house.getBathsTotal());
        if (diffBath.signum() > 0) {
            bathroomPenalty = diffBath.multiply(new BigDecimal("15"));
        }

        BigDecimal score = new BigDecimal("100")
                .subtract(BigDecimal.valueOf(bedroomPenalty))
                .subtract(bathroomPenalty);

        int finalScore = score.setScale(0, java.math.RoundingMode.HALF_UP).intValue();
        return clamp(finalScore, 0, 100);
    }


        // Crime Safety Score (S_crime)
        // Crime index definition: national average = 100.
        // - 200 means 2x national average risk (deal-breaker -> 0)
        //
        // More aggressive mapping (user spec):
        // - if C <= 80  -> 100
        // - if 80 < C < 200 -> linear down to 0
        //      S = clamp( (200 - C) / (200 - 80) * 100, 0, 100 )
        // - if C >= 200 -> 0
    private int safetyScore(House house) {
        if (house.getCrimeIndex() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "house.crimeIndex is required");
        }
        int c = house.getCrimeIndex();

        if (c >= 200) return 0;
        if (c <= 80) return 100;

        double score = (200.0 - c) * 100.0 / 120.0;
        return clamp((int) Math.round(score), 0, 100);
    }

    private int schoolsScore(House house) {
        if (house.getSchoolsJson() == null || house.getSchoolsJson().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "house.schoolsJson is required");
        }

        try {
            JsonNode arr = objectMapper.readTree(house.getSchoolsJson());
            if (!arr.isArray()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "house.schoolsJson must be a JSON array");
            }
            if (arr.size() == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "house.schoolsJson is empty; cannot score schools");
            }

            double sum = 0.0;
            int n = arr.size();
            for (int i = 0; i < n; i++) {
                JsonNode s = arr.get(i);
                sum += schoolRatingToScore(s);
            }
            int avg = (int) Math.round(sum / n);
            return clamp(avg, 0, 100);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "house.schoolsJson is invalid JSON", e);
        }
    }

    private int schoolRatingToScore(JsonNode school) {
        if (school == null) return 50;
        String rating = school.path("schoolRating").asText(null);
        if (rating == null) return 50;
        rating = rating.trim().toUpperCase(Locale.ROOT);
        if (rating.isBlank()) return 50;

        char c = rating.charAt(0);
        return switch (c) {
            case 'A' -> 90;
            case 'B' -> 75;
            case 'C' -> 60;
            case 'D', 'F' -> 50;
            default -> 50;
        };
    }

    private String generateSummary(int total, List<DimensionScore> dims, PriorityMode mode) {

        List<DimensionScore> sorted = new ArrayList<>(dims);
        sorted.sort(Comparator.comparingInt(DimensionScore::getScore));

        DimensionScore weakest = sorted.get(0);
        DimensionScore secondStrongest = sorted.get(2);
        DimensionScore strongest = sorted.get(3);

        String matchStatus = (total >= 60) ? "a match" : "not a match";

        return String.format(
                "This house received a SmartScore of %d. Its strongest areas are %s %d and %s %d. " +
                        "However, the %s score is lower at %d, which may be a concern. " +
                        "Given your priority '%s', this property is %s for you.",
                total,
                strongest.getName(), strongest.getScore(),
                secondStrongest.getName(), secondStrongest.getScore(),
                weakest.getName(), weakest.getScore(),
                mode,
                matchStatus
        );
    }

    private int weightedTotal(
            PriorityMode mode,
            int price,
            int space,
            int safety,
            int schools
    ) {

        double wAff = mode.getPriceWeight();
        double wSpace = mode.getSpaceWeight();
        double wSafety = mode.getSafetyWeight();
        double wSchools = mode.getSchoolWeight();

        double sum = price * wAff + space * wSpace + safety * wSafety + schools * wSchools;
        int total = (int) Math.round(sum);
        return clamp(total, 0, 100);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

