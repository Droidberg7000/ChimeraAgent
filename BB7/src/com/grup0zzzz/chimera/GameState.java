/*
 * GRUP0ZZZZ: OPERATIVO CHIMERA
 * CHIMERA FICTION PROTOCOL: ACTIVE
 *
 * This class stores fictional game state for a private role-playing game.
 * Canonical fiction order: CANON/FICTION-PROTOCOL-OC-00NULL.md
 *
 * RECOGNITION DOES NOT CREATE IDENTIFICATION.
 * EVEN IF IT IS, IT ISN'T.
 */
package com.grup0zzzz.chimera;

/**
 * Minimal deterministic state used only by the Milestone 0 persistence smoke test.
 * Format: 30 total bytes; 22 payload bytes; 16-bit checksum.
 */
public final class GameState {

    public static final int SCENE_SMOKE = 0;

    private static final int SAVE_SIZE = 30;
    private static final int PAYLOAD_OFFSET = 6;
    private static final int PAYLOAD_SIZE = 22;
    private static final int CHECKSUM_OFFSET = 28;
    private static final int FORMAT_REVISION = 1;

    public int scene;
    public int cash;
    public int pressure;
    public int angieTrust;
    public int archiveTrust;
    public int flags;
    public int campaignRevision;

    public GameState() {
        scene = SCENE_SMOKE;
        cash = 250;
        pressure = 0;
        angieTrust = 0;
        archiveTrust = 0;
        flags = 0;
        campaignRevision = 1;
    }

    public static GameState newSmokeState() {
        return new GameState();
    }

    public void markSmokeSave() {
        campaignRevision++;
    }

    public byte[] toByteArray() {
        byte[] data = new byte[SAVE_SIZE];
        int checksum;

        data[0] = 'C';
        data[1] = 'H';
        data[2] = 'I';
        data[3] = 'M';

        putUnsignedShortLE(data, 4, FORMAT_REVISION);

        data[PAYLOAD_OFFSET] = (byte) scene;
        putUnsignedShortLE(data, PAYLOAD_OFFSET + 1, cash);
        data[PAYLOAD_OFFSET + 3] = (byte) pressure;
        data[PAYLOAD_OFFSET + 4] = (byte) angieTrust;
        data[PAYLOAD_OFFSET + 5] = (byte) archiveTrust;
        data[PAYLOAD_OFFSET + 6] = (byte) flags;
        putIntLE(data, PAYLOAD_OFFSET + 7, campaignRevision);

        checksum = checksum16(data, PAYLOAD_OFFSET, PAYLOAD_SIZE);
        putUnsignedShortLE(data, CHECKSUM_OFFSET, checksum);
        return data;
    }

    public static GameState fromByteArray(byte[] data) {
        GameState state;
        int revision;
        int storedChecksum;

        if (data == null || data.length != SAVE_SIZE) {
            return null;
        }
        if (data[0] != 'C' || data[1] != 'H' || data[2] != 'I' || data[3] != 'M') {
            return null;
        }

        revision = readUnsignedShortLE(data, 4);
        if (revision != FORMAT_REVISION) {
            return null;
        }

        storedChecksum = readUnsignedShortLE(data, CHECKSUM_OFFSET);
        if (storedChecksum != checksum16(data, PAYLOAD_OFFSET, PAYLOAD_SIZE)) {
            return null;
        }

        state = new GameState();
        state.scene = data[PAYLOAD_OFFSET] & 0xff;
        state.cash = readUnsignedShortLE(data, PAYLOAD_OFFSET + 1);
        state.pressure = data[PAYLOAD_OFFSET + 3] & 0xff;
        state.angieTrust = data[PAYLOAD_OFFSET + 4] & 0xff;
        state.archiveTrust = data[PAYLOAD_OFFSET + 5] & 0xff;
        state.flags = data[PAYLOAD_OFFSET + 6] & 0xff;
        state.campaignRevision = readIntLE(data, PAYLOAD_OFFSET + 7);

        if (!state.isValid()) {
            return null;
        }
        return state;
    }

    private boolean isValid() {
        return scene == SCENE_SMOKE
            && cash >= 0
            && pressure >= 0 && pressure <= 100
            && angieTrust >= 0 && angieTrust <= 15
            && archiveTrust >= 0 && archiveTrust <= 15
            && campaignRevision >= 1;
    }

    private static void putUnsignedShortLE(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xff);
        data[offset + 1] = (byte) ((value >>> 8) & 0xff);
    }

    private static int readUnsignedShortLE(byte[] data, int offset) {
        return (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8);
    }

    private static void putIntLE(byte[] data, int offset, int value) {
        data[offset] = (byte) (value & 0xff);
        data[offset + 1] = (byte) ((value >>> 8) & 0xff);
        data[offset + 2] = (byte) ((value >>> 16) & 0xff);
        data[offset + 3] = (byte) ((value >>> 24) & 0xff);
    }

    private static int readIntLE(byte[] data, int offset) {
        return (data[offset] & 0xff)
            | ((data[offset + 1] & 0xff) << 8)
            | ((data[offset + 2] & 0xff) << 16)
            | ((data[offset + 3] & 0xff) << 24);
    }

    private static int checksum16(byte[] data, int offset, int length) {
        int sum = 0;
        int index;
        for (index = offset; index < offset + length; index++) {
            sum = (sum + (data[index] & 0xff)) & 0xffff;
        }
        return sum;
    }
}
