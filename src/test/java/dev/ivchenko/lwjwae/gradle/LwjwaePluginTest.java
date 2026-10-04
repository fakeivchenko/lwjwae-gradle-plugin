package dev.ivchenko.lwjwae.gradle;

import dev.ivchenko.lwjwae.gradle.windows.GenerateWindowsResourceScript;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Applies the plugin in a real build through TestKit and reads the dependencies it declared. The
 * configurations are never resolved, so the test needs no repository and no network.
 */
class LwjwaePluginTest {
  private static final String PRINT_BUILD_ARGS =
      """
      tasks.register("printBuildArgs") {
          val args = graalvmNative.binaries.named("main").flatMap { it.buildArgs }
          val jvmArgs = application.applicationDefaultJvmArgs
          val resources = graalvmNative.binaries.named("main").flatMap { it.resources.detectionOptions.enabled }
          doLast {
              println("BUILD_ARGS=" + args.get().joinToString(" "))
              println("JVM_ARGS=" + jvmArgs.joinToString(" "))
              println("RESOURCES=" + resources.get())
          }
      }
      """;

  private static final String PRINT_DEPENDENCIES =
      """
      tasks.register("printDependencies") {
          val names = { configuration: String ->
              configurations[configuration].dependencies.map { "${it.group}:${it.name}:${it.version}" }
          }
          val implementation = names("implementation")
          val runtimeOnly = names("runtimeOnly")
          val annotationProcessor = names("annotationProcessor")
          doLast {
              println("IMPLEMENTATION=" + implementation.joinToString(" "))
              println("RUNTIME_ONLY=" + runtimeOnly.joinToString(" "))
              println("ANNOTATION_PROCESSOR=" + annotationProcessor.joinToString(" "))
          }
      }
      """;

  @TempDir Path project;

  @BeforeEach
  void writeSettings() throws IOException {
    Files.writeString(this.project.resolve("settings.gradle.kts"), "rootProject.name = \"demo\"\n");
  }

  @Test
  void addsTheCoreAndTheBackendOfThisMachineAtTheDefaultVersion() throws IOException {
    this.writeBuild("");
    BuildResult result = this.run();
    Assertions.assertEquals(
        "dev.ivchenko.lwjwae:lwjwae-core:" + DefaultVersions.library(),
        line(result, "IMPLEMENTATION="));
    Assertions.assertEquals(
        "dev.ivchenko.lwjwae:"
            + DependencyConfiguration.currentBackend(LinuxBackend.GTK3)
            + ":"
            + DefaultVersions.library(),
        line(result, "RUNTIME_ONLY="));
  }

  @Test
  void versionsBackendsAndCodecAreConfigurable() throws IOException {
    this.writeBuild(
        """
        lwjwae {
            version = "9.9.9"
            codecsVersion = "8.8.8"
            backends = dev.ivchenko.lwjwae.gradle.Backends.ALL
            codec = dev.ivchenko.lwjwae.gradle.Codec.JSONB
            jsonbProvider = "org.example:jsonb"
            jsonbProviderVersion = "1.0"
        }
        """);
    BuildResult result = this.run();
    Assertions.assertEquals(
        "dev.ivchenko.lwjwae:lwjwae-core:9.9.9", line(result, "IMPLEMENTATION="));
    Assertions.assertEquals(
        List.of(
            "dev.ivchenko.lwjwae:lwjwae-gtk:9.9.9",
            "dev.ivchenko.lwjwae:lwjwae-gtk4:9.9.9",
            "dev.ivchenko.lwjwae:lwjwae-windows:9.9.9",
            "dev.ivchenko.lwjwae:lwjwae-macos:9.9.9",
            "dev.ivchenko.lwjwae.codec:lwjwae-codec-jsonb:8.8.8",
            "org.example:jsonb:1.0"),
        List.of(line(result, "RUNTIME_ONLY=").split(" ")));
    Assertions.assertEquals(
        "dev.ivchenko.lwjwae:lwjwae-processor:9.9.9", line(result, "ANNOTATION_PROCESSOR="));
  }

  @Test
  void shortcutsPickTheCodecAndThePackages() throws IOException {
    this.writeBuild(
        """
        lwjwae {
            backends = dev.ivchenko.lwjwae.gradle.Backends.NONE
            jsonb("org.apache.johnzon:johnzon-jsonb:2.0.2")
            packaging.all()
        }
        tasks.register("printPackages") {
            val packaging = lwjwae.packaging
            val enabled = listOf(packaging.deb.enabled, packaging.appImage.enabled, packaging.dmg.enabled, packaging.msi.enabled)
            doLast { println("PACKAGES=" + enabled.map { it.get() }) }
        }
        """);
    BuildResult result = this.run("printDependencies", "printPackages");
    Assertions.assertEquals(
        List.of(
            "dev.ivchenko.lwjwae.codec:lwjwae-codec-jsonb:" + DefaultVersions.codecs(),
            "org.apache.johnzon:johnzon-jsonb:2.0.2"),
        List.of(line(result, "RUNTIME_ONLY=").split(" ")));
    Assertions.assertEquals("[true, true, true, true]", line(result, "PACKAGES="));
  }

  @Test
  void formatBlockTurnsTheFormatOn() throws IOException {
    this.writeBuild(
        """
        lwjwae { packaging { deb(); msi { perUser = false }; dmg { enabled = false } } }
        tasks.register("printPackages") {
            val packaging = lwjwae.packaging
            val enabled = listOf(packaging.deb.enabled, packaging.appImage.enabled, packaging.dmg.enabled, packaging.msi.enabled)
            doLast { println("PACKAGES=" + enabled.map { it.get() }) }
        }
        """);
    Assertions.assertEquals(
        "[true, false, false, true]", line(this.run("printPackages"), "PACKAGES="));
  }

  @Test
  void codecShortcutsNeedNoImport() throws IOException {
    this.writeBuild("lwjwae { backends = dev.ivchenko.lwjwae.gradle.Backends.NONE; jackson() }\n");
    Assertions.assertEquals(
        "dev.ivchenko.lwjwae.codec:lwjwae-codec-jackson:" + DefaultVersions.codecs(),
        line(this.run(), "RUNTIME_ONLY="));
  }

  @Test
  void processorComesOnlyWithTheReleasesThatHaveIt() throws IOException {
    this.writeBuild("lwjwae { version = \"0.7.1\" }\n");
    Assertions.assertEquals("", line(this.run(), "ANNOTATION_PROCESSOR="));
    Assertions.assertFalse(DependencyConfiguration.hasProcessor("0.7.9"));
    Assertions.assertTrue(DependencyConfiguration.hasProcessor("0.8.0"));
    Assertions.assertTrue(DependencyConfiguration.hasProcessor("1.0.0"));
    Assertions.assertTrue(DependencyConfiguration.hasProcessor("0.99.0-local"));
    Assertions.assertTrue(DependencyConfiguration.hasProcessor("main-SNAPSHOT"));
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  void gtk4IsTheBackendOfThisLinuxWhenAsked() throws IOException {
    this.writeBuild("lwjwae { linuxBackend = dev.ivchenko.lwjwae.gradle.LinuxBackend.GTK4 }\n");
    Assertions.assertEquals(
        "dev.ivchenko.lwjwae:lwjwae-gtk4:" + DefaultVersions.library(),
        line(this.run(), "RUNTIME_ONLY="));
  }

  @Test
  void codecComesAloneWithoutBackends() throws IOException {
    this.writeBuild(
        """
        lwjwae {
            backends = dev.ivchenko.lwjwae.gradle.Backends.NONE
            codec = dev.ivchenko.lwjwae.gradle.Codec.GSON
        }
        """);
    BuildResult result = this.run();
    Assertions.assertEquals(
        "dev.ivchenko.lwjwae.codec:lwjwae-codec-gson:" + DefaultVersions.codecs(),
        line(result, "RUNTIME_ONLY="));
  }

  @Test
  void managedOffAddsNothing() throws IOException {
    this.writeBuild("lwjwae { managed = false }\n");
    BuildResult result = this.run();
    Assertions.assertEquals("", line(result, "IMPLEMENTATION="));
    Assertions.assertEquals("", line(result, "RUNTIME_ONLY="));
    Assertions.assertEquals("", line(result, "ANNOTATION_PROCESSOR="));
  }

  @Test
  void configuresTheNativeImageAndTheJvmRun() throws IOException {
    this.writeBuild("lwjwae { buildArgs.add(\"--verbose\") }\n");
    BuildResult result = this.run("printBuildArgs");
    String args = line(result, "BUILD_ARGS=");
    Assertions.assertTrue(
        args.startsWith("--enable-native-access=ALL-UNNAMED -Os -R:MaxHeapSize=64m"), args);
    Assertions.assertTrue(args.endsWith("--verbose"), args);
    Assertions.assertTrue(line(result, "JVM_ARGS=").contains("--enable-native-access=ALL-UNNAMED"));
    Assertions.assertEquals("true", line(result, "RESOURCES="), "the page goes into the image");
  }

  @Test
  void nativeAccessSurvivesJvmArgsOfTheBuildScript() throws IOException {
    this.writeBuild("application { applicationDefaultJvmArgs = listOf(\"-Xmx1g\") }\n");
    String jvmArgs = line(this.run("printBuildArgs"), "JVM_ARGS=");
    Assertions.assertEquals("-Xmx1g --enable-native-access=ALL-UNNAMED", jvmArgs);
  }

  @Test
  void sizeHeapAndResourcesCanBeSwitchedOff() throws IOException {
    this.writeBuild(
        "lwjwae { optimizeForSize = false; maxHeapSize = \"\"; embedResources = false }\n");
    BuildResult result = this.run("printBuildArgs");
    String args = line(result, "BUILD_ARGS=");
    Assertions.assertFalse(args.contains("-Os"), args);
    Assertions.assertFalse(args.contains("MaxHeapSize"), args);
    Assertions.assertEquals("false", line(result, "RESOURCES="));
  }

  @Test
  void rendersTheIconAtEverySize() throws IOException {
    this.writeIcon();
    this.writeBuild("");
    this.run("generateWindowsIcon");
    ByteBuffer ico =
        ByteBuffer.wrap(Files.readAllBytes(this.project.resolve("build/lwjwae/windows/app.ico")))
            .order(ByteOrder.LITTLE_ENDIAN);
    Assertions.assertEquals(1, ico.getShort(2), "icon type");
    Assertions.assertEquals(7, ico.getShort(4), "entries");
    List<Integer> sizes = new ArrayList<>();
    for (int entry = 0; entry < 7; entry++) {
      int width = ico.get(6 + entry * 16) & 0xFF;
      sizes.add(width == 0 ? 256 : width);
      int length = ico.getInt(6 + entry * 16 + 8);
      int offset = ico.getInt(6 + entry * 16 + 12);
      Assertions.assertTrue(offset + length <= ico.capacity(), "entry " + entry + " lies inside");
    }
    Assertions.assertEquals(List.of(16, 24, 32, 48, 64, 128, 256), sizes);
    int last = 6 + 6 * 16;
    byte[] png = new byte[8];
    ico.get(ico.getInt(last + 12), png);
    Assertions.assertArrayEquals(
        new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'},
        png,
        "the 256 pixel entry is PNG");
    // The 16 pixel bitmap: 40 header bytes, 16 rows of 16 BGRA pixels, then 16 mask rows of 4
    // bytes,
    // bottom-up. The circle leaves the corners transparent and fills the middle.
    int mask = ico.getInt(6 + 12) + 40 + 16 * 16 * 4;
    Assertions.assertEquals((byte) 0x80, (byte) (ico.get(mask + 15 * 4) & 0x80), "corner masked");
    Assertions.assertEquals(0, ico.get(mask + 7 * 4 + 1) & 0x80, "middle drawn");
  }

  @Test
  void generatesTheWindowsResourceScript() throws IOException {
    this.writeIcon();
    this.writeBuild(
        """
        group = "com.example"
        version = "1.2.3-SNAPSHOT"
        lwjwae {
            imageName = "demo-app"
            windows {
                fileDescription = "Demo \\"quoted\\""
                companyName = "Example & Co"
            }
        }
        """);
    this.run("generateWindowsResourceScript");
    String script = Files.readString(this.project.resolve("build/lwjwae/windows/app.rc"));
    // Gradle names the project directory by its real path: /private/var for /var on macOS, the long
    // form of an 8.3 name on Windows.
    String icon = this.project.toRealPath().resolve("build/lwjwae/windows/app.ico").toString();
    Assertions.assertTrue(script.contains("1 ICON \"" + icon.replace('\\', '/') + "\""), script);
    Assertions.assertTrue(
        Files.exists(this.project.resolve("build/lwjwae/windows/app.ico")),
        "the script task renders the icon first");
    Assertions.assertTrue(script.contains("FILEVERSION     1,2,3,0"), script);
    Assertions.assertTrue(
        script.contains("VALUE \"FileDescription\", \"Demo \"\"quoted\"\"\""), script);
    Assertions.assertTrue(script.contains("VALUE \"ProductName\", \"demo-app\""), script);
    Assertions.assertTrue(script.contains("VALUE \"OriginalFilename\", \"demo-app.exe\""), script);
    Assertions.assertTrue(script.contains("VALUE \"CompanyName\", \"Example & Co\""), script);
    Assertions.assertFalse(script.contains("LegalCopyright"), script);
    Assertions.assertTrue(script.startsWith("#pragma code_page(65001)\r\n"), script);
  }

  @Test
  void displayNameAndVendorNameTheApplicationEverywhere() throws IOException {
    this.writeBuild(
        """
        group = "com.example"
        lwjwae {
            imageName = "demo_app"
            displayName = "Démo"
            vendor = "Example <hello@example.com>"
        }
        """);
    this.run("generateWindowsResourceScript", "generateInfoPlist");
    String script =
        Files.readString(
            this.project.resolve("build/lwjwae/windows/app.rc"), StandardCharsets.UTF_8);
    Assertions.assertTrue(script.contains("VALUE \"FileDescription\", \"Démo\""), script);
    Assertions.assertTrue(script.contains("VALUE \"ProductName\", \"Démo\""), script);
    Assertions.assertTrue(script.contains("VALUE \"CompanyName\", \"Example\""), script);
    String plist = Files.readString(this.project.resolve("build/lwjwae/macos/Info.plist"));
    Assertions.assertTrue(
        plist.contains("<key>CFBundleName</key>\n    <string>Démo</string>"), plist);
  }

  @Test
  void generatesTheInfoPlist() throws IOException {
    this.writeBuild(
        """
        group = "com.example"
        version = "1.2.3-SNAPSHOT"
        lwjwae { macos { bundleName = "Demo" } }
        """);
    this.run("generateInfoPlist");
    String plist = Files.readString(this.project.resolve("build/lwjwae/macos/Info.plist"));
    Assertions.assertTrue(plist.contains("<string>com.example.demo</string>"), plist);
    Assertions.assertTrue(
        plist.contains("<key>CFBundleName</key>\n    <string>Demo</string>"), plist);
    Assertions.assertTrue(plist.contains("<string>1.2.3-SNAPSHOT</string>"), plist);
  }

  @Test
  void infoPlistRegistersTheSchemesAndTheTypesOfFiles() throws IOException {
    this.writeBuild(
        """
        group = "com.example"
        lwjwae {
            displayName = "Demo"
            urlScheme("demo", "demo-dev")
            fileType("note", "application/x-demo-note", "Note")
        }
        """);
    this.run("generateInfoPlist");
    String plist = Files.readString(this.project.resolve("build/lwjwae/macos/Info.plist"));
    Assertions.assertTrue(
        plist.contains(
            """
                <key>CFBundleURLSchemes</key>
                        <array>
                            <string>demo</string>
                            <string>demo-dev</string>
                        </array>
            """
                .strip()),
        plist);
    Assertions.assertTrue(
        plist.contains(
            """
                        <key>CFBundleTypeName</key>
                        <string>Note</string>
            """
                .strip()),
        plist);
    Assertions.assertTrue(plist.contains("<string>note</string>"), plist);
    Assertions.assertTrue(plist.contains("<string>application/x-demo-note</string>"), plist);
    Assertions.assertTrue(plist.endsWith("    </array>\n</dict>\n</plist>\n"), plist);
  }

  @Test
  void infoPlistWithoutSchemesOrTypesHasNeither() throws IOException {
    this.writeBuild("group = \"com.example\"\n");
    this.run("generateInfoPlist");
    String plist = Files.readString(this.project.resolve("build/lwjwae/macos/Info.plist"));
    Assertions.assertFalse(plist.contains("CFBundleURLTypes"), plist);
    Assertions.assertFalse(plist.contains("CFBundleDocumentTypes"), plist);
    Assertions.assertTrue(plist.contains("<true/>\n</dict>\n</plist>\n"), plist);
  }

  @Test
  void schemesAndTypesOfFilesAreChecked() {
    Assertions.assertEquals("md", new FileAssociation(".MD", null, null).extension());
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> new FileAssociation("tar.gz", null, null));
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> new FileAssociation("note", "not a type", null));
    Assertions.assertEquals(
        new FileAssociation("note", "application/x-my-app-note", "My App document"),
        new FileAssociation("note", null, null).resolve("My App", "My App"));
  }

  @Test
  void versionNumbersFitTheVersionBlock() {
    Assertions.assertEquals(
        "1,2,3,0", GenerateWindowsResourceScript.numericVersion("1.2.3-SNAPSHOT"));
    Assertions.assertEquals("2,0,0,0", GenerateWindowsResourceScript.numericVersion("2"));
    Assertions.assertEquals("1,2,3,4", GenerateWindowsResourceScript.numericVersion("1.2.3.4.5"));
    Assertions.assertEquals("0,0,0,0", GenerateWindowsResourceScript.numericVersion("unspecified"));
  }

  @Test
  void defaultVersionsComeFromThePluginBuild() {
    Assertions.assertTrue(DefaultVersions.library().matches("\\d+\\.\\d+\\.\\d+"));
    Assertions.assertTrue(DefaultVersions.codecs().matches("\\d+\\.\\d+\\.\\d+"));
  }

  private void writeBuild(String configuration) throws IOException {
    Files.writeString(
        this.project.resolve("build.gradle.kts"),
        """
        plugins {
            id("application")
            id("dev.ivchenko.lwjwae")
        }
        """
            + configuration
            + PRINT_DEPENDENCIES
            + PRINT_BUILD_ARGS);
  }

  private void writeIcon() throws IOException {
    Files.createDirectories(this.project.resolve("src/main/icons"));
    BufferedImage image = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    try {
      graphics.setColor(Color.ORANGE);
      graphics.fillOval(20, 20, 260, 260);
    } finally {
      graphics.dispose();
    }
    ImageIO.write(image, "png", this.project.resolve(LwjwaeExtension.DEFAULT_ICON).toFile());
  }

  private BuildResult run() {
    return this.run("printDependencies");
  }

  private BuildResult run(String... tasks) {
    List<String> arguments = new ArrayList<>(List.of(tasks));
    arguments.add("-q");
    return GradleRunner.create()
        .withProjectDir(this.project.toFile())
        .withPluginClasspath()
        .withArguments(arguments)
        .build();
  }

  private static String line(BuildResult result, String prefix) {
    return result
        .getOutput()
        .lines()
        .filter(line -> line.startsWith(prefix))
        .map(line -> line.substring(prefix.length()).strip())
        .findFirst()
        .orElseThrow(() -> new AssertionError("No " + prefix + " in:\n" + result.getOutput()));
  }
}
