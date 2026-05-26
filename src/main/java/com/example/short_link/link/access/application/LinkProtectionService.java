package com.example.short_link.link.access.application;

import com.example.short_link.link.access.application.dto.LinkProtectionResult;
import com.example.short_link.link.access.domain.LinkAccessControlEntity;
import com.example.short_link.link.access.domain.repository.LinkAccessControlRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LinkProtectionService {

  private final LinkRepository repository;
  private final LinkAccessControlRepository accessControlRepository;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

  @Transactional
  @CacheEvict(value = "link", key = "#shortCode")
  public LinkProtectionResult update(
      Long userId, String shortCode, String password, Integer maxViews) {
    LinkEntity link =
        repository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new LinkException(LinkErrorCode.LINK_NOT_FOUND, shortCode));
    if (!link.isOwnedBy(userId)) {
      throw new LinkException(LinkErrorCode.LINK_NOT_OWNED, shortCode);
    }
    LinkAccessControlEntity access =
        accessControlRepository
            .findById(link.getId())
            .orElseGet(() -> new LinkAccessControlEntity(link.getId()));
    if (password != null) {
      String hash = password.isBlank() ? null : encoder.encode(password);
      link.setPasswordHash(hash);
      access.changePasswordHash(hash);
    }
    link.setMaxViews(maxViews);
    access.changeMaxViews(maxViews);
    accessControlRepository.save(access);
    return new LinkProtectionResult(
        link.getShortCode().value(), link.hasPassword(), link.getMaxViews(), link.getViewCount());
  }

  public boolean checkPassword(LinkEntity link, String supplied) {
    if (!link.hasPassword()) return true;
    if (supplied == null) return false;
    return encoder.matches(supplied, link.getPasswordHash());
  }
}
