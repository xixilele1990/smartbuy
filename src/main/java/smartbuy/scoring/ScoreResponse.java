package smartbuy.scoring;

import smartbuy.house.House;

import java.util.List;


public record ScoreResponse(
        House house,
        Integer totalScore,
        List<String> warnings
) {
}

