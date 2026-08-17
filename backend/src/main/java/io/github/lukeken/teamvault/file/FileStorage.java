package io.github.lukeken.teamvault.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Local-filesystem blob store. Deliberately hidden behind this one class so the
 * later swap to S3/MinIO touches nothing but it (see README "deliberately not built").
 * Storage keys are server-generated UUIDs, never client input.
 */
@Service
public class FileStorage {

	private final Path root;

	public FileStorage(@Value("${teamvault.storage.dir}") String dir) {
		this.root = Path.of(dir).toAbsolutePath().normalize();
		try {
			Files.createDirectories(root);
		} catch (IOException e) {
			throw new UncheckedIOException("Cannot create storage dir " + root, e);
		}
	}

	public void store(String storageKey, InputStream content) {
		try (content) {
			Files.copy(content, root.resolve(storageKey));
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to store blob " + storageKey, e);
		}
	}

	public Resource load(String storageKey) {
		return new FileSystemResource(root.resolve(storageKey));
	}

	public void delete(String storageKey) {
		try {
			Files.deleteIfExists(root.resolve(storageKey));
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to delete blob " + storageKey, e);
		}
	}
}
