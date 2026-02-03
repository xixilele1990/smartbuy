package smartbuy.buyerprofile;

import com.fasterxml.jackson.databind.ObjectMapper;

import smartbuy.buyerprofile.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BuyerProfileController.class)
public class BuyerProfileControllerTest {

    @Autowired
    private MockMvc mockMvc; 

    @MockitoBean
    private BuyerProfileService service; 

    @Autowired
    private ObjectMapper objectMapper;

    private BuyerProfile testProfile;
    private BuyerProfileDTO testDto;

    @BeforeEach
    void setUp() {
        testProfile = new BuyerProfile();
        testProfile.setSessionId("session-xyz");
        testProfile.setMaxPrice(BigDecimal.valueOf(600000.0));
        testProfile.setPriorityMode(PriorityMode.BALANCED);

        testDto = new BuyerProfileDTO();
        testDto.setSessionId("session-xyz");
        testDto.setMaxPrice(BigDecimal.valueOf(600000.0));
        testDto.setMinBedrooms(3);
        testDto.setMinBathrooms(BigDecimal.valueOf(2));
        testDto.setPriorityMode(PriorityMode.BALANCED);
    }

    @Test
    void saveProfile_ShouldReturnOk_WhenValidInput() throws Exception {
        
        when(service.saveOrUpdateProfile(any(BuyerProfileDTO.class))).thenReturn(testProfile);

      
        mockMvc.perform(post("/buyerProfile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-xyz"))
                .andExpect(jsonPath("$.maxPrice").value(600000.0));
    }

    @Test
    void getProfile_ShouldReturnProfile_WhenIdExists() throws Exception {
     
        when(service.getProfile("session-xyz")).thenReturn(testProfile);

      
        mockMvc.perform(get("/buyerProfile/session-xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-xyz"));
    }

    @Test
    void deleteProfile_ShouldReturnNoContent() throws Exception {
        
        doNothing().when(service).deleteProfile("session-xyz");

     
        mockMvc.perform(delete("/buyerProfile/session-xyz"))
                .andExpect(status().isNoContent()); 
    }

    @Test
    void saveProfile_ShouldReturnBadRequest_WhenPriceIsNegative() throws Exception {
      
        testDto.setMaxPrice(BigDecimal.valueOf(-100.0));

   
        mockMvc.perform(post("/buyerProfile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(testDto)))
                .andExpect(status().isBadRequest());
    }
}