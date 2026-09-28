package dev.ivchenko.lwjwae.gradle.linux;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Writes desktop entries that a line break in a name can't break. */
class DesktopEntryTest {
  @Test
  void escapesStringValues() {
    String entry =
        DesktopEntry.render(
            "Demo\nApp", "Back\\slash\tand tab", "demo", "demo", List.of("Utility"));
    Assertions.assertTrue(entry.contains("Name=Demo\\nApp\n"), entry);
    Assertions.assertTrue(entry.contains("Comment=Back\\\\slash\\tand tab\n"), entry);
  }

  @Test
  void leavesOutCommentThatRepeatsTheName() {
    String entry = DesktopEntry.render("Demo", "Demo", "demo", "demo", List.of("Utility"));
    Assertions.assertFalse(entry.contains("Comment="), entry);
  }
}
