package smartbuy.scoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import smartbuy.buyerprofile.BuyerProfileDTO;
import smartbuy.buyerprofile.BuyerProfileRepository;
import smartbuy.buyerprofile.PriorityMode;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@SuppressWarnings("null")
class BuyerProfileScoringIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BuyerProfileRepository buyerProfileRepository;

    @BeforeEach
    void cleanDb() {
        buyerProfileRepository.deleteAll();
    }

    @Test
    void createProfile_thenScoreHouseBySession_returnsExpectedTotal() throws Exception {
        String sessionId = "it-session-1";

        saveProfile(sessionId, new BigDecimal("2000000"), 3, new BigDecimal("2.0"), PriorityMode.BALANCED);

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("sessionId", sessionId);
        req.put("house", housePayload(
                1_900_000L,
                3,
                new BigDecimal("2.0"),
                33,
                "[{\"schoolRating\":\"A\"},{\"schoolRating\":\"B+\"}]"
        ));

        // price=90, space=100, safety=100, schools=83 -> balanced(0.25 each) => round(93.25)=93
        mockMvc.perform(post("/api/score/house-by-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(93))
                .andExpect(jsonPath("$.dimensions").isArray())
                .andExpect(jsonPath("$.dimensions.length()").value(4));
    }

    @Test
    void updateSameSessionProfile_thenScoreUsesLatestProfile() throws Exception {
        String sessionId = "it-session-2";

        saveProfile(sessionId, new BigDecimal("2000000"), 3, new BigDecimal("2.0"), PriorityMode.BALANCED);

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("sessionId", sessionId);
        req.put("house", housePayload(
                1_900_000L,
                3,
                new BigDecimal("2.0"),
                33,
                "[{\"schoolRating\":\"A\"},{\"schoolRating\":\"B+\"}]"
        ));

        mockMvc.perform(post("/api/score/house-by-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(93));

        
        saveProfile(sessionId, new BigDecimal("1000000"), 3, new BigDecimal("2.0"), PriorityMode.BALANCED);

        // total = round((30+100+100+83)/4)=round(78.25)=78
        mockMvc.perform(post("/api/score/house-by-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScore").value(78));
    }

    @Test
    void deleteProfile_thenScoreHouseBySession_returns404() throws Exception {
        String sessionId = "it-session-3";

        saveProfile(sessionId, new BigDecimal("2000000"), 3, new BigDecimal("2.0"), PriorityMode.BALANCED);

        mockMvc.perform(delete("/buyerProfile/{sessionId}", sessionId))
                .andExpect(status().isNoContent());

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("sessionId", sessionId);
        req.put("house", housePayload(
                1_900_000L,
                3,
                new BigDecimal("2.0"),
                33,
                "[{\"schoolRating\":\"A\"}]"
        ));

        mockMvc.perform(post("/api/score/house-by-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Profile not found for session: " + sessionId));
    }

    private void saveProfile(
            String sessionId,
            BigDecimal maxPrice,
            int minBedrooms,
            BigDecimal minBathrooms,
            PriorityMode mode
    ) throws Exception {
        BuyerProfileDTO dto = new BuyerProfileDTO();
        dto.setSessionId(sessionId);
        dto.setMaxPrice(maxPrice);
        dto.setMinBedrooms(minBedrooms);
        dto.setMinBathrooms(minBathrooms);
        dto.setPriorityMode(mode);

        mockMvc.perform(post("/buyerProfile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId));
    }

    private Map<String, Object> housePayload(
            long avmValue,
            int beds,
            BigDecimal bathsTotal,
            int crimeIndex,
            String schoolsJson
    ) {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("address1", "x");
        h.put("address2", "y");
        h.put("avmValue", avmValue);
        h.put("beds", beds);
        h.put("bathsTotal", bathsTotal);
        h.put("crimeIndex", crimeIndex);
        h.put("schoolsJson", schoolsJson);
        return h;
    }
}

