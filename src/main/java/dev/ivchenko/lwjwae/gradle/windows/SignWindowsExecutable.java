package dev.ivchenko.lwjwae.gradle.windows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.inject.Inject;
import lombok.SneakyThrows;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.work.DisableCachingByDefault;

/**
 * Signs a copy of the native executable, which the installer then packs: the executable of {@code
 * nativeCompile} stays as it was, so a rebuild of the image doesn't sign twice.
 */
@DisableCachingByDefault(because = "A signature carries a time stamp of the moment it was made")
public abstract class SignWindowsExecutable extends DefaultTask {
  /** The executable to sign. */
  @InputFile
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getExecutable();

  /** The certificate as a {@code .pfx} file, or nothing for a thumbprint. */
  @InputFile
  @Optional
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getCertificateFile();

  /** The password of the certificate file, kept out of the inputs of the task. */
  @Internal
  public abstract Property<String> getCertificatePassword();

  /** The thumbprint of a certificate of the store, or nothing for a file. */
  @Input
  @Optional
  public abstract Property<String> getCertificateThumbprint();

  /** The time stamp server. */
  @Input
  public abstract Property<String> getTimestampUrl();

  /** What the dialog of User Account Control calls the executable. */
  @Input
  public abstract Property<String> getFileDescription();

  /** The signed copy. */
  @OutputFile
  public abstract RegularFileProperty getSigned();

  /** Gradle's process runner. */
  @Inject
  protected abstract ExecOperations getExecOperations();

  /** Copies the executable and signs the copy. */
  @TaskAction
  @SneakyThrows
  public void sign() {
    Path signed = this.getSigned().get().getAsFile().toPath();
    Files.createDirectories(signed.getParent());
    Files.copy(
        this.getExecutable().get().getAsFile().toPath(),
        signed,
        StandardCopyOption.REPLACE_EXISTING);
    Signtool.sign(
        this.getExecOperations(),
        Signtool.arguments(
            this.getCertificateFile().isPresent()
                ? this.getCertificateFile().get().getAsFile()
                : null,
            this.getCertificatePassword().getOrNull(),
            this.getCertificateThumbprint().getOrNull(),
            this.getTimestampUrl().get(),
            this.getFileDescription().get(),
            signed.toFile()));
  }
}
