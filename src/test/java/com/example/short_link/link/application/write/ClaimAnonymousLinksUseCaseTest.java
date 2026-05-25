package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.link.application.dto.ClaimResult;
import com.example.short_link.link.application.dto.LinkCreated;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ClaimAnonymousLinksUseCaseTest {

  @Autowired private ClaimAnonymousLinksUseCase useCase;
  @Autowired private CreateLinkUseCase creationService;
  @Autowired private LinkRepository linkRepository;
  @Autowired private UserRepository userRepository;

  @Test
  void claimsAnonymousLinkByToken() {
    UserEntity user = userRepository.save(new UserEntity("c@example.com", "google", "g-c"));
    LinkCreated created =
        creationService.execute(
            CreateLinkCommand.of("https://example.com/claim", null, null, null));
    String token = created.claimToken();
    assertThat(token).isNotNull();

    ClaimResult result =
        useCase.execute(ClaimAnonymousLinksCommand.of(user.getId(), List.of(token)));
    assertThat(result.claimed()).isEqualTo(1);
    assertThat(result.skipped()).isZero();

    LinkEntity link = linkRepository.findByShortCode(created.shortCode()).orElseThrow();
    assertThat(link.getUserId()).isEqualTo(user.getId());
    assertThat(link.getClaimToken()).isNull();
  }

  @Test
  void replayingClaimIsNoOp() {
    UserEntity user = userRepository.save(new UserEntity("c2@example.com", "google", "g-c2"));
    LinkCreated created =
        creationService.execute(
            CreateLinkCommand.of("https://example.com/claim2", null, null, null));
    useCase.execute(ClaimAnonymousLinksCommand.of(user.getId(), List.of(created.claimToken())));

    ClaimResult second =
        useCase.execute(ClaimAnonymousLinksCommand.of(user.getId(), List.of(created.claimToken())));
    assertThat(second.claimed()).isZero();
    assertThat(second.skipped()).isEqualTo(1);
  }

  @Test
  void unknownTokensSkipped() {
    UserEntity user = userRepository.save(new UserEntity("c3@example.com", "google", "g-c3"));
    ClaimResult result =
        useCase.execute(
            ClaimAnonymousLinksCommand.of(
                user.getId(), List.of("00000000000000000000000000000000")));
    assertThat(result.claimed()).isZero();
    assertThat(result.skipped()).isEqualTo(1);
  }

  @Test
  void claimClearsExpiry() {
    UserEntity user = userRepository.save(new UserEntity("c5@example.com", "google", "g-c5"));
    Instant ttl = Instant.now().plus(Duration.ofHours(24));
    LinkCreated created =
        creationService.execute(
            CreateLinkCommand.of("https://example.com/claim5", null, null, ttl));

    LinkEntity beforeClaim = linkRepository.findByShortCode(created.shortCode()).orElseThrow();
    assertThat(beforeClaim.getExpiresAt()).isNotNull();

    useCase.execute(ClaimAnonymousLinksCommand.of(user.getId(), List.of(created.claimToken())));

    LinkEntity afterClaim = linkRepository.findByShortCode(created.shortCode()).orElseThrow();
    assertThat(afterClaim.getUserId()).isEqualTo(user.getId());
    assertThat(afterClaim.getExpiresAt()).isNull();
  }

  @Test
  void authenticatedShorteningHasNoClaimToken() {
    UserEntity user = userRepository.save(new UserEntity("c4@example.com", "google", "g-c4"));
    LinkCreated created =
        creationService.execute(
            CreateLinkCommand.of("https://example.com/claim4", user.getId(), null, null));
    assertThat(created.claimToken()).isNull();
  }
}
