package com.javaboy.chimera;
/** Every method belongs to Runner's single worker. This interface is not an emulator. */
public interface Core {
    interface Factory { Core create() throws Exception; }
    void active(boolean value) throws Exception;
    void buttons(int mask) throws Exception;
    boolean hasAudio();
    void sound(boolean value) throws Exception;
    void step(boolean render) throws Exception;
    void pixels(int[] rgb160x144) throws Exception;
    String batteryKey();
    byte[] battery() throws Exception;
    /** Validate the entire image before mutating live state. */
    void battery(byte[] image) throws Exception;
    void close() throws Exception;
}
