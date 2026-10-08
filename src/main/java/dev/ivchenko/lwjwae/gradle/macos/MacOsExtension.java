package dev.ivchenko.lwjwae.gradle.macos;

import org.gradle.api.Action;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;

/**
 * The {@code Info.plist} file that is embedded into a macOS executable, the way the {@code java}
 * launcher embeds its own. The helper processes of WebKit need the bundle identifier, and the menu
 * bar and the Dock show the name.
 *
 * <p>{@link #getInfoPlist()} replaces the generated property list with one of your own.
 */
public abstract class MacOsExtension {
  /**
   * {@code CFBundleIdentifier}. Default: the project group and name, for example {@code
   * com.example.my-app}.
   */
  public abstract Property<String> getBundleIdentifier();

  /** {@code CFBundleName} and {@code CFBundleDisplayName}. Default: the image name. */
  public abstract Property<String> getBundleName();

  /** {@code CFBundleShortVersionString}. Default: the project version. */
  public abstract Property<String> getVersion();

  /** A complete {@code Info.plist} to embed instead of the generated one. */
  public abstract RegularFileProperty getInfoPlist();

  /**
   * The identity that {@code codesign} signs the {@code .app} bundle and the disk image with, for
   * example {@code Developer ID Application: Example (TEAMID)}. Unset by default: both stay
   * unsigned, and a user opens the application once through the context menu.
   */
  public abstract Property<String> getSigningIdentity();

  /**
   * Whether the signature turns on the hardened runtime, which notarization requires. Default: on.
   */
  public abstract Property<Boolean> getHardenedRuntime();

  /**
   * A {@code .plist} of entitlements for the signature, for an application that needs one under the
   * hardened runtime, such as the camera. Unset by default.
   */
  public abstract RegularFileProperty getEntitlements();

  /** How the bundle and the disk image reach the notary service of Apple. Off by default. */
  @Nested
  public abstract NotarizationExtension getNotarization();

  /** Configures {@link #getNotarization()}. */
  public void notarization(Action<? super NotarizationExtension> action) {
    action.execute(this.getNotarization());
  }
}
