package dev.ivchenko.lwjwae.gradle;

import dev.ivchenko.lwjwae.gradle.util.Platform;
import dev.ivchenko.lwjwae.gradle.windows.CompileWindowsResources;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.graalvm.buildtools.gradle.dsl.GraalVMExtension;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

/**
 * Configures the run of the application: {@code --enable-native-access} for {@code gradle run}, and
 * the {@code main} binary of the GraalVM Native Build Tools plugin: the resources of the project,
 * and the linker options that give the Windows and macOS executables their icon, version block, and
 * property list.
 */
@UtilityClass
class NativeImageConfiguration {
  /** What the FFM API needs, on the JVM and in the native image alike. */
  final String NATIVE_ACCESS = "--enable-native-access=ALL-UNNAMED";

  /**
   * A GUI subsystem executable opens no console. The entry point of {@code native-image} stays
   * {@code main}, so the linker must not look for {@code WinMain}.
   */
  private final List<String> WINDOWS_GUI_SUBSYSTEM =
      List.of(
          "-H:NativeLinkerOption=/SUBSYSTEM:WINDOWS",
          "-H:NativeLinkerOption=/ENTRY:mainCRTStartup");

  /**
   * Adds the JVM argument to the {@code application} plugin, if the project has it, and configures
   * {@code nativeCompile}.
   *
   * @param windowsResources The {@code .res} file of the Windows executable.
   * @param infoPlist The {@code Info.plist} of the macOS executable.
   */
  void configure(
      Project project,
      LwjwaeExtension extension,
      TaskProvider<CompileWindowsResources> windowsResources,
      Provider<RegularFile> infoPlist) {
    project
        .getPluginManager()
        .withPlugin(
            "application",
            // After the build script: an assignment of applicationDefaultJvmArgs there replaces the
            // list, and would take the flag away if it went in any earlier.
            _ ->
                project.afterEvaluate(
                    _ -> {
                      JavaApplication application =
                          project.getExtensions().getByType(JavaApplication.class);
                      List<String> jvmArgs = new ArrayList<>();
                      application.getApplicationDefaultJvmArgs().forEach(jvmArgs::add);
                      if (!jvmArgs.contains(NATIVE_ACCESS)) {
                        jvmArgs.add(NATIVE_ACCESS);
                        application.setApplicationDefaultJvmArgs(jvmArgs);
                      }
                    }));

    GraalVMExtension graal = project.getExtensions().getByType(GraalVMExtension.class);
    graal.getToolchainDetection().set(extension.getToolchainDetection());
    graal
        .getBinaries()
        .named(
            "main",
            options -> {
              options.getImageName().set(extension.getImageName());
              options.resources(
                  embedded ->
                      embedded
                          .getDetectionOptions()
                          .getEnabled()
                          .set(extension.getEmbedResources()));
              options
                  .getBuildArgs()
                  .addAll(
                      project.provider(
                          () ->
                              NativeImageConfiguration.buildArgs(
                                  extension, windowsResources, infoPlist)));
            });
    project
        .getTasks()
        .named(
            "nativeCompile",
            task -> {
              if (Platform.isWindows()) {
                task.dependsOn(windowsResources);
              }
              if (Platform.isMacOs()) {
                task.dependsOn(infoPlist);
              }
            });
  }

  private List<String> buildArgs(
      LwjwaeExtension extension,
      TaskProvider<CompileWindowsResources> windowsResources,
      Provider<RegularFile> infoPlist) {
    List<String> args = new ArrayList<>();
    args.add(NATIVE_ACCESS);
    if (extension.getOptimizeForSize().get()) {
      args.add("-Os");
    }
    String heap = extension.getMaxHeapSize().getOrElse("");
    if (!heap.isEmpty()) {
      args.add("-R:MaxHeapSize=" + heap);
    }

    List<String> linker = new ArrayList<>();
    if (Platform.isWindows()) {
      String resource = windowsResources.get().getResource().get().getAsFile().getAbsolutePath();
      linker.add("-H:NativeLinkerOption=" + resource);
      if (!extension.getWindows().getConsole().get()) {
        linker.addAll(WINDOWS_GUI_SUBSYSTEM);
      }
    }
    if (Platform.isMacOs()) {
      linker.add(
          "-H:NativeLinkerOption=-Wl,-sectcreate,__TEXT,__info_plist,"
              + infoPlist.get().getAsFile().getAbsolutePath());
    }
    if (!linker.isEmpty()) {
      args.add("-H:+UnlockExperimentalVMOptions");
      args.addAll(linker);
      args.add("-H:-UnlockExperimentalVMOptions");
    }
    args.addAll(extension.getBuildArgs().get());
    return args;
  }
}
