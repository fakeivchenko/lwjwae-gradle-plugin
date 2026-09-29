package dev.ivchenko.lwjwae.gradle;

import dev.ivchenko.lwjwae.gradle.frontend.FrontendConfiguration;
import dev.ivchenko.lwjwae.gradle.linux.LinuxConfiguration;
import dev.ivchenko.lwjwae.gradle.macos.MacOsConfiguration;
import dev.ivchenko.lwjwae.gradle.util.Platform;
import dev.ivchenko.lwjwae.gradle.windows.CompileWindowsResources;
import dev.ivchenko.lwjwae.gradle.windows.WindowsConfiguration;
import java.util.List;
import org.graalvm.buildtools.gradle.NativeImagePlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

/**
 * Sets a Java project up as an lwjwae application.
 *
 * <ul>
 *   <li>The library: {@code lwjwae-core} to compile against, the backends of the chosen platforms
 *       and, when asked, a codec at runtime. See {@link LwjwaeExtension}.
 *   <li>{@code --enable-native-access=ALL-UNNAMED}, for the JVM run and for the native image.
 *   <li>A GraalVM native image through the Native Build Tools plugin, with {@code -Os} and a capped
 *       heap. {@code graalvmNative {}} stays available for everything else.
 *   <li>Windows: a GUI subsystem executable with an icon and a version block, compiled by {@code
 *       rc.exe}, and an {@code .msi} installer.
 *   <li>macOS: an embedded {@code Info.plist} with the bundle identifier that the helper processes
 *       of WebKit need, an {@code .app} bundle, and a {@code .dmg}.
 *   <li>Linux: a {@code .deb} package and an AppImage.
 * </ul>
 *
 * <p>This class only puts the parts together; each platform package configures its own tasks and
 * defaults. Everything is wired through providers, so the {@code lwjwae {}} block can come after
 * the {@code plugins {}} block: Gradle reads the values when it needs them, not when the plugin is
 * applied.
 */
public class LwjwaePlugin implements Plugin<Project> {
  private static final String YASSON = "org.eclipse:yasson";
  private static final String YASSON_VERSION = "3.0.4";

  @Override
  public void apply(Project project) {
    project.getPluginManager().apply(JavaPlugin.class);
    project.getPluginManager().apply(NativeImagePlugin.class);
    LwjwaeExtension extension = project.getExtensions().create("lwjwae", LwjwaeExtension.class);
    LwjwaeLayout layout = new LwjwaeLayout(project, extension);
    LwjwaePlugin.applyDefaults(project, extension);

    DependencyConfiguration.configure(project, extension);
    TaskProvider<CompileWindowsResources> resources =
        WindowsConfiguration.configure(project, extension, layout);
    Provider<RegularFile> infoPlist = MacOsConfiguration.configure(project, extension, layout);
    LinuxConfiguration.configure(project, extension, layout);
    FrontendConfiguration.configure(project, extension, layout);
    NativeImageConfiguration.configure(project, extension, resources, infoPlist);
    LwjwaePlugin.registerPackageAll(project, extension);
  }

  /** The defaults that aren't tied to one platform; each platform applies its own. */
  private static void applyDefaults(Project project, LwjwaeExtension extension) {
    extension.getManaged().convention(true);
    extension.getVersion().convention(DefaultVersions.library());
    extension.getCodecsVersion().convention(DefaultVersions.codecs());
    extension.getBackends().convention(Backends.CURRENT_PLATFORM);
    extension.getLinuxBackend().convention(LinuxBackend.GTK3);
    extension.getCodec().convention(Codec.NONE);
    extension.getJsonbProvider().convention(YASSON);
    extension.getJsonbProviderVersion().convention(YASSON_VERSION);

    extension.getImageName().convention(project.getName());
    extension.getDisplayName().convention(extension.getImageName());
    RegularFile icon = project.getLayout().getProjectDirectory().file(LwjwaeExtension.DEFAULT_ICON);
    extension.getIcon().convention(project.provider(() -> icon.getAsFile().isFile() ? icon : null));
    extension.getEmbedResources().convention(true);
    extension.getOptimizeForSize().convention(true);
    extension.getMaxHeapSize().convention("64m");
    extension.getToolchainDetection().convention(false);

    PackagingExtension packaging = extension.getPackaging();
    packaging
        .getDescription()
        .convention(
            project.provider(
                () ->
                    project.getDescription() == null || project.getDescription().isBlank()
                        ? extension.getDisplayName().get()
                        : project.getDescription()));
    packaging.getCategories().convention(List.of("Utility"));
  }

  /** {@code packageAll}: the enabled packages that the operating system of the build can make. */
  private static void registerPackageAll(Project project, LwjwaeExtension extension) {
    PackagingExtension packaging = extension.getPackaging();
    project
        .getTasks()
        .register(
            "packageAll",
            task -> {
              task.setDescription("Builds every enabled package that this operating system can.");
              task.setGroup("distribution");
              if (Platform.isLinux()) {
                task.dependsOn(
                    LwjwaePlugin.ifEnabled(packaging.getDeb().getEnabled(), "packageDeb"));
                task.dependsOn(
                    LwjwaePlugin.ifEnabled(packaging.getArch().getEnabled(), "packageArch"));
                task.dependsOn(
                    LwjwaePlugin.ifEnabled(
                        packaging.getAppImage().getEnabled(), "packageAppImage"));
              }
              if (Platform.isMacOs()) {
                task.dependsOn(
                    LwjwaePlugin.ifEnabled(packaging.getDmg().getEnabled(), "packageDmg"));
              }
              if (Platform.isWindows()) {
                task.dependsOn(
                    LwjwaePlugin.ifEnabled(packaging.getMsi().getEnabled(), "packageMsi"));
              }
            });
  }

  private static Provider<List<String>> ifEnabled(Provider<Boolean> enabled, String task) {
    return enabled.map(on -> on ? List.of(task) : List.of());
  }
}
