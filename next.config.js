/** @type {import('next').NextConfig} */
const nextConfig = {
  // Required for static export
  output: 'export',

  // Required for deploying to a subdirectory on GitHub Pages
  basePath: '/harmonystream',

  // Required for static export
  images: {
    unoptimized: true,
  },
};

module.exports = nextConfig;
