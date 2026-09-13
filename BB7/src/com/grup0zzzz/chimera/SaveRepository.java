/*
 * GRUP0ZZZZ: OPERATIVO CHIMERA
 * CHIMERA FICTION PROTOCOL: ACTIVE
 *
 * This class stores fictional local game state only.
 * Canonical fiction order: CANON/FICTION-PROTOCOL-OC-00NULL.md
 */
package com.grup0zzzz.chimera;

import net.rim.device.api.system.PersistentObject;
import net.rim.device.api.system.PersistentStore;

/** Local BlackBerry PersistentStore adapter for the Milestone 0 smoke test. */
public final class SaveRepository {

    private static final long STORE_GUID = 0x5A3B9C2D4E6F1A0BL;

    private SaveRepository() {
    }

    public static boolean save(GameState state) {
        PersistentObject store;
        byte[] data;

        if (state == null) {
            return false;
        }

        try {
            store = PersistentStore.getPersistentObject(STORE_GUID);
            data = state.toByteArray();
            synchronized (store) {
                store.setContents(data);
                store.commit();
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static GameState load() {
        PersistentObject store;
        Object contents;

        try {
            store = PersistentStore.getPersistentObject(STORE_GUID);
            synchronized (store) {
                contents = store.getContents();
            }
            if (!(contents instanceof byte[])) {
                return null;
            }
            return GameState.fromByteArray((byte[]) contents);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static boolean clear() {
        PersistentObject store;

        try {
            store = PersistentStore.getPersistentObject(STORE_GUID);
            synchronized (store) {
                store.setContents(null);
                store.commit();
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
