# Walkthrough 3: credentials and the token

Three moments in the life of a credential: the password is stored at registration, it is
exchanged for a token at login, and the token is checked on every later request. Our code
is small on this page, which is the lesson: the password hashing, the credential check, the
signing and the verification are all Spring Security, and we wrote the lookup, the claims
and the configuration around them. Decision record: [ADR-004](../adr/004-self-issued-jwt-auth.md).
Lanes as in [walkthrough 1](01-secured-request-add-member.md): green is code in this
repository, blue is Spring, grey is infrastructure.

## A: registration stores a hash, never the password

```http
POST /api/auth/register
{"email": "Carol@Acme.io", "password": "correct horse battery", "displayName": "Carol"}
```

![Registration](03-credentials-and-the-token.svg)

Full size: [open the raw SVG](https://raw.githubusercontent.com/luke-ken/teamvault/main/docs/walkthroughs/03-credentials-and-the-token.svg).

<details>
<summary>Mermaid source (editable mirror of the diagram, re-render the SVG after a change)</summary>

```mermaid
sequenceDiagram
    autonumber
    actor Client
    box rgba(56,139,253,0.18) Spring Security and MVC
        participant Chain as Security filter chain
        participant MVC as DispatcherServlet, Validator
    end
    box rgba(46,160,67,0.18) TeamVault code
        participant Ctl as AuthController
        participant Svc as AuthService
    end
    box rgba(56,139,253,0.18) Spring Security
        participant Enc as BCryptPasswordEncoder
    end
    box rgba(56,139,253,0.18) Spring Data JPA
        participant JPA as AppUserRepository, Hibernate
    end
    box rgba(139,148,158,0.22) Infrastructure
        participant PG as Postgres
    end

    Client->>Chain: POST /api/auth/register, no token
    Note over Chain: /api/auth/** is permitAll: no token is demanded, none is read
    Chain->>MVC: pass
    Note over MVC: @Valid on RegisterRequest: email format, password 8 to 72 characters, displayName up to 100
    MVC->>Ctl: register(request)
    Ctl->>Svc: register(request)
    Note over Svc: email.strip().toLowerCase(ROOT): "carol@acme.io"
    Svc->>JPA: existsByEmail("carol@acme.io")
    JPA->>PG: SELECT ... FROM app_user WHERE email = ?
    PG-->>JPA: true or false (true: ConflictException, 409)
    Svc->>Enc: encode("correct horse battery")
    Note over Enc: bcrypt, cost 10, fresh random salt per call, about 100 ms on purpose.<br/>Same password twice gives two different hashes.
    Enc-->>Svc: hash string (version, cost, salt and digest in one field)
    Svc->>JPA: save(new AppUser(email, hash, displayName))
    JPA->>PG: INSERT at flush, COMMIT when register() returns
    Note over PG: uq_app_user_email catches two registrations in the same instant: 409 from the advice
    PG-->>JPA: 1 row
    Svc-->>Ctl: UserResponse(id, email, displayName)
    Note over Ctl,Svc: the hash never leaves the service: UserResponse has no field for it.<br/>RegisterRequest and LoginRequest print without the password, AuthenticatedUser without the hash
    Ctl-->>Client: 201, the user without any secret
```

</details>

## B: login exchanges the password for a token

```http
POST /api/auth/login
{"email": "carol@acme.io", "password": "correct horse battery"}
```

![Login](03-credentials-and-the-token-2.svg)

Full size: [open the raw SVG](https://raw.githubusercontent.com/luke-ken/teamvault/main/docs/walkthroughs/03-credentials-and-the-token-2.svg).

<details>
<summary>Mermaid source (editable mirror of the diagram, re-render the SVG after a change)</summary>

```mermaid
sequenceDiagram
    autonumber
    actor Client
    box rgba(46,160,67,0.18) TeamVault code
        participant Ctl as AuthController
        participant Svc as AuthService
    end
    box rgba(56,139,253,0.18) Spring Security
        participant AM as ProviderManager, DaoAuthenticationProvider
    end
    box rgba(46,160,67,0.18) TeamVault code
        participant UDS as AppUserDetailsService
    end
    box rgba(56,139,253,0.18) Spring Data JPA
        participant JPA as AppUserRepository, Hibernate
    end
    box rgba(46,160,67,0.18) TeamVault code
        participant Tok as TokenService
    end
    box rgba(56,139,253,0.18) Spring Security OAuth2 JOSE
        participant Enc as NimbusJwtEncoder
    end

    Client->>Ctl: POST /api/auth/login (permitAll, @Valid ran)
    Ctl->>Svc: login(request)
    Svc->>AM: authenticate(unauthenticated(email, password))
    Note over AM: the manager asks its one provider, built in AuthenticationConfig<br/>from our UserDetailsService and the BCryptPasswordEncoder bean
    AM->>UDS: loadUserByUsername(email)
    Note over UDS: same normalisation as registration: strip, lower-case
    UDS->>JPA: findByEmail("carol@acme.io")
    JPA-->>UDS: AppUser or empty
    UDS-->>AM: AuthenticatedUser(id, email, passwordHash), or UsernameNotFoundException
    Note over AM: passwordEncoder.matches(raw, hash): bcrypt again, about 100 ms.<br/>Unknown email: a dummy bcrypt compare runs anyway, so timing does not reveal which emails exist,<br/>and the exception is replaced by BadCredentialsException (hideUserNotFoundExceptions).<br/>Any failure: AuthenticationException to the advice, one 401 bad-credentials body.
    AM-->>Svc: Authentication, principal = AuthenticatedUser
    Svc->>Tok: issue(principal)
    Note over Tok: claims: iss "teamvault", sub = user UUID (stable if the email changes),<br/>email, iat = now, exp = now + 15 minutes (teamvault.jwt.ttl). Header: HS256.
    Tok->>Enc: encode(header, claims)
    Note over Enc: HMAC-SHA256 over header.payload with the key from TEAMVAULT_JWT_SECRET,<br/>at least 32 characters, validated at boot by JwtProperties
    Enc-->>Tok: compact JWS: header.payload.signature
    Tok-->>Svc: TokenResponse(token, expiresAt)
    Svc-->>Ctl: TokenResponse
    Ctl-->>Client: 200, the token and its expiry
```

</details>

## C: every later request verifies the token

```http
GET /api/me
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

![Verification on a later request](03-credentials-and-the-token-3.svg)

Full size: [open the raw SVG](https://raw.githubusercontent.com/luke-ken/teamvault/main/docs/walkthroughs/03-credentials-and-the-token-3.svg).

<details>
<summary>Mermaid source (editable mirror of the diagram, re-render the SVG after a change)</summary>

```mermaid
sequenceDiagram
    autonumber
    actor Client
    box rgba(56,139,253,0.18) Spring Security
        participant Filter as BearerTokenAuthenticationFilter, JwtAuthenticationProvider
        participant Dec as NimbusJwtDecoder
    end
    box rgba(46,160,67,0.18) TeamVault code
        participant Ctl as UserController
        participant Svc as UserService
    end
    box rgba(56,139,253,0.18) Spring Data JPA
        participant JPA as AppUserRepository
    end

    Client->>Filter: GET /api/me, Authorization: Bearer ...
    Filter->>Dec: decode(token)
    Note over Dec: built in JwtConfig from the same key as the encoder.<br/>Parse, check the HS256 signature, then the validators:<br/>exp and nbf with 60 s clock skew, iss must be "teamvault".<br/>No database, no network: the signature is the proof.
    Dec-->>Filter: Jwt with its claims, or JwtException
    Note over Filter: success: JwtAuthenticationToken in the SecurityContext, principal = the Jwt.<br/>failure: InvalidBearerTokenException to the entry point, 401 invalid-token (walkthrough 2, Exit B)
    Filter->>Ctl: me(@AuthenticationPrincipal Jwt caller)
    Note over Ctl: Caller.id(jwt) = UUID.fromString(jwt.getSubject())
    Ctl->>Svc: me(callerId)
    Svc->>JPA: findById(callerId)
    JPA-->>Svc: AppUser, or empty (row deleted within the token's 15 minutes: ForbiddenException, 403)
    Svc-->>Ctl: UserResponse
    Ctl-->>Client: 200
```

</details>

## What happens, step by step

1. **Registration never sees the chain (A, arrows 1 and 2).** The two auth routes are
   `permitAll`, because a client cannot present a token before it has one. Validation runs
   before the controller: the password length rule has an upper bound of 72 because bcrypt
   reads no more than 72 bytes.
2. **Normalise, check, hash, insert (A, arrows 3 to 12).** The email is stripped and
   lower-cased once, and every later lookup uses the same rule, so the unique constraint on
   `app_user.email` is effectively case-insensitive. The exists check gives a readable 409;
   the constraint handles the race. The hash comes from `BCryptPasswordEncoder` with cost 10,
   a random salt per call, and a deliberate cost of about 100 ms, which is the lever against
   offline guessing and also the reason login rate limiting is on the ladder.
3. **The hash stays inside (A, arrows 13 and 14).** `UserResponse` has no field for it. The
   two request records override `toString` without the password, and `AuthenticatedUser`,
   the login principal, without the hash, so a log line of either cannot leak them.
4. **Login delegates the check to Spring (B, arrows 3 to 9).** `AuthService` does not compare
   passwords. It hands an unauthenticated token to the `AuthenticationManager`, a
   `ProviderManager` with one `DaoAuthenticationProvider`, built in `AuthenticationConfig`
   from our `AppUserDetailsService` and the encoder bean. The provider loads the user through
   our service, which applies the same normalisation, then runs `matches` against the stored
   hash. An unknown email still costs one bcrypt compare and is reported as bad credentials,
   so neither the body nor the timing tells which emails exist.
5. **Issue the token (B, arrows 10 to 16).** `TokenService` builds the claims: the issuer,
   the user's UUID as subject, the email as a convenience claim, issued-at and a 15 minute
   expiry. `NimbusJwtEncoder` signs with HMAC-SHA256 using the key from
   `TEAMVAULT_JWT_SECRET`. The client gets the compact token and its expiry and keeps it; the
   server keeps nothing, which is why there is no logout endpoint.
6. **Verify without the database (C, arrows 1 to 4).** On every later request the bearer
   filter hands the token to `NimbusJwtDecoder`, built in `JwtConfig` from the same key. It
   checks the signature, the expiry with 60 seconds of clock skew, and the issuer. A valid
   token becomes a `JwtAuthenticationToken` whose principal is the `Jwt`; that is what
   `@AuthenticationPrincipal` injects in every controller.
7. **Who the subject is (C, arrows 5 to 9).** `Caller.id` reads the subject claim. Most
   endpoints never look the user up again. `GET /api/me` does, and a user row deleted inside
   the token's lifetime is answered with 403, not with an empty 200 or a 500.

## Who wrote what

| Piece | Written by us | Configured by us | Spring does it |
|---|---|---|---|
| Registration rules | `AuthService.register`, `RegisterRequest` constraints | | validation, `INSERT`, constraint translation |
| Password hashing | | `PasswordEncoder` bean = `BCryptPasswordEncoder` | bcrypt, salt, cost, `matches`, timing mitigation |
| Credential check | `AppUserDetailsService`, `AuthenticatedUser` | `AuthenticationManager` bean in `AuthenticationConfig` | `ProviderManager`, `DaoAuthenticationProvider` |
| Token contents | `TokenService` (claims, TTL) | `JwtProperties`, `teamvault.jwt.*` | `NimbusJwtEncoder`, HMAC |
| Token check | | `JwtDecoder` bean in `JwtConfig`, the issuer validator | bearer filter, parse, signature, exp and nbf, `SecurityContext` |
| Who is calling | `Caller.id`, `UserService.me` | | `@AuthenticationPrincipal` injection |

Our classes on this page together are under 150 lines. The security-relevant algorithms,
bcrypt and HMAC-SHA256, and the sequence of the credential check are Spring's; what we own
is which user is loaded, what goes into the token, how long it lives, and where the key
comes from.

## Where to look

- `backend/src/main/java/.../auth/AuthController.java`, `AuthService.java`,
  `AuthenticationConfig.java`, `TokenService.java`, `JwtConfig.java`, `JwtProperties.java`,
  `Caller.java`
- `backend/src/main/java/.../user/AppUserDetailsService.java`, `AuthenticatedUser.java`,
  `UserService.java`
- `backend/src/main/java/.../config/SecurityConfig.java` for `permitAll` on `/api/auth/**`
  and the resource server line
- `docs/adr/004-self-issued-jwt-auth.md`, `docs/errors.md` for `bad-credentials` and
  `invalid-token`
- Tests: `AuthServiceTest` (unit: normalisation, duplicate, the hash is the encoder's
  output, login delegates and issues), `AuthApiIntegrationTest` (full: register then login,
  the subject is the user id, same body for unknown email and wrong password, `/api/me`)

## What breaks if you remove it

- **The normalisation in `AppUserDetailsService`:** a user who registered as `Carol@Acme.io`
  was stored as `carol@acme.io` and can no longer log in with the spelling they typed. The
  unit test for the lower-casing covers registration; the integration test covers login.
- **The size check in `JwtProperties`:** a short secret is accepted, HMAC-SHA256 runs with a
  key weaker than the algorithm assumes, and nothing fails until someone brute-forces it.
  With the check, a missing or short secret stops the application at boot.
- **The subject as UUID, using the email instead:** every token would die with an email
  change, and every guard lookup would need a join from email to user id. The id is the
  stable key, the email is a label.
- **The 15 minute TTL, making it a day:** there is no revocation, so a leaked token is
  usable for the whole day. The short TTL is the price of keeping the server stateless;
  refresh tokens (ADR-007, stretch) are the way to make it short without logging the user
  out every quarter hour.
- **The `toString` overrides:** a debug log of the request records prints the raw password,
  one of the principal prints the hash. Nothing fails, which is why it is easy to miss.

## Questions this page answers

- Where is the password compared, and why does `AuthService` never call `matches` itself?
- Why do an unknown email and a wrong password produce the same response, in body and in time?
- What is in the token, who signs it, and what does the server have to keep to verify it?
- Why is the subject the user's UUID and not the email?
- Why is there no logout endpoint, and what is the trade-off?
