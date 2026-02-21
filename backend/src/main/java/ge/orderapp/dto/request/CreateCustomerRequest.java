package ge.orderapp.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateCustomerRequest(
        @NotBlank String name,
        String tin
) {}
