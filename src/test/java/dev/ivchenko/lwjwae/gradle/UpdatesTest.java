package dev.ivchenko.lwjwae.gradle;

import dev.ivchenko.lwjwae.gradle.util.Platform;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the tasks of updates on a project whose package is a stand-in file: the keys, the resource
 * that the application reads, the file of a release, and the signed manifest.
 */
class UpdatesTest {
  private static final Pattern KEY =
      Pattern.compile("^\\s+([A-Za-z0-9+/=]{40,})$", Pattern.MULTILINE);

  @TempDir Path project;

  /** The environment of this process without a private key, and with {@code extra}. */
  private static Map<String, String> environment(Map<String, String> extra) {
    Map<String, String> environment = new HashMap<>(System.getenv());
    environment.remove("LWJWAE_UPDATE_PRIVATE_KEY");
    environment.putAll(extra);
    return environment;
  }

  private String run(Map<String, String> environment, String... arguments) {
    return GradleRunner.create()
        .withProjectDir(this.project.toFile())
        .withPluginClasspath()
        .withEnvironment(UpdatesTest.environment(environment))
        .withArguments(arguments)
        .build()
        .getOutput();
  }

  @Test
  void releaseIsSignedByTheKeyThatTheApplicationTrusts() throws Exception {
    Files.writeString(this.project.resolve("settings.gradle.kts"), "rootProject.name = \"demo\"\n");
    Files.writeString(
        this.project.resolve("build.gradle.kts"), "plugins { id(\"dev.ivchenko.lwjwae\") }\n");
    Matcher keys = KEY.matcher(this.run(Map.of(), "generateUpdateKeys", "-q"));
    Assertions.assertTrue(keys.find());
    String publicKey = keys.group(1);
    Assertions.assertTrue(keys.find());
    String privateKey = keys.group(1);

    Files.writeString(this.project.resolve("fake-package"), "the package of 1.4.0");
    Files.writeString(
        this.project.resolve("build.gradle.kts"),
        """
        import dev.ivchenko.lwjwae.gradle.update.*
        plugins { id("dev.ivchenko.lwjwae") }
        version = "v1.4.0"
        lwjwae {
            updates {
                manifestUrl = "https://example.com/demo/manifest.json"
                publicKey = "%s"
                notes = "Faster \\"sync\\""
                minimumVersion = "1.0.0"
            }
        }
        tasks.withType<PackageUpdate>().configureEach {
            setDependsOn(emptyList<Any>())
            bundle.set(null as Directory?)
            packageFile = file("fake-package")
        }
        """
            .formatted(publicKey));
    this.run(
        Map.of("LWJWAE_UPDATE_PRIVATE_KEY", privateKey),
        "processResources",
        "packageUpdate",
        "updateManifest",
        "-q",
        "--stacktrace");

    Properties properties = new Properties();
    try (var in =
        Files.newInputStream(
            this.project.resolve("build/resources/main/META-INF/lwjwae/update.properties"))) {
      properties.load(in);
    }
    Assertions.assertEquals(
        "https://example.com/demo/manifest.json", properties.get("manifestUrl"));
    Assertions.assertEquals(publicKey, properties.get("publicKey"));
    Assertions.assertEquals("1.4.0", properties.get("version"));

    Path release = this.project.resolve("build/lwjwae/update");
    String extension = Platform.isWindows() ? ".msi" : Platform.isMacOs() ? ".zip" : ".AppImage";
    Path file = release.resolve(Platform.updateKey() + extension);
    byte[] content = Files.readAllBytes(file);
    byte[] manifest = Files.readAllBytes(release.resolve("manifest.json"));
    String text = new String(manifest, StandardCharsets.UTF_8);
    Assertions.assertTrue(text.contains("\"version\": \"1.4.0\""), text);
    Assertions.assertTrue(text.contains("\"notes\": \"Faster \\\"sync\\\"\""), text);
    Assertions.assertTrue(text.contains("\"minimumVersion\": \"1.0.0\""), text);
    Assertions.assertTrue(
        text.contains(
            "\"%s\": {\"url\": \"%s\", \"sha256\": \"%s\", \"size\": %d}"
                .formatted(
                    Platform.updateKey(),
                    file.getFileName(),
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)),
                    content.length)),
        text);

    Signature verifier = Signature.getInstance("Ed25519");
    verifier.initVerify(
        KeyFactory.getInstance("Ed25519")
            .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey))));
    verifier.update(manifest);
    Assertions.assertTrue(
        verifier.verify(
            Base64.getDecoder()
                .decode(Files.readString(release.resolve("manifest.json.sig")).strip())));
  }

  @Test
  void applicationWithoutManifestUrlCarriesNoUpdateProperties() throws IOException {
    Files.writeString(this.project.resolve("settings.gradle.kts"), "rootProject.name = \"demo\"\n");
    Files.writeString(
        this.project.resolve("build.gradle.kts"), "plugins { id(\"dev.ivchenko.lwjwae\") }\n");
    Files.createDirectories(this.project.resolve("src/main/resources"));
    Files.writeString(this.project.resolve("src/main/resources/app.txt"), "app");
    this.run(Map.of(), "processResources", "-q");
    Assertions.assertTrue(Files.exists(this.project.resolve("build/resources/main/app.txt")));
    Assertions.assertFalse(Files.exists(this.project.resolve("build/resources/main/META-INF")));
  }

  @Test
  void signingWithoutPrivateKeyFailsWithTheWayToOne() throws IOException {
    Files.writeString(this.project.resolve("settings.gradle.kts"), "rootProject.name = \"demo\"\n");
    Files.writeString(
        this.project.resolve("build.gradle.kts"), "plugins { id(\"dev.ivchenko.lwjwae\") }\n");
    Files.createDirectories(this.project.resolve("build/lwjwae/update"));
    Files.writeString(this.project.resolve("build/lwjwae/update/linux-x64.AppImage"), "x");
    String output =
        GradleRunner.create()
            .withProjectDir(this.project.toFile())
            .withPluginClasspath()
            .withEnvironment(UpdatesTest.environment(Map.of()))
            .withArguments("updateManifest", "-q")
            .buildAndFail()
            .getOutput();
    Assertions.assertTrue(output.contains("LWJWAE_UPDATE_PRIVATE_KEY"), output);
  }
}
