package no.hyttebok.access;

import io.micronaut.context.annotation.Value;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.http.MediaType;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Everything in a cabin as a ZIP, for its owners: one JSON file per kind of data (every table with a cabin_id, so
 * new features are included without changes here), the members, and the photos. A copy to keep, not for importing.
 * Written to a temporary file (photos copied straight from disk, so a big cabin doesn't fill memory), which is sent and
 * then deleted.
 */
@Singleton
public class CabinExport {

    // Not the cabin's content: device keys, invitation links, usage counters, our own logs.
    private static final Set<String> LEFT_OUT = Set.of("push_subscription", "invitation", "chat_usage", "chat_usage_person", "membership", "feedback",
        "audit_event");

    private final JdbcOperations jdbc;
    private final Path uploadDir;

    public CabinExport(JdbcOperations jdbc, @Value("${hyttebok.upload-dir}") String uploadDir) {
        this.jdbc = jdbc;
        this.uploadDir = Path.of(uploadDir);
    }

    private record PhotoFile(long id, String fileName, String contentType) {
    }

    @Transactional(readOnly = true)
    public StreamedFile export(long cabinId, String cabinName) throws IOException {

        var json = new LinkedHashMap<String, String>();
        json.put("cabin.json", one("SELECT row_to_json(c) FROM cabin_info c WHERE id = ?", cabinId));
        json.put("members.json", one("""
            SELECT coalesce(json_agg(json_build_object('name', coalesce(u.name, u.email), 'email', u.email, 'role', m.role, 'since', m.created_at)
                ORDER BY m.created_at), '[]')
            FROM membership m JOIN app_user u ON u.id = m.user_id WHERE m.cabin_id = ?
            """, cabinId));
        for (var table : tables()) {
            json.put(table + ".json", one("SELECT coalesce(json_agg(t ORDER BY t.id), '[]') FROM \"" + table + "\" t WHERE cabin_id = ?", cabinId));
        }
        var photos = photos(cabinId);

        var file = Files.createTempFile("hyttebok-export-", ".zip");
        try {
            write(file, json, photos);
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(file);
            throw e;
        }

        return new StreamedFile(Files.newInputStream(file, StandardOpenOption.DELETE_ON_CLOSE), MediaType.of("application/zip"))
            .attach("hyttebok-" + slug(cabinName) + ".zip");
    }

    private void write(Path file, Map<String, String> json, List<PhotoFile> photos) throws IOException {
        try (var zip = new ZipOutputStream(Files.newOutputStream(file))) {

            zip.putNextEntry(new ZipEntry("LESMEG.txt"));
            zip.write("""
                Alt fra hytta i Hyttebok. Hver .json-fil er én type innhold (bookinger, hytteboka, handlelista ...).
                Bildene ligger i photos/, med samme id som i photo.json. Beløp i expense.json er i øre.

                Everything from the cabin in Hyttebok. Each .json file is one kind of content (bookings, the cabin book, the
                shopping list ...). The photos are in photos/, with the same id as in photo.json. Amounts in expense.json are in øre.
                """.getBytes(StandardCharsets.UTF_8));
            for (var entry : json.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
            }
            for (var photo : photos) {
                var source = uploadDir.resolve(photo.fileName());
                if (Files.exists(source)) {
                    zip.putNextEntry(new ZipEntry("photos/" + photo.id() + extension(photo.contentType())));
                    Files.copy(source, zip);
                }
            }
        }
    }

    /** Every table holding a cabin's content (it has a cabin_id), except {@link #LEFT_OUT}. */
    private List<String> tables() {
        return jdbc.prepareStatement("""
            SELECT table_name FROM information_schema.columns
            WHERE table_schema = current_schema() AND column_name = 'cabin_id' ORDER BY table_name
            """, statement -> {

            var names = new ArrayList<String>();
            try (var rs = statement.executeQuery()) {
                while (rs.next()) {
                    if (!LEFT_OUT.contains(rs.getString(1))) {
                        names.add(rs.getString(1));
                    }
                }
            }

            return names;
        });
    }

    private List<PhotoFile> photos(long cabinId) {
        return jdbc.prepareStatement("SELECT id, file_name, content_type FROM photo WHERE cabin_id = ? ORDER BY id", statement -> {

            statement.setLong(1, cabinId);
            var list = new ArrayList<PhotoFile>();
            try (var rs = statement.executeQuery()) {
                while (rs.next()) {
                    list.add(new PhotoFile(rs.getLong(1), rs.getString(2), rs.getString(3)));
                }
            }

            return list;
        });
    }

    private String one(String sql, long cabinId) {
        return jdbc.prepareStatement(sql, statement -> {

            statement.setLong(1, cabinId);
            try (var rs = statement.executeQuery()) {
                return rs.next() && rs.getString(1) != null ? rs.getString(1) : "null";
            }
        });
    }

    private static String extension(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/heic" -> ".heic";
            case "image/avif" -> ".avif";
            default -> ".jpg";
        };
    }

    /** "Testhytta Fjellro" → "testhytta-fjellro" for the file name. */
    private static String slug(String name) {
        var slug = name.toLowerCase().replaceAll("[^a-z0-9æøå]+", "-").replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "hytte" : slug;
    }
}
