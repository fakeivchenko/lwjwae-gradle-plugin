package dev.ivchenko.lwjwae.gradle.update;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Writes the manifest of a release over the files of every platform in its directory, and signs it
 * with the private key into {@code manifest.json.sig}.
 *
 * <p>A file named {@code <platform>.<extension>}, such as {@code macos-arm64.zip}, updates that
 * platform, and the manifest names it relative to itself, so the directory works at any URL. The
 * signature is Ed25519 over the bytes of the manifest, in Base64.
 */
@DisableCachingByDefault(because = "Signs with a secret key, which must not reach a build cache")
public abstract class SignUpdateManifest extends DefaultTask {
  /** The files of the release, one for each platform. */
  @InputFiles
  @PathSensitive(PathSensitivity.NAME_ONLY)
  public abstract ConfigurableFileCollection getArtifacts();

  /** The version of the release. */
  @Input
  public abstract Property<String> getVersion();

  /** What changed. */
  @Input
  @Optional
  public abstract Property<String> getNotes();

  /** The oldest version that may keep running. */
  @Input
  @Optional
  public abstract Property<String> getMinimumVersion();

  /** The private key, as Base64 of its PKCS #8 encoding; not an input, so no cache sees it. */
  @Internal
  public abstract Property<String> getPrivateKey();

  /** The manifest, {@code manifest.json}. */
  @OutputFile
  public abstract RegularFileProperty getManifest();

  /** Its signature, {@code manifest.json.sig}. */
  @OutputFile
  public abstract RegularFileProperty getSignature();

  /** Writes and signs the manifest. */
  @TaskAction
  public void sign() throws IOException, GeneralSecurityException {
    if (!this.getPrivateKey().isPresent() || this.getPrivateKey().get().isBlank()) {
      throw new GradleException(
          "No private key to sign the manifest: set LWJWAE_UPDATE_PRIVATE_KEY or the Gradle"
              + " property lwjwae.updatePrivateKey; generateUpdateKeys makes one");
    }
    List<Path> files = this.getArtifacts().getFiles().stream().map(File::toPath).sorted().toList();
    if (files.isEmpty()) {
      throw new GradleException("No files of a release: run packageUpdate on each platform first");
    }
    List<String> artifacts = new ArrayList<>();
    for (Path file : files) {
      String name = file.getFileName().toString();
      int dot = name.indexOf('.');
      String platform = dot < 0 ? name : name.substring(0, dot);
      artifacts.add(
          "    %s: {\"url\": %s, \"sha256\": \"%s\", \"size\": %d}"
              .formatted(
                  SignUpdateManifest.json(platform),
                  SignUpdateManifest.json(name),
                  SignUpdateManifest.sha256(file),
                  Files.size(file)));
    }
    StringBuilder manifest = new StringBuilder("{\n");
    manifest.append("  \"version\": ").append(SignUpdateManifest.json(this.getVersion().get()));
    if (this.getNotes().isPresent()) {
      manifest.append(",\n  \"notes\": ").append(SignUpdateManifest.json(this.getNotes().get()));
    }
    if (this.getMinimumVersion().isPresent()) {
      manifest
          .append(",\n  \"minimumVersion\": ")
          .append(SignUpdateManifest.json(this.getMinimumVersion().get()));
    }
    manifest.append(",\n  \"artifacts\": {\n").append(String.join(",\n", artifacts));
    manifest.append("\n  }\n}\n");
    byte[] bytes = manifest.toString().getBytes(StandardCharsets.UTF_8);

    PrivateKey key =
        KeyFactory.getInstance("Ed25519")
            .generatePrivate(
                new PKCS8EncodedKeySpec(
                    Base64.getDecoder().decode(this.getPrivateKey().get().strip())));
    Signature signer = Signature.getInstance("Ed25519");
    signer.initSign(key);
    signer.update(bytes);
    Files.write(this.getManifest().get().getAsFile().toPath(), bytes);
    Files.writeString(
        this.getSignature().get().getAsFile().toPath(),
        Base64.getEncoder().encodeToString(signer.sign()) + "\n",
        StandardCharsets.US_ASCII);
  }

  private static String sha256(Path file) throws IOException, GeneralSecurityException {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream in = Files.newInputStream(file)) {
      byte[] buffer = new byte[64 * 1024];
      for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
        digest.update(buffer, 0, read);
      }
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  /** {@code text} as a JSON string. */
  static String json(String text) {
    StringBuilder out = new StringBuilder("\"");
    for (char character : text.toCharArray()) {
      switch (character) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (character < 0x20) {
            out.append("\\u%04x".formatted((int) character));
          } else {
            out.append(character);
          }
        }
      }
    }
    return out.append('"').toString();
  }
}
