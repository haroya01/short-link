package com.example.short_link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.short_link.link.domain.LinkEntity;
import com.example.short_link.link.domain.repository.LinkRepository;
import com.example.short_link.link.health.application.DestinationCheck;
import com.example.short_link.link.health.application.DestinationCheck.Outcome;
import com.example.short_link.link.health.application.DestinationHealthRecorder;
import com.example.short_link.link.health.application.DestinationProbe;
import com.example.short_link.link.health.domain.DestinationFailure;
import com.example.short_link.link.health.domain.repository.LinkDestinationHealthRepository;
import com.example.short_link.link.health.domain.repository.LinkDestinationHealthRepository.DueDestination;
import com.example.short_link.link.health.scheduler.DestinationHealthJob;
import com.example.short_link.user.application.JwtTokenService;
import com.example.short_link.user.domain.UserEntity;
import com.example.short_link.user.domain.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DestinationHealthE2ETest {

  private static final String GONE_URL = "https://gone.example.com/spring-sale";
  private static final DestinationCheck NOT_FOUND =
      new DestinationCheck(Outcome.BROKEN, DestinationFailure.NOT_FOUND, 404);

  @Autowired private MockMvc mvc;
  @Autowired private UserRepository userRepository;
  @Autowired private LinkRepository linkRepository;
  @Autowired private LinkDestinationHealthRepository healths;
  @Autowired private DestinationHealthRecorder recorder;
  @Autowired private DestinationHealthJob job;
  @Autowired private JwtTokenService jwt;
  @MockitoBean private DestinationProbe probe;

  @Test
  void onlyLiveOwnedLinksAreChecked() {
    UserEntity owner = newUser();
    LinkEntity owned =
        linkRepository.save(new LinkEntity(GONE_URL, "dhc0001", owner.getId(), null));
    LinkEntity anonymous = linkRepository.save(new LinkEntity(GONE_URL, "dhc0002", null, null));
    LinkEntity expired =
        linkRepository.save(
            new LinkEntity(
                GONE_URL, "dhc0003", owner.getId(), Instant.now().minus(Duration.ofDays(1))));

    List<Long> due = dueIds();

    assertThat(due).contains(owned.getId()).doesNotContain(anonymous.getId(), expired.getId());
  }

  @Test
  void theJobProbesDueLinksAndRemembersTheResult() {
    when(probe.check(any())).thenReturn(new DestinationCheck(Outcome.INCONCLUSIVE, null, null));
    when(probe.check(GONE_URL)).thenReturn(NOT_FOUND);
    UserEntity owner = newUser();
    LinkEntity link = linkRepository.save(new LinkEntity(GONE_URL, "dhc0004", owner.getId(), null));

    job.run();

    var health = healths.findById(link.getId()).orElseThrow();
    assertThat(health.getFailures()).isEqualTo(1);
    assertThat(health.isBrokenFor(GONE_URL)).isFalse();
    assertThat(dueIds()).doesNotContain(link.getId());
  }

  @Test
  void aConfirmedBreakageShowsOnTheLinkUntilTheOwnerMovesIt() throws Exception {
    UserEntity owner = newUser();
    LinkEntity link = linkRepository.save(new LinkEntity(GONE_URL, "dhc0005", owner.getId(), null));
    DueDestination due = new DueDestination(link.getId(), "dhc0005", owner.getId(), GONE_URL, null);
    Instant first = Instant.now().minus(Duration.ofHours(30));
    recorder.record(due, NOT_FOUND, first);
    recorder.record(due, NOT_FOUND, first.plus(Duration.ofHours(21)));

    mvc.perform(get("/api/v1/links/dhc0005/detail").header("Authorization", bearer(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.destinationHealth.broken").value(true))
        .andExpect(jsonPath("$.destinationHealth.failure").value("NOT_FOUND"))
        .andExpect(jsonPath("$.destinationHealth.httpStatus").value(404));

    mvc.perform(
            patch("/api/v1/links/dhc0005")
                .header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"originalUrl\":\"https://shop.example.com/spring-sale\"}"))
        .andExpect(status().isOk());

    mvc.perform(get("/api/v1/links/dhc0005/detail").header("Authorization", bearer(owner)))
        .andExpect(jsonPath("$.destinationHealth.broken").value(false));
    assertThat(dueIds()).contains(link.getId());
  }

  private List<Long> dueIds() {
    Instant now = Instant.now();
    return healths.findDue(now, now.minus(Duration.ofHours(20)), 500).stream()
        .map(DueDestination::linkId)
        .toList();
  }

  private UserEntity newUser() {
    String tag = UUID.randomUUID().toString();
    return userRepository.save(new UserEntity(tag + "@x.com", "google", tag));
  }

  private String bearer(UserEntity user) {
    return "Bearer " + jwt.createAccessToken(user.getId(), "USER");
  }
}
