package smartbuy.scoring;

import lombok.Getter;

@Getter
public class DimensionScore {
    private String name;
    private int score;

    public DimensionScore(String name, int score) {
        this.name = name;
        this.score = score;
    }
}
