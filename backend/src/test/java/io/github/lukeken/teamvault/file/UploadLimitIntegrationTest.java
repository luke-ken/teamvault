package io.github.lukeken.teamvault.file;

import com.jayway.jsonpath.JsonPath;
import io.github.lukeken.teamvault.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one test that needs a real server: MockMvc hands the parts to the request already
 * parsed, so the container's multipart limit never runs under it. Here Tomcat reads the
 * body, trips the 25 MB part limit, and the advice turns the exception into 413. Costs a
 * context and a container of its own, like SwaggerUiEnabledTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "teamvault.storage.dir=${java.io.tmpdir}/teamvault-limit-test-blobs")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class UploadLimitIntegrationTest {

	private static final String ACME = "11111111-1111-1111-1111-111111111111";

	@LocalServerPort
	private int port;

	@Test
	void oversizedPart_413_contentTooLarge() {
		RestClient client = RestClient.create("http://localhost:" + port);
		String token = login(client);

		// One byte over the part limit, well under the request limit: Tomcat rejects the
		// part at its end, with only the multipart tail left to swallow, and the response
		// gets out instead of a dropped connection. Built by hand so the request carries
		// a Content-Length; a streamed (chunked) body made the server close the
		// connection.
		String boundary = "upload-limit-test";
		byte[] head = ("--" + boundary + "\r\n"
				+ "Content-Disposition: form-data; name=\"file\"; filename=\"too-big.bin\"\r\n"
				+ "Content-Type: application/octet-stream\r\n\r\n")
			.getBytes(StandardCharsets.US_ASCII);
		byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII);
		byte[] body = ByteBuffer.allocate(head.length + 25 * 1024 * 1024 + 1 + tail.length)
			.put(head)
			.position(head.length + 25 * 1024 * 1024 + 1)
			.put(tail)
			.array();

		ResponseEntity<String> response = client.post()
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.parseMediaType("multipart/form-data; boundary=" + boundary))
			.body(body)
			.retrieve()
			.onStatus(status -> true, (request, res) -> {
			})
			.toEntity(String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
		assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(response.getBody()).contains("/docs/errors.md#content-too-large")
			.contains("Uploaded file exceeds the size limit");
	}

	private String login(RestClient client) {
		String body = client.post()
			.uri("/api/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.body("{\"email\":\"alice@acme.example\",\"password\":\"devpass12\"}")
			.retrieve()
			.body(String.class);
		return JsonPath.read(body, "$.token");
	}

}
