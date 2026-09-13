/*
 * GRUP0ZZZZ: OPERATIVO CHIMERA
 * CHIMERA FICTION PROTOCOL: ACTIVE
 */
package com.grup0zzzz.chimera;

import net.rim.device.api.ui.Graphics;
import net.rim.device.api.ui.container.MainScreen;

/** Shared visual base for the native BB7 smoke-test screens. */
public abstract class ChimeraScreen extends MainScreen {

    protected static final int COLOR_NEAR_BLACK = 0x1A1A1A;
    protected static final int COLOR_AMBER = 0xFF9900;
    protected static final int COLOR_CYAN = 0x00FFFF;
    protected static final int COLOR_WHITE = 0xFFFFFF;

    protected ChimeraScreen(long style) {
        super(style);
    }

    protected void paintBackground(Graphics graphics) {
        graphics.setColor(COLOR_NEAR_BLACK);
        graphics.fillRect(0, 0, getWidth(), getHeight());
    }
}
