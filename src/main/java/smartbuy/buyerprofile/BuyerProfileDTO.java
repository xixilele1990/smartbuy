package smartbuy.buyerprofile;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BuyerProfileDTO {

    @NotBlank(message = "Session ID cannot be missing")
    private String sessionId;

    @NotNull(message = "Max Price is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "Price can not be negative")
    private BigDecimal maxPrice;

    @NotNull(message = "Bedrooms count is required")
    @Min(value = 0)
    private Integer minBedrooms;

    @NotNull(message = "Bathrooms count is required")
    @Min(value = 0)
    private BigDecimal minBathrooms;

    @NotNull(message = "Priority Mode must be selected")
    private PriorityMode priorityMode;
}
