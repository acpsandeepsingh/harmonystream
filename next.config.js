/** @type {import('next').NextConfig} */
const isGithubActions = process.env.GITHUB_ACTIONS === 'true';
const repo = process.env.GITHUB_REPOSITORY?.split('/')[1] || 'harmonystream';

const nextConfig = {
  output: 'export',
  images: { unoptimized: true },

};

module.exports = nextConfig;
