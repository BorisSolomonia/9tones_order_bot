package ge.orderapp.dto.request;

public record UpdateCustomerRequest(
        String name,
        Boolean active
) {}
