package no.hyttebok.photo;

import io.micronaut.core.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Which image a file really is, read from its first bytes (not what the browser claims). Only these are kept:
 * an SVG or HTML file "as an image" could run scripts when opened from our own address.
 */
public final class ImageType {

    public static final Set<String> SAFE = Set.of("image/jpeg", "image/png", "image/gif", "image/webp", "image/heic", "image/avif");

    private ImageType() {
    }

    /** The image's type, or null if it isn't one of the {@link #SAFE} ones. */
    @Nullable
    public static String sniff(byte[] bytes) {

        if (starts(bytes, 0, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (starts(bytes, 0, 0x89, 'P', 'N', 'G')) {
            return "image/png";
        }
        if (ascii(bytes, 0, "GIF8")) {
            return "image/gif";
        }
        if (ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP")) {
            return "image/webp";
        }
        // ISO media ("....ftyp<brand>"): HEIC from iPhones, or AVIF.
        if (ascii(bytes, 4, "ftyp") && bytes.length >= 12) {
            var brand = new String(bytes, 8, 4, StandardCharsets.US_ASCII);
            if (brand.startsWith("avi")) {
                return "image/avif";
            }
            if (Set.of("heic", "heix", "heim", "heis", "hevc", "hevx", "mif1", "msf1").contains(brand)) {
                return "image/heic";
            }
        }

        return null;
    }

    /** A PDF ("%PDF-" first), for manuals on wiki pages. */
    public static boolean isPdf(byte[] bytes) {
        return ascii(bytes, 0, "%PDF-");
    }

    private static boolean starts(byte[] bytes, int at, int... expected) {

        if (bytes.length < at + expected.length) {
            return false;
        }
        for (var i = 0; i < expected.length; i++) {
            if ((bytes[at + i] & 0xFF) != expected[i]) {
                return false;
            }
        }

        return true;
    }

    private static boolean ascii(byte[] bytes, int at, String text) {
        return starts(bytes, at, text.chars().toArray());
    }
}
