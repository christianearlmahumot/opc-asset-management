package edu.citu.opc.fams.common;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * A business error whose message is safe to show to the user. The status decides the HTTP code;
 * {@code field} highlights one form field; {@code details} is extra data for the screen.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final Map<String, String> fields;
    private final transient Object details;

    public ApiException(HttpStatus status, String message) {
        this(status, message, Map.of(), null);
    }

    public ApiException(HttpStatus status, String message, Map<String, String> fields, Object details) {
        super(message);
        this.status = status;
        this.fields = fields == null ? Map.of() : fields;
        this.details = details;
    }

    public HttpStatus getStatus() { return status; }
    public Map<String, String> getFields() { return fields; }
    public Object getDetails() { return details; }

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, what + " not found.");
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    /** A 400 that also highlights one form field with the same message. */
    public static ApiException field(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message, Map.of(field, message), null);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, message);
    }
}
