package com.example.short_link.link.classifier.application;

import com.example.short_link.link.application.dto.UserAgentInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 클릭·프로필 방문·글 조회·행동 이벤트가 같은 순서로 봇을 가린다. 버스트 판정은 호출할 때마다 IP 카운터를 올리므로, 앞 단계에서 봇으로 정해지면 부르지 않는다. */
@Component
@RequiredArgsConstructor
public class BotClassifier {

  private final BotHeuristic botHeuristic;

  public Verdict classify(UserAgentInfo userAgent, AsnResolver.AsnInfo asn, String clientIp) {
    if (userAgent.bot()) {
      return Verdict.bot(userAgent.botName());
    }
    if (botHeuristic.isSuspectBurst(clientIp)) {
      return Verdict.bot(BotHeuristic.SUSPECT_LABEL);
    }
    if (asn.datacenter()) {
      return Verdict.bot(
          "datacenter:" + (asn.organization() == null ? "unknown" : asn.organization()));
    }
    return Verdict.HUMAN;
  }

  public record Verdict(boolean isBot, String botName) {
    public static final Verdict HUMAN = new Verdict(false, null);

    public static Verdict bot(String botName) {
      return new Verdict(true, botName);
    }
  }
}
