package com.example.short_link.profile.application.visit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.short_link.common.geoip.GeoLocation;
import com.example.short_link.link.application.AsnResolver;
import com.example.short_link.link.application.BotHeuristic;
import com.example.short_link.link.application.GeoIpResolver;
import com.example.short_link.link.application.UserAgentClassifier;
import com.example.short_link.link.application.dto.UserAgentInfo;
import com.example.short_link.profile.domain.visit.ProfileVisitEntity;
import com.example.short_link.profile.domain.visit.ProfileVisitRepository;
import com.example.short_link.user.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileVisitRecorderTest {

  @Mock private ProfileVisitRepository repository;
  @Mock private UserRepository userRepository;
  @Mock private UserAgentClassifier uaClassifier;
  @Mock private GeoIpResolver geoResolver;
  @Mock private AsnResolver asnResolver;
  @Mock private BotHeuristic botHeuristic;

  private ProfileVisitRecorder recorder;

  @BeforeEach
  void setUp() {
    recorder =
        new ProfileVisitRecorder(
            repository, userRepository, uaClassifier, geoResolver, asnResolver, botHeuristic);
  }

  @Test
  void recordsHumanVisitWithEnrichment() {
    when(uaClassifier.classify("ua")).thenReturn(uaInfo(false, null));
    when(geoResolver.resolve("1.2.3.4")).thenReturn(new GeoLocation("KR", "Seoul", "Gangnam"));
    when(asnResolver.resolve("1.2.3.4"))
        .thenReturn(new AsnResolver.AsnInfo(15169, "Google", false));
    when(botHeuristic.isSuspectBurst("1.2.3.4")).thenReturn(false);

    recorder.record(
        7L,
        "https://t.co/x",
        "ua",
        "1.2.3.4",
        "ko-KR",
        "x",
        "twitter",
        "social",
        "launch",
        null,
        null);

    ArgumentCaptor<ProfileVisitEntity> cap = ArgumentCaptor.forClass(ProfileVisitEntity.class);
    verify(repository).save(cap.capture());
    ProfileVisitEntity e = cap.getValue();
    assertThat(e.getProfileUserId()).isEqualTo(7L);
    assertThat(e.getCountryCode()).isEqualTo("KR");
    assertThat(e.getUtmSource()).isEqualTo("twitter");
    assertThat(e.isBot()).isFalse();
    assertThat(e.getBotName()).isNull();
  }

  @Test
  void marksAsBotWhenUaBotPositive() {
    when(uaClassifier.classify("bot-ua")).thenReturn(uaInfo(true, "Googlebot"));
    when(geoResolver.resolve("1.2.3.4")).thenReturn(GeoLocation.empty());
    when(asnResolver.resolve("1.2.3.4")).thenReturn(new AsnResolver.AsnInfo(15169, "Google", true));

    recorder.record(7L, null, "bot-ua", "1.2.3.4", null, null, null, null, null, null, null);

    ArgumentCaptor<ProfileVisitEntity> cap = ArgumentCaptor.forClass(ProfileVisitEntity.class);
    verify(repository).save(cap.capture());
    assertThat(cap.getValue().isBot()).isTrue();
    assertThat(cap.getValue().getBotName()).isEqualTo("Googlebot");
  }

  @Test
  void marksAsSuspectWhenBurstDetected() {
    when(uaClassifier.classify("ua")).thenReturn(uaInfo(false, null));
    when(geoResolver.resolve("1.2.3.4")).thenReturn(GeoLocation.empty());
    when(asnResolver.resolve("1.2.3.4"))
        .thenReturn(new AsnResolver.AsnInfo(15169, "Google", false));
    when(botHeuristic.isSuspectBurst("1.2.3.4")).thenReturn(true);

    recorder.record(7L, null, "ua", "1.2.3.4", null, null, null, null, null, null, null);

    ArgumentCaptor<ProfileVisitEntity> cap = ArgumentCaptor.forClass(ProfileVisitEntity.class);
    verify(repository).save(cap.capture());
    assertThat(cap.getValue().isBot()).isTrue();
    assertThat(cap.getValue().getBotName()).isEqualTo(BotHeuristic.SUSPECT_LABEL);
  }

  @Test
  void marksAsDatacenterBotWhenAsnSaysSo() {
    when(uaClassifier.classify("ua")).thenReturn(uaInfo(false, null));
    when(geoResolver.resolve("1.2.3.4")).thenReturn(GeoLocation.empty());
    when(asnResolver.resolve("1.2.3.4")).thenReturn(new AsnResolver.AsnInfo(16509, "AWS", true));
    when(botHeuristic.isSuspectBurst("1.2.3.4")).thenReturn(false);

    recorder.record(7L, null, "ua", "1.2.3.4", null, null, null, null, null, null, null);

    ArgumentCaptor<ProfileVisitEntity> cap = ArgumentCaptor.forClass(ProfileVisitEntity.class);
    verify(repository).save(cap.capture());
    assertThat(cap.getValue().isBot()).isTrue();
    assertThat(cap.getValue().getBotName()).isEqualTo("datacenter:AWS");
  }

  @Test
  void datacenterBotFallsBackWhenOrgMissing() {
    when(uaClassifier.classify("ua")).thenReturn(uaInfo(false, null));
    when(geoResolver.resolve("1.2.3.4")).thenReturn(GeoLocation.empty());
    when(asnResolver.resolve("1.2.3.4")).thenReturn(new AsnResolver.AsnInfo(0, null, true));
    when(botHeuristic.isSuspectBurst("1.2.3.4")).thenReturn(false);

    recorder.record(7L, null, "ua", "1.2.3.4", null, null, null, null, null, null, null);

    ArgumentCaptor<ProfileVisitEntity> cap = ArgumentCaptor.forClass(ProfileVisitEntity.class);
    verify(repository).save(cap.capture());
    assertThat(cap.getValue().getBotName()).isEqualTo("datacenter:unknown");
  }

  @Test
  void swallowsExceptionAndDoesNotThrow() {
    when(uaClassifier.classify(any())).thenThrow(new RuntimeException("boom"));
    recorder.record(7L, null, "ua", null, null, null, null, null, null, null, null);
    verify(repository, never()).save(any());
  }

  @Test
  void everyInvocationIndependentlyEnrichedAndSaved() {
    when(uaClassifier.classify(any())).thenReturn(uaInfo(false, null));
    when(geoResolver.resolve(any())).thenReturn(GeoLocation.empty());
    when(asnResolver.resolve(any())).thenReturn(new AsnResolver.AsnInfo(0, null, false));
    when(botHeuristic.isSuspectBurst(any())).thenReturn(false);

    for (int i = 0; i < 3; i++) {
      recorder.record(7L, null, "ua", "1.1.1.1", null, null, null, null, null, null, null);
    }
    verify(repository, times(3)).save(any());
  }

  private static UserAgentInfo uaInfo(boolean bot, String botName) {
    return new UserAgentInfo("Desktop", "macOS", "Chrome", bot, botName);
  }
}
