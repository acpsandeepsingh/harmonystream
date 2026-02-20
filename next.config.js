/** @type {import('next').NextConfig} */
const WEB_BASE_PATH = '/harmonystream';
const isAndroidBuild = process.env.BUILD_TARGET === 'android';

const nextConfig = {
  output: 'export',
  images: { unoptimized: true },
  trailingSlash: true,
  
  // Website: Use basePath
  ...(!isAndroidBuild && {
    basePath: WEB_BASE_PATH,
    assetPrefix: WEB_BASE_PATH,
  }),
  
  // Android: NO assetPrefix! 
  // Remove the assetPrefix: './' line completely!
};

module.exports = nextConfig;
