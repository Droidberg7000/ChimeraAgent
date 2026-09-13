/*
 * GRUP0ZZZZ: OPERATIVO CHIMERA
 * CHIMERA FICTION PROTOCOL: ACTIVE
 *
 * Native BlackBerry Java smoke-test application.
 * This is fictional game-interface code, not a real-world service.
 */
package com.grup0zzzz.chimera;

import net.rim.device.api.ui.UiApplication;

/** Entry point for the Milestone 0 native BB7 persistence smoke test. */
public final class OperativoChimeraApp extends UiApplication {

    private GameState state;

    public static void main(String[] args) {
        OperativoChimeraApp app = new OperativoChimeraApp();
        app.enterEventDispatcher();
    }

    private OperativoChimeraApp() {
        state = SaveRepository.load();
        if (state == null) {
            state = GameState.newSmokeState();
        }
        pushScreen(new SmokeTestScreen(this));
    }

    public GameState getState() {
        return state;
    }

    public boolean saveState() {
        return SaveRepository.save(state);
    }

    public void exitApplication() {
        while (getScreenCount() > 0) {
            popScreen(getActiveScreen());
        }
    }
}
