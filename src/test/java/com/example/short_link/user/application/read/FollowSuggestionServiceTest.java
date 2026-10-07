package com.example.short_link.user.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.FollowSuggestionReader;
import com.example.short_link.user.domain.repository.UserRepository;
import com.example.short_link.user.exception.UserException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FollowSuggestionServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-08T00:00:00Z");

  @Mock private FollowSuggestionReader reader;
  @Mock private UserRepository users;

  private FollowSuggestionService service() {
    return new FollowSuggestionService(reader, users, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void suggestionsLookAtTheLastThirtyDaysAndClampTheLimit() {
    List<FollowSuggestionReader.Suggestion> picks =
        List.of(
            new FollowSuggestionReader.Suggestion(
                "sori", "소리", null, null, 2, FollowSuggestionReader.Reason.FRIENDS, false));
    when(reader.suggestions(7L, Instant.parse("2026-09-08T00:00:00Z"), 40)).thenReturn(picks);
    when(reader.suggestions(7L, Instant.parse("2026-09-08T00:00:00Z"), 1)).thenReturn(List.of());

    assertThat(service().suggestions(7L, 500)).isEqualTo(picks);
    assertThat(service().suggestions(7L, 0)).isEmpty();
  }

  @Test
  void settingSomeoneAsideNeedsThemToExist() {
    UserEntity sori = new UserEntity("s@x.com", "google", "g-2");
    ReflectionTestUtils.setField(sori, "id", 2L);
    when(users.findByUsername("sori")).thenReturn(Optional.of(sori));
    when(users.findByUsername("ghost")).thenReturn(Optional.empty());
    FollowSuggestionService service = service();

    service.dismiss(7L, "sori");
    verify(reader).dismiss(7L, 2L);
    assertThatThrownBy(() -> service.dismiss(7L, "ghost")).isInstanceOf(UserException.class);
  }

  @Test
  void theProductionConstructorUsesTheSystemClock() {
    assertThat(new FollowSuggestionService(reader, users)).isNotNull();
    verifyNoInteractions(reader);
  }
}
