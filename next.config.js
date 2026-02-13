/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'export',
  images: { unoptimized: true },
  basePath: '/harmonystream',
  trailingSlash: true,
};

module.exports = nextConfig;
