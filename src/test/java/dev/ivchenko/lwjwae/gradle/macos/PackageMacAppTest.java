package dev.ivchenko.lwjwae.gradle.macos;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Patches property lists that the plugin didn't write, and names bundles. */
class PackageMacAppTest {
  @Test
  void addsTheIconAndTheExecutableOnce() {
    String plist =
        """
        <plist version="1.0">
        <dict>
          <key> CFBundleIconFile </key>
          <string>custom.icns</string>
        </dict>
        </plist>
        """;
    String patched = PackageMacApp.withIcon(plist, "demo & co");
    Assertions.assertEquals(1, patched.split("CFBundleIconFile", -1).length - 1, patched);
    Assertions.assertTrue(
        patched.contains("<key>CFBundleExecutable</key>\n    <string>demo &amp; co</string>"),
        patched);
    Assertions.assertEquals(patched, PackageMacApp.withIcon(patched, "demo & co"));
  }

  @Test
  void failsWithoutDictionary() {
    Assertions.assertThrows(
        GradleException.class, () -> PackageMacApp.withExecutable("<plist/>", "demo"));
  }

  @Test
  void bundleIdentifiersHoldOnlyWhatMacOsAllows() {
    Assertions.assertEquals(
        "com.example.my-app", MacOsConfiguration.bundleIdentifier("com.example", "my_app"));
    Assertions.assertEquals("demo", MacOsConfiguration.bundleIdentifier("", "demo"));
  }
}
