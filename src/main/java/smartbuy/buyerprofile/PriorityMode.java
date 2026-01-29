package smartbuy.buyerprofile;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Defines the scoring strategy selected by the user.
 * Each mode provides a specific set of weights for (Price, Space, Safety, Schools).
 */
@Getter
@RequiredArgsConstructor
public enum PriorityMode {
    BALANCED(0.25, 0.25, 0.25, 0.25),
    BUDGET_DRIVEN(0.50, 0.20, 0.20, 0.10),
    SAFETY_FIRST(0.25, 0.15, 0.50, 0.10),
    EDUCATION_FIRST(0.20, 0.15, 0.15, 0.50);

    private final double priceWeight;
    private final double spaceWeight;
    private final double safetyWeight;
    private final double schoolWeight;

    public double[] getWeights() {
        return new double[]{priceWeight, spaceWeight, safetyWeight, schoolWeight};
    }
}