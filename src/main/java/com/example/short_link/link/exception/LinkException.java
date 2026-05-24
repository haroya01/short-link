package com.example.short_link.link.exception;

import com.example.short_link.common.exception.DomainException;

/**
 * Sealed root for every link-feature exception. A single ExceptionHandler can render any subtype
 * via {@link DomainException#status()} + {@link DomainException#code()} without a per-class mapping
 * table — and adding a new subtype is a compile-error until it's added to the {@code permits} list,
 * so the throw site and the handler stay in lock-step.
 */
public abstract sealed class LinkException extends RuntimeException implements DomainException
    permits BulkImportTooLargeException,
        CustomDomainNotFoundException,
        CustomDomainNotVerifiedException,
        DuplicateShortCodeException,
        DuplicateTagNameException,
        InvalidCursorException,
        InvalidExportDimensionException,
        InvalidWebhookUrlException,
        LinkExpiredException,
        LinkNotFoundException,
        LinkNotOwnedException,
        LinkQuotaExceededException,
        LinkViewLimitExceededException,
        MaliciousUrlException,
        ReservedShortCodeException,
        ShortCodeGenerationException,
        TagNotFoundException,
        TooManyWebhooksException,
        WebhookNotFoundException {

  protected LinkException(String message) {
    super(message);
  }
}
