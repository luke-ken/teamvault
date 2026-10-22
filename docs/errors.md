# Error catalogue

Every error answers `application/problem+json` (RFC 9457) with `type`, `title`, `status`,
`detail`, `instance` (the request path) and `requestId`. The `type` is this page plus the
anchor below; clients switch on it, never on `detail`. Decision: [ADR-005](adr/005-error-contract-problem-details.md).

```json
{
  "type": "https://github.com/luke-ken/teamvault/blob/main/docs/errors.md#membership-required",
  "title": "Forbidden",
  "status": 403,
  "detail": "Not a member of this company",
  "instance": "/api/companies/11111111-1111-1111-1111-111111111111/files",
  "requestId": "6f1c2a9e-5b7d-4c3e-9a1f-2d8e7b6c5a40"
}
```

Every response, success included, carries `X-Request-Id`. Quote it when reporting a problem.

## authentication-required
401. No bearer token on a protected route. `WWW-Authenticate: Bearer` is set. Log in and retry
with the token.

## invalid-token
401. The bearer token is malformed, has a bad signature, the wrong issuer, or has expired
(15 minutes). `WWW-Authenticate: Bearer` is set. Log in again.

## bad-credentials
401. Login with an unknown email or a wrong password. One body and one timing for both, so
the response never reveals which emails exist.

## membership-required
403. The caller is not a member of the company in the path. Returned whether or not the
company exists, so ids cannot be probed.

## admin-role-required
403. The caller is a member, but the operation needs the ADMIN role in this company.

## forbidden
403. The caller's account no longer exists although the token is still valid.

## not-found
404. No resource with that id inside the caller's company, or no user with that email when
adding a member. A foreign company's file id under your company is a 404, not a leak.

## already-exists
409. A unique rule is violated: email already registered, file name already used in this
company, user already a member. Also the answer when two identical requests race and the
database constraint wins.

## validation-failed
400. A request body failed validation. `detail` is "Validation failed"; the extension member
`errors` lists `{field, message}` pairs, sorted by field.

## malformed-body
400. The JSON body cannot be read: broken syntax, or a value the field cannot take (an unknown
tier, a word where a number goes).

## malformed-parameter
400. A path variable or query parameter has the wrong form, for example a company id that is
not a UUID.

## content-too-large
413. The upload exceeds the configured size limit.

## Framework errors
Unknown route (404), wrong HTTP method (405), unsupported or unacceptable media type (415, 406)
and the generic 500 carry no `type` member, which RFC 9457 reads as `about:blank`. The status
and the request id are all a client needs there, and the 500 never carries internal detail.
