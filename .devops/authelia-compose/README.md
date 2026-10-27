# Authelia (dev OIDC provider)

Reference provider for trying OIDC sign-in locally. Dev only: self-signed TLS, users in a file,
notification emails written to `/data/notification.txt` in the container.

Authelia must run on https and its cookie domain must contain a dot and not be `localhost`, so it is
served as `https://auth.example.com:9091`. Add to `/etc/hosts`:

```
127.0.0.1 auth.example.com
```

## Secrets (once)

Generated into `secrets/` (git-ignored): session/storage/JWT/HMAC secrets, the TLS certificate and
the ID-token signing key, plus a truststore so the backend JVM trusts the self-signed certificate.

```bash
cd .devops/authelia-compose
mkdir -p secrets
docker run --rm -u "$(id -u):$(id -g)" -v "$PWD/secrets:/secrets" authelia/authelia:4.39.28 sh -c '
  authelia crypto rand --length 64 --charset alphanumeric /secrets/session /secrets/storage /secrets/jwt /secrets/hmac &&
  authelia crypto certificate rsa generate --common-name auth.example.com --sans auth.example.com \
    --directory /secrets --file.certificate tls.crt --file.private-key tls.key &&
  authelia crypto pair rsa generate --bits 2048 --directory /secrets --file.private-key oidc.key --file.public-key oidc.pub'
keytool -importcert -noprompt -alias authelia -file secrets/tls.crt \
  -keystore secrets/truststore.p12 -storetype PKCS12 -storepass changeit
```

## Run

```bash
docker compose -f .devops/authelia-compose/compose.yaml up -d
```

Open `https://auth.example.com:9091` once and accept the certificate. Users (`users_database.yml`):
`admin` / `admin-dev-password` (admin@example.com) and `alice` / `alice-dev-password` (alice@example.com).

Backend against it (from the repo root):

```bash
JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=$PWD/.devops/authelia-compose/secrets/truststore.p12 -Djavax.net.ssl.trustStorePassword=changeit" \
  ADMIN_EMAIL=admin@example.com OIDC_ISSUER=https://auth.example.com:9091 OIDC_CLIENT_ID=statistics-ui OIDC_NAME=Authelia \
  ./gradlew run
```

Client `statistics-ui` is public (PKCE S256, no secret) with redirect URIs `http://localhost:8081/`
(web UI dev server) and `http://127.0.0.1/callback` (desktop app; any port is accepted for a loopback IP).
