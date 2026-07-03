package com.example.short_link.post.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.geoip.GeoLocation;
import com.example.short_link.link.application.dto.UserAgentInfo;
import com.example.short_link.link.classifier.application.AsnResolver;
import com.example.short_link.link.classifier.application.BotHeuristic;
import com.example.short_link.link.classifier.application.GeoIpResolver;
import com.example.short_link.link.classifier.application.UserAgentClassifier;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostViewEventEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostViewEventRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
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

@ExtendWith(MockitoExtension.class)
class RecordPostViewUseCaseTest {

  private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

  @Mock private UserRepository userRepository;
  @Mock private PostRepository postRepository;
  @Mock private PostViewEventRepository postViewEventRepository;
  @Mock private UserAgentClassifier userAgentClassifier;
  @Mock private GeoIpResolver geoIpResolver;
  @Mock private AsnResolver asnResolver;
  @Mock private BotHeuristic botHeuristic;

  private RecordPostViewUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase =
        new RecordPostViewUseCase(
            userRepository,
            postRepository,
            postViewEventRepository,
            userAgentClassifier,
            geoIpResolver,
            asnResolver,
            botHeuristic,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private UserEntity author(String username) {
    UserEntity u = new UserEntity("u@x.com", "google", "g-1");
    u.claimUsername(username);
    return u;
  }

  @Test
  void incrementsViewCountAndAppendsEventForPublished() {
    UserEntity author = author("john");
    PostEntity post = new PostEntity(author.getId(), "p", "P", "ko");
    post.publish();
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    when(postRepository.findByUserIdAndSlug(author.getId(), "p")).thenReturn(Optional.of(post));
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(new RecordPostViewCommand("john", "p"), ViewContext.empty());

    assertThat(post.getViewCount()).isEqualTo(1L);
    verify(postRepository).save(post);
    ArgumentCaptor<PostViewEventEntity> event = ArgumentCaptor.forClass(PostViewEventEntity.class);
    verify(postViewEventRepository).save(event.capture());
    assertThat(event.getValue().getViewedAt()).isEqualTo(NOW);
  }

  @Test
  void normalizesUsername() {
    UserEntity author = author("john");
    PostEntity post = new PostEntity(author.getId(), "p", "P", "ko");
    post.publish();
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    when(postRepository.findByUserIdAndSlug(author.getId(), "p")).thenReturn(Optional.of(post));
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    useCase.execute(new RecordPostViewCommand("  JOHN  ", "p"), ViewContext.empty());

    assertThat(post.getViewCount()).isEqualTo(1L);
  }

  @Test
  void noopForUnknownUser() {
    when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

    useCase.execute(new RecordPostViewCommand("ghost", "p"), ViewContext.empty());

    verify(postRepository, never()).save(any());
    verify(postViewEventRepository, never()).save(any());
  }

  @Test
  void noopForDeletedUser() {
    UserEntity author = author("john");
    author.softDelete();
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));

    useCase.execute(new RecordPostViewCommand("john", "p"), ViewContext.empty());

    verify(postRepository, never()).save(any());
    verify(postViewEventRepository, never()).save(any());
  }

  @Test
  void noopForDraftPost() {
    UserEntity author = author("john");
    PostEntity post = new PostEntity(author.getId(), "p", "P", "ko");
    // status DRAFT
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    when(postRepository.findByUserIdAndSlug(author.getId(), "p")).thenReturn(Optional.of(post));

    useCase.execute(new RecordPostViewCommand("john", "p"), ViewContext.empty());

    assertThat(post.getViewCount()).isZero();
    verify(postRepository, never()).save(any());
    verify(postViewEventRepository, never()).save(any());
  }

  @Test
  void noopForUnpublishedPost() {
    UserEntity author = author("john");
    PostEntity post = new PostEntity(author.getId(), "p", "P", "ko");
    post.publish();
    post.unpublish();
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    when(postRepository.findByUserIdAndSlug(author.getId(), "p")).thenReturn(Optional.of(post));

    useCase.execute(new RecordPostViewCommand("john", "p"), ViewContext.empty());

    verify(postRepository, never()).save(any());
    verify(postViewEventRepository, never()).save(any());
  }

  @Test
  void enrichesEventFromContext() {
    UserEntity author = author("john");
    PostEntity post = new PostEntity(author.getId(), "p", "P", "ko");
    post.publish();
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    when(postRepository.findByUserIdAndSlug(author.getId(), "p")).thenReturn(Optional.of(post));
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    when(userAgentClassifier.classify(any()))
        .thenReturn(new UserAgentInfo("mobile", "iOS", "Safari", false, null));
    when(geoIpResolver.resolve(any())).thenReturn(new GeoLocation("KR", "Seoul", "Seoul"));
    when(asnResolver.resolve(any())).thenReturn(new AsnResolver.AsnInfo(0, "ISP", false));
    when(botHeuristic.isSuspectBurst(any())).thenReturn(false);

    useCase.execute(
        new RecordPostViewCommand("john", "p"),
        new ViewContext(
            "https://news.example.com/x",
            "Mozilla/5.0",
            "1.2.3.4",
            "ko-KR",
            "social",
            "twitter",
            null,
            null,
            null,
            null,
            false));

    ArgumentCaptor<PostViewEventEntity> event = ArgumentCaptor.forClass(PostViewEventEntity.class);
    verify(postViewEventRepository).save(event.capture());
    PostViewEventEntity saved = event.getValue();
    assertThat(saved.getCountryCode()).isEqualTo("KR");
    assertThat(saved.getDeviceClass()).isEqualTo("mobile");
    assertThat(saved.getBrowserName()).isEqualTo("Safari");
    assertThat(saved.getReferrerHost()).isNotBlank();
    assertThat(saved.isBot()).isFalse();
    assertThat(saved.getViewedAt()).isEqualTo(NOW);
    assertThat(saved.getVisitorHash()).hasSize(64);
  }

  private void stubPublished() {
    UserEntity author = author("john");
    PostEntity post = new PostEntity(author.getId(), "p", "P", "ko");
    post.publish();
    when(userRepository.findByUsername("john")).thenReturn(Optional.of(author));
    when(postRepository.findByUserIdAndSlug(author.getId(), "p")).thenReturn(Optional.of(post));
    when(postRepository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  private ViewContext ctx() {
    return new ViewContext(
        "https://x.com",
        "Mozilla/5.0",
        "1.2.3.4",
        "ko",
        "social",
        "tw",
        null,
        null,
        null,
        null,
        false);
  }

  private PostViewEventEntity savedEvent() {
    ArgumentCaptor<PostViewEventEntity> c = ArgumentCaptor.forClass(PostViewEventEntity.class);
    verify(postViewEventRepository).save(c.capture());
    return c.getValue();
  }

  @Test
  void marksBotOnSuspectBurst() {
    stubPublished();
    when(userAgentClassifier.classify(any()))
        .thenReturn(new UserAgentInfo("desktop", "Win", "Chrome", false, null));
    when(geoIpResolver.resolve(any())).thenReturn(new GeoLocation("US", null, null));
    when(asnResolver.resolve(any())).thenReturn(new AsnResolver.AsnInfo(0, "ISP", false));
    when(botHeuristic.isSuspectBurst(any())).thenReturn(true);

    useCase.execute(new RecordPostViewCommand("john", "p"), ctx());

    assertThat(savedEvent().isBot()).isTrue();
    assertThat(savedEvent().getBotName()).isEqualTo(BotHeuristic.SUSPECT_LABEL);
  }

  @Test
  void marksBotOnDatacenterAsn() {
    stubPublished();
    when(userAgentClassifier.classify(any()))
        .thenReturn(new UserAgentInfo("desktop", "Win", "Chrome", false, null));
    when(geoIpResolver.resolve(any())).thenReturn(new GeoLocation("US", null, null));
    when(asnResolver.resolve(any())).thenReturn(new AsnResolver.AsnInfo(15169, "GOOGLE", true));
    when(botHeuristic.isSuspectBurst(any())).thenReturn(false);

    useCase.execute(new RecordPostViewCommand("john", "p"), ctx());

    assertThat(savedEvent().isBot()).isTrue();
    assertThat(savedEvent().getBotName()).startsWith("datacenter:");
  }

  @Test
  void fallsBackToBareEventWhenEnrichmentThrows() {
    stubPublished();
    when(userAgentClassifier.classify(any())).thenThrow(new RuntimeException("classifier down"));

    useCase.execute(new RecordPostViewCommand("john", "p"), ctx());

    PostViewEventEntity saved = savedEvent();
    assertThat(saved.getCountryCode()).isNull();
    assertThat(saved.getViewedAt()).isEqualTo(NOW);
  }

  @Test
  void gpcOptOutSkipsVisitorHash() {
    stubPublished();
    when(userAgentClassifier.classify(any()))
        .thenReturn(new UserAgentInfo("desktop", "Win", "Chrome", false, null));
    when(geoIpResolver.resolve(any())).thenReturn(GeoLocation.empty());
    when(asnResolver.resolve(any())).thenReturn(new AsnResolver.AsnInfo(0, "ISP", false));
    when(botHeuristic.isSuspectBurst(any())).thenReturn(false);

    // Sec-GPC 옵트아웃 → 재방문 식별 해시를 만들지 않는다(§0, 측정 아닌 존중).
    useCase.execute(
        new RecordPostViewCommand("john", "p"),
        new ViewContext(
            "https://x.com",
            "Mozilla/5.0",
            "1.2.3.4",
            "ko",
            "social",
            "tw",
            null,
            null,
            null,
            null,
            true));

    assertThat(savedEvent().getVisitorHash()).isNull();
  }
}
