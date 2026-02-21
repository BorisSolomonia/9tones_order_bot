package ge.orderapp.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AddMyCustomerRequest(
        @NotBlank String customerName,
        @NotBlank String customerId
) {}
