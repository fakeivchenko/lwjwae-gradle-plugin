package dev.ivchenko.lwjwae.gradle.macos;

import dev.ivchenko.lwjwae.gradle.Associations;
import dev.ivchenko.lwjwae.gradle.LwjwaeExtension;
import dev.ivchenko.lwjwae.gradle.LwjwaeLayout;
import dev.ivchenko.lwjwae.gradle.util.Platform;
import lombok.experimental.UtilityClass;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

/**
 * Everything the plugin does for macOS: the defaults of {@code macos {}} and {@code packaging.dmg
 * {}}, the {@code Info.plist} that goes into the executable, and the {@code .app} bundle and the
 * disk image around it.
 */
@UtilityClass
public class MacOsConfiguration {
  /**
   * Applies the defaults and registers the tasks.
   *
   * @return The {@code Info.plist} to embed into the executable: the one of the build script, or
   *     the generated one.
   */
  public Provider<RegularFile> configure(
      Project project, LwjwaeExtension extension, LwjwaeLayout layout) {
    MacOsConfiguration.applyDefaults(project, extension, layout);
    MacOsExtension macos = extension.getMacos();
    TaskProvider<GenerateInfoPlist> generated =
        project
            .getTasks()
            .register(
                "generateInfoPlist",
                GenerateInfoPlist.class,
                task -> {
                  task.setDescription("Writes the Info.plist embedded into the macOS executable.");
                  task.setGroup("build");
                  task.getBundleIdentifier().set(macos.getBundleIdentifier());
                  task.getBundleName().set(macos.getBundleName());
                  task.getVersion().set(macos.getVersion());
                  Associations.wire(task, extension);
                  task.getInfoPlist().set(layout.file("macos/Info.plist"));
                });
    Provider<RegularFile> infoPlist =
        macos.getInfoPlist().orElse(generated.flatMap(GenerateInfoPlist::getInfoPlist));
    MacOsConfiguration.registerPackageTasks(project, extension, layout, infoPlist);
    return infoPlist;
  }

  private void applyDefaults(Project project, LwjwaeExtension extension, LwjwaeLayout layout) {
    MacOsExtension macos = extension.getMacos();
    macos
        .getBundleIdentifier()
        .convention(
            project.provider(
                () ->
                    MacOsConfiguration.bundleIdentifier(
                        project.getGroup().toString(), project.getName())));
    macos.getBundleName().convention(extension.getDisplayName());
    macos.getVersion().convention(layout.projectVersion());
    macos.getHardenedRuntime().convention(true);
    macos
        .getNotarization()
        .getPassword()
        .convention(
            project
                .getProviders()
                .gradleProperty("lwjwae.notaryPassword")
                .orElse(project.getProviders().environmentVariable("LWJWAE_NOTARY_PASSWORD")));

    DmgExtension dmg = extension.getPackaging().getDmg();
    dmg.getEnabled().convention(false);
    dmg.getVolumeName().convention(macos.getBundleName());
  }

  /**
   * {@code group.name} as a bundle identifier, which may hold only letters, digits, hyphens, and
   * dots: every other character becomes a hyphen, as in {@code com.example.my-app} of {@code
   * my_app}.
   */
  public String bundleIdentifier(String group, String name) {
    String identifier = group.isEmpty() ? name : group + "." + name;
    return identifier.replaceAll("[^A-Za-z0-9.-]", "-");
  }

  private void registerPackageTasks(
      Project project,
      LwjwaeExtension extension,
      LwjwaeLayout layout,
      Provider<RegularFile> infoPlist) {
    MacOsExtension macos = extension.getMacos();
    TaskProvider<PackageMacApp> app =
        project
            .getTasks()
            .register(
                "packageApp",
                PackageMacApp.class,
                task -> {
                  task.setDescription("Lays out the macOS .app bundle.");
                  task.setGroup("distribution");
                  task.dependsOn("nativeCompile");
                  task.getExecutable().set(layout.executable());
                  task.getInfoPlist().set(infoPlist);
                  task.getIcon().set(extension.getIcon());
                  task.getImageName().set(extension.getImageName());
                  MacSigning.wire(task, macos);
                  task.getBundle()
                      .set(
                          layout.distributionDirectory(
                              macos.getBundleName().map(name -> name + ".app")));
                });

    DmgExtension dmg = extension.getPackaging().getDmg();
    project
        .getTasks()
        .register(
            "packageDmg",
            PackageDmg.class,
            task -> {
              task.setDescription("Builds the macOS disk image.");
              task.setGroup("distribution");
              task.onlyIf(_ -> Platform.isMacOs());
              task.getBundle().set(app.flatMap(PackageMacApp::getBundle));
              task.getVolumeName().set(dmg.getVolumeName());
              MacSigning.wire(task, macos);
              task.getDiskImage()
                  .set(
                      layout.distributionFile(
                          macos
                              .getBundleName()
                              .zip(
                                  layout.projectVersion(),
                                  (name, version) -> name + "-" + version + ".dmg")));
            });
  }
}
