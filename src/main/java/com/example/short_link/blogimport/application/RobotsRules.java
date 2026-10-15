package com.example.short_link.blogimport.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// RFC 9309: the groups naming this agent's product token apply, otherwise the "*" groups; among
// matching rules the longest pattern wins and allow wins a tie; "*" matches any run of characters
// and a trailing "$" anchors the end. With namedOnly the "*" groups are not consulted.
public final class RobotsRules {

  private record Rule(boolean allow, String pattern) {}

  private record Group(List<String> agents, List<Rule> rules) {}

  private final List<Group> groups;

  private RobotsRules(List<Group> groups) {
    this.groups = groups;
  }

  public static RobotsRules parse(String text) {
    List<Group> groups = new ArrayList<>();
    List<String> agents = new ArrayList<>();
    List<Rule> rules = new ArrayList<>();
    boolean inRules = false;
    for (String raw : (text == null ? "" : text).split("\r?\n|\r")) {
      String line = raw.contains("#") ? raw.substring(0, raw.indexOf('#')) : raw;
      int colon = line.indexOf(':');
      if (colon < 0) {
        continue;
      }
      String key = line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
      String value = line.substring(colon + 1).strip();
      if (key.equals("user-agent")) {
        if (inRules) {
          groups.add(new Group(agents, rules));
          agents = new ArrayList<>();
          rules = new ArrayList<>();
          inRules = false;
        }
        agents.add(value.toLowerCase(Locale.ROOT));
      } else if (key.equals("allow") || key.equals("disallow")) {
        if (agents.isEmpty()) {
          continue;
        }
        inRules = true;
        if (!value.isEmpty()) {
          rules.add(new Rule(key.equals("allow"), value));
        }
      }
    }
    if (!agents.isEmpty()) {
      groups.add(new Group(agents, rules));
    }
    return new RobotsRules(groups);
  }

  public boolean allows(String agentToken, String pathAndQuery, boolean namedOnly) {
    String token = agentToken.toLowerCase(Locale.ROOT);
    List<Rule> applicable = rulesFor(token);
    if (applicable == null && !namedOnly) {
      applicable = rulesFor("*");
    }
    if (applicable == null) {
      return true;
    }
    String path = pathAndQuery == null || pathAndQuery.isEmpty() ? "/" : pathAndQuery;
    Rule best = null;
    for (Rule rule : applicable) {
      if (!matches(rule.pattern(), path)) {
        continue;
      }
      if (best == null
          || rule.pattern().length() > best.pattern().length()
          || (rule.pattern().length() == best.pattern().length() && rule.allow())) {
        best = rule;
      }
    }
    return best == null || best.allow();
  }

  private List<Rule> rulesFor(String token) {
    List<Rule> combined = null;
    for (Group group : groups) {
      if (group.agents().contains(token)) {
        if (combined == null) {
          combined = new ArrayList<>();
        }
        combined.addAll(group.rules());
      }
    }
    return combined;
  }

  static boolean matches(String pattern, String path) {
    boolean anchored = pattern.endsWith("$");
    String body = anchored ? pattern.substring(0, pattern.length() - 1) : pattern;
    return matchFrom(body, 0, path, 0, anchored);
  }

  private static boolean matchFrom(String p, int pi, String s, int si, boolean anchored) {
    while (pi < p.length()) {
      char c = p.charAt(pi);
      if (c == '*') {
        for (int k = si; k <= s.length(); k++) {
          if (matchFrom(p, pi + 1, s, k, anchored)) {
            return true;
          }
        }
        return false;
      }
      if (si >= s.length() || s.charAt(si) != c) {
        return false;
      }
      pi++;
      si++;
    }
    return !anchored || si == s.length();
  }
}
