package com.example.short_link.post.application.write;

import com.example.short_link.common.geoip.GeoLocation;
import com.example.short_link.link.application.dto.UserAgentInfo;
import com.example.short_link.link.classifier.application.AsnResolver;
import com.example.short_link.link.classifier.application.BotClassifier;
import com.example.short_link.link.classifier.application.GeoIpResolver;
import com.example.short_link.link.classifier.application.UserAgentClassifier;
import com.example.short_link.link.classifier.application.helper.IpMasker;
import com.example.short_link.link.classifier.application.helper.LanguageExtractor;
import com.example.short_link.link.classifier.application.helper.ReferrerNormalizer;
import com.example.short_link.link.classifier.application.helper.SourceChannelNormalizer;
import com.example.short_link.link.classifier.application.helper.VisitorHasher;
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

// 요청마다 조회수와 방문 이벤트를 기록하고 방문자 중복은 제거하지 않는다.
@Slf4j
@Service
public class RecordPostViewUseCase {

  private final UserSummaryReader users;
  private final PostRepository postRepository;
  private final PostViewEventRepository postViewEventRepository;
  private final UserAgentClassifier userAgentClassifier;
  private final GeoIpResolver geoIpResolver;
  private final AsnResolver asnResolver;
  private final BotClassifier botClassifier;
  private final PostAccessPolicy access;
  private final PostReaders readers;
  private final Clock clock;

  public RecordPostViewUseCase(
      UserSummaryReader users,
      PostRepository postRepository,
      PostViewEventRepository postViewEventRepository,
      UserAgentClassifier userAgentClassifier,
      GeoIpResolver geoIpResolver,
      AsnResolver asnResolver,
      BotClassifier botClassifier,
      PostAccessPolicy access,
      PostReaders readers,
      Clock clock) {
    this.users = users;
    this.postRepository = postRepository;
    this.postViewEventRepository = postViewEventRepository;
    this.userAgentClassifier = userAgentClassifier;
    this.geoIpResolver = geoIpResolver;
    this.asnResolver = asnResolver;
    this.botClassifier = botClassifier;
    this.access = access;
    this.readers = readers;
    this.clock = clock;
  }

  @Transactional
  public void execute(RecordPostViewCommand cmd, ViewContext ctx, PostReader reader) {
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

  private PostViewEventEntity buildEvent(Long postId, ViewContext ctx) {
    if (ctx == null || ctx.isEmpty()) {
      return new PostViewEventEntity(postId, clock.instant());
    }
    try {
      UserAgentInfo ua = userAgentClassifier.classify(ctx.userAgent());
      GeoLocation geo = geoIpResolver.resolve(ctx.clientIp());
      AsnResolver.AsnInfo asn = asnResolver.resolve(ctx.clientIp());
      BotClassifier.Verdict bot = botClassifier.classify(ua, asn, ctx.clientIp());
      return PostViewEventEntity.builder()
          .postId(postId)
          .viewedAt(clock.instant())
          .referrer(ReferrerNormalizer.normalize(ctx.referrer()))
          .referrerHost(ReferrerNormalizer.hostOf(ctx.referrer()))
          .userAgent(ctx.userAgent())
          .clientIp(IpMasker.mask(ctx.clientIp()))
          .utmSource(ctx.utmSource())
          .utmMedium(ctx.utmMedium())
          .utmCampaign(ctx.utmCampaign())
          .utmTerm(ctx.utmTerm())
          .utmContent(ctx.utmContent())
          .deviceClass(ua.deviceClass())
          .osName(ua.osName())
          .browserName(ua.browserName())
          .bot(bot.isBot())
          .botName(bot.botName())
          .countryCode(geo.countryCode())
          .regionName(geo.region())
          .cityName(geo.city())
          .language(LanguageExtractor.extract(ctx.acceptLanguage()))
          // GPC 수신 시 익명 방문만 집계하고 재방문 식별 해시는 만들지 않는다.
          .visitorHash(
              ctx.gpc() ? null : VisitorHasher.hash(postId, ctx.clientIp(), ctx.userAgent()))
          .sourceChannel(SourceChannelNormalizer.normalize(ctx.sourceChannel()))
          .sessionId(ctx.sessionId())
          .build();
    } catch (RuntimeException e) {
      log.warn("post view enrichment failed for postId={}", postId, e);
      return new PostViewEventEntity(postId, clock.instant());
    }
  }
}
