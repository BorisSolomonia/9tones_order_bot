package ge.orderapp.exception;

public class ExternalServiceException extends RuntimeException {
    private final String service;

    public ExternalServiceException(String service, String message) {
        super(service + ": " + message);
        this.service = service;
    }

    public ExternalServiceException(String service, String message, Throwable cause) {
        super(service + ": " + message, cause);
        this.service = service;
    }

    public String getService() {
        return service;
    }
}
