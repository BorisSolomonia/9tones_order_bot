package ge.orderapp.dto.response;

public record MyCustomerDto(
        String managerId,
        String customerName,
        String customerId,
        String addedAt
) {}
