package com.example.short_link.profile.application.email;

import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.ProfileBlockRepository;
import com.example.short_link.profile.domain.ProfileBlockType;
import com.example.short_link.profile.domain.email.EmailLeadEntity;
import com.example.short_link.profile.domain.email.EmailLeadRepository;
import com.example.short_link.profile.exception.EmailLeadRateLimitedException;
import com.example.short_link.profile.exception.InvalidUsernameException;
import com.example.short_link.profile.exception.ProfileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailLeadService {

  // RFC 5321 caps the local-part at 64 and total at 254; this regex matches the common form and
  // we trust the cap for length, not for "deliverable" — that's the sender's job.
  private static final Pattern EMAIL =
      Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

  /**
   * Caps per-block submissions inside a short window so a single bot can't fill the owner's
   * dashboard. Combined with the per-IP throttle this should keep things sane without rejecting
   * legit "first 100 signups in 5min during a launch" traffic.
   */
  private static final int BLOCK_WINDOW_MAX = 200;

  private static final Duration BLOCK_WINDOW = Duration.ofMinutes(5);
  private static final int IP_WINDOW_MAX = 5;
  private static final Duration IP_WINDOW = Duration.ofMinutes(1);
  private static final int PAGE_SIZE_MAX = 500;

  private final EmailLeadRepository repository;
  private final ProfileBlockRepository blockRepository;

  /** Optional salt so leaked DB hashes can't be reversed by a generic rainbow table. */
  @Value("${short-link.email-lead.ip-hash-salt:}")
  private String ipHashSalt;

  @Transactional
  public EmailLeadEntity submit(Long ownerUserId, Long blockId, String email, String clientIp) {
    String normalizedEmail = normalizeEmail(email);
    ProfileBlockEntity block =
        blockRepository
            .findById(blockId)
            .filter(b -> b.getType() == ProfileBlockType.EMAIL_FORM)
            .filter(b -> b.isOwnedBy(ownerUserId))
            .orElseThrow(() -> new ProfileNotFoundException("email form block " + blockId));
    String ipHash = hashIp(clientIp);
    Instant blockSince = Instant.now().minus(BLOCK_WINDOW);
    if (repository.countByBlockIdAndSubmittedAtAfter(block.getId(), blockSince)
        >= BLOCK_WINDOW_MAX) {
      throw new EmailLeadRateLimitedException("block window exhausted");
    }
    Instant ipSince = Instant.now().minus(IP_WINDOW);
    if (ipHash != null
        && repository.countByIpHashAndSubmittedAtAfter(ipHash, ipSince) >= IP_WINDOW_MAX) {
      throw new EmailLeadRateLimitedException("ip window exhausted");
    }
    if (repository.existsByBlockIdAndEmail(block.getId(), normalizedEmail)) {
      // Idempotent for the visitor — return a synthetic OK without writing again. The owner
      // doesn't get a duplicate, the visitor doesn't get a "you already signed up" error wall.
      return new EmailLeadEntity(ownerUserId, block.getId(), normalizedEmail, ipHash);
    }
    return repository.save(
        new EmailLeadEntity(ownerUserId, block.getId(), normalizedEmail, ipHash));
  }

  @Transactional
  public EmailLeadEntity submitPublic(Long blockId, String email, String clientIp) {
    ProfileBlockEntity block =
        blockRepository
            .findById(blockId)
            .filter(b -> b.getType() == ProfileBlockType.EMAIL_FORM)
            .orElseThrow(() -> new ProfileNotFoundException("block " + blockId));
    return submit(block.getUserId(), block.getId(), email, clientIp);
  }

  @Transactional(readOnly = true)
  public List<EmailLeadEntity> list(Long userId, int page, int size) {
    int safeSize = Math.min(Math.max(size, 1), PAGE_SIZE_MAX);
    int safePage = Math.max(page, 0);
    return repository.findAllByUserIdOrderBySubmittedAtDesc(
        userId, PageRequest.of(safePage, safeSize));
  }

  @Transactional(readOnly = true)
  public List<EmailLeadEntity> listActive(Long userId, int page, int size) {
    int safeSize = Math.min(Math.max(size, 1), PAGE_SIZE_MAX);
    int safePage = Math.max(page, 0);
    return repository.findAllByUserIdAndOptedOutFalseOrderBySubmittedAtDesc(
        userId, PageRequest.of(safePage, safeSize));
  }

  @Transactional(readOnly = true)
  public long count(Long userId) {
    return repository.countByUserId(userId);
  }

  @Transactional
  public void delete(Long userId, Long leadId) {
    EmailLeadEntity lead =
        repository
            .findById(leadId)
            .filter(l -> l.isOwnedBy(userId))
            .orElseThrow(() -> new ProfileNotFoundException("lead " + leadId));
    repository.delete(lead);
  }

  @Transactional
  public EmailLeadEntity setOptedOut(Long userId, Long leadId, boolean optedOut) {
    EmailLeadEntity lead =
        repository
            .findById(leadId)
            .filter(l -> l.isOwnedBy(userId))
            .orElseThrow(() -> new ProfileNotFoundException("lead " + leadId));
    lead.setOptedOut(optedOut);
    return repository.save(lead);
  }

  private static String normalizeEmail(String raw) {
    String trimmed = raw == null ? "" : raw.trim().toLowerCase();
    if (trimmed.isEmpty()) throw new InvalidUsernameException("email required");
    if (trimmed.length() > 254) throw new InvalidUsernameException("email too long");
    if (!EMAIL.matcher(trimmed).matches()) throw new InvalidUsernameException("email malformed");
    return trimmed;
  }

  private String hashIp(String ip) {
    if (ip == null || ip.isBlank()) return null;
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      String input = (ipHashSalt == null ? "" : ipHashSalt) + ip;
      byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException ex) {
      return null;
    }
  }
}
