package ge.orderapp.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TriggerSyncRequest(
        @NotBlank String type,
        String date
) {}
