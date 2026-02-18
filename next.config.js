/** @type {import('next').NextConfig} */

// Detect if building for Android
const isAndroidBuild = process.env.BUILD_TARGET === 'android';

const nextConfig = {
  output: 'export',
  images: { unoptimized: true },
  trailingSlash: true,
  
  // Only use basePath for web builds (GitHub Pages)
  // NOT for Android builds
  ...(!isAndroidBuild && {
    basePath: '/harmonystream',
    assetPrefix: '/harmonystream',
  }),
  
  exportPathMap: async function () {
    return {
      '/': { page: '/' },
      '/search': { page: '/search' },
      '/library': { page: '/library' },
      '/library/playlist': { page: '/library/playlist' },
      '/history': { page: '/history' },
      '/profile': { page: '/profile' },
      '/settings': { page: '/settings' },
      '/login': { page: '/login' },
      '/signup': { page: '/signup' },
      '/404': { page: '/404' },
    }
  },
};

module.exports = nextConfig;
