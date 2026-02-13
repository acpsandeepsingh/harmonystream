/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'export',
  basePath: '/harmonystream',
  images: {
    unoptimized: true,
  },
  trailingSlash: true,
};

module.exports = nextConfig;
