package edu.bits.itsm.web.dto;

import edu.bits.itsm.ai.feature.ChatReportingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Request payloads of the AI endpoints, validated at the boundary (the services check the rest, e.g. image bytes). */
public final class AiRequests {
    private AiRequests() { }

    /** {@code imageBase64} without a {@code data:} prefix; image size and type are checked by the service. */
    public record ScreenshotTriage(@NotBlank String imageBase64, @NotBlank String mediaType) { }

    public record ChatMessage(@NotNull @Pattern(regexp = "user|assistant") String role,
                              @NotBlank @Size(max = 2000) String content) { }

    public record Chat(@NotEmpty @Size(max = ChatReportingService.MAX_MESSAGES) List<@Valid @NotNull ChatMessage> messages) { }

    public record Search(@NotBlank @Size(max = 300) String query) { }

    public record AiSwitch(@NotNull Boolean enabled) { }
}
