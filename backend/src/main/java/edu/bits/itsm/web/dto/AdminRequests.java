package edu.bits.itsm.web.dto;

import edu.bits.itsm.domain.incident.Priority;
import edu.bits.itsm.domain.service.BusinessCriticality;
import edu.bits.itsm.domain.user.Role;
import jakarta.validation.constraints.*;

import java.util.Set;

public final class AdminRequests {
    private AdminRequests() { }

    public record CreateUser(@NotBlank @Size(min = 3, max = 60) String username,
                             @NotBlank @Size(min = 8, max = 72) String password,
                             @NotBlank String fullName, @NotBlank @Email String email, String phone,
                             @NotNull Role role, String department,
                             Set<Long> skillCategoryIds, Integer experienceYears, Integer maxWorkload, Boolean teamLead) { }

    public record UpdateTechnician(Set<Long> skillCategoryIds, @Min(0) Integer experienceYears,
                                   @Min(1) @Max(50) Integer maxWorkload, Boolean available, Boolean teamLead) { }

    public record ServiceReq(@NotBlank @Size(max = 100) String name, @Size(max = 255) String description,
                             @NotNull BusinessCriticality criticality, String owner, @Min(0) Integer userBase, Boolean active) { }

    public record CategoryReq(@NotBlank @Size(max = 60) String name, @Size(max = 255) String description,
                              @Size(max = 500) String keywords, Boolean active) { }

    public record SlaReq(@NotNull Priority priority, @Min(1) int responseMinutes, @Min(1) int resolutionMinutes,
                         @Min(1) @Max(99) Integer warningThresholdPct) { }

    public record StrategyReq(String assignmentStrategy, String priorityStrategy) { }
}
