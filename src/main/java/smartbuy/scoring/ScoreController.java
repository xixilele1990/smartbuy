package smartbuy.scoring;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import smartbuy.attom.AttomClient;
import smartbuy.buyerprofile.BuyerProfile;
import smartbuy.buyerprofile.BuyerProfileService;
import smartbuy.house.House;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 给前端的 scoring API（不落库）。
 */
@RestController
@RequestMapping("/api/score")
public class ScoreController {

    private static final int MAX_BATCH_SIZE = 5;
    private static final int MAX_BATCH_CONCURRENCY = 5;

    private final AttomClient attomClient;
    private final BuyerProfileService buyerProfileService;
    private final ScoringService scoringService;

    public ScoreController(AttomClient attomClient, BuyerProfileService buyerProfileService, ScoringService scoringService) {
        this.attomClient = attomClient;
        this.buyerProfileService = buyerProfileService;
        this.scoringService = scoringService;
    }

    /**
     * 直接对一个 House DTO 打分（House 通常来自 /api/houses/from-attom-hardcoded 或前端缓存）。
     * buyerProfile 当前可省略（会用 hardcoded default）。
     */
    @PostMapping("/house")
    public ScoreResponse scoreHouse(@RequestBody ScoreHouseRequest req) {
        if (req == null || req.house == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "house is required");
        }
        BuyerProfile profile = (req.buyerProfile != null) ? req.buyerProfile : buyerProfileService.getDefaultProfile();
        return scoringService.score(profile, req.house);
    }

    /**
     * 批量评分（前端不需要传 avmAvg）：
     * - 输入：地址列表（最多 5）
     * - 后端：并发调用 ATTOM 拿到每套房的 avmValue
     * - 后端：计算 avmAvg（仅统计 avmValue != null 的房源）
     * - 输出：每套房的 totalScore（以及 house 本身）
     */
    @PostMapping("/batch-from-attom")
    public ScoreBatchResponse batchScoreFromAttom(@RequestBody BatchScoreRequest req) {
        if (req == null || req.addresses == null || req.addresses.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "addresses is required");
        }
        if (req.addresses.size() > MAX_BATCH_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "addresses max size is " + MAX_BATCH_SIZE);
        }

        ExecutorService executor = Executors.newFixedThreadPool(MAX_BATCH_CONCURRENCY);
        try {
            List<CompletableFuture<House>> futures = new ArrayList<>();
            for (AddressInput a : req.addresses) {
                futures.add(CompletableFuture.supplyAsync(() -> fetchHouseFromAttomSafe(a), executor));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            List<House> houses = futures.stream().map(CompletableFuture::join).toList();

            BuyerProfile profile = buyerProfileService.getDefaultProfile();

            List<ScoreResponse> results = houses.stream()
                    .map(h -> scoringService.score(profile, h))
                    .toList();

            return new ScoreBatchResponse(results);
        } finally {
            executor.shutdown();
        }
    }

    /**
     * 一条请求：按地址拉 ATTOM -> 组装 House -> 用 hardcoded buyerProfile 打分 -> 返回前端。
     * 这就是你要的“不落库，直接返回 scoring 结果”入口。
     */
    @PostMapping("/from-attom-hardcoded")
    public ScoreResponse scoreFromAttomHardcoded(
            @RequestParam(required = false) String address1,
            @RequestParam(required = false) String address2
    ) {
        String a1 = (address1 == null || address1.isBlank()) ? "2464 Forbes Ave" : address1;
        String a2 = (address2 == null || address2.isBlank()) ? "Santa Clara, CA 95050" : address2;

        House house = fetchHouseFromAttom(a1, a2);
        BuyerProfile profile = buyerProfileService.getDefaultProfile();
        return scoringService.score(profile, house);
    }

    private House fetchHouseFromAttom(String address1, String address2) {
        try {
            List<String> warnings = new ArrayList<>();
            AttomClient.AvmDetailData data = attomClient.avmDetailData(address1, address2);
            AttomClient.SchoolsData schools = attomClient.schoolsData(data.attomId());

            Integer crimeIndex = null;
            if (data.crimeId() == null || data.crimeId().isBlank()) {
                warnings.add("Missing crimeId (geoIdV4.N2); crimeIndex unavailable");
            } else {
                crimeIndex = attomClient.crimeIndex(data.crimeId());
            }

            // 注：这里不落库，所以直接 new House；warnings 留给 scoring 的 warnings（后续可以合并返回）
            return new House(
                    address1,
                    address2,
                    data.attomId(),
                    data.geoIdV4(),
                    data.crimeId(),
                    data.beds(),
                    data.bathsTotal(),
                    data.roomsTotal(),
                    data.avmValue(),
                    schools.schoolsJson(),
                    crimeIndex
            );
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }
    }

    private House fetchHouseFromAttomSafe(AddressInput input) {
        if (input == null || input.address1 == null || input.address1.isBlank() || input.address2 == null || input.address2.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "address1/address2 are required");
        }
        return fetchHouseFromAttom(input.address1.trim(), input.address2.trim());
    }

    public static class ScoreHouseRequest {
        public BuyerProfile buyerProfile;
        public House house;
    }

    public static class BatchScoreRequest {
        public List<AddressInput> addresses;
    }

    public static class AddressInput {
        public String address1;
        public String address2;
    }

    public record ScoreBatchResponse(List<ScoreResponse> results) {}
}

