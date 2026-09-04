#!/usr/bin/env node
// QA convenience script (invoked via `npm run docker:up`). Plain Node.js - see
// docker-build.js's own comment for why (a real WSL-vs-Git-Bash PATH bug on
// Windows). Brings up Postgres + Mailpit + the real production app + a local
// HTTPS terminator in one command.
'use strict';
const { spawnSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');
const cwd = repoRoot;

// A pre-existing DB_USERNAME/DB_PASSWORD in the OS environment shadows the
// generated .env.production values below (a real, documented gotcha - see
// CLAUDE.md/LOCAL_SETUP.md).
const env = { ...process.env };
delete env.DB_USERNAME;
delete env.DB_PASSWORD;
delete env.DB_HOST;
delete env.DB_NAME;
delete env.DB_PORT;

function run(command, args) {
  const result = spawnSync(command, args, { cwd, stdio: 'inherit', env });
  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}

console.log('Starting Postgres + Mailpit...');
run('docker', ['compose', '-f', 'docker-compose.yml', 'up', '-d']);

const certPath = path.join(repoRoot, 'infra', 'local-https', 'certs', 'localhost.crt');
if (!fs.existsSync(certPath)) {
  console.log('Generating a local throwaway HTTPS certificate...');
  const certDir = path.join(repoRoot, 'infra', 'local-https', 'certs');
  fs.mkdirSync(certDir, { recursive: true });
  const opensslEnv = { ...env, MSYS_NO_PATHCONV: '1' };
  const opensslResult = spawnSync(
    'openssl',
    [
      'req', '-x509', '-nodes', '-newkey', 'rsa:2048',
      '-keyout', path.join(certDir, 'localhost.key'),
      '-out', path.join(certDir, 'localhost.crt'),
      '-days', '30',
      '-subj', '/CN=localhost',
      '-addext', 'subjectAltName=DNS:localhost,IP:127.0.0.1',
    ],
    { cwd, stdio: 'inherit', env: opensslEnv },
  );
  if (opensslResult.status !== 0) {
    console.error(
      "\nCould not generate a local HTTPS certificate (is 'openssl' on your PATH? " +
        'Git for Windows includes it - try a Git Bash terminal, or install OpenSSL ' +
        'separately). The app itself will not start without it.',
    );
    process.exit(opensslResult.status ?? 1);
  }
}

const envProductionPath = path.join(repoRoot, 'infra', '.env.production');
if (!fs.existsSync(envProductionPath)) {
  console.log('Generating infra/.env.production for local QA use (never committed - see .gitignore)...');
  const contents = [
    'APP_PUBLIC_URL=https://localhost:8443',
    'DB_HOST=host.docker.internal',
    'DB_PORT=5432',
    'DB_NAME=foreigner_warsaw',
    'DB_USERNAME=foreigner_warsaw',
    'DB_PASSWORD=foreigner_warsaw_local_dev_only',
    'MAIL_HOST=host.docker.internal',
    'MAIL_PORT=1025',
    'MAIL_USERNAME=',
    'MAIL_PASSWORD=',
    'HTTP_PORT=18080',
    'IMAGE_TAG=qa-local',
    'BUILD_COMMIT=local',
    'APP_ADMIN_BOOTSTRAP_ENABLED=false',
    'ADMIN_BOOTSTRAP_EMAIL=',
    'ADMIN_BOOTSTRAP_PASSWORD=',
    '',
  ].join('\n');
  fs.writeFileSync(envProductionPath, contents);
}

console.log('Starting the app (backend + frontend + HTTPS terminator)...');
run('docker', [
  'compose',
  '-f', 'infra/docker-compose.prod.yml',
  '-f', 'infra/local-https/docker-compose.local-https.yml',
  '--env-file', 'infra/.env.production',
  'up', '-d',
]);

console.log('\nReady:');
console.log('  App:      https://localhost:8443  (self-signed cert - click through the browser warning once)');
console.log('  Mailpit:  http://localhost:8025   (every email the app sends appears here)');
console.log("\nRun 'npm run docker:down' to stop everything.");
