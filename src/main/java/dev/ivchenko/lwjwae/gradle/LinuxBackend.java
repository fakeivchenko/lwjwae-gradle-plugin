package dev.ivchenko.lwjwae.gradle;

import java.util.List;

/**
 * The backend that runs an lwjwae application on Linux, and the packages of the system that it
 * needs, by their Debian and Ubuntu names and by their Arch Linux names.
 */
public enum LinuxBackend {
  /**
   * {@code lwjwae-gtk}: GTK 3 and WebKitGTK 4.1, which every current desktop distribution ships.
   * The name that Debian and Ubuntu used for GTK 3 before the 64-bit {@code time_t} transition is
   * the alternative.
   */
  GTK3(
      "lwjwae-gtk",
      List.of("libgtk-3-0t64 | libgtk-3-0", "libwebkit2gtk-4.1-0"),
      List.of("gtk3", "webkit2gtk-4.1")),

  /**
   * {@code lwjwae-gtk4}: GTK 4 and WebKitGTK 6.0, for distributions that ship WebKitGTK 6.0 without
   * 4.1. It can't place windows, because GTK 4 has no API for it.
   */
  GTK4(
      "lwjwae-gtk4", List.of("libgtk-4-1", "libwebkitgtk-6.0-4"), List.of("gtk4", "webkitgtk-6.0"));

  private final String module;
  private final List<String> packages;
  private final List<String> archPackages;

  LinuxBackend(String module, List<String> packages, List<String> archPackages) {
    this.module = module;
    this.packages = packages;
    this.archPackages = archPackages;
  }

  /** The artifact of the backend. */
  public String module() {
    return this.module;
  }

  /** The {@code Depends} of a Debian package that runs on this backend. */
  public List<String> packages() {
    return this.packages;
  }

  /** The {@code depend} entries of an Arch Linux package that runs on this backend. */
  public List<String> archPackages() {
    return this.archPackages;
  }
}
