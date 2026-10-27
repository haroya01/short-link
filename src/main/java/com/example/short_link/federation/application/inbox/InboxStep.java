package com.example.short_link.federation.application.inbox;

import java.util.function.Function;

sealed interface InboxStep<T> {

  record Next<T>(T value) implements InboxStep<T> {}

  record Done<T>(InboxOutcome outcome) implements InboxStep<T> {}

  static <T> InboxStep<T> next(T value) {
    return new Next<>(value);
  }

  static <T> InboxStep<T> done(InboxOutcome outcome) {
    return new Done<>(outcome);
  }

  default InboxOutcome then(Function<T, InboxOutcome> step) {
    return switch (this) {
      case Next<T> next -> step.apply(next.value());
      case Done<T> done -> done.outcome();
    };
  }
}
