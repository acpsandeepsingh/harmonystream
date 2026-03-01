export const PLAYER_STATE_VERSION = 'player_v2';

const versionedKey = (key: string) => `${PLAYER_STATE_VERSION}:${key}`;

export function clearPlayerPersistence(keys: string[]) {
  for (const key of keys) {
    localStorage.removeItem(versionedKey(key));
  }
}

export function safeReadVersionedStorage<T>(
  key: string,
  fallback: T,
  validator: (value: unknown) => value is T
): T {
  const storageKey = versionedKey(key);
  const rawValue = localStorage.getItem(storageKey);
  if (!rawValue) {
    return fallback;
  }

  try {
    const parsed: unknown = JSON.parse(rawValue);
    if (!validator(parsed)) {
      throw new Error(`Invalid value shape for ${storageKey}`);
    }
    return parsed;
  } catch (error) {
    console.error(`[storage] Corrupted persisted state at ${storageKey}; clearing`, error);
    localStorage.removeItem(storageKey);
    return fallback;
  }
}

export function writeVersionedStorage(key: string, value: unknown) {
  localStorage.setItem(versionedKey(key), JSON.stringify(value));
}

export function removeVersionedStorage(key: string) {
  localStorage.removeItem(versionedKey(key));
}
