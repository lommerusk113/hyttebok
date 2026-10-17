package no.hyttebok.photo;

import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.server.types.files.SystemFile;
import io.micronaut.serde.annotation.Serdeable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Controller("/api/photos")
public class PhotoController {

    private final Path uploadDir;
    private final PhotoRepository repository;

    public PhotoController(@Value("${hyttebok.upload-dir}") String uploadDir,
                           PhotoRepository repository) throws IOException {
        this.uploadDir = Files.createDirectories(Path.of(uploadDir));
        this.repository = repository;
    }

    @Get
    public List<Photo> list() {
        return repository.findByForumPostIdIsNullAndForumReplyIdIsNullOrderByCreatedAtDesc();
    }

    @Post(consumes = MediaType.MULTIPART_FORM_DATA)
    public Photo upload(CompletedFileUpload file,
                        @Nullable @Part String caption,
                        @Nullable @Part String uploadedBy,
                        @Nullable @Part Long logEntryId,
                        @Nullable @Part Long forumPostId,
                        @Nullable @Part Long forumReplyId) throws IOException {

        var contentType = file.getContentType().map(MediaType::toString).orElse("");
        if (!contentType.startsWith("image/")) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Only images can be uploaded");
        }

        // ponytail: originals only, add thumbnails if the gallery gets slow
        var fileName = UUID.randomUUID().toString();
        Files.write(uploadDir.resolve(fileName), file.getBytes());

        return repository.save(new Photo(null, fileName, contentType, caption, uploadedBy, logEntryId, forumPostId, forumReplyId, null, null));
    }

    /**
     * Only the caption is editable; file name and type stay server-controlled.
     */
    @Put("/{id}/caption")
    public Photo updateCaption(Long id, @Body CaptionUpdate body) {

        var photo = findOrThrow(id);
        var updated = new Photo(photo.id(), photo.fileName(), photo.contentType(), body.caption(),
            photo.uploadedBy(), photo.logEntryId(), photo.forumPostId(), photo.forumReplyId(), photo.createdAt(), photo.cabinId());

        return repository.update(updated);
    }

    @Get("/{id}/file")
    public HttpResponse<SystemFile> file(Long id) {

        var photo = findOrThrow(id);
        var file = new SystemFile(uploadDir.resolve(photo.fileName()).toFile(), MediaType.of(photo.contentType()));

        return HttpResponse.ok(file).header("Cache-Control", "public, max-age=31536000, immutable");
    }

    @Delete("/{id}")
    public void delete(Long id) throws IOException {

        var photo = repository.findById(id).orElse(null);
        if (photo == null) {
            return;
        }

        repository.delete(photo);
        Files.deleteIfExists(uploadDir.resolve(photo.fileName()));
    }

    private Photo findOrThrow(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "Photo not found"));
    }

    @Serdeable
    public record CaptionUpdate(@Nullable String caption) {
    }
}
