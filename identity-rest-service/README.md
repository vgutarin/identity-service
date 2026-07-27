# Identity REST service

## Application API-key authentication

Machine clients authenticate with one `X-VG-Identity-API-Key` header. Keys are issued for an
`IdentityApplication` by an administrator in the Vaadin application-management screen. The
full value is displayed only once; store it in the client's secret manager and never in source
code, URLs, or logs.

Every key has a required expiry and can be revoked immediately. Authentication is sessionless,
but the service checks the key on every request so revocation and expiry apply without delay.
Use TLS for every request.

The initial authenticated endpoint returns only the calling application's safe metadata:

```sh
curl --fail-with-body \
  -H "X-API-Key: ${IDENTITY_API_KEY}" \
  https://identity.example.com/api/v1/applications/me
```

It returns `uniqueId`, `workspaceUniqueId`, `name`, and `uri`. It never returns the API key,
key hash, or application payload. Missing, malformed, expired, revoked, or otherwise invalid
keys receive `401 Unauthorized` without credential-specific details.

Verify the changed modules with:

```sh
./gradlew :identity-logic:test :identity-rest-service:test
```
