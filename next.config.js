/** @type {import('next').NextConfig} */

const WEB_BASE_PATH = '/harmonystream';

// Detect if building for Android
const isAndroidBuild = process.env.BUILD_TARGET === 'android';

const nextConfig = {
  output: 'export',
  images: { unoptimized: true },
  trailingSlash: true,

  // Keep website hosted under the project subpath.
  ...(!isAndroidBuild && {
    basePath: WEB_BASE_PATH,
    assetPrefix: WEB_BASE_PATH,
  }),

  // Relative assets are required for the standalone Android WebView build.
  ...(isAndroidBuild && {
    assetPrefix: './',
  }),
};

module.exports = nextConfig;
