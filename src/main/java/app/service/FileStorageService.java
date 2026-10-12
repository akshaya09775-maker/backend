package app.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/** Stores uploaded resumes and images on local disk (folder "uploads/resumes" and "uploads/images"). */
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED = Set.of("pdf", "doc", "docx");
    private static final Set<String> ALLOWED_IMAGES = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private final Path root;
    private final Path imageRoot;

    public FileStorageService(@Value("${app.upload-dir:uploads}") String dir) {
        this.root = Paths.get(dir, "resumes").toAbsolutePath().normalize();
        this.imageRoot = Paths.get(dir, "images").toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            Files.createDirectories(imageRoot);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create upload directory " + root, e);
        }
    }

    public String store(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please choose a file");
        }
        String ext = extension(file.getOriginalFilename());
        if (!ALLOWED.contains(ext)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF, DOC or DOCX resumes are allowed");
        }
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(8);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the file");
        }
        if (!signatureMatches(ext, head)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The file content does not match a valid " + ext.toUpperCase() + " document");
        }
        String stored = "resume-" + userId + "-" + UUID.randomUUID() + "." + ext;
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, root.resolve(stored), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save the file");
        }
        return stored;
    }

    public String storeImage(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please choose an image");
        }
        String ext = extension(file.getOriginalFilename());
        if (!ALLOWED_IMAGES.contains(ext)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only JPG, PNG or WEBP images are allowed");
        }
        String stored = "avatar-" + userId + "-" + UUID.randomUUID() + "." + ext;
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, imageRoot.resolve(stored), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save the image");
        }
        return stored;
    }

    public Resource load(String stored) {
        if (stored == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No resume uploaded");
        Path p = root.resolve(stored).normalize();
        if (!p.startsWith(root) || !Files.exists(p)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume file not found");
        }
        return new FileSystemResource(p);
    }

    public Resource loadImage(String stored) {
        if (stored == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
        Path p = imageRoot.resolve(stored).normalize();
        if (!p.startsWith(imageRoot) || !Files.exists(p)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image file not found");
        }
        return new FileSystemResource(p);
    }

    public void delete(String stored) {
        if (stored == null) return;
        try {
            Path p = root.resolve(stored).normalize();
            if (p.startsWith(root)) Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // best effort
        }
    }

    public void deleteImage(String stored) {
        if (stored == null) return;
        try {
            Path p = imageRoot.resolve(stored).normalize();
            if (p.startsWith(imageRoot)) Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // best effort
        }
    }

    public String contentType(String name) {
        String ext = extension(name);
        return switch (ext) {
            case "pdf" -> "application/pdf";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            default -> "application/octet-stream";
        };
    }

    /** Checks the first bytes of the file so a renamed .exe or .html cannot pass as a resume. */
    static boolean signatureMatches(String ext, byte[] h) {
        if (h == null || h.length < 4) return false;
        return switch (ext) {
            case "pdf" -> h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F';
            case "docx" -> h[0] == 'P' && h[1] == 'K' && h[2] == 3 && h[3] == 4;
            case "doc" -> (h[0] & 0xFF) == 0xD0 && (h[1] & 0xFF) == 0xCF && (h[2] & 0xFF) == 0x11 && (h[3] & 0xFF) == 0xE0;
            default -> false;
        };
    }

    private static String extension(String name) {
        if (name == null || !name.contains(".")) return "";
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase();
    }
}
