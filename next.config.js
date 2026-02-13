/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'export',
  images: { unoptimized: true },
  trailingSlash: true,
  basePath: '/harmonystream',
  assetPrefix: '/harmonystream',
  
  // Explicitly define all routes to export
  exportPathMap: async function (
    defaultPathMap,
    { dev, dir, outDir, distDir, buildId }
  ) {
    return {
      '/': { page: '/' },
      '/search': { page: '/search' },
      '/library': { page: '/library' },
      '/history': { page: '/history' },
      '/profile': { page: '/profile' },
      '/settings': { page: '/settings' },
      '/login': { page: '/login' },
      '/signup': { page: '/signup' },
      '/404': { page: '/404' },
    }
  },
};

module.exports = nextConfig
