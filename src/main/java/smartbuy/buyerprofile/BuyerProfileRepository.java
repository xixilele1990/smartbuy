package smartbuy.buyerprofile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface BuyerProfileRepository extends JpaRepository<BuyerProfile, Long> {
    Optional<BuyerProfile> findBySessionId(String sessionId);

    // delete according the session id
    void deleteBySessionId(String sessionId);
}
