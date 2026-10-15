# Hyttebok

```bash
docker compose up -d --build   # http://localhost:8113
```

Local dev with hot reload: start Postgres (`docker compose up -d db` after adding a `5432:5432` port mapping), run `./gradlew run` in `backend/` (needs JDK 25), then `npm run dev` in `frontend/`.

## Login (optional)

People log in with Google or with email and password. Login is on once `GOOGLE_CLIENT_ID` or `ADMIN_EMAILS` is set;
without either, the app has no login and is open to everyone who can reach it.

1. In [Google Cloud Console](https://console.cloud.google.com/apis/credentials), create an OAuth client ID of type *Web application*.
   Add the authorised redirect URI `http://localhost:8113/oauth/callback/google` (and `https://<your-domain>/oauth/callback/google` when hosted).
   Google only accepts `localhost` or `https` addresses.
2. Put the client ID and secret in a `.env` file next to `docker-compose.yml` (not committed):

   ```
   GOOGLE_CLIENT_ID=....apps.googleusercontent.com
   GOOGLE_CLIENT_SECRET=...
   ADMIN_EMAILS=you@gmail.com
   ```
3. `docker compose up -d backend`

Anyone with a Google account can log in, but only members see the cabin. System admins (`ADMIN_EMAILS`, comma-separated) open *Tilgang* and give access, their own included.
Owners can then share invitation links (valid 14 days) and approve people who tap *Be om tilgang*.
Sessions last 90 days; the signing key is generated on first start and kept in the database.
Forgotten password: an owner or admin makes a one-day *new password* link on *Tilgang* (there is no email service).
Admins must log in with Google at least once: a password account doesn't prove the email is theirs.
