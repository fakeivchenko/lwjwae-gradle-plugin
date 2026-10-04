package dev.ivchenko.lwjwae.gradle;

import java.util.List;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;

/**
 * The inputs of a task that registers the schemes of links and the types of files of the
 * application, which every package format does in its own way: the desktop entry and the
 * shared-mime-info of Linux, the {@code Info.plist} of macOS, and the registry of Windows.
 */
public interface Associations {
  /** The schemes of links, such as {@code notes}. */
  @Input
  ListProperty<String> getUrlSchemes();

  /** The types of files, with every default filled in. */
  @Input
  ListProperty<FileAssociation> getFileTypes();

  /**
   * Takes the schemes and the types of {@code extension}, with their defaults, into {@code task}.
   */
  static void wire(Associations task, LwjwaeExtension extension) {
    task.getUrlSchemes().set(extension.getUrlSchemes());
    task.getFileTypes()
        .set(
            extension
                .getFileTypes()
                .zip(
                    extension.getImageName().zip(extension.getDisplayName(), List::of),
                    (types, names) ->
                        types.stream()
                            .map(type -> type.resolve(names.get(0), names.get(1)))
                            .toList()));
  }
}
