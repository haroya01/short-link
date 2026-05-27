package com.example.short_link.common.security;

import java.util.Optional;

public interface UserAccessLookup {

  boolean isAdmin(Long userId);

  Optional<String> timezone(Long userId);
}
