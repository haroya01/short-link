package com.example.short_link.link.access.application;

import com.example.short_link.link.access.application.dto.LinkProtectionResult;
import com.example.short_link.link.access.domain.LinkAccessControlEntity;
import com.example.short_link.link.access.domain.repository.LinkAccessControlRepository;
import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LinkProtectionService {

  private final LinkRepository repository;
  private final LinkAccessControlRepository accessControlRepository;
  private final PasswordEncoder encoder;
  private final LinkCacheEviction linkCacheEviction;

  public LinkProtectionService(
      LinkRepository repository,
      LinkAccessControlRepository accessControlRepository,
      @Qualifier("linkPasswordEncoder") PasswordEncoder encoder,
      LinkCacheEviction linkCacheEviction) {
    this.repository = repository;
    this.accessControlRepository = accessControlRepository;
    this.encoder = encoder;
    this.linkCacheEviction = linkCacheEviction;
  }

  @Transactional
  public LinkProtectionResult update(
      Long userId, ShortCode shortCode, String password, Integer maxViews) {
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
            .orElseGet(() -> new LinkAccessControlEntity(link.linkId()));
    if (password != null) {
      String hash = password.isBlank() ? null : encoder.encode(password);
      link.setPasswordHash(hash);
      access.changePasswordHash(hash);
    }
    link.setMaxViews(maxViews);
    access.changeMaxViews(maxViews);
    accessControlRepository.save(access);
    linkCacheEviction.evictAfterCommit(shortCode);
    return new LinkProtectionResult(
        link.getShortCode(), link.hasPassword(), link.getMaxViews(), link.getViewCount());
  }

  public boolean checkPassword(LinkEntity link, String supplied) {
    if (!link.hasPassword()) return true;
    if (supplied == null) return false;
    return encoder.matches(supplied, link.getPasswordHash());
  }
}
