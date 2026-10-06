package com.radiotech.radiotech_backend.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
public record AiChatRequest(@NotBlank @Size(max = 3000) String message,
        @Size(max = 8) List<@Valid Message> history, boolean includeOperationalContext) {
    public record Message(@NotBlank @Pattern(regexp = "user|assistant") String role, @NotBlank @Size(max = 3000) String content) {}
}
