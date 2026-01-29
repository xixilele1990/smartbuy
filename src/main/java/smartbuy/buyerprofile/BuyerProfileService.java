package smartbuy.buyerprofile;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BuyerProfileService {

    @Autowired
    private BuyerProfileRepository repository;

    @Transactional
    public BuyerProfile saveOrUpdateProfile(BuyerProfileDTO request) {
        BuyerProfile profile = repository.findBySessionId(request.getSessionId())
                .orElse(new BuyerProfile());

        if (profile.getSessionId() == null) {
            profile.setSessionId(request.getSessionId());
        }

        profile.setMaxPrice(request.getMaxPrice());
        profile.setMinBedrooms(request.getMinBedrooms());
        profile.setMinBathrooms(request.getMinBathrooms());
        profile.setPriorityMode(request.getPriorityMode());

        return repository.save(profile);
    }

    @Transactional(readOnly = true)
    public BuyerProfile getProfile(String sessionId) {
        return repository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found for session: " + sessionId));
    }
    @Transactional
    public void deleteProfile(String sessionId) {
        BuyerProfile profile = repository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cannot delete, profile not found with ID: " + sessionId));

        repository.delete(profile);
    }
}
