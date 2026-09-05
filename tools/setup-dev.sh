#!/usr/bin/env bash
# One-time developer setup. Safe to re-run.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

echo "→ pointing git at the shared hooks in .githooks"
git config core.hooksPath .githooks

echo "→ checking toolchain"
java -version 2>&1 | head -1
node -v
npm -v

echo "→ installing frontend dependencies"
(cd frontend && npm ci)

echo "→ warming the backend build"
(cd backend && ./mvnw -q -B compile)

cat <<'MSG'

Setup complete.

  Backend   cd backend  && ./mvnw spring-boot:run     → http://localhost:8080
            H2 console                                 → http://localhost:8080/h2-console
            Swagger UI                                 → http://localhost:8080/swagger-ui.html
  Frontend  cd frontend && npm start                   → http://localhost:4200

MSG
