package dev.ivchenko.lwjwae.gradle.windows;

import java.io.File;
import java.nio.file.Files;
import javax.inject.Inject;
import lombok.SneakyThrows;
import org.gradle.api.DefaultTask;
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
              spec.setExecutable(WindowsSdk.tool("rc.exe"));
              spec.setWorkingDir(script.getParentFile());
              spec.args("/nologo", "/fo", resource.getAbsolutePath(), script.getName());
            });
  }
}
