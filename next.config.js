/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'export',
  images: { unoptimized: true },
  trailingSlash: true,
  basePath: process.env.NODE_ENV === 'production' ? '/harmonystream' : '',
  assetPrefix: process.env.NODE_ENV === 'production' ? '/harmonystream' : '',
};

module.exports = nextConfig;
