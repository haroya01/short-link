package com.example.short_link.link.classifier.application;

import com.example.short_link.common.geoip.GeoLocation;
import com.example.short_link.link.application.dto.UserAgentInfo;

public record ClassifiedVisit(
    Referrer referrer,
    String maskedIp,
    UserAgentInfo device,
    BotClassifier.Verdict bot,
    GeoLocation geo,
    AsnResolver.AsnInfo asn,
    String language,
    String visitorHash,
    String sourceChannel) {

  public record Referrer(String url, String host) {}
}
