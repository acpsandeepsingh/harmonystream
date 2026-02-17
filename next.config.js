/** @type {import('next').NextConfig} */
 const buildTarget = process.env.BUILD_TARGET;
const isGitHubPagesBuild = buildTarget === 'gh-pages';

const nextConfig = {
  output: 'export',
  images: { unoptimized: true },
  trailingSlash: true,
  ...(isGitHubPagesBuild
    ? {
        basePath: '/harmonystream',
        assetPrefix: '/harmonystream',
      }
    : {}),
};
