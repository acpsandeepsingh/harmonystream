/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'export',
  basePath: '/harmonystream',
  assetPrefix: '/harmonystream/',
  images: {
    unoptimized: true,
  },
};

module.exports = nextConfig;
