package com.javaboy.chimera;
/** Actual generated test pattern. Deliberately NOT a ROM execution engine. */
public final class Diagnostic implements Core,Core.Factory {
    private int tick,mask;private boolean running;
    public Core create() { return new Diagnostic(); }
    public void active(boolean value) { running=value; }
    public void buttons(int value) { mask=value; }
    public boolean hasAudio() { return false; }
    public void sound(boolean value) { }
    public void step(boolean render) { if(!running) throw new IllegalStateException("paused");tick++; }
    public void pixels(int[] rgb) {
        for(int y=0;y<144;y++) for(int x=0;x<160;x++)
            rgb[y*160+x]=(x==(tick&127)?0xffffff:((x*255/160)<<16)|((y*255/144)<<8)|(mask&255));
    }
    public String batteryKey() { return "diagnostic_v1"; }
    public byte[] battery() { return new byte[]{'D','I','A','G',(byte)(tick>>>24),(byte)(tick>>>16),(byte)(tick>>>8),(byte)tick}; }
    public void battery(byte[] b) {
        if(b==null||b.length!=8||b[0]!='D'||b[1]!='I'||b[2]!='A'||b[3]!='G') throw new IllegalArgumentException("diagnostic save");
        tick=((b[4]&255)<<24)|((b[5]&255)<<16)|((b[6]&255)<<8)|(b[7]&255);
    }
    public void close() { running=false; }
}
