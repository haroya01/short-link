package com.example.short_link.user.application.write;

import static com.example.short_link.support.TestEntities.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.common.audit.AuditAction;
import com.example.short_link.common.audit.AuditLogService;
import com.example.short_link.common.cache.ProfileCacheInvalidator;
import com.example.short_link.common.event.AccountDeletedEvent;
import com.example.short_link.common.storage.ObjectStorage;
import com.example.short_link.link.application.LinkAccountData;
import com.example.short_link.support.FakeTransactions;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.BlockRepository;
import com.example.short_link.user.domain.repository.DeviceTokenRepository;
import com.example.short_link.user.domain.repository.FollowRepository;
import com.example.short_link.user.domain.repository.MuteRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.domain.repository.WebPushSubscriptionRepository;
import com.example.short_link.user.exception.UserException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SoftDeleteAccountTest {

  private final UserRepository users = mock(UserRepository.class);
  private final LinkAccountData links = mock(LinkAccountData.class);
  private final RefreshTokenStore tokens = mock(RefreshTokenStore.class);
  private final DeviceTokenRepository devices = mock(DeviceTokenRepository.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final AuditLogService audit = mock(AuditLogService.class);
  private final ProfileCacheInvalidator profiles = mock(ProfileCacheInvalidator.class);
  private final List<Object> published = new ArrayList<>();
  private final UserDeletionService deletion =
      new UserDeletionService(
          users,
          links,
          mock(FollowRepository.class),
          mock(BlockRepository.class),
          mock(MuteRepository.class),
          mock(WebPushSubscriptionRepository.class),
          List.of(),
          tokens,
          devices,
          meters,
          audit,
          published::add,
          profiles,
          mock(ObjectStorage.class),
          FakeTransactions.template());

  private UserEntity anAccount() {
    UserEntity account = withId(new UserEntity("gone@example.com", "google", "g-gone"), 9L);
    account.claimUsername("gone");
    when(users.findById(9L)).thenReturn(Optional.of(account));
    return account;
  }

  private double softDeletes() {
    return meters.counter("user.soft_deleted").count();
  }

  @Test
  void closingAnAccountFlagsItAndDropsItsSessionsDevicesAndProfileCache() {
    UserEntity account = anAccount();

    deletion.deleteAccount(9L);

    assertThat(account.isDeleted()).isTrue();
    verify(profiles).evictByUsername("gone");
    verify(tokens).deleteAllForUser(9L);
    verify(devices).deleteByUserId(9L);
    assertThat(published).containsExactly(new AccountDeletedEvent(9L));
    assertThat(softDeletes()).isEqualTo(1.0);
  }

  @Test
  void theAuditRowCarriesHowManyLinksTheAccountKeeps() {
    anAccount();
    when(links.countOwned(9L)).thenReturn(3L);

    deletion.deleteAccount(9L);

    verify(audit)
        .record(AuditAction.USER_DELETED, "user", "9", 9L, Map.of("soft", true, "ownedLinks", 3L));
  }

  @Test
  void theAccountIsFlaggedInsideTheTransactionAndAuditedAfterIt() {
    anAccount();
    List<String> seen = new ArrayList<>();
    doAnswer(
            inv -> {
              seen.add("sessions " + FakeTransactions.where());
              return null;
            })
        .when(tokens)
        .deleteAllForUser(9L);
    doAnswer(
            inv -> {
              seen.add("audit " + FakeTransactions.where());
              return null;
            })
        .when(audit)
        .record(any(), any(), any(), any(), any());

    deletion.deleteAccount(9L);

    assertThat(seen).containsExactly("sessions inside", "audit outside");
  }

  @Test
  void closingAnAccountAgainDoesNothing() {
    anAccount().softDelete();

    deletion.deleteAccount(9L);

    verifyNoInteractions(audit, tokens, devices, profiles, links);
    assertThat(published).isEmpty();
    assertThat(softDeletes()).isZero();
  }

  @Test
  void anUnknownAccountIsRefusedAndNotAudited() {
    when(users.findById(9L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> deletion.deleteAccount(9L)).isInstanceOf(UserException.class);

    verifyNoInteractions(audit, tokens, devices, profiles);
    assertThat(published).isEmpty();
  }
}
