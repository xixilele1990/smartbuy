package smartbuy.buyerprofile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import smartbuy.buyerprofile.*;

import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BuyerProfileServiceTest {

    @Mock
    private BuyerProfileRepository repository;

    @InjectMocks
    private BuyerProfileService service;

    private BuyerProfileDTO testDto;
    private BuyerProfile existingProfile;

    @BeforeEach
    void setUp() {
        testDto = new BuyerProfileDTO();
        testDto.setSessionId("test-session-123");
        testDto.setMaxPrice(BigDecimal.valueOf(500000.0));
        testDto.setMinBedrooms(2);
        testDto.setMinBathrooms(BigDecimal.valueOf(1));
        testDto.setPriorityMode(PriorityMode.BALANCED);

        existingProfile = new BuyerProfile();
        existingProfile.setSessionId("test-session-123");
        existingProfile.setMaxPrice(BigDecimal.valueOf(400000.0));
    }


    @Test
    void saveOrUpdateProfile_ShouldCreateNew_WhenProfileDoesNotExist() {
        when(repository.findBySessionId("test-session-123")).thenReturn(Optional.empty());
        when(repository.save(any(BuyerProfile.class))).thenAnswer(i -> i.getArguments()[0]);

       
        BuyerProfile result = service.saveOrUpdateProfile(testDto);

     
        assertNotNull(result);
        assertEquals("test-session-123", result.getSessionId());
        verify(repository, times(1)).save(any(BuyerProfile.class));
    }


    @Test
    void saveOrUpdateProfile_ShouldUpdateExisting_WhenProfileExists() {
   
        when(repository.findBySessionId("test-session-123")).thenReturn(Optional.of(existingProfile));
        when(repository.save(any(BuyerProfile.class))).thenReturn(existingProfile);

    
        BuyerProfile result = service.saveOrUpdateProfile(testDto);

      
        assertEquals(BigDecimal.valueOf(500000.0), result.getMaxPrice());
        verify(repository, times(1)).save(existingProfile);
    }


    @Test
    void getProfile_ShouldReturnProfile_WhenIdExists() {
    
        when(repository.findBySessionId("test-session-123")).thenReturn(Optional.of(existingProfile));

     
        BuyerProfile result = service.getProfile("test-session-123");

     
        assertNotNull(result);
        assertEquals("test-session-123", result.getSessionId());
    }

    @Test
    void getProfile_ShouldThrowException_WhenNotFound() {
     
        when(repository.findBySessionId("unknown")).thenReturn(Optional.empty());

   
        Exception exception = assertThrows(RuntimeException.class, () -> {
            service.getProfile("unknown");
        });
        assertTrue(exception.getMessage().contains("Profile not found"));
    }



    @Test
    void deleteProfile_ShouldCallDelete_WhenProfileExists() {
    
        when(repository.findBySessionId("test-session-123")).thenReturn(Optional.of(existingProfile));

  
        service.deleteProfile("test-session-123");

     
        verify(repository, times(1)).delete(existingProfile);
    }

    @Test
    void deleteProfile_ShouldThrowException_WhenProfileDoesNotExist() {
       
        when(repository.findBySessionId("non-existent")).thenReturn(Optional.empty());

  
        assertThrows(RuntimeException.class, () -> {
            service.deleteProfile("non-existent");
        });

        verify(repository, never()).delete(any());
    }
}