package smartbuy.scoring;

public class DimensionScore {
    private String name;
    private int score;

    public DimensionScore(String name, int score) {
        this.name = name;
        this.score = score;
    }

    public String getName() { return name; }
    public int getScore() { return score; }
}
