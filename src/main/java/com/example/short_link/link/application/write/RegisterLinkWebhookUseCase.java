package com.example.short_link.link.application.write;

import com.example.short_link.common.net.PublicHttpUrlGuard;
import com.example.short_link.link.application.IssuedWebhook;
import com.example.short_link.link.application.WebhookFormat;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkWebhookEntity;
import com.example.short_link.link.domain.LinkWebhookRepository;
import com.example.short_link.link.exception.InvalidWebhookUrlException;
import com.example.short_link.link.exception.TooManyWebhooksException;
import io.micrometer.core.instrument.MeterRegistry;
import java.security.SecureRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterLinkWebhookUseCase {

  public static final int MAX_PER_LINK = 5;
  private static final String SECRET_ALPHABET =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
  private static final int SECRET_LENGTH = 48;
  private static final SecureRandom RANDOM = new SecureRandom();

  private final WebhookOwnership ownership;
  private final LinkWebhookRepository repository;
  private final MeterRegistry meterRegistry;

  @Transactional
  public IssuedWebhook execute(RegisterLinkWebhookCommand cmd) {
    LinkEntity link = ownership.ownedLink(cmd.userId(), cmd.shortCode());
    if (!PublicHttpUrlGuard.isPublic(cmd.url())) {
      throw new InvalidWebhookUrlException();
    }
    if (repository.countByLinkId(link.getId()) >= MAX_PER_LINK) {
      throw new TooManyWebhooksException(MAX_PER_LINK);
    }
    String secret = generateSecret();
    WebhookFormat format = WebhookFormat.detect(cmd.url());
    String sanitizedName = sanitizeName(cmd.name());
    LinkWebhookEntity saved =
        repository.save(
            new LinkWebhookEntity(link.getId(), cmd.url(), secret, sanitizedName, format));
    meterRegistry
        .counter("link.webhook.registered", "format", format.name().toLowerCase())
        .increment();
    return new IssuedWebhook(
        saved.getId(), cmd.url(), secret, sanitizedName, saved.getCreatedAt(), format);
  }

  private static String generateSecret() {
    StringBuilder sb = new StringBuilder(SECRET_LENGTH);
    for (int i = 0; i < SECRET_LENGTH; i++) {
      sb.append(SECRET_ALPHABET.charAt(RANDOM.nextInt(SECRET_ALPHABET.length())));
    }
    return sb.toString();
  }

  private static String sanitizeName(String name) {
    if (name == null) return null;
    String trimmed = name.trim();
    if (trimmed.isEmpty()) return null;
    return trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
  }
}
