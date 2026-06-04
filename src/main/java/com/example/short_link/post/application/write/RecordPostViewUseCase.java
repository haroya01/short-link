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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public post 단건 view 카운터. visitor 가 publishing page 열 때 frontend 가 fire. 익명, dedup 없음 (v0 minimum)
 * — 정확한 unique visitor 추적은 L3 tracking JS 별도 트랙. PUBLISHED 글만 집계 (DRAFT / UNPUBLISHED 는 noop).
 *
 * <p>집계는 두 갈래다: posts.view_count 누적 카운터(카드에 보이는 총 조회수)를 올리고, 동시에 post_view_event 에 한 줄을 남긴다. 후자가
 * "trending" 을 누적이 아니라 최근 윈도우 조회수로 계산하는 근거이자, **글별/시리즈별 독자 분석**(누가·어디서 봤나)의 원천이다. 이벤트 한 줄은 요청
 * 컨텍스트({@link ViewContext})로 enrich 한다 — referrer/UA/IP/UTM → 국가/기기/브라우저/유입/채널, {@code
 * ProfileVisitRecorder} 와 같은 classifier 를 재사용해 enrichment 품질을 맞춘다. enrichment 가 실패해도 조회수 증가는 막지
 * 않는다(핫패스): bare 이벤트로 폴백한다.
 */
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

  @Autowired
  public RecordPostViewUseCase(
      UserRepository userRepository,
      PostRepository postRepository,
      PostViewEventRepository postViewEventRepository,
      UserAgentClassifier userAgentClassifier,
      GeoIpResolver geoIpResolver,
      AsnResolver asnResolver,
      BotHeuristic botHeuristic) {
    this(
        userRepository,
        postRepository,
        postViewEventRepository,
        userAgentClassifier,
        geoIpResolver,
        asnResolver,
        botHeuristic,
        Clock.systemUTC());
  }

  RecordPostViewUseCase(
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

  /** Bare view (no request context) — kept for callers without an HTTP request. */
  @Transactional
  public void execute(RecordPostViewCommand cmd) {
    execute(cmd, ViewContext.empty());
  }

  @Transactional
  public void execute(RecordPostViewCommand cmd, ViewContext ctx) {
    String normalized = cmd.username().trim().toLowerCase();
    UserEntity author =
        userRepository.findByUsername(normalized).filter(u -> !u.isDeleted()).orElse(null);
    if (author == null) return;
    PostEntity post = postRepository.findByUserIdAndSlug(author.getId(), cmd.slug()).orElse(null);
    if (post == null || !post.isPublished()) return;
    post.incrementViewCount();
    postRepository.save(post);
    postViewEventRepository.save(buildEvent(post.getId(), ctx));
  }

  /**
   * Enriched view event from the request context; falls back to a bare row if enrichment throws so
   * a classifier hiccup never costs the view (the count is already persisted above).
   */
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
          .visitorHash(VisitorHasher.hash(postId, ctx.clientIp(), ctx.userAgent()))
          .sourceChannel(SourceChannelNormalizer.normalize(ctx.sourceChannel()))
          .build();
    } catch (RuntimeException e) {
      log.warn("post view enrichment failed for postId={}", postId, e);
      return new PostViewEventEntity(postId, clock.instant());
    }
  }
}
