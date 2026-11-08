package com.javaboy.chimera;
public final class Runner implements Runnable {
    public interface Battery { byte[] load(String key) throws Exception;void save(String key,byte[] data) throws Exception; }
    public interface Events { void frame();void status(String message);void stopped(); }
    public final Keys keys=new Keys();
    private final Core.Factory factory;private final Battery store;private final Events events;private final int skip;
    private final Thread worker;private final int[] front=new int[23040],back=new int[23040];
    private boolean paused=true,closed,started;private int commands;
    public Runner(Core.Factory f,Battery b,Events e,int s) {
        if(s<0||s>5) throw new IllegalArgumentException("skip");
        factory=f;store=b;events=e;skip=s;worker=new Thread(this);
    }
    private void start() { if(!started) { started=true;worker.start(); } }
    public synchronized void resume() { if(!closed) { paused=false;start();notifyAll(); } }
    public synchronized void pause() { if(!closed) { paused=true;keys.clear();commands|=1;notifyAll(); } }
    public synchronized void command(int n) { if(!closed) { commands|=n&3;if((n&4)!=0) commands^=4;notifyAll(); } }
    public synchronized void close() { closed=true;keys.clear();start();notifyAll(); }
    public synchronized void frame(int[] target) { System.arraycopy(front,0,target,0,23040); }
    public void run() {
        Core core=null;
        try {
            synchronized(this) { if(closed) return; }
            core=factory.create();if(core==null) throw new Exception("No core returned");
            boolean active=false,audio=false;core.active(false);int counter=0,fraction=0;long due=System.currentTimeMillis();
            while(true) {
                boolean play;int cmd;
                synchronized(this) {
                    while(paused&&commands==0&&!closed) wait();if(closed) break;
                    play=!paused;cmd=commands;commands=0;
                }
                if(active!=play) { core.active(play);active=play;due=System.currentTimeMillis(); }
                if((cmd&4)!=0) {
                    if(core.hasAudio()) audio=!audio;else events.status("Audio is unavailable in this backend");
                }
                core.sound(play&&audio);core.buttons(play?keys.mask():0);
                if((cmd&3)!=0) try {
                    if((cmd&1)!=0) { store.save(core.batteryKey(),core.battery());events.status("Battery save completed (or no battery present)"); }
                    if((cmd&2)!=0) { byte[] data=store.load(core.batteryKey());if(data!=null) core.battery(data);events.status(data==null?"No saved battery":"Battery loaded"); }
                } catch(Exception e) { events.status("Battery operation failed: "+e.toString()); }
                if(!play) continue;
                long delay=due-System.currentTimeMillis();
                if(delay>0) { synchronized(this) { if(!closed&&!paused&&commands==0) wait(delay); } continue; }
                boolean render=counter==0;counter=(counter+1)%(skip+1);core.step(render);
                if(render) {
                    core.pixels(back);boolean publish;
                    synchronized(this) { publish=!closed&&!paused;if(publish) System.arraycopy(back,0,front,0,23040); }
                    if(publish) events.frame();
                }
                due+=16;fraction+=743;if(fraction>=1000) { due++;fraction-=1000; }
                if(System.currentTimeMillis()-due>100) due=System.currentTimeMillis();
            }
        } catch(Exception e) { events.status("Stopped: "+e.toString()); }
        finally {
            synchronized(this) { closed=true;keys.clear(); }
            if(core!=null) {
                try { core.buttons(0);core.active(false);store.save(core.batteryKey(),core.battery()); }
                catch(Exception e) { events.status("Final save failed: "+e.toString()); }
                try { core.close(); } catch(Exception e) { events.status("Close failed: "+e.toString()); }
            }
            events.stopped();
        }
    }
}
