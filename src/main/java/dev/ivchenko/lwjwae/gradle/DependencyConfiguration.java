package dev.ivchenko.lwjwae.gradle;

import dev.ivchenko.lwjwae.gradle.util.Platform;
import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;

/**
 * Adds the library: {@code lwjwae-core} to compile against, and the backends and the codec at
 * runtime, at the versions of the {@code lwjwae {}} block.
 *
 * <p>A module that the build asks for through a block of its own comes with that block: {@code
 * lwjwae-updater} with a manifest URL in {@code updates {}}. Each joins only from the first release
 * of the library that has it, because a build pinned to an older one would fail to resolve it.
 *
 * <p>The dependencies are added lazily, with {@code addAllLater}, so the block can change the
 * versions, the backends, or the codec after the plugin was applied, and {@code managed = false}
 * takes all of it back.
 */
@UtilityClass
class DependencyConfiguration {
  /** The Maven group of the library and the backends. */
  final String GROUP = "dev.ivchenko.lwjwae";

  /** The Maven group of the codec modules. */
  final String CODECS_GROUP = "dev.ivchenko.lwjwae.codec";

  /** The first release of the library with {@code lwjwae-processor}. */
  final String FIRST_PROCESSOR_VERSION = "0.8.0";

  /**
   * The first release of the library with {@code lwjwae-updater}; the ones before carry the updater
   * in {@code lwjwae-core}.
   */
  final String FIRST_UPDATER_VERSION = "0.13.0";

  /**
   * Adds the dependencies to {@code implementation}, {@code runtimeOnly}, and {@code
   * annotationProcessor}: the processor writes the native-image metadata of the types that cross
   * the bridge, those marked {@code @BridgeType}, from the first release of the library that has
   * it.
   */
  void configure(Project project, LwjwaeExtension extension) {
    DependencyHandler dependencies = project.getDependencies();
    Provider<Boolean> managed = extension.getManaged();
    Provider<Boolean> updates =
        extension.getUpdates().getManifestUrl().map(_ -> true).orElse(false);
    project
        .getConfigurations()
        .named(
            JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME,
            configuration ->
                configuration
                    .getDependencies()
                    .addAllLater(
                        managed.zip(
                            extension.getVersion(),
                            (on, version) ->
                                on
                                    ? DependencyConfiguration.libraryDependencies(
                                        dependencies, version, updates.get())
                                    : List.of())));
    project
        .getConfigurations()
        .named(
            JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME,
            configuration ->
                configuration
                    .getDependencies()
                    .addAllLater(
                        managed.map(
                            on ->
                                on
                                    ? DependencyConfiguration.runtimeDependencies(
                                        dependencies, extension)
                                    : List.of())));
    project
        .getConfigurations()
        .named(
            JavaPlugin.ANNOTATION_PROCESSOR_CONFIGURATION_NAME,
            configuration ->
                configuration
                    .getDependencies()
                    .addAllLater(
                        managed.zip(
                            extension.getVersion(),
                            (on, version) ->
                                on && DependencyConfiguration.hasProcessor(version)
                                    ? List.of(
                                        dependencies.create(GROUP + ":lwjwae-processor:" + version))
                                    : List.of())));
  }

  /** Whether the release {@code version} of the library has {@code lwjwae-processor}. */
  boolean hasProcessor(String version) {
    return DependencyConfiguration.isAtLeast(version, FIRST_PROCESSOR_VERSION);
  }

  /** Whether the release {@code version} of the library has {@code lwjwae-updater}. */
  boolean hasUpdater(String version) {
    return DependencyConfiguration.isAtLeast(version, FIRST_UPDATER_VERSION);
  }

  /**
   * Whether {@code version} is {@code firstVersion} or later, with missing parts read as zeros. A
   * version that doesn't start with a number, such as {@code main-SNAPSHOT}, counts as later: it's
   * built from the sources, which have every module.
   */
  private boolean isAtLeast(String version, String firstVersion) {
    List<Integer> given = DependencyConfiguration.numbers(version);
    if (given.isEmpty()) {
      return true;
    }
    List<Integer> first = DependencyConfiguration.numbers(firstVersion);
    for (int index = 0; index < first.size(); index++) {
      int part = index < given.size() ? given.get(index) : 0;
      if (part != first.get(index)) {
        return part > first.get(index);
      }
    }
    return true;
  }

  /** The leading numeric parts of {@code version}: {@code [0, 99]} of {@code 0.99.0-local}. */
  private List<Integer> numbers(String version) {
    List<Integer> numbers = new ArrayList<>();
    for (String part : version.split("\\.", -1)) {
      if (!part.matches("\\d+")) {
        break;
      }
      numbers.add(Integer.parseInt(part));
    }
    return numbers;
  }

  /** The backend module of the operating system that runs the build. */
  String currentBackend(LinuxBackend linux) {
    if (Platform.isWindows()) {
      return "lwjwae-windows";
    }
    if (Platform.isMacOs()) {
      return "lwjwae-macos";
    }
    return linux.module();
  }

  private List<Dependency> libraryDependencies(
      DependencyHandler dependencies, String version, boolean updates) {
    List<Dependency> library = new ArrayList<>();
    library.add(dependencies.create(GROUP + ":lwjwae-core:" + version));
    if (updates && DependencyConfiguration.hasUpdater(version)) {
      library.add(dependencies.create(GROUP + ":lwjwae-updater:" + version));
    }
    return library;
  }

  private List<Dependency> runtimeDependencies(
      DependencyHandler dependencies, LwjwaeExtension extension) {
    String version = extension.getVersion().get();
    String codecsVersion = extension.getCodecsVersion().get();
    List<String> coordinates = new ArrayList<>();
    coordinates.addAll(
        switch (extension.getBackends().get()) {
          case ALL ->
              List.of(
                  GROUP + ":" + LinuxBackend.GTK3.module() + ":" + version,
                  GROUP + ":" + LinuxBackend.GTK4.module() + ":" + version,
                  GROUP + ":lwjwae-windows:" + version,
                  GROUP + ":lwjwae-macos:" + version);
          case CURRENT_PLATFORM ->
              List.of(
                  GROUP
                      + ":"
                      + DependencyConfiguration.currentBackend(extension.getLinuxBackend().get())
                      + ":"
                      + version);
          case NONE -> List.<String>of();
        });
    coordinates.addAll(
        switch (extension.getCodec().get()) {
          case JACKSON -> List.of(CODECS_GROUP + ":lwjwae-codec-jackson:" + codecsVersion);
          case GSON -> List.of(CODECS_GROUP + ":lwjwae-codec-gson:" + codecsVersion);
          case JSONB ->
              List.of(
                  CODECS_GROUP + ":lwjwae-codec-jsonb:" + codecsVersion,
                  extension.getJsonbProvider().get()
                      + ":"
                      + extension.getJsonbProviderVersion().get());
          case NONE -> List.<String>of();
        });
    return coordinates.stream().map(dependencies::create).toList();
  }
}
