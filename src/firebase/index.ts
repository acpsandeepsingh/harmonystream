'use client';

import { firebaseConfig } from '@/firebase/config';
import { initializeApp, getApps, getApp, FirebaseApp } from 'firebase/app';
import {
  getAuth,
  initializeAuth,
  indexedDBLocalPersistence,
  browserLocalPersistence,
  type Auth,
} from 'firebase/auth';
import { getFirestore } from 'firebase/firestore';

// IMPORTANT: DO NOT MODIFY THIS FUNCTION
export function initializeFirebase() {
  if (!getApps().length) {
    // During SSR/static generation there is no App Hosting runtime auto-injection, so we should
    // initialize directly from explicit config to avoid noisy app/no-options warnings in builds.
    const isServerRender = typeof window === 'undefined';
    if (isServerRender) {
      return getSdks(initializeApp(firebaseConfig));
    }

    // In browser runtimes, first try Firebase App Hosting auto initialization.
    let firebaseApp;
    try {
      firebaseApp = initializeApp();
    } catch (e) {
      // Fallback for local/dev and non-App Hosting deployments.
      if (process.env.NODE_ENV === 'production') {
        console.warn(
          'Automatic initialization failed. Falling back to firebase config object.',
          e
        );
      }
      firebaseApp = initializeApp(firebaseConfig);
    }

    return getSdks(firebaseApp);
  }

  // If already initialized, return the SDKs with the already initialized App
  return getSdks(getApp());
}

export function getSdks(firebaseApp: FirebaseApp) {
  let auth: Auth;
  try {
    // Try to initialize with specific persistence. This is more robust against
    // third-party cookie blocking issues in some browsers.
    auth = initializeAuth(firebaseApp, {
      persistence: [indexedDBLocalPersistence, browserLocalPersistence],
    });
  } catch (error) {
    // If auth is already initialized (e.g. on hot reload), get the existing instance.
    auth = getAuth(firebaseApp);
  }

  return {
    firebaseApp,
    auth,
    firestore: getFirestore(firebaseApp),
  };
}

export * from './provider';
export * from './client-provider';
export * from './firestore/use-collection';
export * from './firestore/use-doc';
export * from './non-blocking-updates';
export * from './non-blocking-login';
export * from './errors';
export * from './error-emitter';
