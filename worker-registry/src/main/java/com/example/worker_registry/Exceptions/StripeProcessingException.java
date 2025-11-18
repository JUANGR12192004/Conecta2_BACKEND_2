package com.example.worker_registry.exceptions;

import com.stripe.exception.StripeException;
import org.springframework.http.HttpStatus;

public class StripeProcessingException extends RuntimeException {

    private final HttpStatus status;

    public StripeProcessingException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public StripeProcessingException(String message, int statusCode) {
        this(message, HttpStatus.valueOf(statusCode));
    }

    public StripeProcessingException(String context, StripeException cause) {
        super(context + ": " + cause.getMessage(), cause);
        this.status = HttpStatus.valueOf(cause.getStatusCode());
    }

    public HttpStatus getStatus() {
        return status;
    }
}
