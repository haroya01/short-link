package com.example.short_link.link.application.write;

import static com.example.short_link.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.audit.AuditAction;
import com.example.short_link.common.audit.AuditLogService;
import com.example.short_link.common.security.BlockedDomainChecker;
import com.example.short_link.link.application.ShortCodeGenerator;
import com.example.short_link.link.application.dto.LinkCreated;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.og.application.dto.LinkOgFetchRequested;
import com.example.short_link.link.safety.application.UrlSafetyChecker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class CreateLinkUseCaseOutcomesTest {

  private final LinkRepository repository = mock(LinkRepository.class);
  private final ShortCodeGenerator generator = mock(ShortCodeGenerator.class);
  private final AuditLogService audit = mock(AuditLogService.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final List<Object> events = new ArrayList<>();
  private final List<LinkEntity> saved = new ArrayList<>();
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
  private CreateLinkUseCase links;

  @BeforeEach
  void setUp() {
    AtomicLong ids = new AtomicLong();
    when(repository.save(any(LinkEntity.class)))
        .thenAnswer(
            call -> {
              LinkEntity link = withId(call.getArgument(0), ids.incrementAndGet());
              saved.add(link);
              return link;
            });
    UrlSafetyChecker safety = mock(UrlSafetyChecker.class);
    when(safety.isSafe(anyString())).thenReturn(true);
    links =
        new CreateLinkUseCase(
            repository,
            generator,
            meters,
            events::add,
            audit,
            new CreateLinkValidator(
                mock(BlockedDomainChecker.class),
                safety,
                new SimpleMeterRegistry(),
                "https://kurl.me",
                "https://kurl.me"),
            linkId -> false,
            encoder,
            LinkCreationServiceCollisionTest.noopTransactionManager(),
            200L);
  }

  @Test
  void onlyAnAnonymousLinkGetsAClaimTokenAndEachOneIsItsOwn() {
    when(generator.generate()).thenReturn("anon001", "anon002", "mine001");

    LinkCreated first =
        links.execute(CreateLinkCommand.of("https://example.com/a", null, null, null));
    LinkCreated second =
        links.execute(CreateLinkCommand.of("https://example.com/b", null, null, null));
    LinkCreated owned =
        links.execute(CreateLinkCommand.of("https://example.com/c", 7L, null, null));

    assertThat(first.claimToken()).matches("[0-9a-f]{32}").isNotEqualTo(second.claimToken());
    assertThat(saved.get(0).getClaimToken()).isEqualTo(first.claimToken());
    assertThat(owned.claimToken()).isNull();
    assertThat(saved.get(2).getClaimToken()).isNull();
  }

  @Test
  void aSignedInOwnersPasswordIsStoredHashedOnTheLink() {
    when(generator.generate()).thenReturn("pass001");

    LinkCreated created =
        links.execute(CreateLinkCommand.of("https://example.com/a", 7L, null, null, "s3cret"));

    LinkEntity link = saved.get(0);
    assertThat(created.passwordProtected()).isTrue();
    assertThat(encoder.matches("s3cret", link.getPasswordHash())).isTrue();
  }

  @Test
  void aBlankPasswordOrAnAnonymousOneLeavesTheLinkOpen() {
    when(generator.generate()).thenReturn("open001", "open002");

    LinkCreated blank =
        links.execute(CreateLinkCommand.of("https://example.com/a", 7L, null, null, "  "));
    LinkCreated anonymous =
        links.execute(CreateLinkCommand.of("https://example.com/b", null, null, null, "s3cret"));

    assertThat(blank.passwordProtected()).isFalse();
    assertThat(anonymous.passwordProtected()).isFalse();
    assertThat(saved).allSatisfy(link -> assertThat(link.getPasswordHash()).isNull());
  }

  @Test
  void aGeneratedCodeThatIsReservedIsSkipped() {
    when(generator.generate()).thenReturn("admin", "fresh01");

    LinkCreated created =
        links.execute(CreateLinkCommand.of("https://example.com/a", 7L, null, null));

    assertThat(created.shortCode()).isEqualTo(new ShortCode("fresh01"));
  }

  @Test
  void aNewLinkAsksForItsPreviewIsAuditedAndCounted() {
    when(generator.generate()).thenReturn("new0001");

    links.execute(CreateLinkCommand.of("https://example.com/a", 7L, null, null));

    assertThat(events)
        .containsExactly(
            new LinkOgFetchRequested(new ShortCode("new0001"), "https://example.com/a"));
    verify(audit).record(eq(AuditAction.LINK_CREATED), eq("link"), eq("new0001"), eq(7L), any());
    assertThat(
            meters
                .counter(
                    "short_link.created",
                    "authenticated",
                    "true",
                    "custom",
                    "false",
                    "result",
                    "ok")
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void handingBackAnExistingLinkIsCountedAsDeduplicatedAndCreatesNothing() {
    LinkEntity existing = withId(new LinkEntity("https://example.com/a", "old0001", 7L, null), 50L);
    when(repository.findUnrestrictedByUserIdAndOriginalUrl(7L, "https://example.com/a"))
        .thenReturn(List.of(existing));

    LinkCreated created =
        links.execute(CreateLinkCommand.of("https://example.com/a", 7L, null, null));

    assertThat(created.shortCode()).isEqualTo(new ShortCode("old0001"));
    assertThat(saved).isEmpty();
    assertThat(events).isEmpty();
    assertThat(
            meters
                .counter(
                    "short_link.created",
                    "authenticated",
                    "true",
                    "custom",
                    "false",
                    "result",
                    "deduplicated")
                .count())
        .isEqualTo(1.0);
  }
}
