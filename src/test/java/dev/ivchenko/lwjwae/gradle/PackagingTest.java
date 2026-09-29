package dev.ivchenko.lwjwae.gradle;

import dev.ivchenko.lwjwae.gradle.linux.PackageAppImage;
import dev.ivchenko.lwjwae.gradle.linux.PackageArch;
import dev.ivchenko.lwjwae.gradle.linux.PackageDeb;
import dev.ivchenko.lwjwae.gradle.windows.PackageMsi;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import javax.imageio.ImageIO;
import org.apache.commons.compress.archivers.ar.ArArchiveEntry;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.gradle.api.GradleException;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the package tasks over a stand-in executable and takes the packages apart. Nothing is
 * compiled natively: the build script points the tasks at a script and drops their dependency on
 * {@code nativeCompile}.
 */
class PackagingTest {
  @TempDir Path project;

  @BeforeEach
  void writeProject() throws IOException {
    Files.writeString(this.project.resolve("settings.gradle.kts"), "rootProject.name = \"demo\"\n");
    Files.writeString(this.project.resolve("fake-app"), "#!/bin/sh\necho demo\n");
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
    Files.writeString(
        this.project.resolve("build.gradle.kts"),
        """
        import dev.ivchenko.lwjwae.gradle.linux.*
        import dev.ivchenko.lwjwae.gradle.macos.*
        plugins {
            id("application")
            id("dev.ivchenko.lwjwae")
        }
        group = "com.example"
        version = "1.2.3"
        description = "A demo that shows the packages"
        application { mainClass = "com.example.Main" }
        lwjwae {
            imageName = "demo-app"
            displayName = "Demo App"
            vendor = "Example <hello@example.com>"
            packaging.homepage = "https://example.com"
        }
        tasks.withType<PackageDeb>().configureEach { setDependsOn(emptyList<Any>()); executable = file("fake-app") }
        tasks.withType<PackageArch>().configureEach { setDependsOn(emptyList<Any>()); executable = file("fake-app") }
        tasks.withType<PackageAppImage>().configureEach { setDependsOn(emptyList<Any>()); executable = file("fake-app") }
        tasks.withType<PackageMacApp>().configureEach { setDependsOn(emptyList<Any>()); executable = file("fake-app") }
        """);
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  void debCarriesTheExecutableTheEntryTheIconsAndTheControlFile() throws IOException {
    this.run("packageDeb");
    Path deb =
        this.project.resolve(
            "build/lwjwae/dist/demo-app_1.2.3_" + PackageDeb.architecture() + ".deb");
    Assertions.assertTrue(Files.exists(deb), "no package at " + deb);

    Map<String, byte[]> members = new LinkedHashMap<>();
    try (ArArchiveInputStream ar = new ArArchiveInputStream(Files.newInputStream(deb))) {
      ArArchiveEntry entry;
      while ((entry = ar.getNextEntry()) != null) {
        members.put(entry.getName(), ar.readAllBytes());
      }
    }
    Assertions.assertEquals(
        "[debian-binary, control.tar.gz, data.tar.gz]", members.keySet().toString());
    Assertions.assertEquals(
        "2.0\n", new String(members.get("debian-binary"), StandardCharsets.US_ASCII));

    Map<String, TarArchiveEntry> control = entries(members.get("control.tar.gz"));
    String fields =
        new String(read(members.get("control.tar.gz"), "./control"), StandardCharsets.UTF_8);
    Assertions.assertTrue(control.containsKey("./control"));
    Assertions.assertTrue(
        fields.startsWith("Package: demo-app\nVersion: 1.2.3\nArchitecture: "), fields);
    Assertions.assertTrue(fields.contains("Maintainer: Example <hello@example.com>\n"), fields);
    Assertions.assertTrue(
        fields.contains("Depends: libgtk-3-0t64 | libgtk-3-0, libwebkit2gtk-4.1-0\n"), fields);
    Assertions.assertTrue(fields.contains("Homepage: https://example.com\n"), fields);
    Assertions.assertTrue(fields.endsWith("Description: A demo that shows the packages\n"), fields);

    Map<String, TarArchiveEntry> data = entries(members.get("data.tar.gz"));
    Assertions.assertEquals(0755, data.get("./usr/bin/demo-app").getMode() & 0777);
    Assertions.assertEquals(
        0644, data.get("./usr/share/applications/demo-app.desktop").getMode() & 0777);
    Assertions.assertTrue(
        data.containsKey("./usr/share/icons/hicolor/256x256/apps/com.example.demo-app.png"));
    Assertions.assertTrue(
        data.containsKey("./usr/share/icons/hicolor/16x16/apps/com.example.demo-app.png"));
    String desktop =
        new String(
            read(members.get("data.tar.gz"), "./usr/share/applications/demo-app.desktop"),
            StandardCharsets.UTF_8);
    Assertions.assertTrue(desktop.contains("Name=Demo App\n"), desktop);
    Assertions.assertTrue(desktop.contains("Exec=/usr/bin/demo-app\n"), desktop);
    Assertions.assertTrue(desktop.contains("Icon=com.example.demo-app\n"), desktop);
    Assertions.assertTrue(desktop.contains("Categories=Utility;\n"), desktop);
  }

  @Test
  void appBundleHasTheLayoutFinderExpects() throws IOException {
    this.run("packageApp");
    Path app = this.project.resolve("build/lwjwae/dist/Demo App.app/Contents");
    Assertions.assertTrue(Files.isExecutable(app.resolve("MacOS/demo-app")) || !isPosix());
    Assertions.assertEquals("APPL????", Files.readString(app.resolve("PkgInfo")));
    String plist = Files.readString(app.resolve("Info.plist"));
    Assertions.assertTrue(
        plist.contains("<key>CFBundleExecutable</key>\n    <string>demo-app</string>"), plist);
    Assertions.assertTrue(
        plist.contains("<key>CFBundleIconFile</key>\n    <string>app.icns</string>"), plist);
    Assertions.assertTrue(plist.contains("<string>com.example.demo</string>"), plist);
    byte[] icns = Files.readAllBytes(app.resolve("Resources/app.icns"));
    Assertions.assertEquals("icns", new String(icns, 0, 4, StandardCharsets.US_ASCII));
    Assertions.assertEquals(
        "icp4", new String(icns, 8, 4, StandardCharsets.US_ASCII), "the 16 px entry comes first");
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  void appImageUnpacksToTheAppDir() throws Exception {
    this.run("packageAppImage");
    Path image =
        this.project.resolve(
            "build/lwjwae/dist/demo-app-1.2.3-" + PackageAppImage.architecture() + ".AppImage");
    Assertions.assertTrue(Files.exists(image), "no AppImage at " + image);

    // The runtime extracts itself without FUSE, which is how the contents can be checked here.
    Path extracted = Files.createDirectories(this.project.resolve("extracted"));
    Process process =
        new ProcessBuilder(image.toString(), "--appimage-extract")
            .directory(extracted.toFile())
            .redirectErrorStream(true)
            .start();
    process.getInputStream().readAllBytes();
    Assertions.assertEquals(0, process.waitFor());
    Path appDir = extracted.resolve("squashfs-root");
    Assertions.assertTrue(Files.isSymbolicLink(appDir.resolve("AppRun")));
    Assertions.assertTrue(Files.exists(appDir.resolve("usr/bin/demo-app")));
    Assertions.assertTrue(Files.exists(appDir.resolve("com.example.demo-app.png")));
    Assertions.assertTrue(Files.exists(appDir.resolve(".DirIcon")));
    String desktop = Files.readString(appDir.resolve("demo-app.desktop"));
    Assertions.assertTrue(desktop.contains("Exec=demo-app\n"), desktop);
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  void debNeedsMaintainer() throws IOException {
    this.rewriteBuild("vendor = \"Example <hello@example.com>\"", "vendor = \"Example\"");
    String output =
        GradleRunner.create()
            .withProjectDir(this.project.toFile())
            .withPluginClasspath()
            .withArguments("packageDeb", "-q")
            .buildAndFail()
            .getOutput();
    Assertions.assertTrue(output.contains("packaging.deb.maintainer"), output);
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  void appImageWithoutIconGetsPlaceholder() throws Exception {
    Files.delete(this.project.resolve(LwjwaeExtension.DEFAULT_ICON));
    this.run("packageAppImage");
    Path appDir = this.project.resolve("build/lwjwae/appimage/AppDir");
    BufferedImage icon = ImageIO.read(appDir.resolve("com.example.demo-app.png").toFile());
    Assertions.assertEquals(256, icon.getWidth());
    Assertions.assertTrue(Files.exists(appDir.resolve(".DirIcon")));
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  void archPackageCarriesTheMetadataTheTreeAndTheFiles() throws IOException {
    this.run("packageArch");
    Path archive =
        this.project.resolve(
            "build/lwjwae/dist/demo-app-1.2.3-1-" + PackageArch.architecture() + ".pkg.tar.xz");
    Assertions.assertTrue(Files.exists(archive), "no package at " + archive);

    Map<String, byte[]> files = new LinkedHashMap<>();
    Map<String, TarArchiveEntry> entries = new LinkedHashMap<>();
    try (TarArchiveInputStream tar =
        new TarArchiveInputStream(new XZCompressorInputStream(Files.newInputStream(archive)))) {
      TarArchiveEntry entry;
      while ((entry = tar.getNextEntry()) != null) {
        entries.put(entry.getName(), entry);
        files.put(entry.getName(), tar.readAllBytes());
      }
    }
    Assertions.assertEquals(
        List.of(".PKGINFO", ".MTREE"),
        List.copyOf(entries.keySet()).subList(0, 2),
        "metadata first");
    String info = new String(files.get(".PKGINFO"), StandardCharsets.UTF_8);
    Assertions.assertTrue(info.contains("\npkgname = demo-app\n"), info);
    Assertions.assertTrue(info.contains("\npkgver = 1.2.3-1\n"), info);
    Assertions.assertTrue(info.contains("\npkgdesc = A demo that shows the packages\n"), info);
    Assertions.assertTrue(info.contains("\nurl = https://example.com\n"), info);
    Assertions.assertTrue(info.contains("\npackager = Example <hello@example.com>\n"), info);
    Assertions.assertTrue(info.contains("\narch = " + PackageArch.architecture() + "\n"), info);
    Assertions.assertTrue(info.endsWith("depend = gtk3\ndepend = webkit2gtk-4.1\n"), info);

    Assertions.assertEquals(0755, entries.get("usr/bin/demo-app").getMode() & 0777);
    Assertions.assertEquals(0, entries.get("usr/bin/demo-app").getLongUserId());
    Assertions.assertTrue(entries.containsKey("usr/share/applications/demo-app.desktop"));
    Assertions.assertTrue(
        entries.containsKey("usr/share/icons/hicolor/256x256/apps/com.example.demo-app.png"));

    String mtree;
    try (GZIPInputStream gzip =
        new GZIPInputStream(new ByteArrayInputStream(files.get(".MTREE")))) {
      mtree = new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
    }
    Assertions.assertTrue(mtree.startsWith("#mtree\n/set type=file uid=0 gid=0 mode=644\n"), mtree);
    Assertions.assertTrue(mtree.contains("\n./usr/bin time="), mtree);
    byte[] program = files.get("usr/bin/demo-app");
    String digest = HexFormat.of().formatHex(PackagingTest.sha256(program));
    Assertions.assertTrue(
        mtree.contains(" mode=755 size=" + program.length + " sha256digest=" + digest + "\n"),
        mtree);
    long time = entries.get("usr/bin/demo-app").getModTime().getTime() / 1000;
    Assertions.assertTrue(mtree.contains("./usr/bin/demo-app time=" + time + ".0 "), mtree);
  }

  @Test
  void archNamesAndVersionsFitPacman() {
    Assertions.assertEquals("my-app", PackageArch.packageName("My App"));
    Assertions.assertEquals("app", PackageArch.packageName(".app"));
    Assertions.assertEquals("1.2.3_SNAPSHOT", PackageArch.packageVersion("1.2.3-SNAPSHOT"));
  }

  @Test
  void upgradeCodeIsStableAndVersionsFitTheInstaller() {
    Assertions.assertEquals(
        PackageMsi.upgradeCode("com.example", "demo"),
        PackageMsi.upgradeCode("com.example", "demo"));
    Assertions.assertNotEquals(
        PackageMsi.upgradeCode("com.example", "demo"),
        PackageMsi.upgradeCode("com.example", "other"));
    Assertions.assertTrue(PackageMsi.upgradeCode("com.example", "demo").matches("[0-9A-F-]{36}"));
    Assertions.assertEquals("1.2.3", PackageMsi.numericVersion("1.2.3-SNAPSHOT"));
    Assertions.assertEquals("2.0.0", PackageMsi.numericVersion("2"));
    Assertions.assertEquals("1.2.3", PackageMsi.numericVersion("1.2.3.4"));
    Assertions.assertThrows(GradleException.class, () -> PackageMsi.numericVersion("2026.9.28"));
    Assertions.assertThrows(GradleException.class, () -> PackageMsi.numericVersion("1.2.70000"));
  }

  @Test
  void debianNamesAreLowercaseAndSafe() {
    Assertions.assertEquals("my-app", PackageDeb.packageName("My App"));
    Assertions.assertEquals("demo-app", PackageDeb.packageName("demo-app"));
  }

  private void rewriteBuild(String from, String to) throws IOException {
    Path build = this.project.resolve("build.gradle.kts");
    String script = Files.readString(build);
    Assertions.assertTrue(script.contains(from), script);
    Files.writeString(build, script.replace(from, to));
  }

  private void run(String task) {
    GradleRunner.create()
        .withProjectDir(this.project.toFile())
        .withPluginClasspath()
        .withArguments(task, "-q", "--stacktrace")
        .build();
  }

  private static byte[] sha256(byte[] content) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(content);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static boolean isPosix() {
    return !System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
  }

  private static Map<String, TarArchiveEntry> entries(byte[] gzip) throws IOException {
    Map<String, TarArchiveEntry> entries = new LinkedHashMap<>();
    try (TarArchiveInputStream tar =
        new TarArchiveInputStream(new GZIPInputStream(new ByteArrayInputStream(gzip)))) {
      TarArchiveEntry entry;
      while ((entry = tar.getNextEntry()) != null) {
        entries.put(entry.getName(), entry);
      }
    }
    return entries;
  }

  private static byte[] read(byte[] gzip, String name) throws IOException {
    try (TarArchiveInputStream tar =
        new TarArchiveInputStream(new GZIPInputStream(new ByteArrayInputStream(gzip)))) {
      TarArchiveEntry entry;
      while ((entry = tar.getNextEntry()) != null) {
        if (entry.getName().equals(name)) {
          return tar.readAllBytes();
        }
      }
    }
    throw new AssertionError("No " + name + " in the archive");
  }
}
