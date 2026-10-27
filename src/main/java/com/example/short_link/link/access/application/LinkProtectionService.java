package com.example.short_link.link.access.application;

import com.example.short_link.link.access.application.dto.LinkProtectionResult;
import com.example.short_link.link.access.domain.LinkAccessControlEntity;
import com.example.short_link.link.access.domain.repository.LinkAccessControlRepository;
import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.application.write.LinkOwnership;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LinkProtectionService {

  private final LinkOwnership ownership;
  private final LinkAccessControlRepository accessControlRepository;
  private final PasswordEncoder encoder;
  private final LinkCacheEviction linkCacheEviction;

  public LinkProtectionService(
      LinkOwnership ownership,
      LinkAccessControlRepository accessControlRepository,
      @Qualifier("linkPasswordEncoder") PasswordEncoder encoder,
      LinkCacheEviction linkCacheEviction) {
    this.ownership = ownership;
    this.accessControlRepository = accessControlRepository;
    this.encoder = encoder;
    this.linkCacheEviction = linkCacheEviction;
  }

  @Transactional
  public LinkProtectionResult update(
      Long userId, ShortCode shortCode, String password, Integer maxViews) {
    LinkEntity link = ownership.requireOwned(userId, shortCode);
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
    linkCacheEviction.evictAfterCommit(link);
    return new LinkProtectionResult(
        link.getShortCode(), link.hasPassword(), link.getMaxViews(), link.getViewCount());
  }

  public boolean checkPassword(LinkEntity link, String supplied) {
    if (!link.hasPassword()) return true;
    if (supplied == null) return false;
    return encoder.matches(supplied, link.getPasswordHash());
  }
}
