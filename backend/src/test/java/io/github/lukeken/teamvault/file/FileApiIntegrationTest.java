package io.github.lukeken.teamvault.file;

import com.jayway.jsonpath.JsonPath;
import io.github.lukeken.teamvault.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileApiIntegrationTest extends AbstractIntegrationTest {

	@Test
	void uploadListDownload_roundTrip_withinOwnCompany() throws Exception {
		var upload = new MockMultipartFile("file", "report.txt", "text/plain", "quarterly numbers".getBytes());

		String body = mvc.perform(multipart("/api/companies/" + ACME + "/files").file(upload).with(bearer(ALICE)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.filename", is("report.txt")))
			.andExpect(jsonPath("$.uploadedBy", is("Alice Admin")))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String fileId = JsonPath.read(body, "$.id");

		mvc.perform(get("/api/companies/" + ACME + "/files").with(bearer(ALICE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id", is(fileId)));

		mvc.perform(get("/api/companies/" + ACME + "/files/" + fileId + "/download").with(bearer(ALICE)))
			.andExpect(status().isOk())
			.andExpect(content().contentType("text/plain"))
			.andExpect(content().string("quarterly numbers"));
	}

	// THE negative test (ADR-003): cross-tenant access must fail at the backend.
	@Test
	void crossTenantAccess_isImpossible() throws Exception {
		var upload = new MockMultipartFile("file", "secret.txt", "text/plain", "acme only".getBytes());
		String body = mvc.perform(multipart("/api/companies/" + ACME + "/files").file(upload).with(bearer(ALICE)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String acmeFileId = JsonPath.read(body, "$.id");

		// Bob is no member of Acme: listing and direct reads are forbidden.
		mvc.perform(get("/api/companies/" + ACME + "/files").with(bearer(BOB))).andExpect(status().isForbidden());
		mvc.perform(get("/api/companies/" + ACME + "/files/" + acmeFileId + "/download").with(bearer(BOB)))
			.andExpect(status().isForbidden());

		// The sneaky path: Bob queries an Acme file id under HIS company. The
		// company-scoped lookup makes it a 404, not a leak.
		mvc.perform(get("/api/companies/" + GLOBEX + "/files/" + acmeFileId + "/download").with(bearer(BOB)))
			.andExpect(status().isNotFound());
	}

	@Test
	void duplicateFilenameInSameCompany_conflicts() throws Exception {
		var upload = new MockMultipartFile("file", "twice.txt", "text/plain", "v1".getBytes());
		mvc.perform(multipart("/api/companies/" + ACME + "/files").file(upload).with(bearer(ALICE)))
			.andExpect(status().isCreated());
		mvc.perform(multipart("/api/companies/" + ACME + "/files").file(upload).with(bearer(ALICE)))
			.andExpect(status().isConflict());
	}

	// OWASP A05: the filename is client input. It is display data, stored as sent; the
	// path on disk is the UUID key, and the header encodes the name on the way out.
	@Test
	void hostileFilename_storedVerbatim_blobUnderUuid_headerEncoded() throws Exception {
		String hostile = "../../etc/passwd \"quoted\".txt";
		var upload = new MockMultipartFile("file", hostile, "text/plain", "harmless".getBytes());
		String body = mvc.perform(multipart("/api/companies/" + ACME + "/files").file(upload).with(bearer(ALICE)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.filename", is(hostile)))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String fileId = JsonPath.read(body, "$.id");

		// Nothing escaped the storage root: every blob is named by its UUID key.
		try (Stream<Path> blobs = Files.list(STORAGE_DIR)) {
			assertThat(blobs.map(p -> p.getFileName().toString()))
				.allMatch(name -> name.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
		}
		assertThat(Files.exists(STORAGE_DIR.resolve("../../etc/passwd \"quoted\".txt").normalize())).isFalse();

		// The slashes and quotes are percent-encoded in the form browsers use.
		mvc.perform(get("/api/companies/" + ACME + "/files/" + fileId + "/download").with(bearer(ALICE)))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
					containsString("filename*=UTF-8''..%2F..%2Fetc%2Fpasswd%20%22quoted%22.txt")));
	}

	@Test
	void filenameOver255Characters_400_invalidUpload() throws Exception {
		var upload = new MockMultipartFile("file", "x".repeat(256) + ".txt", "text/plain", "long".getBytes());
		mvc.perform(multipart("/api/companies/" + ACME + "/files").file(upload).with(bearer(ALICE)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.type", endsWith("#invalid-upload")))
			.andExpect(jsonPath("$.detail", is("Filename must be at most 255 characters")));
	}

	@Test
	void emptyUpload_400_invalidUpload() throws Exception {
		var upload = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
		mvc.perform(multipart("/api/companies/" + ACME + "/files").file(upload).with(bearer(ALICE)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.type", endsWith("#invalid-upload")))
			.andExpect(jsonPath("$.detail", is("Uploaded file must have content and a filename")));
	}

	// The part's Content-Type is client input too. Garbage must not be stored, or the
	// download would fail on parsing it for the rest of the file's life.
	@Test
	void garbageContentType_storedAsOctetStream() throws Exception {
		var upload = new MockMultipartFile("file", "blob.bin", "not a media type", "bytes".getBytes());
		String body = mvc.perform(multipart("/api/companies/" + ACME + "/files").file(upload).with(bearer(ALICE)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.contentType", is("application/octet-stream")))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String fileId = JsonPath.read(body, "$.id");

		mvc.perform(get("/api/companies/" + ACME + "/files/" + fileId + "/download").with(bearer(ALICE)))
			.andExpect(status().isOk())
			.andExpect(content().contentType("application/octet-stream"));
	}

	@Test
	void withoutCredentials_401() throws Exception {
		mvc.perform(get("/api/companies/" + ACME + "/files")).andExpect(status().isUnauthorized());
	}

}
