package com.example.short_link.admin.infrastructure.persistence;

import com.example.short_link.admin.domain.repository.AdminBrowseRepository;
import com.example.short_link.admin.domain.repository.AdminMetricsRepository.StatPage;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.user.domain.UserEntity;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class AdminBrowseRepositoryAdapter implements AdminBrowseRepository {

  /**
   * Same shape {@link ShortCode} enforces — a search term shorter/longer than this isn't a code.
   */
  private static final Pattern SHORT_CODE = Pattern.compile("^[0-9A-Za-z]{3,16}$");

  private final JpaAdminBrowseRepository jpa;

  @Override
  public StatPage<UserRow> findUsers(String q, String role, int page, int size) {
    UserEntity.Role roleFilter = role == null ? null : UserEntity.Role.valueOf(role);
    Page<UserRow> rows = jpa.findUsers(likePattern(q), roleFilter, PageRequest.of(page, size));
    return new StatPage<>(rows.getContent(), rows.getTotalElements());
  }

  @Override
  public Optional<UserRow> findUser(long userId) {
    return jpa.findUserRowById(userId);
  }

  @Override
  public StatPage<LinkRow> findLinks(String q, Long ownerId, int page, int size) {
    Page<LinkRow> rows =
        jpa.findLinks(likePattern(q), toShortCodeOrNull(q), ownerId, PageRequest.of(page, size));
    return new StatPage<>(rows.getContent(), rows.getTotalElements());
  }

  private static String likePattern(String q) {
    return q == null ? null : "%" + q.toLowerCase(Locale.ROOT) + "%";
  }

  private static ShortCode toShortCodeOrNull(String q) {
    return q != null && SHORT_CODE.matcher(q).matches() ? new ShortCode(q) : null;
  }
}
