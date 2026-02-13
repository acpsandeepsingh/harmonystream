const isProd = process.env.NODE_ENV === 'production';

module.exports = {
  /**
   * Tell Next.js to export the app as a static site.
   * This is required for deployment on services like GitHub Pages.
   */
  output: 'export',

  /**
   * The base path for the app.
   * This is the name of your repository, and it's required for GitHub Pages.
   */
  basePath: isProd ? '/harmonystream' : '',
  
  /**
   * The asset prefix for the app.
   * This is also required for GitHub Pages to find your files (like CSS and JS).
   */
  assetPrefix: isProd ? '/harmonystream/' : undefined,

  /**
   * Disable image optimization, as it's not supported in a static export.
   */
  images: {
    unoptimized: true,
  },

  /**
   * Ensure that all page links end with a slash and are exported as index.html files.
   * This helps with compatibility on many static hosting platforms.
   */
  trailingSlash: true,
};
