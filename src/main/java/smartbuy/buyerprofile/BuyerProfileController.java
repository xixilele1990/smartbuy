package smartbuy.buyerprofile;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/buyerProfile")
@CrossOrigin(origins = "${app.cors.allowed-origins}") // Enable CORS for configured frontend origins
public class BuyerProfileController {

    @Autowired
    private BuyerProfileService service;

    // create and update profile
    @PostMapping
    public ResponseEntity<BuyerProfile> saveProfile(@Valid @RequestBody BuyerProfileDTO request) {
        BuyerProfile savedProfile = service.saveOrUpdateProfile(request);
        return ResponseEntity.ok(savedProfile);
    }

    // get profile
    @GetMapping("/{sessionId}")
    public ResponseEntity<BuyerProfile> getProfile(@PathVariable String sessionId) {
        BuyerProfile profile = service.getProfile(sessionId);
        return ResponseEntity.ok(profile);
    }

    // delete profile
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteProfile(@PathVariable String sessionId) {
        service.deleteProfile(sessionId);

 
        return ResponseEntity.noContent().build();
    }
}
