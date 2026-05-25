package com.example.short_link.user.domain;

import com.example.short_link.user.domain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap {

  private final UserRepository userRepository;

  @Value("${short-link.bootstrap-admin-email:}")
  private String bootstrapAdminEmail;

  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void promote() {
    if (bootstrapAdminEmail == null || bootstrapAdminEmail.isBlank()) {
      return;
    }
    userRepository
        .findByEmail(bootstrapAdminEmail.trim())
        .ifPresent(
            user -> {
              if (!user.isAdmin()) {
                user.promoteToAdmin();
                userRepository.save(user);
                log.info("promoted {} to ADMIN role", user.getEmail());
              }
            });
  }
}
