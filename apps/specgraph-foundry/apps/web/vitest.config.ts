import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./vitest.setup.ts"],
    restoreMocks: true,
    unstubGlobals: true,
    css: true,
    // React + axe tests are materially slower on constrained Termux/Android
    // workers. This is a bounded test timeout, not a disabled timeout.
    testTimeout: 15_000,
    include: ["src/**/*.test.ts", "src/**/*.test.tsx"],
  },
  resolve: {
    alias: {
      "@": new URL("./src", import.meta.url).pathname,
    },
  },
});
