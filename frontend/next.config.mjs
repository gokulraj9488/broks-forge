const apiOrigin = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

// Mirrors the backend's CSP baseline (SecurityConfig#securityFilterChain), plus connect-src for the
// API origin the SPA calls directly from the browser. 'unsafe-inline' on script/style matches Next.js's
// own requirements (inline hydration bootstrap, Tailwind-adjacent inline styles) — the same trade-off
// already accepted on the backend's responses.
const contentSecurityPolicy = [
  "default-src 'self'",
  "img-src 'self' data:",
  "font-src 'self'",
  "style-src 'self' 'unsafe-inline'",
  "script-src 'self' 'unsafe-inline'",
  `connect-src 'self' ${apiOrigin}`,
  "frame-ancestors 'none'",
  "object-src 'none'",
  "base-uri 'none'",
  "form-action 'self'",
].join("; ");

const securityHeaders = [
  { key: "Content-Security-Policy", value: contentSecurityPolicy },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Strict-Transport-Security", value: "max-age=31536000; includeSubDomains" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
];

/** @type {import('next').NextConfig} */
const nextConfig = {
  // Produce a self-contained server bundle for a slim production Docker image.
  output: "standalone",
  reactStrictMode: true,
  poweredByHeader: false,
  // Type-checking and linting run in CI (`npm run typecheck` / `npm run lint`).
  // They are skipped during the container image build to keep image builds fast
  // and deterministic. Flip these to `false` in CI to enforce on every commit.
  eslint: {
    ignoreDuringBuilds: true,
  },
  typescript: {
    ignoreBuildErrors: true,
  },
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
};

export default nextConfig;
