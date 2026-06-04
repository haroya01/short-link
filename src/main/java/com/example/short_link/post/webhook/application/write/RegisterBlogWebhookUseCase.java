package com.example.short_link.post.webhook.application.write;

import com.example.short_link.common.event.BlogInteractionType;
import com.example.short_link.common.net.PublicHttpUrlGuard;
import com.example.short_link.post.exception.PostErrorCode;
import com.example.short_link.post.exception.PostException;
import com.example.short_link.post.webhook.application.dto.IssuedBlogWebhook;
import com.example.short_link.post.webhook.domain.BlogWebhookEntity;
import com.example.short_link.post.webhook.domain.BlogWebhookFormat;
import com.example.short_link.post.webhook.domain.repository.BlogWebhookRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registers a blog notification webhook for the author. The secret is issued once, here. */
@Service
@RequiredArgsConstructor
public class RegisterBlogWebhookUseCase {

  public static final int MAX_PER_USER = 5;
  private static final String SECRET_ALPHABET =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
  private static final int SECRET_LENGTH = 48;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final BlogWebhookRepository repository;
  private final MeterRegistry meterRegistry;

  @Transactional
  public IssuedBlogWebhook execute(
      Long userId, String url, String name, Set<BlogInteractionType> events) {
    if (!PublicHttpUrlGuard.isPublic(url)) {
      throw new PostException(PostErrorCode.INVALID_WEBHOOK_URL);
    }
    if (repository.countByUserId(userId) >= MAX_PER_USER) {
      throw new PostException(PostErrorCode.TOO_MANY_WEBHOOKS, MAX_PER_USER);
    }
    String secret = generateSecret();
    BlogWebhookFormat format = BlogWebhookFormat.detect(url);
    Set<BlogInteractionType> subscribed =
        events == null || events.isEmpty() ? EnumSet.allOf(BlogInteractionType.class) : events;
    BlogWebhookEntity saved =
        repository.save(
            new BlogWebhookEntity(userId, url, secret, sanitizeName(name), format, subscribed));
    meterRegistry
        .counter("blog.webhook.registered", "format", format.name().toLowerCase())
        .increment();
    return new IssuedBlogWebhook(
        saved.getId(), url, secret, saved.getName(), format, saved.events(), saved.getCreatedAt());
  }

  private static String generateSecret() {
    StringBuilder sb = new StringBuilder(SECRET_LENGTH);
    for (int i = 0; i < SECRET_LENGTH; i++) {
      sb.append(SECRET_ALPHABET.charAt(RANDOM.nextInt(SECRET_ALPHABET.length())));
    }
    return sb.toString();
  }

  private static String sanitizeName(String name) {
    if (name == null) {
      return null;
    }
    String trimmed = name.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    return trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
  }
}
