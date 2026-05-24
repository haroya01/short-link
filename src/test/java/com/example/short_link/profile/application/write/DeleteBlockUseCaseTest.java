package com.example.short_link.profile.application.write;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.profile.domain.ProfileBlockEntity;
import com.example.short_link.profile.domain.ProfileBlockType;
import com.example.short_link.profile.domain.repository.ProfileBlockRepository;
import com.example.short_link.profile.exception.ProfileException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteBlockUseCaseTest {

  @Mock private ProfileBlockRepository profileBlockRepository;

  private DeleteBlockUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new DeleteBlockUseCase(profileBlockRepository);
  }

  @Test
  void ownerSucceeds() {
    ProfileBlockEntity block = new ProfileBlockEntity(7L, ProfileBlockType.TEXT, "x", 1);
    when(profileBlockRepository.findById(11L)).thenReturn(Optional.of(block));
    useCase.execute(new DeleteBlockCommand(7L, 11L));
    verify(profileBlockRepository).delete(block);
  }

  @Test
  void notFoundThrows() {
    when(profileBlockRepository.findById(11L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> useCase.execute(new DeleteBlockCommand(7L, 11L)))
        .isInstanceOf(ProfileException.class);
  }

  @Test
  void notOwnedThrows() {
    ProfileBlockEntity block = new ProfileBlockEntity(99L, ProfileBlockType.TEXT, "x", 1);
    when(profileBlockRepository.findById(11L)).thenReturn(Optional.of(block));
    assertThatThrownBy(() -> useCase.execute(new DeleteBlockCommand(7L, 11L)))
        .isInstanceOf(ProfileException.class);
  }
}
