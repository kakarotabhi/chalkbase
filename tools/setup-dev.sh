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

echo "→ creating backend/src/main/resources/application-local.yml if missing"
local_cfg=backend/src/main/resources/application-local.yml
if [ -f "$local_cfg" ]; then
  echo "   already present, leaving it alone"
else
  cp "$local_cfg.example" "$local_cfg"
  echo "   created from the example. It is gitignored — put the database password there,"
  echo "   or better, export CHALKBASE_DB_PASSWORD in your shell."
fi

echo "→ installing frontend dependencies"
(cd frontend && npm ci)

echo "→ warming the backend build"
(cd backend && ./mvnw -q -B compile)

cat <<'MSG'

Setup complete.

  Backend   cd backend  && ./mvnw spring-boot:run     → http://localhost:8080
            Swagger UI                                 → http://localhost:8080/swagger-ui.html
            Health                                     → http://localhost:8080/actuator/health
  Frontend  cd frontend && npm start                   → http://localhost:4200

MSG
