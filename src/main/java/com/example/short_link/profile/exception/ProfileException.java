package com.example.short_link.profile.exception;

import com.example.short_link.common.exception.DomainException;

public abstract sealed class ProfileException extends RuntimeException implements DomainException
    permits EmailLeadRateLimitedException,
        InvalidUsernameException,
        OembedNotApplicableException,
        ProfileNotFoundException,
        UsernameTakenException {

  protected ProfileException(String message) {
    super(message);
  }
}
