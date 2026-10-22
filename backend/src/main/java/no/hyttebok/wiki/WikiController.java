package no.hyttebok.wiki;

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
import jakarta.validation.Valid;
import no.hyttebok.photo.ImageType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** The cabin's wiki: pages anyone in the cabin can write and edit. The chatbot reads them too. */
@Controller("/api/wiki")
public class WikiController {

    private static final String PDF = "application/pdf";

    private static final int MAX_NAME = 120;

    private final WikiPageRepository repository;
    private final WikiAttachmentRepository attachments;
    private final Path uploadDir;

    public WikiController(WikiPageRepository repository, WikiAttachmentRepository attachments, @Value("${hyttebok.upload-dir}") String uploadDir) throws IOException {
        this.repository = repository;
        this.attachments = attachments;
        this.uploadDir = Files.createDirectories(Path.of(uploadDir));
    }

    @Get
    public List<WikiPage> list() {
        return repository.listOrderByTitle();
    }

    @Post
    public WikiPage create(@Body @Valid WikiPage page) {
        return repository.save(new WikiPage(null, page.title().trim(), page.content(), page.updatedBy(), null, null));
    }

    @Put
    public WikiPage update(@Body @Valid WikiPage page) {

        var old = repository.findById(page.id()).orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "No such page"));

        return repository.update(new WikiPage(old.id(), page.title().trim(), page.content(), page.updatedBy(), null, old.cabinId()));
    }

    /** A page's files go with it. */
    @Delete("/{id}")
    public void delete(Long id) {

        var files = attachments.findByWikiPageId(id);
        repository.deleteById(id);
        files.forEach(a -> deleteFile(a.fileName()));
    }

    // ---- attachments: photos and PDFs on a page ----

    /** Every page's attachments (the app shows each page's own). */
    @Get("/attachments")
    public List<WikiAttachment> attachments() {
        return attachments.listOrderByCreatedAt();
    }

    /** A photo or a PDF; the type comes from the file itself, never from what the browser says. */
    @Post(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA)
    public WikiAttachment attach(Long id, CompletedFileUpload file, @Nullable @Part String uploadedBy) throws IOException {

        repository.findById(id).orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "No such page"));
        var bytes = file.getBytes();
        var type = ImageType.isPdf(bytes) ? PDF : ImageType.sniff(bytes);
        if (type == null) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "Only photos and PDF files can be attached");
        }

        var fileName = UUID.randomUUID().toString();
        Files.write(uploadDir.resolve(fileName), bytes);
        var name = file.getFilename() == null || file.getFilename().isBlank() ? "fil" : file.getFilename().strip();

        return attachments.save(new WikiAttachment(null, id, fileName, type, name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name, bytes.length,
            uploadedBy, null, null));
    }

    /** Photos are shown; PDFs are downloaded (opened by the phone's own viewer), never run as a page from our address. */
    @Get("/attachments/{id}/file")
    public HttpResponse<SystemFile> file(Long id) {

        var a = attachments.findById(id).orElseThrow(() -> new HttpStatusException(HttpStatus.NOT_FOUND, "No such file"));
        var image = ImageType.SAFE.contains(a.contentType());
        var file = new SystemFile(uploadDir.resolve(a.fileName()).toFile(), image ? MediaType.of(a.contentType()) : MediaType.of(PDF));
        if (!image) {
            file = file.attach(a.originalName());
        }

        return HttpResponse.ok(file)
            .header("Cache-Control", "private, max-age=31536000, immutable")
            .header("X-Content-Type-Options", "nosniff")
            .header("Content-Security-Policy", "default-src 'none'; sandbox");
    }

    @Delete("/attachments/{id}")
    public void deleteAttachment(Long id) {
        attachments.findById(id).ifPresent(a -> {
            attachments.delete(a);
            deleteFile(a.fileName());
        });
    }

    private void deleteFile(String name) {
        try {
            Files.deleteIfExists(uploadDir.resolve(name));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
