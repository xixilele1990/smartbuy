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

        int total = weightedTotal(mode, price, space, safety, schools);
        return new ScoreResponse(house, total, null);
    }

    /**
     * Δ = |AVM_i - max| / max
     *
     * Tiered:
     * Δ < 5%    -> 100
     * 5%-10%    -> 90
     * 10%-15%   -> 80
     * 15%-20%   -> 70
     * >= 20%    -> 60
     */
    private int priceFitScore(House house, BuyerProfile profile) {
        if (house.getAvmValue() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "house.avmValue is required");
        }
        if (profile.getMaxPrice() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "buyerProfile.maxPrice is required");
        }

        BigDecimal avmI = BigDecimal.valueOf(house.getAvmValue());
        BigDecimal baseline = profile.getMaxPrice();
        if (baseline.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "buyerProfile.maxPrice must be > 0");
        }

        BigDecimal diff = avmI.subtract(baseline).abs();
        BigDecimal delta = diff.divide(baseline, 6, java.math.RoundingMode.HALF_UP);

        // thresholds we can change or discuss if needed
        if (delta.compareTo(new BigDecimal("0.05")) < 0) return 100;
        if (delta.compareTo(new BigDecimal("0.10")) < 0) return 90;
        if (delta.compareTo(new BigDecimal("0.15")) < 0) return 80;
        if (delta.compareTo(new BigDecimal("0.20")) < 0) return 70;
        return 60;
    }

    private int spaceScore(BuyerProfile profile, House house) {
        // bedroom penalty: -50 each missing bedroom
        // bathroom penalty: -15 each missing bathroom

        if (profile.getMinBedrooms() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "buyerProfile.minBeds is required");
        }
        if (profile.getMinBathrooms() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "buyerProfile.minBathsTotal is required");
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


        // Crime Safety Score (S_crime) - inverse tier mapping ？ need to confirm with Li Yan,
        // Very Safe:     C < 35   -> 100
        // Low Risk:      35-50    -> 90
        // Moderate Risk: 50-65    -> 75
        // High Risk:     65-80    -> 60
        // Very Unsafe:   C >= 80  -> 0
    private int safetyScore(House house) {
        if (house.getCrimeIndex() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "house.crimeIndex is required");
        }
        int c = house.getCrimeIndex();

        if (c < 35) return 100;
        if (c < 50) return 90;
        if (c < 65) return 75;
        if (c < 80) return 60;
        return 0;
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

    private int weightedTotal(
            PriorityMode mode,
            int price,
            int space,
            int safety,
            int schools
    ) {
        double wAff, wSpace, wSafety, wSchools;
        switch (mode) {
            // Final version weights we can change if needed:
            case BALANCED -> { wAff = 0.40; wSpace = 0.25; wSafety = 0.20; wSchools = 0.15; }
            case BUDGET_DRIVEN -> { wAff = 0.50; wSpace = 0.20; wSafety = 0.20; wSchools = 0.10; }
            case SAFETY_FIRST -> { wAff = 0.25; wSpace = 0.15; wSafety = 0.50; wSchools = 0.10; }
            case EDUCATION_FIRST -> { wAff = 0.20; wSpace = 0.15; wSafety = 0.15; wSchools = 0.50; }
            default -> { wAff = 0.40; wSpace = 0.25; wSafety = 0.20; wSchools = 0.15; }
        }

        double sum = price * wAff + space * wSpace + safety * wSafety + schools * wSchools;
        int total = (int) Math.round(sum);
        return clamp(total, 0, 100);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

