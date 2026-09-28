package dev.ivchenko.lwjwae.gradle.util;

import lombok.experimental.UtilityClass;

/** Text for the XML files that the tasks write: property lists and WiX sources. */
@UtilityClass
public class Xml {
  /** Escapes {@code text} for an element or an attribute value in double quotes. */
  public String escape(String text) {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
