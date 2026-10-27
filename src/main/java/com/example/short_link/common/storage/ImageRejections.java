package com.example.short_link.common.storage;

import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public record ImageRejections(
    Supplier<RuntimeException> unconfigured,
    Function<Set<String>, RuntimeException> unsupportedType,
    Supplier<RuntimeException> notOwned,
    Supplier<RuntimeException> missing,
    BiFunction<Long, Long, RuntimeException> oversized) {}
