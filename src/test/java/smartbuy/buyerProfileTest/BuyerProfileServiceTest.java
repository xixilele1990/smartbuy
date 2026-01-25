package smartbuy.buyerProfileTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import smartbuy.buyerProfile.BuyerProfile;
import smartbuy.buyerProfile.BuyerProfileDTO;
import smartbuy.buyerProfile.BuyerProfileRepository;
import smartbuy.buyerProfile.BuyerProfileService;

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
        testDto.setMaxPrice(500000.0);
        testDto.setMinBedrooms(2);
        testDto.setMinBathrooms(1);
        testDto.setPriorityMode(BuyerProfile.PriorityMode.BALANCED);

        // Initialize Entity for mock returns
        existingProfile = new BuyerProfile();
        existingProfile.setSessionId("test-session-123");
        existingProfile.setMaxPrice(400000.0);
    }


    @Test
    void saveOrUpdateProfile_ShouldCreateNew_WhenProfileDoesNotExist() {
        // Given
        when(repository.findBySessionId("test-session-123")).thenReturn(Optional.empty());
        when(repository.save(any(BuyerProfile.class))).thenAnswer(i -> i.getArguments()[0]);

        // When
        BuyerProfile result = service.saveOrUpdateProfile(testDto);

        // Then
        assertNotNull(result);
        assertEquals("test-session-123", result.getSessionId());
        verify(repository, times(1)).save(any(BuyerProfile.class));
    }


    @Test
    void saveOrUpdateProfile_ShouldUpdateExisting_WhenProfileExists() {
        // Given
        when(repository.findBySessionId("test-session-123")).thenReturn(Optional.of(existingProfile));
        when(repository.save(any(BuyerProfile.class))).thenReturn(existingProfile);

        // When
        BuyerProfile result = service.saveOrUpdateProfile(testDto);

        // Then
        assertEquals(500000.0, result.getMaxPrice()); // Verify the price was updated from 400k to 500k
        verify(repository, times(1)).save(existingProfile);
    }

    // --- Get Profile Tests ---
    @Test
    void getProfile_ShouldReturnProfile_WhenIdExists() {
        // Given
        when(repository.findBySessionId("test-session-123")).thenReturn(Optional.of(existingProfile));

        // When
        BuyerProfile result = service.getProfile("test-session-123");

        // Then
        assertNotNull(result);
        assertEquals("test-session-123", result.getSessionId());
    }

    @Test
    void getProfile_ShouldThrowException_WhenNotFound() {
        // Given
        when(repository.findBySessionId("unknown")).thenReturn(Optional.empty());

        // When & Then
        Exception exception = assertThrows(RuntimeException.class, () -> {
            service.getProfile("unknown");
        });
        assertEquals("Profile not found", exception.getMessage());
    }

    // --- Delete Tests ---

    @Test
    void deleteProfile_ShouldCallDelete_WhenProfileExists() {
        // Given
        when(repository.findBySessionId("test-session-123")).thenReturn(Optional.of(existingProfile));

        // When
        service.deleteProfile("test-session-123");

        // Then
        verify(repository, times(1)).delete(existingProfile);
    }

    @Test
    void deleteProfile_ShouldThrowException_WhenProfileDoesNotExist() {
        // Given
        when(repository.findBySessionId("non-existent")).thenReturn(Optional.empty());

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            service.deleteProfile("non-existent");
        });
        // Verify delete was NEVER called
        verify(repository, never()).delete(any());
    }
}