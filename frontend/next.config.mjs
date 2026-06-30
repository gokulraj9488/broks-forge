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
};

export default nextConfig;
