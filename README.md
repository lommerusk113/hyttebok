# Hyttebok

```bash
docker compose up -d --build   # http://localhost:8113
```

Tests: `./gradlew test` in `backend/` (needs Docker: Testcontainers starts a Postgres). With Colima, set
`DOCKER_HOST=unix://$HOME/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`.

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

Anyone can log in and create cabins of their own (*Mine hytter*); everyone only sees the cabins they belong to.
Each cabin lives under `/h/<id>/…`. Others get in through an invitation link, valid for 14 days. Cabins are never
listed for anyone else. System admins (`ADMIN_EMAILS`) find every cabin and user in the admin console (*Admin* in the
menu) and can switch on *Adminmodus* to act as owner in any cabin, for support.
Sessions last 90 days; the signing key is generated on first start and kept in the database. A new password, or
*Logg ut overalt* on *Min profil*, logs out every device.
Forgotten password (there is no email service): a recovery code saved earlier on *Min profil*, or a one-day
*new password* link made by a system admin (on the user's page in the admin console).
Admins must log in with Google at least once: a password account doesn't prove the email is theirs.

## Email (optional)

Without email, forgotten passwords go through recovery codes or an admin. With it, people get a "send me a link"
button, owners can send invitations by email, and admins can email reset links. Any SMTP provider works; put this
in `.env` (port 465 = SSL, otherwise STARTTLS) and set up SPF/DKIM for the sender's domain with the provider:

```
SMTP_HOST=smtp-relay.example.com
SMTP_PORT=587
SMTP_USERNAME=...
SMTP_PASSWORD=...
MAIL_FROM=hyttebok@your-domain.no
PUBLIC_URL=https://your-domain.no
```

## AI chat

Set `OPENAI_API_KEY`, then a system admin switches the chat on per cabin (*Tilgang*). Each cabin may use $1 a month
(`hyttebok.chat.monthly-cap-usd`); the app only shows how many percent of it is used.

## Backups

The `backup` service dumps the database once a day into `./backups` (or `BACKUP_DIR`), keeps 14 days of dumps
(`BACKUP_KEEP_DAYS`), and copies new photos into `./backups/uploads`. Keep that folder on another disk or sync it
somewhere else. To restore:

```bash
docker compose stop backend
docker compose exec -T db pg_restore -U hyttebok -d hyttebok --clean --if-exists < backups/hyttebok-YYYY-MM-DD.dump
docker compose cp backups/uploads/. backend:/data/uploads/
docker compose start backend
```
