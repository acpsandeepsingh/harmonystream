/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'export',
  basePath: '/harmonystream',
  assetPrefix: '/harmonystream/',
  trailingSlash: true,
  images: {
    unoptimized: true,
  },
};
 
module.exports = nextConfig;
