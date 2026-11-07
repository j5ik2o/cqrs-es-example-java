package com.github.j5ik2o.cqrs.es.java.rmu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class DatabaseSettingsTest {
  private static final String TEST_USER = "unit-test-user";
  private static final String TEST_VALUE = "unit-test-value";

  @Test
  void readsCredentialsAndUrlFromTheSecret() {
    var settings =
        DatabaseSettings.fromSecretJson(
            secretJson("jdbc:mysql://secret:3306/ceer", TEST_USER, TEST_VALUE),
            "jdbc:mysql://env:3306/ceer");

    assertEquals("jdbc:mysql://secret:3306/ceer", settings.url());
    assertEquals(TEST_USER, settings.username());
    assertEquals(TEST_VALUE, settings.password());
  }

  @Test
  void fallsBackToTheEnvironmentUrlWhenTheSecretHasNoUrl() {
    var settings =
        DatabaseSettings.fromSecretJson(
            secretJson(null, TEST_USER, TEST_VALUE), "jdbc:mysql://env:3306/ceer");

    assertEquals("jdbc:mysql://env:3306/ceer", settings.url());
  }

  @Test
  void rejectsASecretWithoutAUsername() {
    var failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                DatabaseSettings.fromSecretJson(
                    secretJson(null, null, TEST_VALUE), "jdbc:mysql://env:3306/ceer"));

    assertEquals("database secret has no username", failure.getMessage());
  }

  @Test
  void rejectsASecretWithoutAPassword() {
    var failure =
        assertThrows(
            IllegalStateException.class,
            () ->
                DatabaseSettings.fromSecretJson(
                    secretJson(null, TEST_USER, null), "jdbc:mysql://env:3306/ceer"));

    assertEquals("database secret has no password", failure.getMessage());
  }

  @Test
  void rejectsASecretWithoutAUrlAndAFallback() {
    assertThrows(
        IllegalStateException.class,
        () -> DatabaseSettings.fromSecretJson(secretJson(null, TEST_USER, TEST_VALUE), null));
  }

  @Test
  void rejectsNonJsonSecretValues() {
    assertThrows(
        IllegalStateException.class,
        () -> DatabaseSettings.fromSecretJson("not-json", "jdbc:mysql://env:3306/ceer"));
  }

  @Test
  void keepsTheEnvironmentPathForLocalRuns() {
    var settings =
        DatabaseSettings.fromEnvironment("jdbc:mysql://localhost:3306/ceer", "root", "root");

    assertEquals("jdbc:mysql://localhost:3306/ceer", settings.url());
    assertEquals("root", settings.username());
    assertEquals("root", settings.password());
  }

  @Test
  void rejectsAnEnvironmentWithoutAUrl() {
    assertThrows(
        IllegalStateException.class, () -> DatabaseSettings.fromEnvironment(null, "root", "root"));
  }

  private static String secretJson(String url, String username, String password) {
    var fields = new ArrayList<String>();
    if (url != null) {
      fields.add("\"url\":\"" + url + "\"");
    }
    if (username != null) {
      fields.add("\"username\":\"" + username + "\"");
    }
    if (password != null) {
      fields.add("\"password\":\"" + password + "\"");
    }
    return "{" + String.join(",", fields) + "}";
  }
}
