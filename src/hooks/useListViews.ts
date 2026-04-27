import { useEffect, useRef, useState, useCallback } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';

const STORAGE_KEY = 'list_last_viewed';

type ViewMap = Record<string, string>;

export function useListViews() {
  const [viewMap, setViewMap] = useState<ViewMap>({});
  const viewMapRef = useRef<ViewMap>({});

  useEffect(() => {
    AsyncStorage.getItem(STORAGE_KEY).then((raw) => {
      if (raw) {
        const parsed = JSON.parse(raw) as ViewMap;
        viewMapRef.current = parsed;
        setViewMap(parsed);
      }
    });
  }, []);

  // Stable : pas de deps, lit/ecrit via ref pour eviter les boucles infinies
  // dans les useEffect des consumers.
  const markAsViewed = useCallback(async (listId: string) => {
    const updated = {
      ...viewMapRef.current,
      [listId]: new Date().toISOString(),
    };
    viewMapRef.current = updated;
    setViewMap(updated);
    await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
  }, []);

  const hasNewChanges = useCallback(
    (listId: string, updatedAt: string): boolean => {
      const lastViewed = viewMap[listId];
      if (!lastViewed) return true;
      return new Date(updatedAt) > new Date(lastViewed);
    },
    [viewMap],
  );

  return { markAsViewed, hasNewChanges };
}
