package com.example.short_link.profile.application.email;

import com.example.short_link.profile.domain.email.EmailLeadEntity;
import java.util.List;

public interface EmailLeadPageReader {
  List<EmailLeadEntity> findByUser(Long userId, int page, int size);

  List<EmailLeadEntity> findActiveByUser(Long userId, int page, int size);
}
