package ge.orderapp.dto.request;

public record UpdateUserRequest(
        String displayName,
        String role,
        Boolean active
) {}
