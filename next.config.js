module.exports = {
  output: 'export',
  images: {
    unoptimized: true,
  },
  // The basePath is the name of your repository
  basePath: process.env.NODE_ENV === 'production' ? '/harmonystream' : '',
  assetPrefix: process.env.NODE_ENV === 'production' ? '/harmonystream/' : '',
};
