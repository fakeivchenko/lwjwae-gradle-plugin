package dev.ivchenko.lwjwae.gradle.util;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.gradle.api.GradleException;

/**
 * Renders the one application icon into what each platform wants: scaled PNG files, a Windows
 * {@code .ico}, a macOS {@code .icns}.
 */
@UtilityClass
public class Icons {
  private static final int ICO_HEADER_SIZE = 6;
  private static final int ICO_ENTRY_SIZE = 16;
  private static final int BITMAP_INFO_HEADER_SIZE = 40;
  private static final int PNG_FROM_SIZE = 256;

  /** The {@code .icns} entry type of each size that holds a PNG image. */
  private static final Map<Integer, String> ICNS_TYPES =
      Map.of(
          16, "icp4", 32, "icp5", 64, "icp6", 128, "ic07", 256, "ic08", 512, "ic09", 1024, "ic10");

  /** The sizes that a {@code .icns} file carries. */
  public final List<Integer> ICNS_SIZES = List.of(16, 32, 64, 128, 256, 512);

  /** The sizes that a {@code hicolor} icon theme is usually populated with. */
  public final List<Integer> LINUX_SIZES = List.of(16, 24, 32, 48, 64, 128, 256);

  /**
   * Reads the source image.
   *
   * @throws GradleException If the file isn't an image that ImageIO reads.
   */
  @SneakyThrows
  public BufferedImage read(File source) {
    BufferedImage image = ImageIO.read(source);
    if (image == null) {
      throw new GradleException("Not an image ImageIO can read: " + source);
    }
    return image;
  }

  /**
   * A stand-in for an application without an icon: a rounded square with the initial of {@code
   * name}, at 256 pixels. Menus and launchers show a generic icon for an entry without one, but an
   * AppImage can't be built without it.
   */
  public BufferedImage placeholder(String name) {
    int size = 256;
    BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    try {
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      graphics.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      graphics.setColor(new Color(0x3b5bdb));
      graphics.fillRoundRect(16, 16, size - 32, size - 32, 48, 48);
      String initial = name.isBlank() ? "?" : name.strip().substring(0, 1).toUpperCase(Locale.ROOT);
      graphics.setColor(Color.WHITE);
      graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 144));
      FontMetrics metrics = graphics.getFontMetrics();
      graphics.drawString(
          initial,
          (size - metrics.stringWidth(initial)) / 2,
          (size - metrics.getHeight()) / 2 + metrics.getAscent());
    } finally {
      graphics.dispose();
    }
    return image;
  }

  /** Returns {@code image} scaled to {@code size} by {@code size} pixels. */
  public BufferedImage scale(BufferedImage image, int size) {
    BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = scaled.createGraphics();
    try {
      graphics.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      graphics.drawImage(image, 0, 0, size, size, null);
    } finally {
      graphics.dispose();
    }
    return scaled;
  }

  /** Returns {@code image} as PNG bytes. */
  @SneakyThrows
  public byte[] png(BufferedImage image) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ImageIO.write(image, "png", bytes);
    return bytes.toByteArray();
  }

  /**
   * Returns a Windows {@code .ico} file with {@code image} at every size in {@code sizes}. Entries
   * up to 128 pixels are stored as uncompressed 32-bit bitmaps, which every Windows version reads;
   * the 256-pixel entry is stored as PNG, the form that Windows expects for that size.
   */
  public byte[] ico(BufferedImage image, List<Integer> sizes) {
    List<byte[]> entries =
        sizes.stream()
            .map(
                size ->
                    size >= PNG_FROM_SIZE
                        ? Icons.png(Icons.scale(image, size))
                        : Icons.bitmap(Icons.scale(image, size)))
            .toList();
    int total =
        ICO_HEADER_SIZE
            + ICO_ENTRY_SIZE * sizes.size()
            + entries.stream().mapToInt(entry -> entry.length).sum();
    ByteBuffer out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
    out.putShort((short) 0).putShort((short) 1).putShort((short) sizes.size());
    int offset = ICO_HEADER_SIZE + ICO_ENTRY_SIZE * sizes.size();
    for (int i = 0; i < sizes.size(); i++) {
      int size = sizes.get(i);
      byte dimension = (byte) (size >= PNG_FROM_SIZE ? 0 : size);
      out.put(dimension).put(dimension);
      out.put((byte) 0).put((byte) 0);
      out.putShort((short) 1).putShort((short) 32);
      out.putInt(entries.get(i).length).putInt(offset);
      offset += entries.get(i).length;
    }
    entries.forEach(out::put);
    return out.array();
  }

  /**
   * Returns a macOS {@code .icns} file with {@code image} at every size of {@link #ICNS_SIZES},
   * each entry a PNG, which macOS reads since 10.7.
   */
  public byte[] icns(BufferedImage image) {
    List<byte[]> entries =
        ICNS_SIZES.stream().map(size -> Icons.png(Icons.scale(image, size))).toList();
    int total = 8 + entries.stream().mapToInt(entry -> 8 + entry.length).sum();
    ByteBuffer out = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN);
    out.put("icns".getBytes(StandardCharsets.US_ASCII)).putInt(total);
    for (int i = 0; i < entries.size(); i++) {
      byte[] entry = entries.get(i);
      out.put(ICNS_TYPES.get(ICNS_SIZES.get(i)).getBytes(StandardCharsets.US_ASCII));
      out.putInt(8 + entry.length).put(entry);
    }
    return out.array();
  }

  /**
   * A {@code BITMAPINFOHEADER}, bottom-up BGRA rows, and then an all-zero AND mask. The alpha
   * channel does the masking.
   */
  private byte[] bitmap(BufferedImage image) {
    int size = image.getWidth();
    int maskRow = (size + 31) / 32 * 4;
    ByteBuffer out =
        ByteBuffer.allocate(BITMAP_INFO_HEADER_SIZE + size * size * 4 + maskRow * size)
            .order(ByteOrder.LITTLE_ENDIAN);
    out.putInt(BITMAP_INFO_HEADER_SIZE).putInt(size).putInt(size * 2);
    out.putShort((short) 1).putShort((short) 32);
    out.putInt(0).putInt(size * size * 4).putInt(0).putInt(0).putInt(0).putInt(0);
    for (int y = size - 1; y >= 0; y--) {
      for (int x = 0; x < size; x++) {
        int argb = image.getRGB(x, y);
        out.put((byte) argb)
            .put((byte) (argb >> 8))
            .put((byte) (argb >> 16))
            .put((byte) (argb >>> 24));
      }
    }
    return out.array();
  }
}
