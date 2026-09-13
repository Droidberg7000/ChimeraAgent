/*
 * GRUP0ZZZZ: OPERATIVO CHIMERA
 * CHIMERA FICTION PROTOCOL: ACTIVE
 *
 * Milestone 0 visual and persistence smoke-test screen.
 */
package com.grup0zzzz.chimera;

import net.rim.device.api.ui.Field;
import net.rim.device.api.ui.Keypad;
import net.rim.device.api.ui.component.LabelField;

/**
 * One-screen smoke test: render native fields, save a deterministic state,
 * and display the resulting campaign revision.
 */
public final class SmokeTestScreen extends ChimeraScreen {

    private final OperativoChimeraApp app;
    private final LabelField status;

    public SmokeTestScreen(OperativoChimeraApp application) {
        super(Field.NO_VERTICAL_SCROLL);
        app = application;

        setTitle("EL B0Zzzz // BB7 SMOKE");

        LabelField title = new LabelField("GRUP0ZZZZ: OPERATIVO CHIMERA", Field.NON_FOCUSABLE);
        title.setForegroundColor(COLOR_AMBER);
        add(title);

        LabelField subtitle = new LabelField("BB7 M0 NATIVE SMOKE TEST", Field.NON_FOCUSABLE);
        subtitle.setForegroundColor(COLOR_CYAN);
        add(subtitle);

        add(new LabelField(" ", Field.NON_FOCUSABLE));

        status = new LabelField("", Field.NON_FOCUSABLE);
        status.setForegroundColor(COLOR_WHITE);
        add(status);

        add(new LabelField(" ", Field.NON_FOCUSABLE));

        LabelField action = new LabelField("[ FIRE ] SAVE TEST STATE", Field.NON_FOCUSABLE);
        action.setForegroundColor(COLOR_AMBER);
        add(action);

        LabelField exit = new LabelField("[ 0 ] EXIT", Field.NON_FOCUSABLE);
        exit.setForegroundColor(COLOR_AMBER);
        add(exit);

        updateStatus();
    }

    protected boolean navigationClick(int statusValue, int time) {
        saveSmokeState();
        return true;
    }

    protected boolean keyChar(char character, int statusValue, int time) {
        if (character == '0') {
            app.exitApplication();
            return true;
        }
        if (character == ' ') {
            saveSmokeState();
            return true;
        }
        return super.keyChar(character, statusValue, time);
    }

    protected boolean keyDown(int keycode, int time) {
        if (Keypad.key(keycode) == Keypad.KEY_ESCAPE) {
            app.exitApplication();
            return true;
        }
        return super.keyDown(keycode, time);
    }

    private void saveSmokeState() {
        GameState state = app.getState();
        state.markSmokeSave();
        if (app.saveState()) {
            updateStatus();
        } else {
            status.setText("SAVE STATUS: FAILED");
        }
    }

    private void updateStatus() {
        GameState state = app.getState();
        String text;
        if (state.campaignRevision <= 1) {
            text = "SAVE STATUS: NONE\nREVISION: " + state.campaignRevision;
        } else {
            text = "SAVE STATUS: AVAILABLE\nREVISION: " + state.campaignRevision;
        }
        status.setText(text);
    }
}
