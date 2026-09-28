package dev.ivchenko.lwjwae.gradle.util;

import java.util.Locale;
import lombok.experimental.UtilityClass;

/**
 * The operating system and the architecture of the machine that runs the build.
 *
 * <p>A native image is built for the machine that builds it, so this is also the platform that the
 * executable and its packages are for.
 */
@UtilityClass
public class Platform {
  /** Whether the build runs on Linux. */
  public boolean isLinux() {
    return Platform.osName().contains("linux");
  }

  /** Whether the build runs on Windows. */
  public boolean isWindows() {
    return Platform.osName().contains("win");
  }

  /** Whether the build runs on macOS. */
  public boolean isMacOs() {
    return Platform.osName().contains("mac") || Platform.osName().contains("darwin");
  }

  /** Whether the build runs on a 64-bit ARM processor; otherwise it's x86-64. */
  public boolean isArm64() {
    String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
    return arch.contains("aarch64") || arch.contains("arm64");
  }

  /** The suffix of an executable: {@code .exe} on Windows, nothing elsewhere. */
  public String executableSuffix() {
    return Platform.isWindows() ? ".exe" : "";
  }

  private String osName() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
  }
}
