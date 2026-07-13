package com.example.short_link.user.exception;

import org.springframework.http.HttpStatus;

public enum UserErrorCode {
  USER_NOT_FOUND(HttpStatus.NOT_FOUND, "user not found"),
  CANNOT_FOLLOW_SELF(HttpStatus.BAD_REQUEST, "cannot follow yourself"),
  CANNOT_BLOCK_SELF(HttpStatus.BAD_REQUEST, "cannot block yourself"),
  INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "invalid or expired refresh token"),
  INVALID_EXCHANGE_CODE(HttpStatus.UNAUTHORIZED, "invalid or expired exchange code"),
  INVALID_TIMEZONE(HttpStatus.BAD_REQUEST, "Invalid timezone: %s"),
  INVALID_AVATAR(HttpStatus.BAD_REQUEST, "%s"),
  AVATAR_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "avatar upload not configured"),
  INVALID_TOTP(HttpStatus.UNAUTHORIZED, "invalid TOTP or recovery code"),
  INVALID_APPLE_IDENTITY(HttpStatus.UNAUTHORIZED, "invalid Apple identity token"),
  APPLE_EMAIL_REQUIRED(
      HttpStatus.BAD_REQUEST, "Apple sign-in did not include an email for a new account"),
  TWO_FACTOR_STATE(HttpStatus.CONFLICT, "%s"),
  INVALID_TOKEN_TYPE(HttpStatus.UNAUTHORIZED, "%s"),
  ACCOUNT_BANNED(HttpStatus.FORBIDDEN, "account is banned"),
  ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "account is suspended"),
  BLOCKED_BY_AUTHOR(HttpStatus.FORBIDDEN, "you can't interact with this author"),
  BLOCKED_TARGET(HttpStatus.FORBIDDEN, "this author has blocked you");

  private final HttpStatus status;
  private final String template;

  UserErrorCode(HttpStatus status, String template) {
    this.status = status;
    this.template = template;
  }

  public HttpStatus status() {
    return status;
  }

  public String format(Object... args) {
    return args == null || args.length == 0 ? template : template.formatted(args);
  }
}
