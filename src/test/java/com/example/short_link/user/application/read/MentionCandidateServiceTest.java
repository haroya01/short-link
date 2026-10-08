package com.example.short_link.user.application.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.example.short_link.user.domain.repository.MentionCandidateReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MentionCandidateServiceTest {

  @Mock private MentionCandidateReader reader;

  @Test
  void anAtSignAloneListsThePeopleTheMemberFollows() {
    new MentionCandidateService(reader).candidates(7L, " @ ", 8);

    verify(reader).followed(7L, 8);
    verifyNoMoreInteractions(reader);
  }

  @Test
  void typedLettersMatchFromTheStartWithoutTheAtSignAndCase() {
    new MentionCandidateService(reader).candidates(7L, "@Yuk", 50);

    verify(reader).matching(7L, "yuk", MentionCandidateService.MAX_LIMIT);
  }

  @Test
  void aLongQueryIsCutAndALimitBelowOneAsksForOne() {
    String long_ = "a".repeat(80);
    new MentionCandidateService(reader).candidates(7L, long_, 0);

    verify(reader).matching(7L, "a".repeat(MentionCandidateService.MAX_QUERY), 1);
    assertThat(MentionCandidateService.normalize(null)).isEmpty();
  }
}
