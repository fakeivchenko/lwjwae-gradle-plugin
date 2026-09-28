package dev.ivchenko.lwjwae.gradle.windows;

import dev.ivchenko.lwjwae.gradle.util.Executables;
import java.io.File;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.stream.Stream;
import javax.inject.Inject;
import lombok.SneakyThrows;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.work.DisableCachingByDefault;

/**
 * Runs {@code rc.exe} from the Windows SDK over a resource script. The {@code .res} file that it
 * produces is passed to the linker.
 *
 * <p>The compiler is taken from the {@code PATH} when the build runs inside a Visual Studio prompt,
 * and from the newest installed Windows SDK otherwise, which is also where {@code native-image}
 * finds its own toolchain.
 */
@DisableCachingByDefault(because = "The script embeds an absolute path, and rc.exe differs per SDK")
public abstract class CompileWindowsResources extends DefaultTask {
  /** The {@code .rc} script. */
  @InputFile
  @PathSensitive(PathSensitivity.ABSOLUTE)
  public abstract RegularFileProperty getScript();

  /** The {@code .res} file to write. */
  @OutputFile
  public abstract RegularFileProperty getResource();

  /** Gradle's process runner. */
  @Inject
  protected abstract ExecOperations getExecOperations();

  /** Compiles the script. */
  @TaskAction
  @SneakyThrows
  public void compile() {
    File script = this.getScript().get().getAsFile();
    File resource = this.getResource().get().getAsFile();
    Files.createDirectories(resource.getParentFile().toPath());
    this.getExecOperations()
        .exec(
            spec -> {
              spec.setExecutable(CompileWindowsResources.resourceCompiler());
              spec.setWorkingDir(script.getParentFile());
              spec.args("/nologo", "/fo", resource.getAbsolutePath(), script.getName());
            });
  }

  /**
   * Finds {@code rc.exe}: on the {@code PATH} first, then in the newest Windows 10 SDK.
   *
   * @throws GradleException If there is none.
   */
  static String resourceCompiler() {
    return Executables.onPath("rc.exe")
        .map(File::getAbsolutePath)
        .orElseGet(CompileWindowsResources::resourceCompilerOfSdk);
  }

  /**
   * Finds {@code rc.exe} in the newest Windows 10 SDK.
   *
   * @throws GradleException If there is none.
   */
  private static String resourceCompilerOfSdk() {
    String programFiles = System.getenv("ProgramFiles(x86)");
    File kits =
        new File(
            programFiles == null ? "C:\\Program Files (x86)" : programFiles,
            "Windows Kits\\10\\bin");
    File[] versions =
        kits.listFiles(file -> file.isDirectory() && file.getName().startsWith("10."));
    return Stream.of(versions == null ? new File[0] : versions)
        .sorted(Comparator.comparing(File::getName).reversed())
        .map(version -> new File(version, "x64\\rc.exe"))
        .filter(File::isFile)
        .map(File::getAbsolutePath)
        .findFirst()
        .orElseThrow(
            () ->
                new GradleException(
                    "rc.exe not found: install the Windows SDK or run from a Visual Studio"
                        + " prompt"));
  }
}
