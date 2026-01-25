package smartbuy.buyerProfileTest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import smartbuy.buyerProfile.BuyerProfile;
import smartbuy.buyerProfile.BuyerProfileController;
import smartbuy.buyerProfile.BuyerProfileDTO;
import smartbuy.buyerProfile.BuyerProfileService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BuyerProfileController.class)
public class BuyerProfileControllerTest {

    @Autowired
    private MockMvc mockMvc; // Main tool for performing HTTP requests

    @MockBean
    private BuyerProfileService service; // Mock the service layer

    @Autowired
    private ObjectMapper objectMapper; // Converts Objects to JSON

    private BuyerProfile testProfile;
    private BuyerProfileDTO testDto;

    @BeforeEach
    void setUp() {
        testProfile = new BuyerProfile();
        testProfile.setSessionId("session-xyz");
        testProfile.setMaxPrice(600000.0);
        testProfile.setPriorityMode(BuyerProfile.PriorityMode.BALANCED);

        testDto = new BuyerProfileDTO();
        testDto.setSessionId("session-xyz");
        testDto.setMaxPrice(600000.0);
        testDto.setMinBedrooms(3);
        testDto.setMinBathrooms(2);
        testDto.setPriorityMode(BuyerProfile.PriorityMode.BALANCED);
    }

    @Test
    void saveProfile_ShouldReturnOk_WhenValidInput() throws Exception {
        // Given: The service successfully saves and returns the profile
        when(service.saveOrUpdateProfile(any(BuyerProfileDTO.class))).thenReturn(testProfile);

        // When & Then
        mockMvc.perform(post("/buyerProfile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-xyz"))
                .andExpect(jsonPath("$.maxPrice").value(600000.0));
    }

    @Test
    void getProfile_ShouldReturnProfile_WhenIdExists() throws Exception {
        // Given
        when(service.getProfile("session-xyz")).thenReturn(testProfile);

        // When & Then
        mockMvc.perform(get("/buyerProfile/session-xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-xyz"));
    }

    @Test
    void deleteProfile_ShouldReturnNoContent() throws Exception {
        // Given: Service delete method completes without errors
        doNothing().when(service).deleteProfile("session-xyz");

        // When & Then
        mockMvc.perform(delete("/buyerProfile/session-xyz"))
                .andExpect(status().isNoContent()); // Matches your return 204
    }

    @Test
    void saveProfile_ShouldReturnBadRequest_WhenPriceIsNegative() throws Exception {
        // Given: Invalid input (negative price)
        testDto.setMaxPrice(-100.0);

        // When & Then: Spring Validation (@DecimalMin) should catch this
        mockMvc.perform(post("/buyerProfile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testDto)))
                .andExpect(status().isBadRequest());
    }
}
