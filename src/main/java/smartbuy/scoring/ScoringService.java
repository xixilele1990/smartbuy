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

        int total = weightedTotal(mode, price, space, safety, schools);

        // Top-Level Rule: Total Score Cap Penalty： If Space Fit or Crime Safety is 0, cap total at 40.
        if (space == 0 || safety == 0) {
            total = Math.min(total, 40);
        }

        String summary = generateSummary(total, price, space, safety, schools, mode);

        return new ScoreResponse(house, total, summary);
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


        // Crime Safety Score (S_crime) - inverse tier mapping ？ need to confirm with Li Yan,
        // Very Safe:     C < 100   -> 100
        // Low Risk:      100-125    -> 90
        // Moderate Risk: 125-150   -> 75
        // High Risk:     150-200    -> 60
        // Very Unsafe:   C > 200    -> 0
    private int safetyScore(House house) {
        if (house.getCrimeIndex() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "house.crimeIndex is required");
        }
        int c = house.getCrimeIndex();

        if (c < 100) return 100;
        if (c < 125) return 90;
        if (c < 150) return 75;
        if (c < 200) return 60;
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

    private String generateSummary(int total, int price, int space, int safety, int schools, PriorityMode mode) {
        // 1. define dimensions
        List<Dimension> dims = new ArrayList<>();
        dims.add(new Dimension("Price", price));
        dims.add(new Dimension("Space", space));
        dims.add(new Dimension("Safety", safety));
        dims.add(new Dimension("Schools", schools));

        // 2. sort
        dims.sort(Comparator.comparingInt(d -> d.score));

        // 3. get the strongest and weakest
        Dimension weakest = dims.get(0);
        Dimension secondStrongest = dims.get(2);
        Dimension strongest = dims.get(3);

        // 4. assume total > 60 means match )
        String matchStatus = (total >= 60) ? "a match" : "not a match";

        // 5. format string
        return String.format(
                "This house received a SmartScore of %d. Its strengths are its %s with %d and %s with %d. " +
                        "However, its %s score is low at %d, which you should pay attention to. " +
                        "Since your priority is '%s', this house is %s for you.",
                total,
                strongest.name, strongest.score,
                secondStrongest.name, secondStrongest.score,
                weakest.name, weakest.score,
                mode,
                matchStatus
        );
    }

    private static class Dimension {
        String name;
        int score;
        Dimension(String name, int score) { this.name = name; this.score = score; }
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

