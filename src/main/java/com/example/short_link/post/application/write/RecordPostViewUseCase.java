package com.example.short_link.post.application.write;

import com.example.short_link.common.analytics.VisitRequest;
import com.example.short_link.link.classifier.application.ClassifiedVisit;
import com.example.short_link.link.classifier.application.VisitClassifier;
import com.example.short_link.post.application.access.PostAccessPolicy;
import com.example.short_link.post.application.access.PostReader;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostViewEventEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostViewEventRepository;
import com.example.short_link.user.application.read.UserSummary;
import com.example.short_link.user.application.read.UserSummaryReader;
import java.time.Clock;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 방문자 중복은 제거하지 않는다. 분류기 오류가 나면 부가 정보 없는 이벤트를 저장해 조회 집계를 유지한다.
@Slf4j
@Service
public class RecordPostViewUseCase {

  private final UserSummaryReader users;
  private final PostRepository postRepository;
  private final PostViewEventRepository postViewEventRepository;
  private final VisitClassifier visitClassifier;
  private final PostAccessPolicy access;
  private final PostReaders readers;
  private final Clock clock;

  public RecordPostViewUseCase(
      UserSummaryReader users,
      PostRepository postRepository,
      PostViewEventRepository postViewEventRepository,
      VisitClassifier visitClassifier,
      PostAccessPolicy access,
      PostReaders readers,
      Clock clock) {
    this.users = users;
    this.postRepository = postRepository;
    this.postViewEventRepository = postViewEventRepository;
    this.visitClassifier = visitClassifier;
    this.access = access;
    this.readers = readers;
    this.clock = clock;
  }

  @Transactional
  public void execute(RecordPostViewCommand cmd, VisitRequest ctx, PostReader reader) {
    String normalized = cmd.username().trim().toLowerCase(Locale.ROOT);
    UserSummary author = users.findActiveByUsername(normalized).orElse(null);
    if (author == null) return;
    PostEntity post =
        postRepository.findByUserIdAndSlugForUpdate(author.id(), cmd.slug()).orElse(null);
    if (post == null || !access.decide(post, reader).full()) return;
    PostViewEventEntity event = buildEvent(post.getId(), ctx);
    readers.countView(post, event.getVisitorHash(), event.isBot(), cmd.viewerId());
    post.incrementViewCount();
    postRepository.save(post);
    postViewEventRepository.save(event);
  }

  private PostViewEventEntity buildEvent(Long postId, VisitRequest ctx) {
    if (ctx == null || ctx.isEmpty()) {
      return new PostViewEventEntity(postId, clock.instant());
    }
    try {
      ClassifiedVisit visit = visitClassifier.classify(ctx, postId);
      return PostViewEventEntity.builder()
          .postId(postId)
          .viewedAt(clock.instant())
          .referrer(visit.referrer().url())
          .referrerHost(visit.referrer().host())
          .userAgent(ctx.userAgent())
          .clientIp(visit.maskedIp())
          .utmSource(ctx.utm().source())
          .utmMedium(ctx.utm().medium())
          .utmCampaign(ctx.utm().campaign())
          .utmTerm(ctx.utm().term())
          .utmContent(ctx.utm().content())
          .deviceClass(visit.device().deviceClass())
          .osName(visit.device().osName())
          .browserName(visit.device().browserName())
          .bot(visit.bot().isBot())
          .botName(visit.bot().botName())
          .countryCode(visit.geo().countryCode())
          .regionName(visit.geo().region())
          .cityName(visit.geo().city())
          .language(visit.language())
          .visitorHash(visit.visitorHash())
          .sourceChannel(visit.sourceChannel())
          .sessionId(ctx.sessionId())
          .build();
    } catch (RuntimeException e) {
      log.warn("post view enrichment failed for postId={}", postId, e);
      return new PostViewEventEntity(postId, clock.instant());
    }
  }
}
