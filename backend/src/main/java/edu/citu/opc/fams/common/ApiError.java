package edu.citu.opc.fams.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * The JSON body of every error response. {@code fields} maps a form field to its problem, so the
 * front end can highlight it. {@code details} carries extra data, e.g. the matching assets of a
 * duplicate warning.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(int status, String message, Map<String, String> fields, Object details) {

    public static ApiError of(int status, String message) {
        return new ApiError(status, message, Map.of(), null);
    }
}
