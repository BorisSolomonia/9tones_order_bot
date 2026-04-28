package ge.orderapp.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AddCustomerLocationRequest(
        @NotBlank String board,
        String address
) {}
