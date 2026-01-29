package smartbuy.house;

import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import smartbuy.attom.AttomClient;

@RestController
@CrossOrigin(origins = "${app.cors.allowed-origins}")
@RequestMapping("/api/houses")
public class HouseController {

    private static final Logger log = LoggerFactory.getLogger(HouseController.class);
    private static final int MAX_IMPORT_SIZE = 5;
    private static final int MAX_IMPORT_CONCURRENCY = 5;

    private final AttomClient attomClient;

    public HouseController(AttomClient attomClient) {
        this.attomClient = attomClient;
    }

    /**
     * Creates a House by fetching ATTOM basicprofile and persisting attomId + geoIdV4.
     *
     * we can make sure frontend check address1 and address2 are not null
     */
    @PostMapping("/from-attom-hardcoded")
    public HouseResponse createFromAttomHardcoded(
            @RequestParam(required = false) String address1,
            @RequestParam(required = false) String address2
    ) {
        String a1 = (address1 == null || address1.isBlank()) ? "2464 Forbes Ave" : address1;
        String a2 = (address2 == null || address2.isBlank()) ? "Santa Clara, CA 95050" : address2;

        return fetchHouseFromAttom(a1, a2);
    }

 // hardcode will be used for testing and removed later 
    @PostMapping("/import-hardcoded-2")
    public ImportHousesResponse importHardcoded2() {
        ImportHousesRequest req = new ImportHousesRequest();
        req.addresses = new ArrayList<>();

        AddressInput a = new AddressInput();
        a.address1 = "2464 Forbes Ave";
        a.address2 = "Santa Clara, CA 95050";

        AddressInput b = new AddressInput();
        b.address1 = "4529 Winona Court";
        b.address2 = "Denver, CO";

        req.addresses.add(a);
        req.addresses.add(b);

        return importHouses(req);
    }

    /**

     * Request example: hard code wil be removed before merging
     * {
     *   "addresses": [
     *     {"address1":"2464 Forbes Ave","address2":"Santa Clara, CA 95050"},
     *     {"address1":"4529 Winona Court","address2":"Denver, CO"}
     *   ]
     * }
     */
    @PostMapping("/import")
    public ImportHousesResponse importHouses(@RequestBody ImportHousesRequest request) {
        if (request == null || request.addresses == null || request.addresses.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "addresses is required");
        }
        if (request.addresses.size() > MAX_IMPORT_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "addresses max size is " + MAX_IMPORT_SIZE);
        }

        ExecutorService executor = Executors.newFixedThreadPool(MAX_IMPORT_CONCURRENCY);
        try {
            List<CompletableFuture<ImportHouseResult>> futures = new ArrayList<>();
            for (int i = 0; i < request.addresses.size(); i++) {
                int idx = i;
                AddressInput input = request.addresses.get(i);
                futures.add(CompletableFuture.supplyAsync(() -> importOne(idx, input), executor));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            List<ImportHouseResult> results = futures.stream().map(CompletableFuture::join).toList();
            return new ImportHousesResponse(results);
        } finally {
            executor.shutdown();
        }
    }

    private ImportHouseResult importOne(int index, AddressInput input) {
        if (input == null || input.address1 == null || input.address1.isBlank() || input.address2 == null || input.address2.isBlank()) {
            return ImportHouseResult.failure(index, nullSafe(input == null ? null : input.address1), nullSafe(input == null ? null : input.address2), "address1/address2 are required");
        }

        String a1 = input.address1.trim();
        String a2 = input.address2.trim();

        try {
            HouseResponse resp = fetchHouseFromAttom(a1, a2);
            return ImportHouseResult.success(index, a1, a2, resp.house(), resp.warnings());
        } catch (ResponseStatusException e) {
            return ImportHouseResult.failure(index, a1, a2, e.getReason());
        } catch (Exception e) {
            return ImportHouseResult.failure(index, a1, a2, e.getMessage());
        }
    }

    private HouseResponse fetchHouseFromAttom(String address1, String address2) {
        try {
            log.info("Fetching house from ATTOM (no-db): address1='{}', address2='{}'", address1, address2);
            List<String> warnings = new ArrayList<>();

            AttomClient.AvmDetailData data = attomClient.avmDetailData(address1, address2);
            AttomClient.SchoolsData schools = attomClient.schoolsData(data.attomId());

            if (data.beds() == null) warnings.add("Missing beds");
            if (data.bathsTotal() == null) warnings.add("Missing bathsTotal");
            if (data.roomsTotal() == null) warnings.add("Missing roomsTotal");
            if (data.avmValue() == null) warnings.add("Missing avmValue");

            Integer crimeIndex = null;
            if (data.crimeId() == null || data.crimeId().isBlank()) {
                //  but set crimeIndex null and return a warning.
                warnings.add("Missing crimeId (geoIdV4.N2); crimeIndex unavailable");
            } else {
                crimeIndex = attomClient.crimeIndex(data.crimeId());
                if (crimeIndex == null) {
                    warnings.add("crimeIndex unavailable from ATTOM");
                }
            }
            House house = new House(
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
            return new HouseResponse(house, warnings);
        } catch (IllegalStateException e) {
            // Return a clean 502 instead of a giant stacktrace to the caller.
            log.warn("Failed to create house from ATTOM: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    public static class ImportHousesRequest {
        public List<AddressInput> addresses;
    }

    public static class AddressInput {
        public String address1;
        public String address2;
    }

    public record ImportHousesResponse(List<ImportHouseResult> results) {}

    public record ImportHouseResult(
            int index,
            String address1,
            String address2,
            boolean success,
            House house,
            List<String> warnings,
            String error
    ) {
        static ImportHouseResult success(int index, String address1, String address2, House house, List<String> warnings) {
            return new ImportHouseResult(index, address1, address2, true, house, warnings, null);
        }

        static ImportHouseResult failure(int index, String address1, String address2, String error) {
            return new ImportHouseResult(index, address1, address2, false, null, null, error);
        }
    }

    public record HouseResponse(House house, List<String> warnings) {}
}

