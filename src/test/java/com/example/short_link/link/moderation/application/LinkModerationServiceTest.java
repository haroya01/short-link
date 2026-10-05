package com.example.short_link.link.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.application.LinkCacheEviction;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.moderation.domain.LinkDisableReason;
import com.example.short_link.link.moderation.domain.LinkModerationEntity;
import com.example.short_link.link.moderation.domain.repository.LinkModerationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LinkModerationServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

  @Mock private LinkRepository links;
  @Mock private LinkModerationRepository moderations;
  @Mock private LinkCacheEviction linkCacheEviction;

  private LinkModerationService service;
  private LinkEntity link;

  @BeforeEach
  void setUp() {
    service =
        new LinkModerationService(
            links, moderations, linkCacheEviction, Clock.fixed(NOW, ZoneOffset.UTC));
    link = new LinkEntity("https://phish.example", "abc123", 7L, null);
    ReflectionTestUtils.setField(link, "id", 5L);
  }

  @Test
  void disablingRecordsWhyAndWhoAndDropsTheCachedRedirect() {
    when(links.findById(5L)).thenReturn(Optional.of(link));
    when(moderations.findByLinkId(5L)).thenReturn(Optional.empty());

    assertThat(service.disable(5L, LinkDisableReason.ABUSE_REPORT, 1L)).isTrue();

    ArgumentCaptor<LinkModerationEntity> saved =
        ArgumentCaptor.forClass(LinkModerationEntity.class);
    verify(moderations).insert(saved.capture());
    assertThat(saved.getValue().getLinkId()).isEqualTo(5L);
    assertThat(saved.getValue().getReason()).isEqualTo(LinkDisableReason.ABUSE_REPORT);
    assertThat(saved.getValue().getDisabledBy()).isEqualTo(1L);
    assertThat(saved.getValue().getDisabledAt()).isEqualTo(NOW);
    verify(linkCacheEviction).evictAfterCommit(link.getShortCode());
  }

  @Test
  void disablingAnAlreadyDisabledLinkChangesNothing() {
    when(links.findById(5L)).thenReturn(Optional.of(link));
    when(moderations.findByLinkId(5L))
        .thenReturn(
            Optional.of(new LinkModerationEntity(5L, LinkDisableReason.SAFE_BROWSING, null, NOW)));

    assertThat(service.disable(5L, LinkDisableReason.ADMIN, 1L)).isFalse();

    verify(moderations, never()).insert(any());
    verify(linkCacheEviction, never()).evictAfterCommit(any());
  }

  @Test
  void anAdminSwitchesALinkOffByItsShortCode() {
    when(links.findByShortCode(ShortCode.of("abc123"))).thenReturn(Optional.of(link));
    when(moderations.findByLinkId(5L)).thenReturn(Optional.empty());

    assertThat(service.disable(ShortCode.of("abc123"), LinkDisableReason.ADMIN, 1L)).isTrue();

    ArgumentCaptor<LinkModerationEntity> saved =
        ArgumentCaptor.forClass(LinkModerationEntity.class);
    verify(moderations).insert(saved.capture());
    assertThat(saved.getValue().getLinkId()).isEqualTo(5L);
    assertThat(saved.getValue().getReason()).isEqualTo(LinkDisableReason.ADMIN);
  }

  @Test
  void enablingRemovesTheModerationAndDropsTheCachedPage() {
    LinkModerationEntity moderation =
        new LinkModerationEntity(5L, LinkDisableReason.ADMIN, 1L, NOW);
    when(links.findByShortCode(ShortCode.of("abc123"))).thenReturn(Optional.of(link));
    when(moderations.findByLinkId(5L)).thenReturn(Optional.of(moderation));

    assertThat(service.enable(ShortCode.of("abc123"))).isTrue();

    verify(moderations).delete(moderation);
    verify(linkCacheEviction).evictAfterCommit(link.getShortCode());
  }

  @Test
  void enablingALiveLinkChangesNothing() {
    when(links.findByShortCode(ShortCode.of("abc123"))).thenReturn(Optional.of(link));
    when(moderations.findByLinkId(5L)).thenReturn(Optional.empty());

    assertThat(service.enable(ShortCode.of("abc123"))).isFalse();

    verify(moderations, never()).delete(any());
  }

  @Test
  void anUnknownLinkIsNotFound() {
    when(links.findById(9L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.disable(9L, LinkDisableReason.ADMIN, 1L))
        .isInstanceOf(LinkException.class)
        .satisfies(
            e ->
                assertThat(((LinkException) e).errorCode())
                    .isEqualTo(LinkErrorCode.LINK_NOT_FOUND));
  }

  @Test
  void anUnknownShortCodeIsNotFound() {
    when(links.findByShortCode(ShortCode.of("gone99"))).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.enable(ShortCode.of("gone99")))
        .isInstanceOf(LinkException.class)
        .satisfies(
            e ->
                assertThat(((LinkException) e).errorCode())
                    .isEqualTo(LinkErrorCode.LINK_NOT_FOUND));
  }
}
