/** @type {import('next').NextConfig} */

// Detect if building for Android
const isAndroidBuild = process.env.BUILD_TARGET === 'android';

const nextConfig = {
  output: 'export',
  images: { unoptimized: true },
  trailingSlash: true,
  // Relative assets are required for the standalone Android WebView build.
  ...(isAndroidBuild && {
    assetPrefix: './',
  }),

  // Only use basePath for web builds (GitHub Pages)
  // NOT for Android builds
  ...(!isAndroidBuild && {
    basePath: '/harmonystream',
    assetPrefix: '/harmonystream',
  }),

};

module.exports = nextConfig;
