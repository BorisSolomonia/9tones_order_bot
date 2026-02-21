package ge.orderapp.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CopyDraftRequest(
        @NotBlank String targetManagerId,
        String name
) {}
