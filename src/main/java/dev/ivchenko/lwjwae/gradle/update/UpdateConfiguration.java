package dev.ivchenko.lwjwae.gradle.update;

import dev.ivchenko.lwjwae.gradle.LwjwaeExtension;
import dev.ivchenko.lwjwae.gradle.LwjwaeLayout;
import dev.ivchenko.lwjwae.gradle.linux.PackageAppImage;
import dev.ivchenko.lwjwae.gradle.macos.PackageMacApp;
import dev.ivchenko.lwjwae.gradle.util.Platform;
import dev.ivchenko.lwjwae.gradle.windows.PackageMsi;
import java.util.List;
import java.util.concurrent.Callable;
import lombok.experimental.UtilityClass;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

/**
 * The updates of the application: {@code generateUpdateProperties}, which puts the manifest URL and
 * the public key into the resources, {@code generateUpdateKeys}, {@code packageUpdate}, and {@code
 * updateManifest}. See {@link UpdatesExtension}.
 */
@UtilityClass
public class UpdateConfiguration {
  private static final String MANIFEST = "manifest.json";

  /** Applies the defaults and registers the tasks. */
  public void configure(Project project, LwjwaeExtension extension, LwjwaeLayout layout) {
    UpdatesExtension updates = extension.getUpdates();
    updates
        .getPrivateKey()
        .convention(
            project
                .getProviders()
                .gradleProperty("lwjwae.updatePrivateKey")
                .orElse(project.getProviders().environmentVariable("LWJWAE_UPDATE_PRIVATE_KEY")));
    updates.getDirectory().convention(layout.directory("update"));
    Provider<Boolean> enabled = updates.getManifestUrl().map(url -> !url.isBlank()).orElse(false);

    Provider<Directory> resources = layout.directory("update-resources");
    TaskProvider<GenerateUpdateProperties> properties =
        project
            .getTasks()
            .register(
                "generateUpdateProperties",
                GenerateUpdateProperties.class,
                task -> {
                  task.setDescription(
                      "Puts the manifest URL and the public key into the resources.");
                  task.onlyIf(_ -> enabled.get());
                  task.getManifestUrl().set(updates.getManifestUrl());
                  task.getPublicKey().set(updates.getPublicKey());
                  task.getVersion().set(layout.projectVersion());
                  task.getOutputDirectory().set(resources);
                });
    // A collection rather than the directory itself: without a manifest URL, it adds nothing.
    ConfigurableFileCollection generated =
        project.files((Callable<Object>) () -> enabled.get() ? resources.get() : List.of());
    generated.builtBy(properties);
    project
        .getExtensions()
        .getByType(JavaPluginExtension.class)
        .getSourceSets()
        .named(
            SourceSet.MAIN_SOURCE_SET_NAME,
            sourceSet -> sourceSet.getResources().srcDir(generated));

    project
        .getTasks()
        .register(
            "generateUpdateKeys",
            GenerateUpdateKeys.class,
            task -> {
              task.setDescription("Prints a new pair of keys that sign the updates.");
              task.setGroup("distribution");
            });

    project
        .getTasks()
        .register(
            "packageUpdate",
            PackageUpdate.class,
            task -> {
              task.setDescription("Puts the package of this platform into the release directory.");
              task.setGroup("distribution");
              if (Platform.isWindows()) {
                task.getPackageFile()
                    .set(
                        project
                            .getTasks()
                            .named("packageMsi", PackageMsi.class)
                            .flatMap(PackageMsi::getInstaller));
              } else if (Platform.isMacOs()) {
                task.getBundle()
                    .set(
                        project
                            .getTasks()
                            .named("packageApp", PackageMacApp.class)
                            .flatMap(PackageMacApp::getBundle));
              } else {
                task.getPackageFile()
                    .set(
                        project
                            .getTasks()
                            .named("packageAppImage", PackageAppImage.class)
                            .flatMap(PackageAppImage::getAppImage));
              }
              task.getUpdateFile()
                  .set(
                      updates
                          .getDirectory()
                          .file(
                              Platform.updateKey()
                                  + (Platform.isWindows()
                                      ? ".msi"
                                      : Platform.isMacOs() ? ".zip" : ".AppImage")));
            });

    project
        .getTasks()
        .register(
            "updateManifest",
            SignUpdateManifest.class,
            task -> {
              task.setDescription("Writes and signs the manifest of the release directory.");
              task.setGroup("distribution");
              // Usually a job of its own over the files of every platform; in one build, it
              // takes the file of this one.
              task.mustRunAfter("packageUpdate");
              task.getArtifacts()
                  .from(
                      updates
                          .getDirectory()
                          .map(
                              directory ->
                                  directory
                                      .getAsFileTree()
                                      .matching(
                                          files ->
                                              files.exclude(MANIFEST, MANIFEST + ".sig", ".*"))));
              task.getVersion().set(layout.projectVersion());
              task.getNotes().set(updates.getNotes());
              task.getMinimumVersion().set(updates.getMinimumVersion());
              task.getPrivateKey().set(updates.getPrivateKey());
              task.getManifest().set(updates.getDirectory().file(MANIFEST));
              task.getSignature().set(updates.getDirectory().file(MANIFEST + ".sig"));
            });
  }
}
