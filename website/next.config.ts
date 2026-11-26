import type { NextConfig } from "next";

/**
 * Static export, for Cloudflare Pages.
 *
 * Every route in this site is prerendered — there are no server components doing
 * work, no route handlers, no cookies() or headers(). `next build` already reports
 * them all as static, so exporting costs nothing and buys a lot: Pages serves plain
 * files from its edge with no Workers runtime, no @opennextjs adapter to keep in step
 * with Next's releases, and nothing that can fail at request time.
 *
 * If this site ever needs a server route, this is the line to revisit — `output:
 * "export"` will fail the build rather than silently dropping it.
 */
const nextConfig: NextConfig = {
  output: "export",

  // Pre-emptive: nothing uses next/image today. Without this, the first <Image>
  // anyone adds fails the build, because the default loader needs a server. Pages
  // caches static images at the edge anyway, so not optimising costs little.
  images: { unoptimized: true },

  // Emit /crisis/index.html rather than /crisis.html, so the URLs keep working the
  // same way with or without a trailing slash.
  trailingSlash: true,
};

export default nextConfig;
