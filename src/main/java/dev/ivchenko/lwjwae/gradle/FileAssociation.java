package dev.ivchenko.lwjwae.gradle;

import java.io.Serial;
import java.io.Serializable;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A type of file that the application opens: what a package registers so that the file manager
 * offers the application for it, and opens the file with it on a double click.
 *
 * @param extension The extension, without the dot, in lowercase: {@code note}.
 * @param mimeType The media type, such as {@code application/x-notes-note}, or {@code null} for
 *     {@code application/x-IMAGE-EXTENSION}.
 * @param description What the file manager calls the type, such as {@code Note}, or {@code null}
 *     for the display name followed by {@code document}.
 */
public record FileAssociation(String extension, String mimeType, String description)
    implements Serializable {
  @Serial private static final long serialVersionUID = 1L;

  private static final Pattern EXTENSION = Pattern.compile("[a-z0-9]+");
  private static final Pattern MIME_TYPE = Pattern.compile("[a-z0-9.+-]+/[a-z0-9.+-]+");

  /**
   * Checks the parts, takes a leading dot off the extension, and lowercases it and the media type.
   *
   * @throws IllegalArgumentException If the extension isn't letters and digits, or the media type
   *     isn't {@code type/subtype}.
   */
  public FileAssociation {
    extension = extension.startsWith(".") ? extension.substring(1) : extension;
    extension = extension.toLowerCase(Locale.ROOT);
    if (!EXTENSION.matcher(extension).matches()) {
      throw new IllegalArgumentException("An extension is letters and digits: " + extension);
    }
    if (mimeType != null) {
      mimeType = mimeType.toLowerCase(Locale.ROOT);
      if (!MIME_TYPE.matcher(mimeType).matches()) {
        throw new IllegalArgumentException("A media type is type/subtype: " + mimeType);
      }
    }
  }

  /** This type with the defaults of the application filled in. */
  FileAssociation resolve(String imageName, String displayName) {
    return new FileAssociation(
        this.extension,
        this.mimeType != null
            ? this.mimeType
            : "application/x-"
                + imageName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.+-]", "-")
                + "-"
                + this.extension,
        this.description != null ? this.description : displayName + " document");
  }
}
