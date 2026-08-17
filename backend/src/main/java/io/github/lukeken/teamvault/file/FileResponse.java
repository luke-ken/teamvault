package io.github.lukeken.teamvault.file;

import java.time.Instant;
import java.util.UUID;

public record FileResponse(
		UUID id,
		String filename,
		String contentType,
		long sizeBytes,
		String uploadedBy,
		Instant createdAt
) {
	static FileResponse from(FileMetadata file) {
		return new FileResponse(
				file.getId(),
				file.getFilename(),
				file.getContentType(),
				file.getSizeBytes(),
				file.getUploadedBy().getDisplayName(),
				file.getCreatedAt());
	}
}
