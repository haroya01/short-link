package com.example.short_link.link.redirect.application;

import java.time.Instant;

public final class RedirectDecision {

  private RedirectDecision() {}

  public sealed interface Live {}

  public sealed interface Expired {}

  public record NotOnThisHost() implements Live, Expired {}

  public record DestinationBlocked() implements Live, Expired {}

  public record Preview(PreviewScope scope, String crawler) implements Live {}

  public record NotYetOpen(Instant opensAt) implements Live {}

  public record PasswordPrompt() implements Live {}

  public record LimitedCrawler(String crawler) implements Live {}

  public record Visit() implements Live {}

  public record AfterExpiry(String url) implements Expired {}

  public record ExpiredMessage(String message) implements Expired {}

  public record Unhandled() implements Expired {}
}
