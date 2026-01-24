package smartbuy.buyerProfile;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BuyerProfileDTO {

    @NotNull(message = "Session ID cannot be missing")
    private String sessionId;

    @NotNull(message = "Max Price is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Price can not be negative")
    private Double maxPrice;

    @NotNull(message = "Bedrooms count is required")
    @Min(value = 0)
    private Integer minBedrooms;

    @NotNull(message = "Bathrooms count is required")
    @Min(value = 0)
    private Integer minBathrooms;

    @NotNull(message = "Priority Mode must be selected")
    private BuyerProfile.PriorityMode priorityMode;
}
