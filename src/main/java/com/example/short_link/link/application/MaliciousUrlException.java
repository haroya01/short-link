package com.example.short_link.link.application;

public class MaliciousUrlException extends RuntimeException {

  public MaliciousUrlException(String url) {
    super("malicious url rejected: " + url);
  }
}
