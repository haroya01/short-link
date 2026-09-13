package com.example.short_link.post.application.write;

import com.example.short_link.common.geoip.GeoLocation;
import com.example.short_link.link.application.dto.UserAgentInfo;
import com.example.short_link.link.classifier.application.AsnResolver;
import com.example.short_link.link.classifier.application.BotHeuristic;
import com.example.short_link.link.classifier.application.GeoIpResolver;
import com.example.short_link.link.classifier.application.UserAgentClassifier;
import com.example.short_link.link.classifier.application.helper.IpMasker;
import com.example.short_link.link.classifier.application.helper.LanguageExtractor;
import com.example.short_link.link.classifier.application.helper.ReferrerNormalizer;
import com.example.short_link.link.classifier.application.helper.SourceChannelNormalizer;
import com.example.short_link.link.classifier.application.helper.VisitorHasher;
import com.example.short_link.post.domain.PostEntity;
import com.example.short_link.post.domain.PostViewEventEntity;
import com.example.short_link.post.domain.repository.PostRepository;
import com.example.short_link.post.domain.repository.PostViewEventRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Clock;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 공개 글의 요청마다 조회수와 방문 이벤트를 기록한다. 방문자 중복은 제거하지 않는다. 분류기 오류가 나면 부가 정보 없는 이벤트를 저장해 조회 집계를 유지한다. */
@Slf4j
@Service
public class RecordPostViewUseCase {

  private final UserRepository userRepository;
  private final PostRepository postRepository;
  private final PostViewEventRepository postViewEventRepository;
  private final UserAgentClassifier userAgentClassifier;
  private final GeoIpResolver geoIpResolver;
  private final AsnResolver asnResolver;
  private final BotHeuristic botHeuristic;
  private final Clock clock;

  public RecordPostViewUseCase(
      UserRepository userRepository,
      PostRepository postRepository,
      PostViewEventRepository postViewEventRepository,
      UserAgentClassifier userAgentClassifier,
      GeoIpResolver geoIpResolver,
      AsnResolver asnResolver,
      BotHeuristic botHeuristic,
      Clock clock) {
    this.userRepository = userRepository;
    this.postRepository = postRepository;
    this.postViewEventRepository = postViewEventRepository;
    this.userAgentClassifier = userAgentClassifier;
    this.geoIpResolver = geoIpResolver;
    this.asnResolver = asnResolver;
    this.botHeuristic = botHeuristic;
    this.clock = clock;
  }

  @Transactional
  public void execute(RecordPostViewCommand cmd, ViewContext ctx) {
    String normalized = cmd.username().trim().toLowerCase(Locale.ROOT);
    UserEntity author =
        userRepository.findByUsername(normalized).filter(u -> !u.isDeleted()).orElse(null);
    if (author == null) return;
    PostEntity post =
        postRepository.findByUserIdAndSlugForUpdate(author.getId(), cmd.slug()).orElse(null);
    if (post == null || !post.isPublished()) return;
    post.incrementViewCount();
    postRepository.save(post);
    postViewEventRepository.save(buildEvent(post.getId(), ctx));
  }

  private PostViewEventEntity buildEvent(Long postId, ViewContext ctx) {
    if (ctx == null || ctx.isEmpty()) {
      return new PostViewEventEntity(postId, clock.instant());
    }
    try {
      UserAgentInfo ua = userAgentClassifier.classify(ctx.userAgent());
      GeoLocation geo = geoIpResolver.resolve(ctx.clientIp());
      AsnResolver.AsnInfo asn = asnResolver.resolve(ctx.clientIp());
      boolean bot = ua.bot();
      String botName = ua.botName();
      if (!bot && botHeuristic.isSuspectBurst(ctx.clientIp())) {
        bot = true;
        botName = BotHeuristic.SUSPECT_LABEL;
      } else if (!bot && asn.datacenter()) {
        bot = true;
        botName = "datacenter:" + (asn.organization() == null ? "unknown" : asn.organization());
      }
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
          .bot(bot)
          .botName(botName)
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
