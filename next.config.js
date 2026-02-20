/** @type {import('next').NextConfig} */

// Detect if building for Android
const isAndroidBuild = process.env.BUILD_TARGET === 'android';

const nextConfig = {
  output: 'export',
  images: { unoptimized: true },
  trailingSlash: true,
  // Android WebViewAssetLoader expects app assets on the appassets origin.
  ...(isAndroidBuild && {
    assetPrefix: 'https://appassets.androidplatform.net',
  }),

  // Only use basePath for web builds (GitHub Pages)
  // NOT for Android builds
  ...(!isAndroidBuild && {
    basePath: '/harmonystream',
    assetPrefix: '/harmonystream',
  }),

};

module.exports = nextConfig;
