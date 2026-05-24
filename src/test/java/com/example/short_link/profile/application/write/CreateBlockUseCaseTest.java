package com.example.short_link.profile.application.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.ProfileBlockType;
import com.example.short_link.profile.domain.repository.ProfileBlockRepository;
import com.example.short_link.profile.exception.ProfileException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateBlockUseCaseTest {

  @Mock private ProfileBlockRepository profileBlockRepository;
  @Mock private ProfileOrdering profileOrdering;

  private CreateBlockUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new CreateBlockUseCase(profileBlockRepository, profileOrdering);
  }

  @Test
  void savesNewBlockWithNextOrder() {
    when(profileOrdering.nextOrder(7L)).thenReturn(3);
    when(profileBlockRepository.save(any(ProfileBlockEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    ProfileBlockEntity created =
        useCase.execute(new CreateBlockCommand(7L, ProfileBlockType.DIVIDER, null));
    assertThat(created.getType()).isEqualTo(ProfileBlockType.DIVIDER);
    assertThat(created.getProfileOrder()).isEqualTo(3);
  }

  @Test
  void rejectsImageWithoutUrl() {
    assertThatThrownBy(
            () -> useCase.execute(new CreateBlockCommand(7L, ProfileBlockType.IMAGE, "  ")))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void rejectsImageNonHttp() {
    assertThatThrownBy(
            () ->
                useCase.execute(
                    new CreateBlockCommand(7L, ProfileBlockType.IMAGE, "javascript:alert(1)")))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void rejectsImageMalformed() {
    assertThatThrownBy(
            () ->
                useCase.execute(new CreateBlockCommand(7L, ProfileBlockType.IMAGE, "http://[bad")))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void rejectsImageTooLong() {
    String long2049 = "https://x.com/" + "a".repeat(2049);
    assertThatThrownBy(
            () -> useCase.execute(new CreateBlockCommand(7L, ProfileBlockType.IMAGE, long2049)))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void rejectsEmbedNonWhitelisted() {
    assertThatThrownBy(
            () ->
                useCase.execute(
                    new CreateBlockCommand(
                        7L, ProfileBlockType.EMBED, "https://unknown-domain.example/x")))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void rejectsEmbedEmpty() {
    assertThatThrownBy(
            () -> useCase.execute(new CreateBlockCommand(7L, ProfileBlockType.EMBED, "")))
        .isInstanceOf(ProfileException.class);
  }
}
