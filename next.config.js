/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'export',
  images: { unoptimized: true },
  trailingSlash: true,
  basePath: '/harmonystream',
  assetPrefix: '/harmonystream/',
};

module.exports = nextConfig;
