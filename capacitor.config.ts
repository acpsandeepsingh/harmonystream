import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.sansoft.harmonystram',
  appName: 'HarmonyStream',
  webDir: 'out',
  server: {
    androidScheme: 'https',
    hostname: 'app.capacitor.dev'
  }
};

export default config;
