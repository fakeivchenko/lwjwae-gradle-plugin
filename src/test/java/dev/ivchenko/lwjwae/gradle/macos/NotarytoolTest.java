package dev.ivchenko.lwjwae.gradle.macos;

import java.io.File;
import java.util.List;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class NotarytoolTest {
  private static final File APP = new File("Demo.app");

  @Test
  void signsWithTimeStampAndHardenedRuntime() {
    Assertions.assertEquals(
        List.of(
            "--force",
            "--timestamp",
            "--options",
            "runtime",
            "--entitlements",
            new File("app.entitlements").getAbsolutePath(),
            "--sign",
            "Developer ID",
            APP.getAbsolutePath()),
        Notarytool.codesignArguments("Developer ID", true, new File("app.entitlements"), APP));
    Assertions.assertEquals(
        List.of("--force", "--timestamp", "--sign", "Developer ID", APP.getAbsolutePath()),
        Notarytool.codesignArguments("Developer ID", false, null, APP));
  }

  @Test
  void submitsWithTheFirstWayToSignInThatIsSet() {
    String zip = APP.getAbsolutePath();
    Assertions.assertEquals(
        List.of("notarytool", "submit", zip, "--wait", "--keychain-profile", "notary"),
        Notarytool.submitArguments(
            new NotaryCredentials("notary", null, null, null, "me@example.com", "TEAM", "pw"),
            APP));
    Assertions.assertEquals(
        List.of(
            "notarytool",
            "submit",
            zip,
            "--wait",
            "--key",
            new File("AuthKey.p8").getAbsolutePath(),
            "--key-id",
            "KEY",
            "--issuer",
            "ISSUER"),
        Notarytool.submitArguments(
            new NotaryCredentials(null, new File("AuthKey.p8"), "KEY", "ISSUER", null, null, null),
            APP));
    Assertions.assertEquals(
        List.of(
            "notarytool",
            "submit",
            zip,
            "--wait",
            "--apple-id",
            "me@example.com",
            "--team-id",
            "TEAM",
            "--password",
            "pw"),
        Notarytool.submitArguments(
            new NotaryCredentials(null, null, null, null, "me@example.com", "TEAM", "pw"), APP));
  }

  @Test
  void incompleteWaysToSignInAreRefused() {
    Assertions.assertFalse(new NotaryCredentials(null, null, null, null, null, null, null).isSet());
    Assertions.assertThrows(
        GradleException.class,
        () ->
            Notarytool.submitArguments(
                new NotaryCredentials(null, null, null, null, "me@example.com", null, "pw"), APP));
    Assertions.assertThrows(
        GradleException.class,
        () ->
            Notarytool.submitArguments(
                new NotaryCredentials(null, null, null, null, null, null, null), APP));
  }
}
