# Hyttebok

```bash
docker compose up -d --build   # http://localhost:8113
```

Local dev with hot reload: start Postgres (`docker compose up -d db` after adding a `5432:5432` port mapping), run `./gradlew run` in `backend/` (needs JDK 25), then `npm run dev` in `frontend/`.
