/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'export',
  images: { unoptimized: true },
  trailingSlash: true,
  // REMOVED basePath + assetPrefix - GitHub Actions handles subfolder automatically
};

module.exports = nextConfig;
