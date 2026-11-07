package com.github.j5ik2o.cqrs.es.java.rmu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

/**
 * MySQL connection settings for the Lambda handlers.
 *
 * <p>The production stack passes a Secrets Manager secret ARN instead of a plaintext password. The
 * secret is a JSON object with {@code username} and {@code password}; an optional {@code url}
 * overrides {@code SPRING_DATASOURCE_URL}, which stays in the function environment because it is
 * not a secret.
 */
record DatabaseSettings(String url, String username, String password) {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  static DatabaseSettings fromSecretJson(String secretJson, String fallbackUrl) {
    JsonNode node;
    try {
      node = MAPPER.readTree(secretJson);
    } catch (IOException e) {
      throw new IllegalStateException("database secret is not valid JSON", e);
    }
    if (node == null || !node.isObject()) {
      throw new IllegalStateException("database secret must be a JSON object");
    }
    var url = optionalText(node, "url");
    if (url == null) {
      url = fallbackUrl;
    }
    if (url == null || url.isBlank()) {
      throw new IllegalStateException(
          "database secret has no url and SPRING_DATASOURCE_URL is unset");
    }
    return new DatabaseSettings(
        url, requiredText(node, "username"), requiredText(node, "password"));
  }

  static DatabaseSettings fromEnvironment(String url, String username, String password) {
    if (url == null || url.isBlank()) {
      throw new IllegalStateException("SPRING_DATASOURCE_URL or DATABASE_URL is required");
    }
    return new DatabaseSettings(url, nullToEmpty(username), nullToEmpty(password));
  }

  private static String requiredText(JsonNode node, String field) {
    var value = optionalText(node, field);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("database secret has no " + field);
    }
    return value;
  }

  private static String optionalText(JsonNode node, String field) {
    var value = node.get(field);
    return value == null || value.isNull() ? null : value.asText();
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
