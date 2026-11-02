package io.github.lukeken.teamvault.file;

import io.github.lukeken.teamvault.common.BadRequestException;
import io.github.lukeken.teamvault.common.ConflictException;
import io.github.lukeken.teamvault.common.NotFoundException;
import io.github.lukeken.teamvault.common.ProblemType;
import io.github.lukeken.teamvault.membership.Membership;
import io.github.lukeken.teamvault.membership.MembershipGuard;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

@Service
public class FileService {

	// The usual file system limit; the one input without a bean validation annotation.
	static final int MAX_FILENAME_LENGTH = 255;

	private final FileMetadataRepository files;

	private final MembershipGuard guard;

	private final FileStorage storage;

	public FileService(FileMetadataRepository files, MembershipGuard guard, FileStorage storage) {
		this.files = files;
		this.guard = guard;
		this.storage = storage;
	}

	@Transactional
	public FileResponse upload(UUID callerId, UUID companyId, MultipartFile upload) {
		Membership membership = guard.requireMember(callerId, companyId);
		String filename = upload.getOriginalFilename();
		if (upload.isEmpty() || filename == null || filename.isBlank()) {
			throw new BadRequestException(ProblemType.INVALID_UPLOAD, "Uploaded file must have content and a filename");
		}
		// Stored verbatim otherwise: the name is display data, the blob lives under the
		// UUID key, and the download header encodes it. Only the length is ours to cap.
		if (filename.length() > MAX_FILENAME_LENGTH) {
			throw new BadRequestException(ProblemType.INVALID_UPLOAD,
					"Filename must be at most " + MAX_FILENAME_LENGTH + " characters");
		}

		String storageKey = UUID.randomUUID().toString();
		try {
			storage.store(storageKey, upload.getInputStream());
		}
		catch (IOException e) {
			throw new UncheckedIOException("Failed to read upload", e);
		}

		FileMetadata metadata = new FileMetadata(membership.getCompany(), membership.getUser(), filename,
				contentTypeOrDefault(upload), upload.getSize(), storageKey);
		try {
			// Flush inside the transaction so the tenant-scoped unique constraint
			// (company_id, filename) surfaces here, where the blob can be cleaned up.
			FileMetadata saved = files.saveAndFlush(metadata);
			return FileResponse.from(saved);
		}
		catch (DataIntegrityViolationException e) {
			storage.delete(storageKey);
			throw new ConflictException("A file named '" + filename + "' already exists in this company");
		}
	}

	@Transactional(readOnly = true)
	public List<FileResponse> list(UUID callerId, UUID companyId) {
		guard.requireMember(callerId, companyId);
		return files.findByCompanyIdOrderByCreatedAtDesc(companyId).stream().map(FileResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public FileResponse get(UUID callerId, UUID companyId, UUID fileId) {
		guard.requireMember(callerId, companyId);
		return FileResponse.from(requireFile(companyId, fileId));
	}

	public record Download(FileResponse metadata, Resource content) {
	}

	@Transactional(readOnly = true)
	public Download download(UUID callerId, UUID companyId, UUID fileId) {
		guard.requireMember(callerId, companyId);
		FileMetadata file = requireFile(companyId, fileId);
		return new Download(FileResponse.from(file), storage.load(file.getStorageKey()));
	}

	private FileMetadata requireFile(UUID companyId, UUID fileId) {
		return files.findByIdAndCompanyId(fileId, companyId).orElseThrow(() -> new NotFoundException("File not found"));
	}

	// The part's Content-Type is client input. Parsed once here, so the download can
	// trust what the row holds instead of failing on a stored string it cannot parse.
	private String contentTypeOrDefault(MultipartFile upload) {
		String contentType = upload.getContentType();
		if (contentType == null || contentType.isBlank()) {
			return MediaType.APPLICATION_OCTET_STREAM_VALUE;
		}
		try {
			MediaType.parseMediaType(contentType);
			return contentType;
		}
		catch (InvalidMediaTypeException e) {
			return MediaType.APPLICATION_OCTET_STREAM_VALUE;
		}
	}

}
