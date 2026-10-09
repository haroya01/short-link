package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.short_link.campaign.application.CampaignBatchService;
import com.example.short_link.campaign.application.write.CampaignBatchCreateCommand;
import com.example.short_link.campaign.application.write.CreateCampaignCommand;
import com.example.short_link.campaign.application.write.CreateCampaignUseCase;
import com.example.short_link.campaign.domain.CampaignEntity;
import com.example.short_link.campaign.domain.CampaignPostEndAction;
import com.example.short_link.link.access.application.LinkProtectionService;
import com.example.short_link.link.application.dto.LinkCreated;
import com.example.short_link.link.destination.application.write.AddDestinationUseCase;
import com.example.short_link.link.destination.application.write.SetBlockedCountriesUseCase;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.moderation.application.LinkModerationService;
import com.example.short_link.link.moderation.domain.LinkDisableReason;
import com.example.short_link.link.visit.application.LinkVisitOptionService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LinkCreationServiceTest {

  @Autowired private CreateLinkUseCase service;
  @Autowired private LinkRepository linkRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private LinkProtectionService protection;
  @Autowired private LinkModerationService moderation;
  @Autowired private LinkVisitOptionService visitOptions;
  @Autowired private SetBlockedCountriesUseCase blockedCountries;
  @Autowired private AddDestinationUseCase addDestination;
  @Autowired private CreateCampaignUseCase createCampaign;
  @Autowired private CampaignBatchService campaignBatches;

  @Test
  void createsAnonymousLinkWithOneDayExpiry() {
    LinkCreated created =
        service.execute(CreateLinkCommand.of("https://example.com/anon", null, null, null));

    LinkEntity saved = linkRepository.findByShortCode(created.shortCode()).orElseThrow();
    assertThat(saved.getShortCode().value()).hasSize(7);
    assertThat(saved.getUserId()).isNull();
    assertThat(saved.getExpiresAt())
        .isBetween(Instant.now().plusSeconds(86000), Instant.now().plusSeconds(87000));
  }

  @Test
  void createsAuthenticatedLinkWithoutExpiry() {
    UserEntity user = userRepository.save(new UserEntity("test@example.com", "google", "g-1"));

    LinkCreated created =
        service.execute(
            CreateLinkCommand.of("https://example.com/owned", user.getId(), null, null));

    LinkEntity saved = linkRepository.findByShortCode(created.shortCode()).orElseThrow();
    assertThat(saved.getUserId()).isEqualTo(user.getId());
    assertThat(saved.getExpiresAt()).isNull();
  }

  @Test
  void createsDistinctCodesForSameUrl() {
    LinkCreated first =
        service.execute(CreateLinkCommand.of("https://example.com", null, null, null));
    LinkCreated second =
        service.execute(CreateLinkCommand.of("https://example.com", null, null, null));

    assertThat(first.shortCode()).isNotEqualTo(second.shortCode());
  }

  @Test
  void createsWithCustomCodeForAuthenticatedUser() {
    UserEntity user = userRepository.save(new UserEntity("test@example.com", "google", "g-2"));

    LinkCreated created =
        service.execute(
            CreateLinkCommand.of("https://example.com/custom", user.getId(), "myLink", null));

    assertThat(created.shortCode().value()).isEqualTo("myLink");
    LinkEntity saved = linkRepository.findByShortCode(new ShortCode("myLink")).orElseThrow();
    assertThat(saved.getUserId()).isEqualTo(user.getId());
  }

  @Test
  void throwsDuplicateForExistingCustomCode() {
    UserEntity user = userRepository.save(new UserEntity("test@example.com", "google", "g-3"));
    service.execute(
        CreateLinkCommand.of("https://example.com/first", user.getId(), "taken1", null));

    assertThatThrownBy(
            () ->
                service.execute(
                    CreateLinkCommand.of(
                        "https://example.com/second", user.getId(), "taken1", null)))
        .isInstanceOf(LinkException.class);
  }

  @Test
  void ignoresCustomCodeForAnonymousUser() {
    LinkCreated created =
        service.execute(CreateLinkCommand.of("https://example.com", null, "ignored", null));

    assertThat(created.shortCode()).isNotEqualTo("ignored");
    assertThat(created.shortCode().value()).hasSize(7);
  }

  @Test
  void acceptsRequestedExpiresAtForAuthenticatedUser() {
    UserEntity user = userRepository.save(new UserEntity("test@example.com", "google", "g-4"));
    Instant requested = Instant.now().plus(Duration.ofDays(30)).truncatedTo(ChronoUnit.SECONDS);

    LinkCreated created =
        service.execute(CreateLinkCommand.of("https://example.com", user.getId(), null, requested));

    LinkEntity saved = linkRepository.findByShortCode(created.shortCode()).orElseThrow();
    assertThat(saved.getExpiresAt()).isEqualTo(requested);
  }

  @Test
  void reusesTheOwnersUnrestrictedLinkForTheSameUrl() {
    UserEntity user = userRepository.save(new UserEntity("test@example.com", "google", "g-d1"));
    LinkCreated first =
        service.execute(CreateLinkCommand.of("https://example.com/same", user.getId(), null, null));

    LinkCreated second =
        service.execute(CreateLinkCommand.of("https://example.com/same", user.getId(), null, null));

    assertThat(second.shortCode()).isEqualTo(first.shortCode());
  }

  @Test
  void aRequestedExpiryGetsItsOwnLinkInsteadOfAPermanentOne() {
    UserEntity user = userRepository.save(new UserEntity("test@example.com", "google", "g-d2"));
    LinkCreated permanent =
        service.execute(CreateLinkCommand.of("https://example.com/same", user.getId(), null, null));
    Instant requested = Instant.now().plus(Duration.ofDays(3)).truncatedTo(ChronoUnit.SECONDS);

    LinkCreated expiring =
        service.execute(
            CreateLinkCommand.of("https://example.com/same", user.getId(), null, requested));

    assertThat(expiring.shortCode()).isNotEqualTo(permanent.shortCode());
    assertThat(linkRepository.findByShortCode(expiring.shortCode()).orElseThrow().getExpiresAt())
        .isEqualTo(requested);
    assertThat(linkRepository.findByShortCode(permanent.shortCode()).orElseThrow().getExpiresAt())
        .isNull();
  }

  @Test
  void anExpiringLinkIsNotHandedOutForAPermanentRequest() {
    assertNotReused(
        "g-d3",
        (userId, code) -> {},
        Instant.now().plus(Duration.ofDays(3)).truncatedTo(ChronoUnit.SECONDS));
  }

  @Test
  void aLinkWithAViewLimitIsNotHandedOutAgain() {
    assertNotReused("g-d4", (userId, code) -> protection.update(userId, code, null, 1), null);
  }

  @Test
  void aPasswordLinkIsNotHandedOutAgain() {
    assertNotReused(
        "g-d5", (userId, code) -> protection.update(userId, code, "secret-pass", null), null);
  }

  @Test
  void aDisabledLinkIsNotHandedOutAgain() {
    assertNotReused(
        "g-d6", (userId, code) -> moderation.disable(code, LinkDisableReason.ADMIN, null), null);
  }

  @Test
  void aLinkThatOpensLaterIsNotHandedOutAgain() {
    assertNotReused(
        "g-d7",
        (userId, code) ->
            visitOptions.update(
                userId, code, null, null, Instant.now().plus(Duration.ofDays(1)), false),
        null);
  }

  @Test
  void aLinkThatBlocksCountriesIsNotHandedOutAgain() {
    assertNotReused("g-d8", (userId, code) -> blockedCountries.execute(userId, code, "JP"), null);
  }

  @Test
  void aLinkThatSplitsTrafficToAnotherDestinationIsNotHandedOutAgain() {
    assertNotReused(
        "g-d9",
        (userId, code) ->
            addDestination.execute(
                userId, code, "https://example.com/other", 50, "B", null, null, null),
        null);
  }

  @Test
  void aCampaignBatchLinkIsNotHandedOutForAnOrdinaryShortening() {
    UserEntity user = userRepository.save(new UserEntity("test@example.com", "google", "g-d10"));
    CampaignEntity campaign =
        createCampaign.execute(
            new CreateCampaignCommand(
                user.getId(),
                "Launch",
                null,
                Instant.now().plus(Duration.ofDays(30)),
                "https://example.com/flyer",
                CampaignPostEndAction.EXPIRE,
                null,
                null));
    ShortCode batchCode =
        campaignBatches
            .create(
                campaign.getId(),
                user.getId(),
                new CampaignBatchCreateCommand("Station", null, null, 10, null, null))
            .link()
            .getShortCode();

    LinkCreated created =
        service.execute(
            CreateLinkCommand.of("https://example.com/flyer", user.getId(), null, null));

    assertThat(created.shortCode()).isNotEqualTo(batchCode);
  }

  private void assertNotReused(
      String subject, BiConsumer<Long, ShortCode> restrict, Instant firstExpiresAt) {
    UserEntity user = userRepository.save(new UserEntity("test@example.com", "google", subject));
    String url = "https://example.com/restricted-" + subject;
    LinkCreated restricted =
        service.execute(CreateLinkCommand.of(url, user.getId(), null, firstExpiresAt));
    restrict.accept(user.getId(), restricted.shortCode());

    LinkCreated created = service.execute(CreateLinkCommand.of(url, user.getId(), null, null));

    assertThat(created.shortCode()).isNotEqualTo(restricted.shortCode());
  }

  @Test
  void ignoresRequestedExpiresAtForAnonymousUser() {
    Instant requested = Instant.now().plus(Duration.ofDays(30));

    LinkCreated created =
        service.execute(CreateLinkCommand.of("https://example.com", null, null, requested));

    LinkEntity saved = linkRepository.findByShortCode(created.shortCode()).orElseThrow();
    assertThat(saved.getExpiresAt())
        .isBetween(Instant.now().plusSeconds(86000), Instant.now().plusSeconds(87000));
  }
}
