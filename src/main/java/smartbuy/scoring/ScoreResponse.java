package smartbuy.scoring;

import smartbuy.house.House;

import java.util.List;

/**
 * 返回给前端的 scoring 结果（不落库）。
 *
 * 当前版本：只返回总分（可选附带 house + warnings，方便前端直接展示）。
 */
public record ScoreResponse(
        House house,
        Integer totalScore,
        List<String> warnings
) {
}

