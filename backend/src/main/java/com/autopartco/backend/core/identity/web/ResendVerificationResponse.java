package com.autopartco.backend.core.identity.web;

public record ResendVerificationResponse(String message, int retryAfterSeconds) {
}
