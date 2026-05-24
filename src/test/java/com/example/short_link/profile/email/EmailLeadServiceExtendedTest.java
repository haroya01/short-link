package com.example.short_link.profile.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.ProfileBlockRepository;
import com.example.short_link.profile.domain.ProfileBlockType;
import com.example.short_link.profile.exception.EmailLeadRateLimitedException;
import com.example.short_link.profile.exception.InvalidUsernameException;
import com.example.short_link.profile.exception.ProfileNotFoundException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

class EmailLeadServiceExtendedTest {

  private EmailLeadRepository repository;
  private ProfileBlockRepository blockRepository;
  private EmailLeadService service;

  @BeforeEach
  void setUp() {
    repository = mock(EmailLeadRepository.class);
    blockRepository = mock(ProfileBlockRepository.class);
    service = new EmailLeadService(repository, blockRepository);
    ReflectionTestUtils.setField(service, "ipHashSalt", "salt");
  }

  private ProfileBlockEntity emailBlock(long blockId, long ownerId) {
    ProfileBlockEntity b = new ProfileBlockEntity(ownerId, ProfileBlockType.EMAIL_FORM, "{}", 1);
    writeField(b, "id", blockId);
    return b;
  }

  @Test
  void submitMissingBlockThrows() {
    when(blockRepository.findById(11L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.submit(7L, 11L, "u@x.com", "1.1.1.1"))
        .isInstanceOf(ProfileNotFoundException.class);
  }

  @Test
  void submitWrongTypeThrows() {
    ProfileBlockEntity wrong = new ProfileBlockEntity(7L, ProfileBlockType.TEXT, "x", 1);
    writeField(wrong, "id", 11L);
    when(blockRepository.findById(11L)).thenReturn(Optional.of(wrong));
    assertThatThrownBy(() -> service.submit(7L, 11L, "u@x.com", "1.1.1.1"))
        .isInstanceOf(ProfileNotFoundException.class);
  }

  @Test
  void submitWrongOwnerThrows() {
    when(blockRepository.findById(11L)).thenReturn(Optional.of(emailBlock(11L, 7L)));
    assertThatThrownBy(() -> service.submit(9L, 11L, "u@x.com", "1.1.1.1"))
        .isInstanceOf(ProfileNotFoundException.class);
  }

  @Test
  void submitInvalidEmailThrows() {
    assertThatThrownBy(() -> service.submit(7L, 11L, "", "1.1.1.1"))
        .isInstanceOf(InvalidUsernameException.class);
    assertThatThrownBy(() -> service.submit(7L, 11L, null, "1.1.1.1"))
        .isInstanceOf(InvalidUsernameException.class);
    assertThatThrownBy(() -> service.submit(7L, 11L, "not-an-email", "1.1.1.1"))
        .isInstanceOf(InvalidUsernameException.class);
    String tooLong = "a".repeat(250) + "@x.com";
    assertThatThrownBy(() -> service.submit(7L, 11L, tooLong, "1.1.1.1"))
        .isInstanceOf(InvalidUsernameException.class);
  }

  @Test
  void submitRateLimitedByBlockWindow() {
    when(blockRepository.findById(11L)).thenReturn(Optional.of(emailBlock(11L, 7L)));
    when(repository.countByBlockIdAndSubmittedAtAfter(eq(11L), any(Instant.class)))
        .thenReturn(999L);
    assertThatThrownBy(() -> service.submit(7L, 11L, "u@x.com", "1.1.1.1"))
        .isInstanceOf(EmailLeadRateLimitedException.class)
        .hasMessageContaining("block window");
  }

  @Test
  void submitRateLimitedByIpWindow() {
    when(blockRepository.findById(11L)).thenReturn(Optional.of(emailBlock(11L, 7L)));
    when(repository.countByBlockIdAndSubmittedAtAfter(anyLong(), any(Instant.class)))
        .thenReturn(0L);
    when(repository.countByIpHashAndSubmittedAtAfter(anyString(), any(Instant.class)))
        .thenReturn(99L);
    assertThatThrownBy(() -> service.submit(7L, 11L, "u@x.com", "1.1.1.1"))
        .isInstanceOf(EmailLeadRateLimitedException.class)
        .hasMessageContaining("ip window");
  }

  @Test
  void submitIdempotentOnDuplicateEmail() {
    when(blockRepository.findById(11L)).thenReturn(Optional.of(emailBlock(11L, 7L)));
    when(repository.existsByBlockIdAndEmail(11L, "u@x.com")).thenReturn(true);
    EmailLeadEntity result = service.submit(7L, 11L, "u@x.com", "1.1.1.1");
    assertThat(result.getEmail()).isEqualTo("u@x.com");
    verify(repository, never()).save(any());
  }

  @Test
  void submitPersistsNewLead() {
    when(blockRepository.findById(11L)).thenReturn(Optional.of(emailBlock(11L, 7L)));
    when(repository.existsByBlockIdAndEmail(11L, "u@x.com")).thenReturn(false);
    when(repository.save(any(EmailLeadEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    EmailLeadEntity result = service.submit(7L, 11L, "  U@X.com  ", "1.2.3.4");
    assertThat(result.getEmail()).isEqualTo("u@x.com");
    assertThat(result.getUserId()).isEqualTo(7L);
    assertThat(result.getIpHash()).isNotNull().hasSize(64);
    verify(repository).save(any(EmailLeadEntity.class));
  }

  @Test
  void submitWithBlankIpStoresNullHash() {
    when(blockRepository.findById(11L)).thenReturn(Optional.of(emailBlock(11L, 7L)));
    when(repository.existsByBlockIdAndEmail(11L, "u@x.com")).thenReturn(false);
    when(repository.save(any(EmailLeadEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    EmailLeadEntity result = service.submit(7L, 11L, "u@x.com", "");
    assertThat(result.getIpHash()).isNull();
  }

  @Test
  void listClampsPageAndSize() {
    when(repository.findAllByUserIdOrderBySubmittedAtDesc(eq(7L), any(PageRequest.class)))
        .thenReturn(List.of());
    service.list(7L, -1, -5);
    service.list(7L, 0, 99999);
  }

  @Test
  void listActiveClampsPageAndSize() {
    when(repository.findAllByUserIdAndOptedOutFalseOrderBySubmittedAtDesc(
            eq(7L), any(PageRequest.class)))
        .thenReturn(List.of());
    service.listActive(7L, -1, -5);
    service.listActive(7L, 0, 99999);
  }

  @Test
  void countDelegatesToRepository() {
    when(repository.countByUserId(7L)).thenReturn(42L);
    assertThat(service.count(7L)).isEqualTo(42L);
  }

  @Test
  void deleteOwnerSucceeds() {
    EmailLeadEntity lead = new EmailLeadEntity(7L, 1L, "u@x.com", null);
    when(repository.findById(99L)).thenReturn(Optional.of(lead));
    service.delete(7L, 99L);
    verify(repository).delete(lead);
  }

  @Test
  void deleteNonOwnerThrows() {
    EmailLeadEntity lead = new EmailLeadEntity(7L, 1L, "u@x.com", null);
    when(repository.findById(99L)).thenReturn(Optional.of(lead));
    assertThatThrownBy(() -> service.delete(8L, 99L)).isInstanceOf(ProfileNotFoundException.class);
  }

  @Test
  void deleteMissingThrows() {
    when(repository.findById(99L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.delete(7L, 99L)).isInstanceOf(ProfileNotFoundException.class);
  }

  @Test
  void entityHelpers() {
    EmailLeadEntity lead = new EmailLeadEntity(7L, 1L, "u@x.com", "hash");
    assertThat(lead.getUserId()).isEqualTo(7L);
    assertThat(lead.getBlockId()).isEqualTo(1L);
    assertThat(lead.getEmail()).isEqualTo("u@x.com");
    assertThat(lead.getIpHash()).isEqualTo("hash");
    assertThat(lead.isOwnedBy(7L)).isTrue();
    assertThat(lead.isOwnedBy(8L)).isFalse();
    lead.setOptedOut(true);
    assertThat(lead.isOptedOut()).isTrue();
  }

  @Test
  void rateLimitedExceptionCarriesMessage() {
    assertThat(new EmailLeadRateLimitedException("x")).hasMessage("x");
  }

  private static void writeField(Object target, String name, Object value) {
    try {
      Field f = findField(target.getClass(), name);
      f.setAccessible(true);
      f.set(target, value);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private static Field findField(Class<?> cls, String name) throws NoSuchFieldException {
    Class<?> c = cls;
    while (c != null) {
      try {
        return c.getDeclaredField(name);
      } catch (NoSuchFieldException ignored) {
        c = c.getSuperclass();
      }
    }
    throw new NoSuchFieldException(name);
  }
}
