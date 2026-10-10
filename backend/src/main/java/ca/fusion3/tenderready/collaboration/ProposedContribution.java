package ca.fusion3.tenderready.collaboration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** What the recipient is asked to contribute. Proposed, not binding. */
public record ProposedContribution(
        @NotBlank @Size(max = 120) String area,
        @NotBlank @Size(max = 200) String service,
        @NotNull @Min(1) @Max(10000) Integer weeklyHours,
        @NotBlank @Size(max = 200) String partnerRole,
        @NotBlank @Size(max = 200) String leadRole,
        @Size(max = 2000) String responsibilities,
        List<String> addressesRequirementIds) {}
