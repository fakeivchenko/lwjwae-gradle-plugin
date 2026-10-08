package dev.ivchenko.lwjwae.gradle.windows;

import dev.ivchenko.lwjwae.gradle.util.Executables;
import java.io.File;
import java.util.Comparator;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.gradle.api.GradleException;

/** Finds the tools of the Windows SDK: {@code rc.exe} and {@code signtool.exe}. */
@UtilityClass
public class WindowsSdk {
  /**
   * The tool {@code name}: on the {@code PATH} first, which a Visual Studio prompt sets, then in
   * the {@code x64} directory of the newest Windows 10 SDK.
   *
   * @throws GradleException If there is none.
   */
  public String tool(String name) {
    return Executables.onPath(name)
        .map(File::getAbsolutePath)
        .orElseGet(() -> WindowsSdk.ofNewestKit(name));
  }

  private String ofNewestKit(String name) {
    String programFiles = System.getenv("ProgramFiles(x86)");
    File kits =
        new File(
            programFiles == null ? "C:\\Program Files (x86)" : programFiles,
            "Windows Kits\\10\\bin");
    File[] versions =
        kits.listFiles(file -> file.isDirectory() && file.getName().startsWith("10."));
    return Stream.of(versions == null ? new File[0] : versions)
        .sorted(Comparator.comparing(File::getName).reversed())
        .map(version -> new File(version, "x64\\" + name))
        .filter(File::isFile)
        .map(File::getAbsolutePath)
        .findFirst()
        .orElseThrow(
            () ->
                new GradleException(
                    name
                        + " not found: install the Windows SDK or run from a Visual Studio"
                        + " prompt"));
  }
}
