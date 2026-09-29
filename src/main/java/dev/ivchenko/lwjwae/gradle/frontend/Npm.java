package dev.ivchenko.lwjwae.gradle.frontend;

import dev.ivchenko.lwjwae.gradle.util.Executables;
import dev.ivchenko.lwjwae.gradle.util.Platform;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.gradle.api.GradleException;

/** The command line of npm on this platform. */
@UtilityClass
class Npm {
  /**
   * {@code npm ARGS}: through {@code cmd /c} on Windows, where npm is a batch file that a process
   * can't start by itself.
   *
   * @throws GradleException If npm isn't on the {@code PATH}.
   */
  List<String> command(String... args) {
    boolean windows = Platform.isWindows();
    if (Executables.onPath(windows ? "npm.cmd" : "npm").isEmpty()) {
      throw new GradleException(
          "The frontend needs npm, which comes with Node.js: install it, and put it on the PATH.");
    }
    List<String> command = new ArrayList<>(windows ? List.of("cmd", "/c", "npm") : List.of("npm"));
    command.addAll(List.of(args));
    return command;
  }
}
