# identity-service

## Identity REST client

Configure applications that use `identity-rest-client` with the Identity service URL and an API key
from their secret manager:

```properties
vg.identity.rest-client.base-url=https://identity.example.com
vg.identity.rest-client.api-key=${VG_IDENTITY_REST_CLIENT_API_KEY}
```

The API key is required and is sent automatically on every request made through the typed client.
