package ca.fusion3.tenderready.companies;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Declared weekly labour hours available for one service in one location. Hours are not transferable between areas. */
public record CapacityEntry(
        @NotBlank String area,
        @NotBlank String service,
        @NotNull @Min(0) @Max(10000) Integer weeklyHours) {}
