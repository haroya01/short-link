package com.example.short_link.link.destination.application.write;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.link.destination.domain.LinkDestinationEntity;
import com.example.short_link.link.destination.domain.repository.LinkDestinationRepository;
import org.junit.jupiter.api.Test;

class DeleteDestinationUseCaseTest {

  private final LinkDestinationOwnership ownership = mock(LinkDestinationOwnership.class);
  private final LinkDestinationRepository repository = mock(LinkDestinationRepository.class);
  private final DeleteDestinationUseCase useCase =
      new DeleteDestinationUseCase(ownership, repository);

  @Test
  void executeDeletesOwnedDestination() {
    LinkDestinationEntity dest =
        new LinkDestinationEntity(1L, "https://dest.com", 50, null, null, null, null);
    when(ownership.ownedDestination(42L, "abc1234", 99L)).thenReturn(dest);

    useCase.execute(42L, "abc1234", 99L);

    verify(repository).delete(dest);
  }
}
