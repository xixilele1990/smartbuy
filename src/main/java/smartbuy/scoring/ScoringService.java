package smartbuy.scoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import smartbuy.buyerprofile.BuyerProfile;
import smartbuy.buyerprofile.PriorityMode;
import smartbuy.house.House;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 纯逻辑层：输入 BuyerProfile + House，输出 score（不做 IO / 不落库）。
 */
@Service
public class ScoringService {

    // Crime safety is handled by tiered mapping (see safetyScore), so no normalization constant is needed.

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ScoreResponse score(BuyerProfile profile, House house) {
        List<String> warnings = new ArrayList<>();

        PriorityMode mode = (profile == null || profile.priorityMode() == null) ? PriorityMode.BALANCED : profile.priorityMode();
        BuyerProfile safeProfile = profile == null
                ? new BuyerProfile(null, null, null, mode)
                : profile;

        Integer price = priceFitScore(house, safeProfile, warnings);
        Integer space = spaceScore(safeProfile, house, warnings);
        Integer safety = safetyScore(safeProfile, house, warnings);
        Integer schools = schoolsScore(house, warnings);

        Integer total = weightedTotal(mode, price, space, safety, schools, warnings);

        // 约定：只要有 totalScore，就不要返回 warnings（前端要求）
        if (total != null) {
            return new ScoreResponse(house, total, null);
        }
        return new ScoreResponse(house, null, warnings);
    }

    /**
     * Price Fit Score (S_price)
     * Inputs: AVM_i (current house AVM), AVM_avg (average AVM of all options)
     * Δ = |AVM_i - AVM_avg| / AVM_avg
     *
     * Tiered:
     * Δ < 5%    -> 100
     * 5%-10%    -> 90
     * 10%-15%   -> 80
     * 15%-20%   -> 70
     * >= 20%    -> 60
     */
    private Integer priceFitScore(House house, BuyerProfile profile, List<String> warnings) {
        if (house == null || house.getAvmValue() == null) {
            warnings.add("house.avmValue 缺失：无法计算 priceFit");
            return null;
        }

        if (profile == null || profile.maxPrice() == null) {
            warnings.add("buyerProfile.maxPrice 缺失：无法计算 priceFit");
            return null;
        }

        long avmI = house.getAvmValue();
        long baseline = profile.maxPrice();
        if (baseline <= 0) {
            warnings.add("buyerProfile.maxPrice 非法：无法计算 priceFit");
            return null;
        }

        BigDecimal diff = BigDecimal.valueOf(Math.abs(avmI - baseline));
        BigDecimal delta = diff.divide(BigDecimal.valueOf(baseline), 6, java.math.RoundingMode.HALF_UP);

        // thresholds
        if (delta.compareTo(new BigDecimal("0.05")) < 0) return 100;
        if (delta.compareTo(new BigDecimal("0.10")) < 0) return 90;
        if (delta.compareTo(new BigDecimal("0.15")) < 0) return 80;
        if (delta.compareTo(new BigDecimal("0.20")) < 0) return 70;
        return 60;
    }

    private Integer spaceScore(BuyerProfile profile, House house, List<String> warnings) {
        // 3.2 Space Fit (S_space)
        // starts at 100
        // bedroom penalty: -50 each missing bedroom
        // bathroom penalty: -15 each missing bathroom
        // deal breaker: max(0, 100 - penalties)

        if (profile.minBeds() == null && profile.minBathsTotal() == null) {
            warnings.add("buyerProfile.minBeds/minBathsTotal 均缺失：无法计算 space");
            return null;
        }

        int bedroomPenalty = 0;
        BigDecimal bathroomPenalty = BigDecimal.ZERO;

        // bedrooms
        if (profile.minBeds() != null) {
            if (house == null || house.getBeds() == null) {
                warnings.add("house.beds 缺失：无法计算 space");
                return null;
            }
            int missingBedrooms = Math.max(0, profile.minBeds() - house.getBeds());
            bedroomPenalty = 50 * missingBedrooms;
        }

        // bathrooms (supports decimal like 1.5)
        if (profile.minBathsTotal() != null) {
            if (house == null || house.getBathsTotal() == null) {
                warnings.add("house.bathsTotal 缺失：无法计算 space");
                return null;
            }
            BigDecimal diff = profile.minBathsTotal().subtract(house.getBathsTotal());
            if (diff.signum() > 0) {
                // proportional penalty: 15 points per 1.0 missing bath
                bathroomPenalty = diff.multiply(new BigDecimal("15"));
            }
        }

        BigDecimal score = new BigDecimal("100")
                .subtract(BigDecimal.valueOf(bedroomPenalty))
                .subtract(bathroomPenalty);

        int finalScore = score.setScale(0, java.math.RoundingMode.HALF_UP).intValue();
        return clamp(finalScore, 0, 100);
    }

    private Integer safetyScore(BuyerProfile profile, House house, List<String> warnings) {
        if (house == null || house.getCrimeIndex() == null) {
            warnings.add("house.crimeIndex 缺失：无法计算 safety");
            return null;
        }
        int c = house.getCrimeIndex();

        // Crime Safety Score (S_crime) - inverse tier mapping
        // Very Safe:     C < 35   -> 100
        // Low Risk:      35-50    -> 90
        // Moderate Risk: 50-65    -> 75
        // High Risk:     65-80    -> 60
        // Very Unsafe:   C >= 80  -> 0
        if (c < 35) return 100;
        if (c < 50) return 90;
        if (c < 65) return 75;
        if (c < 80) return 60;
        return 0;
    }

    private Integer schoolsScore(House house, List<String> warnings) {
        // 3.4 School Quality (S_school)
        // 按 schoolRating 分段映射并对多所学校取平均（无论模式如何都计算；模式只影响权重）
        if (house == null || house.getSchoolsJson() == null || house.getSchoolsJson().isBlank()) {
            // missing -> 50
            return 50;
        }

        try {
            JsonNode arr = objectMapper.readTree(house.getSchoolsJson());
            if (!arr.isArray() || arr.size() == 0) {
                return 50;
            }

            double sum = 0.0;
            int n = arr.size();
            for (int i = 0; i < n; i++) {
                JsonNode s = arr.get(i);
                sum += schoolRatingToScore(s);
            }
            int avg = (int) Math.round(sum / n);
            return clamp(avg, 0, 100);
        } catch (Exception e) {
            // parse failure -> 50
            return 50;
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

    private Integer weightedTotal(
            PriorityMode mode,
            Integer price,
            Integer space,
            Integer safety,
            Integer schools,
            List<String> warnings
    ) {
        double wAff, wSpace, wSafety, wSchools;
        switch (mode) {
            // Final version weights (user spec):
            // Balanced:         price 0.4,  space 0.25, safety 0.2,  school 0.15
            // Budget Driven:    price 0.5,  space 0.2,  safety 0.2,  school 0.1
            // Safety First:     price 0.25, space 0.15, safety 0.5,  school 0.1
            // Education First:  price 0.2,  space 0.15, safety 0.15, school 0.5
            case BALANCED -> { wAff = 0.40; wSpace = 0.25; wSafety = 0.20; wSchools = 0.15; }
            case BUDGET_DRIVEN -> { wAff = 0.50; wSpace = 0.20; wSafety = 0.20; wSchools = 0.10; }
            case SAFETY_FIRST -> { wAff = 0.25; wSpace = 0.15; wSafety = 0.50; wSchools = 0.10; }
            case EDUCATION_FIRST -> { wAff = 0.20; wSpace = 0.15; wSafety = 0.15; wSchools = 0.50; }
            default -> { wAff = 0.40; wSpace = 0.25; wSafety = 0.20; wSchools = 0.15; }
        }

        double sumW = 0.0;
        double sum = 0.0;

        if (price != null) { sumW += wAff; sum += price * wAff; }
        if (space != null)         { sumW += wSpace; sum += space * wSpace; }
        if (safety != null)        { sumW += wSafety; sum += safety * wSafety; }
        if (schools != null)       { sumW += wSchools; sum += schools * wSchools; }

        if (sumW <= 0.0) {
            warnings.add("所有维度都缺失：无法计算 totalScore");
            return null;
        }

        int total = (int) Math.round(sum / sumW);
        return clamp(total, 0, 100);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

