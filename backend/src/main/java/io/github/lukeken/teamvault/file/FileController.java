package io.github.lukeken.teamvault.file;

import io.github.lukeken.teamvault.auth.Caller;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/companies/{companyId}/files")
public class FileController {

	private final FileService fileService;

	public FileController(FileService fileService) {
		this.fileService = fileService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public FileResponse upload(@AuthenticationPrincipal Jwt caller, @PathVariable UUID companyId,
			@RequestParam("file") MultipartFile file) {
		return fileService.upload(Caller.id(caller), companyId, file);
	}

	@GetMapping
	public List<FileResponse> list(@AuthenticationPrincipal Jwt caller, @PathVariable UUID companyId) {
		return fileService.list(Caller.id(caller), companyId);
	}

	@GetMapping("/{fileId}")
	public FileResponse get(@AuthenticationPrincipal Jwt caller, @PathVariable UUID companyId,
			@PathVariable UUID fileId) {
		return fileService.get(Caller.id(caller), companyId, fileId);
	}

	@GetMapping("/{fileId}/download")
	public ResponseEntity<Resource> download(@AuthenticationPrincipal Jwt caller, @PathVariable UUID companyId,
			@PathVariable UUID fileId) {
		FileService.Download download = fileService.download(Caller.id(caller), companyId, fileId);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(download.metadata().contentType()))
			.contentLength(download.metadata().sizeBytes())
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment()
						.filename(download.metadata().filename(), StandardCharsets.UTF_8)
						.build()
						.toString())
			.body(download.content());
	}

}
