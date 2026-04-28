package ge.orderapp.dto.response;

public record CustomerLocationDto(
        String customerId,
        String board,
        String address
) {}
