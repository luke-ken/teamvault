# Feature plan: close the OWASP Top 10:2025 findings

The walk through the OWASP Top 10:2025 against the backend produced ten findings. Six are
closed here in tests and code, four are named on the readiness ladder. No ADR: nothing here
changes a decision, each item hardens what ADR-003, ADR-004 and ADR-005 already decided. Method
as in the other plans: inventory, forks settled first, bottom-up, tests with each step.

## Inventory

- Upload limits: `spring.servlet.multipart.max-file-size: 25MB`, `max-request-size: 26MB`, and
  `ApiExceptionHandler.handleMaxUploadSizeExceededException` answers 413 `content-too-large`.
  No test sends an oversized part. MockMvc cannot: `multipart()` hands the parts to the request
  already parsed, so the servlet container's limit never runs. A real server is needed.
- Response headers: Spring Security's defaults are on (`X-Content-Type-Options: nosniff`,
  `X-Frame-Options: DENY`, `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`,
  `Pragma`, `Expires`, `X-XSS-Protection: 0`). Nowhere asserted. `FileController.downloadFile`
  builds `Content-Disposition` with `ContentDisposition.attachment().filename(name, UTF_8)`.
- Filename: `FileService.upload` stores `upload.getOriginalFilename()` verbatim, the blob goes
  under a server-generated UUID (`FileStorage` resolves the key against its root). The column is
  `text` without a length check. `RegisterRequest` and `CreateCompanyRequest` cap their inputs,
  the filename is the one input without a cap.
- Content type: `contentTypeOrDefault` stores the client's `Content-Type` string as sent;
  `downloadFile` parses it with `MediaType.parseMediaType`, which throws
  `InvalidMediaTypeException` (an `IllegalArgumentException`) on garbage. Stored garbage would
  break every later download of that file.
- Logging: one `log.error` in the catch-all. A failed login, a 403 from the guard: no line.
  `logging.pattern.correlation` already puts the request id in front of every line.
- `IllegalArgumentException`: thrown by our own code once (`FileService.upload`, empty upload or
  missing filename) and mapped by the advice to 400 `malformed-parameter` with the exception
  message as detail. The same handler catches every framework `IllegalArgumentException`, for
  example `UUID.fromString` in `Caller.id` on a non-UUID subject, and ships its message.

## Design forks, settled

1. **Oversized part through a real server.** One new test class with
   `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `RestClient` against `localhost:port`, a part
   of 25 MB plus one byte, bearer from a real login over the wire. Costs a context and a
   container like `SwaggerUiEnabledTest`, the only way to see Tomcat enforce the limit. Part
   size, not request size: Tomcat rejects a too-large request size before reading the body and
   then drops the connection when more than `maxSwallowSize` (2 MB) is left unread; a part that
   crosses its limit near the end leaves a few hundred bytes to swallow, and the 413 gets out.
2. **Headers asserted where they matter.** Two tests in `ErrorContractApiIntegrationTest` and
   `FileApiIntegrationTest`: `nosniff`, `X-Frame-Options`, `Cache-Control` on `GET /api/me`
   (secured JSON) and on the download. The download test also pins `Content-Disposition`:
   `attachment`, the name present, non-ASCII and quotes encoded. No header customisation in
   `SecurityConfig`: the defaults are what the test documents. HSTS is the proxy's job (ladder).
3. **Filename cap in the service, not the column.** 255 characters, the usual file system
   limit, checked in `FileService.upload` next to the empty check. A 400 above. No migration:
   Flyway owns the schema and a `text` column with a service-side rule is the pattern the other
   inputs follow (bean validation on the records). Stored verbatim otherwise: the filename is
   display data, the UUID is the path. The test uploads `../../etc/passwd "quoted".txt`, reads it
   back verbatim, and checks the storage directory holds only UUID-named files.
4. **Content type parsed at upload.** `contentTypeOrDefault` runs `MediaType.parseMediaType`;
   anything it rejects becomes `application/octet-stream`. The download then never throws on
   stored data. No forcing of octet-stream for html, svg or xml: `Content-Disposition: attachment`
   plus `nosniff` makes the browser save instead of render, which is the actual defence. Decision
   recorded in the README "deliberately not built" paragraph.
5. **Own exception, generic handler gone.** New `ProblemType.INVALID_UPLOAD` (400, slug
   `invalid-upload`) and `common/BadRequestException(ProblemType, String)` as the fourth
   `ApiException` subclass. `FileService.upload` throws it for the empty upload and for the long
   filename. The `IllegalArgumentException` handler is removed: a framework
   `IllegalArgumentException` now reaches the catch-all, 500 without its message, logged. The
   slice test gets a case for it. `docs/errors.md` gets the `invalid-upload` entry; walkthrough 5
   loses the `IllegalArgumentException` note.
6. **Two WARN lines, no PII.** Failed login: `AuthService.login` catches
   `AuthenticationException`, logs `Login failed for {}` with the email masked (`c***@acme.io`),
   rethrows. Denied access: `MembershipGuard` logs `Access denied: caller {} is no member of
   company {}` (and the admin variant) before throwing; the caller id is a UUID that means
   nothing without database access. The request id comes from the MDC pattern. Masking lives in
   `common/Redact.email(String)`: first character of the local part, three stars, the domain;
   no `@` gives `***`. Tests: a unit test for `Redact`, and the wrong-password integration test
   grows `OutputCaptureExtension` assertions: the masked form appears, the raw email does not.
7. **Ladder.** New rows: Maven wrapper checksum pin (named, F3), breached-password check
   (named, F8), and a Security row "OWASP Top 10:2025 walked, findings closed or named". The
   quota row (F6) and the email verification row (F7) exist; their notes get the walk's wording.

## Build order

### Commit 1: `fix: upload limits, download headers, filename handling after OWASP walk`
1. F2: `file/UploadLimitIntegrationTest` (RANDOM_PORT): `oversizedPart_413_contentTooLarge`.
2. F1: `ErrorContractApiIntegrationTest.securedJsonResponse_carriesDefaultSecurityHeaders`;
   `FileApiIntegrationTest.download_carriesSecurityHeaders_andEncodedDisposition`.
3. F4 + F5: `FileService.upload` filename cap and content type parsing;
   `FileApiIntegrationTest.hostileFilename_storedVerbatim_blobUnderUuid_headerEncoded`,
   `filenameOver255_400`, `garbageContentType_storedAsOctetStream`. `AbstractIntegrationTest`
   exposes the storage dir for the blob check.
4. README paragraph on the download content type decision.

### Commit 2: `fix: own exception for bad uploads, WARN on failed login and denied access`
5. F10: `ProblemType.INVALID_UPLOAD`, `BadRequestException`, `FileService` throws it, handler
   removed, `ApiExceptionHandlerTest.frameworkIllegalArgument_500_noMessage`, `docs/errors.md`,
   walkthrough 5 note.
6. F9: `common/Redact` + `RedactTest`; `AuthService.login` try/catch/log/rethrow;
   `MembershipGuard` WARN lines; `ErrorContractApiIntegrationTest.wrongPassword_...` grows the
   log assertions with `OutputCaptureExtension`.

### Docs
7. Ladder rows, `CLAUDE.md` state, `PROGRESS.md` entry.
