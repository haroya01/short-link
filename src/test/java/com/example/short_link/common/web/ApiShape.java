package com.example.short_link.common.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

// The wire shape of the HTTP API as springdoc sees it: endpoints, their parameters and bodies, and
// every schema's property names and types. Descriptions, examples, server URLs and ordering are
// left out so the snapshot only moves when a client could notice.
final class ApiShape {

  static final String SELF = "(self)";
  static final String NO_BODY = "-";

  private static final Set<String> METHODS =
      Set.of("get", "put", "post", "delete", "patch", "head", "options");
  private static final Pattern REF = Pattern.compile("#([A-Za-z0-9_.$-]+)");
  private static final Pattern ENUM = Pattern.compile("^(.*) enum\\[([^\\]]*)\\]$");
  private static final Set<String> LOOSE_TYPES = Set.of("object", "any", "map<any>", "map<object>");

  enum Side {
    REQUEST,
    RESPONSE
  }

  record Endpoint(
      SortedMap<String, String> parameters, String request, SortedMap<String, String> responses) {}

  record Shape(
      SortedMap<String, Endpoint> endpoints, SortedMap<String, SortedMap<String, String>> schemas) {

    Map<String, Object> toJson() {
      SortedMap<String, Object> endpointsJson = new TreeMap<>();
      endpoints.forEach(
          (key, endpoint) -> {
            SortedMap<String, Object> json = new TreeMap<>();
            if (!endpoint.parameters().isEmpty()) {
              json.put("parameters", endpoint.parameters());
            }
            if (endpoint.request() != null) {
              json.put("request", endpoint.request());
            }
            json.put("responses", endpoint.responses());
            endpointsJson.put(key, json);
          });
      Map<String, Object> json = new LinkedHashMap<>();
      json.put("endpoints", endpointsJson);
      json.put("schemas", schemas);
      return json;
    }

    static Shape fromJson(JsonNode json) {
      SortedMap<String, Endpoint> endpoints = new TreeMap<>();
      for (Map.Entry<String, JsonNode> entry : json.path("endpoints").properties()) {
        JsonNode endpoint = entry.getValue();
        endpoints.put(
            entry.getKey(),
            new Endpoint(
                strings(endpoint.path("parameters")),
                endpoint.has("request") ? endpoint.get("request").asString() : null,
                strings(endpoint.path("responses"))));
      }
      SortedMap<String, SortedMap<String, String>> schemas = new TreeMap<>();
      for (Map.Entry<String, JsonNode> entry : json.path("schemas").properties()) {
        schemas.put(entry.getKey(), strings(entry.getValue()));
      }
      return new Shape(endpoints, schemas);
    }

    private static SortedMap<String, String> strings(JsonNode node) {
      SortedMap<String, String> values = new TreeMap<>();
      for (Map.Entry<String, JsonNode> entry : node.properties()) {
        values.put(entry.getKey(), entry.getValue().asString());
      }
      return values;
    }
  }

  record Change(boolean breaking, String text) {}

  record Coverage(
      int endpoints,
      int concrete,
      int primitive,
      int noBody,
      int generic,
      SortedMap<String, String> genericEndpoints) {}

  private ApiShape() {}

  static Shape of(JsonNode openApi) {
    SortedMap<String, Endpoint> endpoints = new TreeMap<>();
    for (Map.Entry<String, JsonNode> path : openApi.path("paths").properties()) {
      for (Map.Entry<String, JsonNode> operation : path.getValue().properties()) {
        if (!METHODS.contains(operation.getKey())) {
          continue;
        }
        JsonNode op = operation.getValue();
        endpoints.put(
            operation.getKey().toUpperCase() + " " + path.getKey(),
            new Endpoint(parameters(op), request(op), responses(op)));
      }
    }
    SortedMap<String, SortedMap<String, String>> schemas = new TreeMap<>();
    for (Map.Entry<String, JsonNode> schema :
        openApi.path("components").path("schemas").properties()) {
      schemas.put(schema.getKey(), properties(schema.getValue()));
    }
    return new Shape(endpoints, schemas);
  }

  private static SortedMap<String, String> parameters(JsonNode op) {
    SortedMap<String, String> parameters = new TreeMap<>();
    for (JsonNode parameter : op.path("parameters").values()) {
      parameters.put(
          parameter.path("name").asString(),
          parameter.path("in").asString()
              + " "
              + type(parameter.path("schema"))
              + (parameter.path("required").asBoolean(false) ? " required" : ""));
    }
    return parameters;
  }

  private static String request(JsonNode op) {
    if (!op.has("requestBody")) {
      return null;
    }
    JsonNode body = op.get("requestBody");
    return content(body.path("content"))
        + (body.path("required").asBoolean(false) ? " required" : "");
  }

  private static SortedMap<String, String> responses(JsonNode op) {
    SortedMap<String, String> responses = new TreeMap<>();
    for (Map.Entry<String, JsonNode> response : op.path("responses").properties()) {
      responses.put(response.getKey(), content(response.getValue().path("content")));
    }
    return responses;
  }

  private static String content(JsonNode content) {
    Set<String> types = new TreeSet<>();
    for (JsonNode media : content.values()) {
      types.add(type(media.path("schema")));
    }
    return types.isEmpty() ? NO_BODY : String.join(" | ", types);
  }

  private static SortedMap<String, String> properties(JsonNode schema) {
    SortedMap<String, String> properties = new TreeMap<>();
    if (!schema.has("properties")) {
      properties.put(SELF, type(schema));
      return properties;
    }
    Set<String> required = new HashSet<>();
    schema.path("required").values().forEach(name -> required.add(name.asString()));
    for (Map.Entry<String, JsonNode> property : schema.get("properties").properties()) {
      properties.put(
          property.getKey(),
          type(property.getValue()) + (required.contains(property.getKey()) ? " required" : ""));
    }
    return properties;
  }

  static String type(JsonNode schema) {
    String base = baseType(schema);
    if (schema.has("enum")) {
      Set<String> values = new TreeSet<>();
      schema.get("enum").values().forEach(value -> values.add(value.asString()));
      base += " enum[" + String.join("|", values) + "]";
    }
    if (schema.path("nullable").asBoolean(false) || nullInTypeList(schema)) {
      base += " nullable";
    }
    return base;
  }

  private static String baseType(JsonNode schema) {
    if (schema.has("$ref")) {
      String ref = schema.get("$ref").asString();
      return "#" + ref.substring(ref.lastIndexOf('/') + 1);
    }
    for (String composition : List.of("allOf", "oneOf", "anyOf")) {
      if (schema.has(composition)) {
        List<String> parts = new ArrayList<>();
        schema.get(composition).values().forEach(part -> parts.add(type(part)));
        return composition + "(" + String.join(", ", parts) + ")";
      }
    }
    String type = primaryType(schema);
    if ("array".equals(type)) {
      return "[" + type(schema.path("items")) + "]";
    }
    if (schema.has("properties")) {
      List<String> fields = new ArrayList<>();
      properties(schema).forEach((name, descriptor) -> fields.add(name + ": " + descriptor));
      return "{" + String.join(", ", fields) + "}";
    }
    JsonNode additional = schema.path("additionalProperties");
    if (additional.isObject()) {
      return "map<" + type(additional) + ">";
    }
    if (additional.asBoolean(false)) {
      return "map<any>";
    }
    if (type == null) {
      return "any";
    }
    String format = schema.path("format").asString("");
    return format.isEmpty() ? type : type + "(" + format + ")";
  }

  private static String primaryType(JsonNode schema) {
    JsonNode type = schema.path("type");
    if (type.isArray()) {
      for (JsonNode option : type.values()) {
        if (!"null".equals(option.asString())) {
          return option.asString();
        }
      }
      return null;
    }
    return type.isMissingNode() ? null : type.asString();
  }

  private static boolean nullInTypeList(JsonNode schema) {
    JsonNode type = schema.path("type");
    return type.isArray()
        && type.values().stream().anyMatch(option -> "null".equals(option.asString()));
  }

  // A descriptor is "<type>[ enum[a|b]][ nullable][ required]".
  record Descriptor(String type, Set<String> enumValues, boolean nullable, boolean required) {

    static Descriptor parse(String text) {
      String rest = text;
      boolean required = rest.endsWith(" required");
      if (required) {
        rest = rest.substring(0, rest.length() - " required".length());
      }
      boolean nullable = rest.endsWith(" nullable");
      if (nullable) {
        rest = rest.substring(0, rest.length() - " nullable".length());
      }
      Set<String> values = new TreeSet<>();
      Matcher matcher = ENUM.matcher(rest);
      if (matcher.matches()) {
        rest = matcher.group(1);
        if (!matcher.group(2).isEmpty()) {
          values.addAll(List.of(matcher.group(2).split("\\|")));
        }
      }
      return new Descriptor(rest, values, nullable, required);
    }
  }

  static List<Change> diff(Shape before, Shape after) {
    return new Differ(before, after).run();
  }

  static String report(List<Change> changes) {
    StringBuilder text = new StringBuilder();
    List<String> breaking = changes.stream().filter(Change::breaking).map(Change::text).toList();
    List<String> additive =
        changes.stream().filter(change -> !change.breaking()).map(Change::text).toList();
    if (!breaking.isEmpty()) {
      text.append("BREAKING (")
          .append(breaking.size())
          .append(") — existing clients may stop decoding or sending this:\n");
      breaking.forEach(line -> text.append("  ! ").append(line).append('\n'));
    }
    if (!additive.isEmpty()) {
      text.append("ADDITIVE (").append(additive.size()).append(") — old clients keep working:\n");
      additive.forEach(line -> text.append("  + ").append(line).append('\n'));
    }
    return text.toString();
  }

  static Coverage coverage(Shape shape) {
    int concrete = 0;
    int primitive = 0;
    int noBody = 0;
    SortedMap<String, String> generic = new TreeMap<>();
    for (Map.Entry<String, Endpoint> entry : shape.endpoints().entrySet()) {
      String body = successBody(entry.getValue().responses());
      switch (kind(shape, body, new HashSet<>())) {
        case CONCRETE -> concrete++;
        case PRIMITIVE -> primitive++;
        case NO_BODY -> noBody++;
        case GENERIC -> generic.put(entry.getKey(), body);
      }
    }
    return new Coverage(
        shape.endpoints().size(), concrete, primitive, noBody, generic.size(), generic);
  }

  private enum Kind {
    CONCRETE,
    PRIMITIVE,
    NO_BODY,
    GENERIC
  }

  private static String successBody(SortedMap<String, String> responses) {
    for (Map.Entry<String, String> response : responses.entrySet()) {
      if (response.getKey().startsWith("2")) {
        return response.getValue();
      }
    }
    return responses.isEmpty() ? NO_BODY : responses.get(responses.firstKey());
  }

  private static Kind kind(Shape shape, String descriptor, Set<String> seen) {
    if (NO_BODY.equals(descriptor)) {
      return Kind.NO_BODY;
    }
    String type = Descriptor.parse(descriptor).type();
    while (type.startsWith("[") && type.endsWith("]")) {
      type = type.substring(1, type.length() - 1);
      type = Descriptor.parse(type).type();
    }
    if (type.startsWith("map<") && type.endsWith(">")) {
      Kind values = kind(shape, type.substring(4, type.length() - 1), seen);
      return values == Kind.CONCRETE ? Kind.CONCRETE : Kind.GENERIC;
    }
    if (type.startsWith("{")) {
      return Kind.CONCRETE;
    }
    if (type.startsWith("#")) {
      String name = type.substring(1);
      SortedMap<String, String> schema = shape.schemas().get(name);
      if (schema == null || !seen.add(name)) {
        return Kind.GENERIC;
      }
      if (schema.containsKey(SELF)) {
        return kind(shape, schema.get(SELF), seen);
      }
      return schema.isEmpty() ? Kind.GENERIC : Kind.CONCRETE;
    }
    if (type.startsWith("allOf(") || type.startsWith("oneOf(") || type.startsWith("anyOf(")) {
      return type.contains("#") ? Kind.CONCRETE : Kind.GENERIC;
    }
    if (type.startsWith("string")
        || type.startsWith("integer")
        || type.startsWith("number")
        || type.startsWith("boolean")) {
      return Kind.PRIMITIVE;
    }
    return Kind.GENERIC;
  }

  private static final class Differ {

    private final Shape before;
    private final Shape after;
    private final Map<String, EnumSet<Side>> sidesBefore;
    private final Map<String, EnumSet<Side>> sidesAfter;
    private final Map<String, Set<String>> usersBefore;
    private final Map<String, Set<String>> usersAfter;
    private final List<Change> changes = new ArrayList<>();
    private final Set<String> compared = new HashSet<>();

    Differ(Shape before, Shape after) {
      this.before = before;
      this.after = after;
      this.sidesBefore = new HashMap<>();
      this.sidesAfter = new HashMap<>();
      this.usersBefore = new HashMap<>();
      this.usersAfter = new HashMap<>();
      reach(before, sidesBefore, usersBefore);
      reach(after, sidesAfter, usersAfter);
    }

    List<Change> run() {
      endpoints();
      for (String name : after.schemas().keySet()) {
        if (before.schemas().containsKey(name)) {
          schemas(name, name);
        }
      }
      for (String name : after.schemas().keySet()) {
        if (!before.schemas().containsKey(name) && !compared.contains("?>" + name)) {
          changes.add(new Change(false, "#" + name + ": new schema"));
        }
      }
      for (String name : before.schemas().keySet()) {
        if (!after.schemas().containsKey(name) && !compared.contains(name + ">?")) {
          changes.add(new Change(false, "#" + name + ": schema no longer used"));
        }
      }
      return changes;
    }

    private void endpoints() {
      for (Map.Entry<String, Endpoint> entry : before.endpoints().entrySet()) {
        Endpoint now = after.endpoints().get(entry.getKey());
        if (now == null) {
          changes.add(new Change(true, entry.getKey() + ": endpoint removed"));
          continue;
        }
        endpoint(entry.getKey(), entry.getValue(), now);
      }
      for (String key : after.endpoints().keySet()) {
        if (!before.endpoints().containsKey(key)) {
          changes.add(new Change(false, key + ": new endpoint"));
        }
      }
    }

    private void endpoint(String key, Endpoint was, Endpoint now) {
      for (Map.Entry<String, String> parameter : was.parameters().entrySet()) {
        String current = now.parameters().get(parameter.getKey());
        if (current == null) {
          changes.add(
              new Change(true, key + " parameter " + parameter.getKey() + ": removed or renamed"));
        } else {
          value(
              key + " parameter " + parameter.getKey(),
              EnumSet.of(Side.REQUEST),
              parameter.getValue(),
              current);
        }
      }
      for (Map.Entry<String, String> parameter : now.parameters().entrySet()) {
        if (!was.parameters().containsKey(parameter.getKey())) {
          boolean required = Descriptor.parse(parameter.getValue()).required();
          changes.add(
              new Change(
                  required,
                  key
                      + " parameter "
                      + parameter.getKey()
                      + ": new "
                      + (required ? "required " : "")
                      + "parameter ("
                      + parameter.getValue()
                      + ")"));
        }
      }
      if (was.request() == null && now.request() != null) {
        changes.add(
            new Change(
                Descriptor.parse(now.request()).required(),
                key + " request: new body (" + now.request() + ")"));
      } else if (was.request() != null && now.request() == null) {
        changes.add(new Change(true, key + " request: body removed"));
      } else if (was.request() != null) {
        value(key + " request", EnumSet.of(Side.REQUEST), was.request(), now.request());
      }
      for (Map.Entry<String, String> response : was.responses().entrySet()) {
        String current = now.responses().get(response.getKey());
        if (current == null) {
          changes.add(new Change(true, key + " " + response.getKey() + ": response removed"));
        } else {
          value(
              key + " " + response.getKey(),
              EnumSet.of(Side.RESPONSE),
              response.getValue(),
              current);
        }
      }
      for (String code : now.responses().keySet()) {
        if (!was.responses().containsKey(code)) {
          changes.add(new Change(false, key + " " + code + ": new response"));
        }
      }
    }

    private void schemas(String wasName, String nowName) {
      if (!compared.add(wasName + ">" + nowName)) {
        return;
      }
      compared.add(wasName + ">?");
      compared.add("?>" + nowName);
      SortedMap<String, String> was = before.schemas().get(wasName);
      SortedMap<String, String> now = after.schemas().get(nowName);
      String label = wasName.equals(nowName) ? "#" + nowName : "#" + wasName + " → #" + nowName;
      EnumSet<Side> sides = EnumSet.noneOf(Side.class);
      sides.addAll(sidesBefore.getOrDefault(wasName, EnumSet.noneOf(Side.class)));
      sides.addAll(sidesAfter.getOrDefault(nowName, EnumSet.noneOf(Side.class)));
      String users = users(usersAfter.getOrDefault(nowName, usersBefore.get(wasName)));
      List<String> removed = new ArrayList<>();
      List<String> added = new ArrayList<>();
      for (Map.Entry<String, String> property : was.entrySet()) {
        String current = now.get(property.getKey());
        if (current == null) {
          removed.add(property.getKey());
        } else {
          value(
              label + (SELF.equals(property.getKey()) ? "" : "." + property.getKey()) + users,
              sides,
              property.getValue(),
              current);
        }
      }
      for (String name : now.keySet()) {
        if (!was.containsKey(name)) {
          added.add(name);
        }
      }
      for (String name : removed) {
        String hint =
            removed.size() == 1 && added.size() == 1 ? " (renamed to " + added.get(0) + "?)" : "";
        changes.add(new Change(true, label + "." + name + users + ": property removed" + hint));
      }
      for (String name : added) {
        Descriptor descriptor = Descriptor.parse(now.get(name));
        boolean breaking = sides.contains(Side.REQUEST) && descriptor.required();
        changes.add(
            new Change(
                breaking, label + "." + name + users + ": new property (" + now.get(name) + ")"));
      }
    }

    private void value(String where, EnumSet<Side> sides, String was, String now) {
      if (was.equals(now)) {
        return;
      }
      Descriptor a = Descriptor.parse(was);
      Descriptor b = Descriptor.parse(now);
      if (NO_BODY.equals(a.type()) || NO_BODY.equals(b.type())) {
        changes.add(
            NO_BODY.equals(a.type())
                ? new Change(false, where + ": now has a body (" + now + ")")
                : new Change(true, where + ": body removed (was " + was + ")"));
        return;
      }
      if (!a.type().equals(b.type()) && LOOSE_TYPES.contains(a.type())) {
        changes.add(new Change(false, where + ": now typed " + a.type() + " → " + b.type()));
      } else if (!a.type().equals(b.type())) {
        List<String[]> renames = refsRenamed(a.type(), b.type());
        if (renames == null) {
          changes.add(new Change(true, where + ": type " + a.type() + " → " + b.type()));
        } else {
          for (String[] rename : renames) {
            changes.add(
                new Change(false, where + ": schema renamed #" + rename[0] + " → #" + rename[1]));
            schemas(rename[0], rename[1]);
          }
        }
      }
      boolean response = sides.contains(Side.RESPONSE);
      boolean request = sides.contains(Side.REQUEST);
      if (a.required() && !b.required()) {
        changes.add(new Change(response, where + ": required → optional"));
      } else if (!a.required() && b.required()) {
        changes.add(new Change(request, where + ": optional → required"));
      }
      if (!a.nullable() && b.nullable()) {
        changes.add(new Change(response, where + ": now nullable"));
      } else if (a.nullable() && !b.nullable()) {
        changes.add(new Change(request, where + ": no longer nullable"));
      }
      Set<String> gone = new TreeSet<>(a.enumValues());
      gone.removeAll(b.enumValues());
      Set<String> fresh = new TreeSet<>(b.enumValues());
      fresh.removeAll(a.enumValues());
      if (!gone.isEmpty()) {
        changes.add(new Change(request, where + ": enum values removed " + gone));
      }
      if (!fresh.isEmpty()) {
        changes.add(new Change(response, where + ": enum values added " + fresh));
      }
    }

    // The same structure where only schema names differ, each old name gone and each new one new,
    // is a rename of the schema rather than a change of type.
    private List<String[]> refsRenamed(String was, String now) {
      Matcher a = REF.matcher(was);
      Matcher b = REF.matcher(now);
      if (!a.replaceAll("#").equals(b.replaceAll("#"))) {
        return null;
      }
      List<String[]> renames = new ArrayList<>();
      a.reset();
      b.reset();
      while (a.find() && b.find()) {
        String from = a.group(1);
        String to = b.group(1);
        if (from.equals(to)) {
          continue;
        }
        if (after.schemas().containsKey(from)
            || !before.schemas().containsKey(from)
            || before.schemas().containsKey(to)
            || !after.schemas().containsKey(to)) {
          return null;
        }
        renames.add(new String[] {from, to});
      }
      return renames;
    }

    private static String users(Collection<String> endpoints) {
      if (endpoints == null || endpoints.isEmpty()) {
        return "";
      }
      List<String> sorted = new ArrayList<>(new TreeSet<>(endpoints));
      String shown = String.join(", ", sorted.subList(0, Math.min(2, sorted.size())));
      return " [" + shown + (sorted.size() > 2 ? " +" + (sorted.size() - 2) : "") + "]";
    }

    private static void reach(
        Shape shape, Map<String, EnumSet<Side>> sides, Map<String, Set<String>> users) {
      for (Map.Entry<String, Endpoint> entry : shape.endpoints().entrySet()) {
        Endpoint endpoint = entry.getValue();
        List<String> requestTypes = new ArrayList<>(endpoint.parameters().values());
        if (endpoint.request() != null) {
          requestTypes.add(endpoint.request());
        }
        for (String type : requestTypes) {
          walk(shape, type, Side.REQUEST, entry.getKey(), sides, users, new HashSet<>());
        }
        for (String type : endpoint.responses().values()) {
          walk(shape, type, Side.RESPONSE, entry.getKey(), sides, users, new HashSet<>());
        }
      }
    }

    private static void walk(
        Shape shape,
        String descriptor,
        Side side,
        String endpoint,
        Map<String, EnumSet<Side>> sides,
        Map<String, Set<String>> users,
        Set<String> seen) {
      Matcher refs = REF.matcher(descriptor);
      while (refs.find()) {
        String name = refs.group(1);
        if (!seen.add(name)) {
          continue;
        }
        sides.computeIfAbsent(name, ignored -> EnumSet.noneOf(Side.class)).add(side);
        users.computeIfAbsent(name, ignored -> new HashSet<>()).add(endpoint);
        SortedMap<String, String> schema = shape.schemas().get(name);
        if (schema != null) {
          for (String property : schema.values()) {
            walk(shape, property, side, endpoint, sides, users, seen);
          }
        }
      }
    }
  }
}
