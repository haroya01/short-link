package com.example.short_link.profile.presentation.email;

import com.example.short_link.profile.domain.email.EmailLeadEntity;
import java.time.Instant;

public record MyEmailLeadResponse(
    Long id, Long blockId, String email, Instant submittedAt, boolean optedOut) {
  static MyEmailLeadResponse of(EmailLeadEntity e) {
    return new MyEmailLeadResponse(
        e.getId(), e.getBlockId(), e.getEmail(), e.getSubmittedAt(), e.isOptedOut());
  }
}
