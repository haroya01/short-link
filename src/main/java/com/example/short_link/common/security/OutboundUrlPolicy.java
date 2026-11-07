package com.example.short_link.common.security;

public interface OutboundUrlPolicy {

  boolean allows(String url);
}
