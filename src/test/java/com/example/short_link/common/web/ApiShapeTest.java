package com.example.short_link.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.short_link.common.web.ApiShape.Change;
import com.example.short_link.common.web.ApiShape.Coverage;
import com.example.short_link.common.web.ApiShape.Shape;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ApiShapeTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static final String DOCS =
      """
      {
        "openapi": "3.0.1",
        "info": {"title": "kurl API", "description": "words"},
        "servers": [{"url": "http://localhost:8080"}],
        "paths": {
          "/api/v1/links/{id}": {
            "get": {
              "summary": "Read a link",
              "parameters": [
                {"name": "id", "in": "path", "required": true,
                 "schema": {"type": "integer", "format": "int64"}},
                {"name": "fields", "in": "query", "schema": {"type": "string"}}
              ],
              "responses": {
                "200": {"description": "OK",
                  "content": {"*/*": {"schema": {"$ref": "#/components/schemas/LinkResponse"}}}},
                "404": {"description": "Missing"}
              }
            },
            "delete": {"responses": {"204": {"description": "Gone"}}}
          },
          "/api/v1/links": {
            "post": {
              "requestBody": {"required": true, "content": {"application/json":
                {"schema": {"$ref": "#/components/schemas/CreateLinkRequest"}}}},
              "responses": {"200": {"content": {"*/*":
                {"schema": {"$ref": "#/components/schemas/LinkResponse"}}}}}
            }
          },
          "/api/v1/stats": {
            "get": {"responses": {"200": {"content": {"*/*":
              {"schema": {"type": "object", "additionalProperties": {"type": "object"}}}}}}}
          }
        },
        "components": {
          "schemas": {
            "LinkResponse": {
              "type": "object",
              "required": ["id", "shortCode"],
              "properties": {
                "shortCode": {"type": "string", "example": "abc1234"},
                "id": {"type": "integer", "format": "int64", "description": "row id"},
                "status": {"type": "string", "enum": ["PAUSED", "ACTIVE"]},
                "tags": {"type": "array", "items": {"type": "string"}},
                "owner": {"$ref": "#/components/schemas/OwnerView"}
              }
            },
            "OwnerView": {"type": "object", "properties": {"username": {"type": "string"}}},
            "CreateLinkRequest": {
              "type": "object",
              "required": ["url"],
              "properties": {"url": {"type": "string"}, "note": {"type": "string"}}
            }
          },
          "securitySchemes": {"bearerAuth": {"type": "http", "scheme": "bearer"}}
        }
      }
      """;

  private static Shape shape(String docs) {
    return ApiShape.of(JSON.readTree(docs));
  }

  private static List<String> breaking(List<Change> changes) {
    return changes.stream().filter(Change::breaking).map(Change::text).toList();
  }

  private static List<String> additive(List<Change> changes) {
    return changes.stream().filter(change -> !change.breaking()).map(Change::text).toList();
  }

  @Test
  void theShapeKeepsNamesTypesRequiredAndEnumsAndDropsTheWords() {
    String rendered = JSON.writeValueAsString(shape(DOCS).toJson());
    JsonNode json = JSON.readTree(rendered);

    assertThat(json.path("schemas").path("LinkResponse").path("id").asString())
        .isEqualTo("integer(int64) required");
    assertThat(json.path("schemas").path("LinkResponse").path("status").asString())
        .isEqualTo("string enum[ACTIVE|PAUSED]");
    assertThat(json.path("schemas").path("LinkResponse").path("tags").asString())
        .isEqualTo("[string]");
    assertThat(json.path("schemas").path("LinkResponse").path("owner").asString())
        .isEqualTo("#OwnerView");
    assertThat(json.path("endpoints").path("GET /api/v1/links/{id}").path("parameters"))
        .hasToString("{\"fields\":\"query string\",\"id\":\"path integer(int64) required\"}");
    assertThat(json.path("endpoints").path("GET /api/v1/links/{id}").path("responses"))
        .hasToString("{\"200\":\"#LinkResponse\",\"404\":\"-\"}");
    assertThat(json.path("endpoints").path("POST /api/v1/links").path("request").asString())
        .isEqualTo("#CreateLinkRequest required");
    assertThat(rendered)
        .doesNotContain("abc1234", "row id", "localhost", "Read a link", "bearerAuth");
  }

  @Test
  void theSameDocumentInAnotherOrderHasTheSameShape() {
    String reordered =
        DOCS.replace(
                "\"shortCode\": {\"type\": \"string\", \"example\": \"abc1234\"},\n"
                    + "                \"id\": {\"type\": \"integer\", \"format\": \"int64\","
                    + " \"description\": \"row id\"},",
                "\"id\": {\"type\": \"integer\", \"format\": \"int64\"},\n"
                    + "                \"shortCode\": {\"type\": \"string\"},")
            .replace("[\"PAUSED\", \"ACTIVE\"]", "[\"ACTIVE\", \"PAUSED\"]");

    assertThat(reordered).isNotEqualTo(DOCS);
    assertThat(ApiShape.diff(shape(DOCS), shape(reordered))).isEmpty();
    assertThat(JSON.writeValueAsString(shape(reordered).toJson()))
        .isEqualTo(JSON.writeValueAsString(shape(DOCS).toJson()));
  }

  @Test
  void aRenamedResponseFieldIsBreakingAndNamesTheEndpointsThatReturnIt() {
    List<Change> changes =
        ApiShape.diff(shape(DOCS), shape(DOCS.replace("\"shortCode\"", "\"code\"")));

    assertThat(breaking(changes))
        .contains(
            "#LinkResponse.shortCode [GET /api/v1/links/{id}, POST /api/v1/links]:"
                + " property removed (renamed to code?)");
    assertThat(additive(changes))
        .contains(
            "#LinkResponse.code [GET /api/v1/links/{id}, POST /api/v1/links]:"
                + " new property (string required)");
  }

  @Test
  void aNewResponseFieldIsOnlyAdditive() {
    String added =
        DOCS.replace(
            "\"owner\": {\"$ref\": \"#/components/schemas/OwnerView\"}",
            "\"owner\": {\"$ref\": \"#/components/schemas/OwnerView\"},"
                + " \"clicks\": {\"type\": \"integer\", \"format\": \"int64\"}");

    List<Change> changes = ApiShape.diff(shape(DOCS), shape(added));

    assertThat(breaking(changes)).isEmpty();
    assertThat(additive(changes))
        .containsExactly(
            "#LinkResponse.clicks [GET /api/v1/links/{id}, POST /api/v1/links]:"
                + " new property (integer(int64))");
  }

  @Test
  void regeneratingTheSnapshotClearsTheDifference() {
    Shape changed = shape(DOCS.replace("\"shortCode\"", "\"code\""));
    Shape regenerated = Shape.fromJson(JSON.readTree(JSON.writeValueAsString(changed.toJson())));

    assertThat(ApiShape.diff(regenerated, changed)).isEmpty();
  }

  @Test
  void typeChangesRemovedEndpointsAndLooserResponsesBreakClients() {
    String changed =
        DOCS.replace(
                "\"format\": \"int64\", \"description\"", "\"format\": \"int32\", \"description\"")
            .replace("\"required\": [\"id\", \"shortCode\"]", "\"required\": [\"id\"]")
            .replace(
                "\"delete\": {\"responses\": {\"204\": {\"description\": \"Gone\"}}}",
                "\"put\": {\"responses\": {\"204\": {}}}");

    List<String> breaking = breaking(ApiShape.diff(shape(DOCS), shape(changed)));

    assertThat(breaking)
        .contains(
            "#LinkResponse.id [GET /api/v1/links/{id}, POST /api/v1/links]:"
                + " type integer(int64) → integer(int32)",
            "#LinkResponse.shortCode [GET /api/v1/links/{id}, POST /api/v1/links]:"
                + " required → optional",
            "DELETE /api/v1/links/{id}: endpoint removed");
  }

  @Test
  void aRequestThatDemandsMoreBreaksOldClientsButAcceptingMoreDoesNot() {
    String stricter =
        DOCS.replace("\"required\": [\"url\"]", "\"required\": [\"url\", \"note\"]")
            .replace(
                "\"note\": {\"type\": \"string\"}}",
                "\"note\": {\"type\": \"string\"}, \"tags\": {\"type\": \"array\","
                    + " \"items\": {\"type\": \"string\"}}}");

    List<Change> changes = ApiShape.diff(shape(DOCS), shape(stricter));

    assertThat(breaking(changes))
        .containsExactly("#CreateLinkRequest.note [POST /api/v1/links]: optional → required");
    assertThat(additive(changes))
        .containsExactly("#CreateLinkRequest.tags [POST /api/v1/links]: new property ([string])");
  }

  @Test
  void aNewEnumValueInAResponseIsBreakingBecauseStrictDecodersRejectIt() {
    List<Change> changes =
        ApiShape.diff(
            shape(DOCS),
            shape(
                DOCS.replace("[\"PAUSED\", \"ACTIVE\"]", "[\"PAUSED\", \"ACTIVE\", \"EXPIRED\"]")));

    assertThat(breaking(changes))
        .containsExactly(
            "#LinkResponse.status [GET /api/v1/links/{id}, POST /api/v1/links]:"
                + " enum values added [EXPIRED]");
  }

  @Test
  void renamingARecordWithTheSameFieldsIsNotBreaking() {
    String renamed = DOCS.replace("OwnerView", "OwnerSummary");

    List<Change> changes = ApiShape.diff(shape(DOCS), shape(renamed));

    assertThat(breaking(changes)).isEmpty();
    assertThat(additive(changes))
        .containsExactly(
            "#LinkResponse.owner [GET /api/v1/links/{id}, POST /api/v1/links]:"
                + " schema renamed #OwnerView → #OwnerSummary");
  }

  @Test
  void aNewEndpointIsAdditive() {
    String added =
        DOCS.replace(
            "\"/api/v1/stats\": {",
            "\"/api/v1/health\": {\"get\": {\"responses\": {\"200\": {}}}},\n"
                + "          \"/api/v1/stats\": {");

    List<Change> changes = ApiShape.diff(shape(DOCS), shape(added));

    assertThat(breaking(changes)).isEmpty();
    assertThat(additive(changes)).containsExactly("GET /api/v1/health: new endpoint");
  }

  @Test
  void coverageCountsConcreteResponsesAndListsTheGenericOnes() {
    Coverage coverage = ApiShape.coverage(shape(DOCS));

    assertThat(coverage.endpoints()).isEqualTo(4);
    assertThat(coverage.concrete()).isEqualTo(2);
    assertThat(coverage.noBody()).isEqualTo(1);
    assertThat(coverage.genericEndpoints()).containsOnlyKeys("GET /api/v1/stats");
  }
}
