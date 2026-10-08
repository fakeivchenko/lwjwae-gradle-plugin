package dev.ivchenko.lwjwae.gradle.macos;

import java.io.File;
import java.nio.file.Files;
import javax.inject.Inject;
import lombok.SneakyThrows;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.work.DisableCachingByDefault;

/**
 * Wraps the {@code .app} bundle into a compressed disk image with {@code hdiutil}, the way most
 * macOS applications are downloaded. With a signing identity, {@code codesign} signs the image too,
 * and with notarization set, the notary service checks it and its ticket is stapled to it, so that
 * Gatekeeper opens it without a network. macOS only.
 */
@DisableCachingByDefault(because = "Runs hdiutil over a build output")
public abstract class PackageDmg extends DefaultTask implements MacSigning {
  /** The bundle to put on the image. */
  @InputDirectory
  @PathSensitive(PathSensitivity.NONE)
  public abstract DirectoryProperty getBundle();

  /** The name of the mounted volume. */
  @Input
  public abstract Property<String> getVolumeName();

  /** The disk image to write. */
  @OutputFile
  public abstract RegularFileProperty getDiskImage();

  /** Gradle's process runner. */
  @Inject
  protected abstract ExecOperations getExecOperations();

  /** Builds the image. */
  @TaskAction
  @SneakyThrows
  public void build() {
    File output = this.getDiskImage().get().getAsFile();
    Files.createDirectories(output.getParentFile().toPath());
    Files.deleteIfExists(output.toPath());
    this.getExecOperations()
        .exec(
            spec -> {
              spec.setExecutable("hdiutil");
              spec.args(
                  "create",
                  "-volname",
                  this.getVolumeName().get(),
                  "-srcfolder",
                  this.getBundle().get().getAsFile().getAbsolutePath(),
                  "-ov",
                  "-format",
                  "UDZO",
                  output.getAbsolutePath());
            });
    if (!this.getSigningIdentity().isPresent()) {
      return;
    }
    // A disk image is signed without the hardened runtime, which only executables take.
    Notarytool.codesign(
        this.getExecOperations(),
        Notarytool.codesignArguments(this.getSigningIdentity().get(), false, null, output));
    NotaryCredentials notarization = this.notaryCredentials();
    if (notarization.isSet()) {
      Notarytool.notarizeAndStaple(this.getExecOperations(), notarization, output, output);
    }
  }
}
