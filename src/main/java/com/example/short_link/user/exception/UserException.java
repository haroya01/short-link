package com.example.short_link.user.exception;

import com.example.short_link.common.exception.DomainException;

public abstract sealed class UserException extends RuntimeException implements DomainException
    permits AvatarUnavailableException,
        InvalidAvatarException,
        InvalidRefreshTokenException,
        InvalidTimezoneException,
        InvalidTotpCodeException,
        TwoFactorStateException,
        UserNotFoundException {

  protected UserException(String message) {
    super(message);
  }
}
