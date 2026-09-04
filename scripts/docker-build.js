#!/usr/bin/env node
// QA convenience script (invoked via `npm run docker:build`). Plain Node.js, no
// shell script - deliberately, after a real bug: `bash scripts/docker-build.sh`
// on Windows can resolve `bash` to the WSL launcher shim
// (C:\Windows\System32\bash.exe) instead of Git Bash if WSL isn't installed
// properly, failing with "execvpe(/bin/bash) failed". Node has no such
// ambiguity - `npm run` already requires Node to exist at all.
'use strict';
const { spawnSync } = require('node:child_process');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');
const buildCommit = spawnSync('git', ['rev-parse', 'HEAD'], { cwd: repoRoot, encoding: 'utf8' })
  .stdout?.trim() || 'unknown';
const imageTag = process.env.IMAGE_TAG || 'qa-local';

console.log(`Building foreigner-warsaw-backend:${imageTag} and foreigner-warsaw-frontend:${imageTag} (commit ${buildCommit})...`);

const result = spawnSync(
  'docker',
  ['compose', '-f', 'infra/docker-compose.prod.yml', 'build'],
  {
    cwd: repoRoot,
    stdio: 'inherit',
    env: { ...process.env, IMAGE_TAG: imageTag, BUILD_COMMIT: buildCommit },
  },
);

if (result.status !== 0) {
  process.exit(result.status ?? 1);
}
console.log("\nDone. Run 'npm run docker:up' to start the stack.");
