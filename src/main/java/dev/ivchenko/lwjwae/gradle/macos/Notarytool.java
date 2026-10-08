package dev.ivchenko.lwjwae.gradle.macos;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.gradle.api.GradleException;
import org.gradle.process.ExecOperations;

/**
 * Signs, notarizes, and staples macOS packages with the tools of Xcode: {@code codesign}, {@code
 * xcrun notarytool}, and {@code xcrun stapler}.
 */
@UtilityClass
public class Notarytool {
  /**
   * The arguments of {@code codesign} that sign {@code target} with {@code identity}: with a secure
   * time stamp and, for {@code hardenedRuntime}, the hardened runtime, both of which notarization
   * requires.
   *
   * @param entitlements A {@code .plist} of entitlements, or {@code null} for none.
   */
  public List<String> codesignArguments(
      String identity, boolean hardenedRuntime, File entitlements, File target) {
    List<String> arguments = new ArrayList<>(List.of("--force", "--timestamp"));
    if (hardenedRuntime) {
      arguments.addAll(List.of("--options", "runtime"));
    }
    if (entitlements != null) {
      arguments.addAll(List.of("--entitlements", entitlements.getAbsolutePath()));
    }
    arguments.addAll(List.of("--sign", identity, target.getAbsolutePath()));
    return arguments;
  }

  /**
   * The arguments of {@code xcrun} that submit {@code file} to the notary service and wait for its
   * verdict, signed in the first way that {@code notarization} sets.
   *
   * @throws GradleException If {@code notarization} sets no way to sign in.
   */
  public List<String> submitArguments(NotaryCredentials notarization, File file) {
    List<String> arguments =
        new ArrayList<>(List.of("notarytool", "submit", file.getAbsolutePath(), "--wait"));
    if (notarization.keychainProfile() != null) {
      arguments.addAll(List.of("--keychain-profile", notarization.keychainProfile()));
    } else if (notarization.apiKey() != null) {
      arguments.addAll(
          List.of(
              "--key",
              notarization.apiKey().getAbsolutePath(),
              "--key-id",
              Notarytool.required(notarization.apiKeyId(), "apiKeyId"),
              "--issuer",
              Notarytool.required(notarization.apiIssuer(), "apiIssuer")));
    } else if (notarization.appleId() != null) {
      arguments.addAll(
          List.of(
              "--apple-id",
              notarization.appleId(),
              "--team-id",
              Notarytool.required(notarization.teamId(), "teamId"),
              "--password",
              Notarytool.required(notarization.password(), "password")));
    } else {
      throw new GradleException(
          "Notarization needs a keychainProfile, an apiKey, or an appleId in macos.notarization");
    }
    return arguments;
  }

  /** Signs {@code target} with the arguments of {@link #codesignArguments}. */
  public void codesign(ExecOperations exec, List<String> arguments) {
    exec.exec(
        spec -> {
          spec.setExecutable("codesign");
          spec.args(arguments);
        });
  }

  /**
   * Submits {@code file}, or the ZIP file of a bundle, to the notary service, waits for the ticket,
   * and staples it to {@code target}: the bundle or the disk image itself.
   */
  public void notarizeAndStaple(
      ExecOperations exec, NotaryCredentials notarization, File file, File target) {
    exec.exec(
        spec -> {
          spec.setExecutable("xcrun");
          spec.args(Notarytool.submitArguments(notarization, file));
        });
    exec.exec(
        spec -> {
          spec.setExecutable("xcrun");
          spec.args("stapler", "staple", target.getAbsolutePath());
        });
  }

  private String required(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new GradleException("Notarization needs macos.notarization." + name);
    }
    return value;
  }
}
