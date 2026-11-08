package com.javaboy.chimera;
public final class PortableTest {
    static void check(boolean b) { if(!b) throw new RuntimeException("assertion"); }
    static final class Probe implements Core.Factory,Runner.Battery,Runner.Events {
        Thread owner;volatile boolean done;volatile int frames,saves;volatile String failure;
        void own() { if(owner==null) owner=Thread.currentThread();check(owner==Thread.currentThread()); }
        public Core create() { own();return new Diagnostic(); }
        public byte[] load(String key) { own();return new Diagnostic().battery(); }
        public void save(String key,byte[] b) { own();check(b.length==8);saves++; }
        public void frame() { own();frames++; }
        public void status(String message) { own();if(message.indexOf("failed")>=0||message.indexOf("Stopped")>=0) failure=message; }
        public void stopped() { own();done=true; }
    }
    public static void main(String[] args) throws Exception {
        Keys k=new Keys();k.press(1,Keys.A);k.press(2,Keys.A);k.press(1,Keys.A);k.release(1);check(k.mask()==Keys.A);k.release(2);check(k.mask()==0);
        Diagnostic d=new Diagnostic();d.active(true);d.step(true);int[] pixels=new int[23040];d.pixels(pixels);check(pixels[1]==0xffffff);
        byte[] b=d.battery();d.battery(b);boolean rejected=false;try {d.battery(new byte[1]);}catch(IllegalArgumentException e) {rejected=true;}check(rejected);
        Probe p=new Probe();Runner r=new Runner(p,p,p,0);
        try {r.resume();r.resume();long until=System.currentTimeMillis()+3000;while(p.frames<2&&System.currentTimeMillis()<until) Thread.sleep(5);check(p.frames>=2);r.pause();}
        finally {r.close();long until=System.currentTimeMillis()+3000;while(!p.done&&System.currentTimeMillis()<until) Thread.sleep(5);}
        check(p.done&&p.saves>0&&p.failure==null);
    }
}
