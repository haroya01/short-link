package com.example.short_link.link.exception;

import org.springframework.http.HttpStatus;

/**
 * Catalog of every link-feature error. Each entry: HTTP status + message template ({@link
 * String#formatted}) + ordered metadata keys. Constructor args zip with metadata keys so {@code
 * throw new LinkException(LINK_QUOTA_EXCEEDED, 200L)} auto-populates {@code properties.put(
 * "limit", 200L)} — no separate {@code .with(...)} call at the throw site.
 */
public enum LinkErrorCode {
  LINK_NOT_FOUND(HttpStatus.NOT_FOUND, "link not found: %s"),
  LINK_EXPIRED(HttpStatus.GONE, "link expired: %s"),
  LINK_NOT_OWNED(HttpStatus.FORBIDDEN, "link not owned by current user: %s"),
  LINK_QUOTA_EXCEEDED(HttpStatus.CONFLICT, "link quota exceeded (limit=%d)", "limit"),
  LINK_VIEW_LIMIT_EXCEEDED(HttpStatus.GONE, "link view limit exceeded: %s"),
  DUPLICATE_SHORT_CODE(HttpStatus.CONFLICT, "short code already exists: %s"),
  RESERVED_SHORT_CODE(HttpStatus.BAD_REQUEST, "short code is reserved: %s"),
  SHORT_CODE_EXHAUSTED(
      HttpStatus.INTERNAL_SERVER_ERROR,
      "Failed to generate unique short code after multiple attempts"),
  MALICIOUS_URL(HttpStatus.BAD_REQUEST, "malicious url rejected (sha256_prefix=%s)"),
  TAG_NOT_FOUND(HttpStatus.NOT_FOUND, "tag not found: %s"),
  DUPLICATE_TAG_NAME(HttpStatus.CONFLICT, "tag already exists: %s"),
  INVALID_WEBHOOK_URL(
      HttpStatus.BAD_REQUEST, "webhook url must be public http(s) and resolve to a non-private IP"),
  TOO_MANY_WEBHOOKS(HttpStatus.CONFLICT, "too many webhooks for this link (max %d)", "limit"),
  WEBHOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "webhook not found"),
  BULK_IMPORT_TOO_LARGE(
      HttpStatus.PAYLOAD_TOO_LARGE, "bulk import too large: %d rows (limit %d)", "rows", "limit"),
  INVALID_CURSOR(HttpStatus.BAD_REQUEST, "Invalid cursor"),
  INVALID_EXPORT_DIMENSION(HttpStatus.BAD_REQUEST, "Invalid export dimension: %s"),
  CUSTOM_DOMAIN_NOT_FOUND(HttpStatus.NOT_FOUND, "custom domain not found"),
  CUSTOM_DOMAIN_NOT_VERIFIED(
      HttpStatus.UNPROCESSABLE_ENTITY, "DNS TXT record for %s did not match expected token");

  private final HttpStatus status;
  private final String template;
  private final String[] metadataKeys;

  LinkErrorCode(HttpStatus status, String template, String... metadataKeys) {
    this.status = status;
    this.template = template;
    this.metadataKeys = metadataKeys;
  }

  public HttpStatus status() {
    return status;
  }

  public String[] metadataKeys() {
    return metadataKeys;
  }

  public String format(Object... args) {
    return args == null || args.length == 0 ? template : template.formatted(args);
  }
}
