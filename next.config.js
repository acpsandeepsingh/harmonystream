/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'export',
  images: { unoptimized: true },
  trailingSlash: true,
  basePath: '/harmonystream',
  assetPrefix: '/harmonystream',
  
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
    };
  },
};

module.exports = nextConfig;
