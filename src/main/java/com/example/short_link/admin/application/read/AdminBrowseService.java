package com.example.short_link.admin.application.read;

import com.example.short_link.admin.application.dto.AdminLinkRow;
import com.example.short_link.admin.application.dto.AdminUserRow;
import com.example.short_link.admin.domain.repository.AdminBrowseRepository;
import com.example.short_link.admin.domain.repository.AdminBrowseRepository.LinkRow;
import com.example.short_link.admin.domain.repository.AdminBrowseRepository.UserRow;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository.StatPage;
import com.example.short_link.admin.exception.AdminErrorCode;
import com.example.short_link.admin.exception.AdminException;
import com.example.short_link.user.domain.UserEntity;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operational browse over the full user / link tables for the admin console. Read-only and
 * paginated; input shaping (blank→null, role validation, page/size clamps) happens here so the
 * repository port speaks only in already-normalized filters.
 */
@Service
@RequiredArgsConstructor
public class AdminBrowseService {

  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 100;

  private final AdminBrowseRepository repo;

  @Transactional(readOnly = true)
  public UsersPage users(String q, String role, int page, int size) {
    StatPage<UserRow> rows =
        repo.findUsers(blankToNull(q), normalizeRole(role), clampPage(page), clampSize(size));
    return new UsersPage(
        rows.items().stream().map(AdminBrowseService::toUserRow).toList(), rows.total());
  }

  @Transactional(readOnly = true)
  public AdminUserRow user(long userId) {
    return repo.findUser(userId)
        .map(AdminBrowseService::toUserRow)
        .orElseThrow(() -> new AdminException(AdminErrorCode.USER_NOT_FOUND, userId));
  }

  @Transactional(readOnly = true)
  public LinksPage links(String q, Long ownerId, int page, int size) {
    StatPage<LinkRow> rows =
        repo.findLinks(blankToNull(q), ownerId, clampPage(page), clampSize(size));
    Instant now = Instant.now();
    return new LinksPage(rows.items().stream().map(r -> toLinkRow(r, now)).toList(), rows.total());
  }

  private static AdminUserRow toUserRow(UserRow r) {
    return new AdminUserRow(
        r.getId(),
        r.getEmail(),
        r.getUsername(),
        r.getRole().name(),
        r.getTier().name(),
        r.getDeletedAt() != null,
        r.getCreatedAt(),
        r.getLinkCount());
  }

  private static AdminLinkRow toLinkRow(LinkRow r, Instant now) {
    return new AdminLinkRow(
        r.getShortCode(),
        r.getOriginalUrl(),
        r.getOwnerId(),
        r.getOwnerEmail(),
        r.getClickCount(),
        r.getPasswordProtected() != null && r.getPasswordProtected() > 0,
        r.getMaxViews(),
        r.getViewCount(),
        r.getCreatedAt(),
        r.getExpiresAt(),
        status(r, now));
  }

  private static String status(LinkRow r, Instant now) {
    if (r.getExpiresAt() != null && !now.isBefore(r.getExpiresAt())) {
      return "EXPIRED";
    }
    if (r.getMaxViews() != null && r.getViewCount() >= r.getMaxViews()) {
      return "LIMIT_REACHED";
    }
    return "ACTIVE";
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }

  private static String normalizeRole(String role) {
    if (role == null || role.isBlank()) {
      return null;
    }
    String upper = role.trim().toUpperCase(Locale.ROOT);
    try {
      UserEntity.Role.valueOf(upper);
    } catch (IllegalArgumentException e) {
      throw new AdminException(AdminErrorCode.INVALID_ROLE, role);
    }
    return upper;
  }

  private static int clampPage(int page) {
    return Math.max(0, page);
  }

  private static int clampSize(int size) {
    if (size <= 0) {
      return DEFAULT_SIZE;
    }
    return Math.min(size, MAX_SIZE);
  }

  public record UsersPage(List<AdminUserRow> items, long total) {}

  public record LinksPage(List<AdminLinkRow> items, long total) {}
}
