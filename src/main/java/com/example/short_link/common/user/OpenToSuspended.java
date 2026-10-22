package com.example.short_link.common.user;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// A POST, PUT, PATCH or DELETE handler that a banned or suspended account may still call because it
// writes nothing others see. Every other such handler in SuspendedWriteGate's modules is refused.
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OpenToSuspended {}
