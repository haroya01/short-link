package com.example.short_link.common.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Contract every feature's sealed exception root carries. Lets a single ExceptionHandler render
 * {@link org.springframework.http.ProblemDetail} responses without knowing each subtype — the type
 * itself reports its HTTP status and machine code.
 *
 * <p>Pair: each feature defines its own sealed abstract root (e.g. {@code LinkException}) that
 * extends {@link RuntimeException} and implements this interface. Each concrete exception in that
 * feature {@code permits} list overrides {@link #status()} and {@link #code()} once at declaration
 * — no handler-side mapping table, no drift between the throw site and the response shape.
 */
public interface DomainException {

  HttpStatus status();

  String code();

  default Map<String, Object> properties() {
    return Map.of();
  }
}
