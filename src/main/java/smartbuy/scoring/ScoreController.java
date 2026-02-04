package smartbuy.scoring;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import smartbuy.attom.AttomClient;
import smartbuy.buyerprofile.BuyerProfile;
import smartbuy.buyerprofile.BuyerProfileService;
import smartbuy.house.House;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@RestController
@CrossOrigin(originPatterns = "${app.cors.allowed-origins}")
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

  
    @PostMapping("/house")
    public ScoreResponse scoreHouse(@RequestBody ScoreHouseRequest req) {
        if (req == null || req.house == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "house is required");
        }
        if (req.buyerProfile == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "buyerProfile is required");
        }
        if (req.buyerProfile.getPriorityMode() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "buyerProfile.priorityMode is required");
        }
        return scoringService.score(req.buyerProfile, req.house);
    }

    /**
     * Integration-friendly endpoint:
     * - buyerProfile is persisted and retrieved by sessionId
     * - request only needs (sessionId, house)
     */
    @PostMapping("/house-by-session")
    public ScoreResponse scoreHouseBySession(@RequestBody ScoreHouseBySessionRequest req) {
        if (req == null || req.sessionId == null || req.sessionId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId is required");
        }
        if (req.house == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "house is required");
        }
        BuyerProfile profile = buyerProfileService.getProfile(req.sessionId.trim());
        return scoringService.score(profile, req.house);
    }

    // batch score together 
    @PostMapping("/batch-from-attom")
    public ScoreBatchResponse batchScoreFromAttom(@RequestBody BatchScoreRequest req) {
        if (req == null || req.addresses == null || req.addresses.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "addresses is required");
        }
        if (req.buyerProfile == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "buyerProfile is required");
        }
        if (req.buyerProfile.getPriorityMode() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "buyerProfile.priorityMode is required");
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

            BuyerProfile profile = req.buyerProfile;

            // sort the result by total in descending order
            List<ScoreResponse> results = houses.stream()
                    .map(h -> scoringService.score(profile, h))
                    .sorted(Comparator.comparing(ScoreResponse::totalScore, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();

            return new ScoreBatchResponse(results);
        } finally {
            executor.shutdown();
        }
    }

 
    @PostMapping("/from-attom-hardcoded")
    public ScoreResponse scoreFromAttomHardcoded(
            @RequestParam(required = false) String address1,
            @RequestParam(required = false) String address2
    ) {
        String a1 = (address1 == null || address1.isBlank()) ? "2464 Forbes Ave" : address1;
        String a2 = (address2 == null || address2.isBlank()) ? "Santa Clara, CA 95050" : address2;

        House house = fetchHouseFromAttom(a1, a2);
        BuyerProfile profile = buyerProfileService.getProfile("sessionId");
        return scoringService.score(profile, house);
    }

    private House fetchHouseFromAttom(String address1, String address2) {
        try {
            AttomClient.AvmDetailData data = attomClient.avmDetailData(address1, address2);
            AttomClient.SchoolsData schools = attomClient.schoolsData(data.attomId());

            Integer crimeIndex = null;
            if (data.crimeId() == null || data.crimeId().isBlank()) {
            } else {
                crimeIndex = attomClient.crimeIndex(data.crimeId());
            }

            // no database, using new house object 
            return new House(
                    address1,
                    address2,
                    data.attomId(),
                    data.geoIdV4(),
                    data.crimeId(),
                    data.beds(),
                    data.bathsTotal(),
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

    public static class ScoreHouseBySessionRequest {
        public String sessionId;
        public House house;
    }

    public static class BatchScoreRequest {
        public BuyerProfile buyerProfile;
        public List<AddressInput> addresses;
    }

    public static class AddressInput {
        public String address1;
        public String address2;
    }

    public record ScoreBatchResponse(List<ScoreResponse> results) {}
}

