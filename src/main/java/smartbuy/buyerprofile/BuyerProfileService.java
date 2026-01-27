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
        // 1.try to find session ID in database
        BuyerProfile profile = repository.findBySessionId(request.getSessionId())
                .orElse(new BuyerProfile());

        if (profile.getSessionId() == null) {
            profile.setSessionId(request.getSessionId());
        }

        // 2. update the DTO data into Entity
        profile.setMaxPrice(request.getMaxPrice());
        profile.setMinBedrooms(request.getMinBedrooms());
        profile.setMinBathrooms(request.getMinBathrooms());
        profile.setPriorityMode(request.getPriorityMode());

        // 3. save and return
        return repository.save(profile);
    }

    //  get Profile and handle error
    @Transactional(readOnly = true)
    public BuyerProfile getProfile(String sessionId) {
        return repository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found for session: " + sessionId));
    }

    //  Delete
    @Transactional
    public void deleteProfile(String sessionId) {
    //  if not find a sessionId, then can not delete it. throw error
        BuyerProfile profile = repository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cannot delete, profile not found with ID: " + sessionId));
        // if find a sessionId, then delete the profile
        repository.delete(profile);
    }
}
