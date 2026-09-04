#!/usr/bin/env node
// QA convenience script (invoked via `npm run docker:down`). Plain Node.js -
// see docker-build.js's own comment for why. Stops and removes every
// container `npm run docker:up` started. Database data survives by default -
// pass --volumes to also wipe it for a completely clean slate.
'use strict';
const { spawnSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');
const cwd = repoRoot;
const wipeVolumes = process.argv.includes('--volumes');
const volumeArgs = wipeVolumes ? ['-v'] : [];
if (wipeVolumes) {
  console.log('Also removing volumes (Postgres data will be wiped).');
}

console.log('Stopping the app (backend + frontend + HTTPS terminator)...');
const envProductionPath = path.join(repoRoot, 'infra', '.env.production');
if (fs.existsSync(envProductionPath)) {
  spawnSync(
    'docker',
    [
      'compose',
      '-f', 'infra/docker-compose.prod.yml',
      '-f', 'infra/local-https/docker-compose.local-https.yml',
      '--env-file', 'infra/.env.production',
      'down', ...volumeArgs,
    ],
    { cwd, stdio: 'inherit' },
  );
} else {
  console.log('  (infra/.env.production not found - nothing to stop here, skipping)');
}

console.log('Stopping Postgres + Mailpit...');
const downResult = spawnSync(
  'docker',
  ['compose', '-f', 'docker-compose.yml', 'down', ...volumeArgs],
  { cwd, stdio: 'inherit' },
);
if (downResult.status !== 0) {
  process.exit(downResult.status ?? 1);
}

console.log('Done.');
