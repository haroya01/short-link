package com.example.short_link.link.application;

import com.example.short_link.link.api.LinkProtectionController.LinkProtectionResponse;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.LinkRepository;
import com.example.short_link.link.exception.LinkNotFoundException;
import com.example.short_link.link.exception.LinkNotOwnedException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LinkProtectionService {

  private final LinkRepository repository;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

  @Transactional
  @CacheEvict(value = "link", key = "#shortCode")
  public LinkProtectionResponse update(
      Long userId, String shortCode, String password, Integer maxViews) {
    LinkEntity link =
        repository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkNotFoundException(shortCode));
    if (!link.isOwnedBy(userId)) {
      throw new LinkNotOwnedException(shortCode);
    }
    if (password != null) {
      link.setPasswordHash(password.isBlank() ? null : encoder.encode(password));
    }
    link.setMaxViews(maxViews);
    return new LinkProtectionResponse(
        link.getShortCode(), link.hasPassword(), link.getMaxViews(), link.getViewCount());
  }

  public boolean checkPassword(LinkEntity link, String supplied) {
    if (!link.hasPassword()) return true;
    if (supplied == null) return false;
    return encoder.matches(supplied, link.getPasswordHash());
  }
}
