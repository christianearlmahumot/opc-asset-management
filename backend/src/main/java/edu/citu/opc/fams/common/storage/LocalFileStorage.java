package edu.citu.opc.fams.common.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Development storage: files go under fams.storage.local-dir (default ./uploads, git-ignored). */
@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(@Value("${fams.storage.local-dir:uploads}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    @Override
    public String save(String path, byte[] content, String contentType) throws IOException {
        Path target = resolve(path);
        Files.createDirectories(target.getParent());
        Files.write(target, content);
        return path;
    }

    @Override
    public byte[] load(String path) throws IOException {
        return Files.readAllBytes(resolve(path));
    }

    @Override
    public void delete(String path) throws IOException {
        Files.deleteIfExists(resolve(path));
    }

    /** Refuses paths that would escape the storage folder (e.g. "../"). */
    private Path resolve(String path) {
        Path p = root.resolve(path).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage path");
        }
        return p;
    }
}
