#!/usr/bin/env node
/**
 * Runs the ATROPOS jar under whatever JVM the host has.
 *
 * A launcher, not a reimplementation: every argument, both streams, the signal
 * handling and the exit code belong to the JVM. `stdio: "inherit"` is what
 * makes the terminal UI work at all -- ATROPOS reads raw keys and draws its
 * own frames, and a wrapper that piped those would break both.
 */

const { spawnSync } = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");

const jar =
  process.env.ATROPOS_JAR ||
  path.join(__dirname, "..", "vendor", "ATROPOS.jar");

if (!fs.existsSync(jar)) {
  console.error(
    `ATROPOS: no jar at ${jar}\n` +
      "The install-time download did not complete. Retry with:\n" +
      "  npm rebuild @mjmichaelware/atropos\n" +
      "or point ATROPOS_JAR at a jar you already have."
  );
  process.exit(1);
}

const javaOpts = process.env.ATROPOS_JAVA_OPTS
  ? process.env.ATROPOS_JAVA_OPTS.split(/\s+/).filter(Boolean)
  : [];

const result = spawnSync(
  process.env.JAVA_HOME
    ? path.join(process.env.JAVA_HOME, "bin", "java")
    : "java",
  [...javaOpts, "-jar", jar, ...process.argv.slice(2)],
  { stdio: "inherit" }
);

if (result.error) {
  if (result.error.code === "ENOENT") {
    console.error(
      "ATROPOS: java not found. A JVM 17 or newer is required.\n" +
        "  Termux:  pkg install openjdk-21\n" +
        "  Debian:  sudo apt install openjdk-21-jre-headless"
    );
    process.exit(127);
  }
  console.error(`ATROPOS: ${result.error.message}`);
  process.exit(1);
}

// Killed by a signal, not an exit code. Reporting 0 here would tell a caller's
// shell that a Ctrl-C'd run succeeded.
if (result.signal) {
  process.exit(1);
}

process.exit(result.status ?? 0);
