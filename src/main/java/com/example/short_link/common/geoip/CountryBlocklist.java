package com.example.short_link.common.geoip;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record CountryBlocklist(Set<String> countryCodes) {

  private static final Set<String> KNOWN_CODES =
      Stream.concat(
              Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA2).stream(), Stream.of("XK"))
          .collect(Collectors.toUnmodifiableSet());

  public static CountryBlocklist fromCsv(String csv) {
    return new CountryBlocklist(parse(csv));
  }

  public static String normalize(String csv) {
    Set<String> countryCodes = parse(csv);
    return countryCodes.isEmpty() ? null : String.join(",", countryCodes);
  }

  public static boolean isCountryCode(String code) {
    return code != null && KNOWN_CODES.contains(code.trim().toUpperCase(Locale.ROOT));
  }

  public boolean contains(String countryCode) {
    if (countryCode == null) {
      return false;
    }
    return countryCodes.contains(countryCode.toUpperCase(Locale.ROOT));
  }

  public List<String> codes() {
    return List.copyOf(countryCodes);
  }

  private static Set<String> parse(String csv) {
    if (csv == null || csv.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(csv.split(","))
        .map(String::trim)
        .map(code -> code.toUpperCase(Locale.ROOT))
        .filter(code -> code.length() == 2)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
