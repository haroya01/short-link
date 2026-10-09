package com.example.short_link.link.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.short_link.link.destination.application.write.AddDestinationUseCase;
import com.example.short_link.link.destination.application.write.UpdateDestinationUseCase;
import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.destination.domain.repository.LinkDestinationRepository;
import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.ShortCode;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.exception.LinkErrorCode;
import com.example.short_link.link.exception.LinkException;
import com.example.short_link.link.safety.application.UrlSafetyChecker;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
@ActiveProfiles("test")
class DestinationEditSafetyTest {
  private static final String PHISH = "https://phish.example.com/login";

  @MockitoBean private UrlSafetyChecker safeBrowsing;
  @Autowired private UpdateLinkUseCase updateLink;
  @Autowired private AddDestinationUseCase addDestination;
  @Autowired private UpdateDestinationUseCase updateDestination;
  @Autowired private LinkRepository links;
  @Autowired private LinkDestinationRepository destinations;
  @Autowired private UserRepository users;
  @Autowired private JdbcTemplate jdbc;

  private final List<Long> createdUsers = new ArrayList<>();
  private final List<Boolean> checkedInsideATransaction = new ArrayList<>();

  @BeforeEach
  void phishingIsUnsafe() {
    when(safeBrowsing.isSafe(anyString()))
        .thenAnswer(
            call -> {
              checkedInsideATransaction.add(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return !PHISH.equals(call.getArgument(0));
            });
  }

  @AfterEach
  void deleteCommittedRows() {
    for (Long userId : createdUsers) {
      jdbc.update("DELETE FROM link WHERE user_id = ?", userId);
      jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }
  }

  @Test
  void anUnsafeNewDestinationIsRejectedLikeAtCreation() {
    UserEntity owner = owner();
    ShortCode code = link(owner);

    assertThatThrownBy(
            () ->
                updateLink.execute(
                    new UpdateLinkCommand(owner.getId(), code, PHISH, null, null, null, false)))
        .isInstanceOfSatisfying(
            LinkException.class,
            e -> assertThat(e.errorCode()).isEqualTo(LinkErrorCode.MALICIOUS_URL));

    assertThat(links.findByShortCode(code).orElseThrow().getOriginalUrl())
        .isEqualTo("https://example.com/" + code);
    assertThat(checkedInsideATransaction).containsOnly(false);
  }

  @Test
  void aSafeNewDestinationIsSaved() {
    UserEntity owner = owner();
    ShortCode code = link(owner);

    updateLink.execute(
        new UpdateLinkCommand(
            owner.getId(), code, "https://example.com/moved", null, null, null, false));

    assertThat(links.findByShortCode(code).orElseThrow().getOriginalUrl())
        .isEqualTo("https://example.com/moved");
    assertThat(checkedInsideATransaction).containsExactly(false);
  }

  @Test
  void anUnsafeVariantIsNeitherAddedNorSwappedIn() {
    UserEntity owner = owner();
    ShortCode code = link(owner);
    Long variantId =
        addDestination
            .execute(owner.getId(), code, "https://example.com/b", 50, "B", null, null, null)
            .id();

    assertThatThrownBy(
            () -> addDestination.execute(owner.getId(), code, PHISH, 50, "C", null, null, null))
        .isInstanceOfSatisfying(
            LinkException.class,
            e -> assertThat(e.errorCode()).isEqualTo(LinkErrorCode.MALICIOUS_URL));
    assertThatThrownBy(
            () ->
                updateDestination.execute(
                    owner.getId(), code, variantId, PHISH, null, null, null, null, null, null))
        .isInstanceOfSatisfying(
            LinkException.class,
            e -> assertThat(e.errorCode()).isEqualTo(LinkErrorCode.MALICIOUS_URL));

    long linkId = links.findByShortCode(code).orElseThrow().getId();
    assertThat(destinations.findAllByLinkIdOrderByIdAsc(linkId))
        .extracting(LinkDestinationEntity::getUrl)
        .containsExactly("https://example.com/b");
    assertThat(checkedInsideATransaction).containsOnly(false);
  }

  private UserEntity owner() {
    String tag = "ds" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    UserEntity saved = users.save(new UserEntity(tag + "@example.com", "google", tag));
    createdUsers.add(saved.getId());
    return saved;
  }

  private ShortCode link(UserEntity owner) {
    String code = "d" + UUID.randomUUID().toString().replace("-", "").substring(0, 9);
    links.save(new LinkEntity("https://example.com/" + code, code, owner.getId(), null));
    return new ShortCode(code);
  }
}
