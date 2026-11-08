package com.javaboy.chimera;
import javax.microedition.midlet.MIDlet;
import javax.microedition.lcdui.*;
public abstract class App extends MIDlet implements CommandListener,Runner.Events,Runnable {
    private final int profile;private Display display;private Screen screen;private Runner runner;
    private boolean queued,exiting,stopped;private String message;
    private final Command exit=new Command("Exit",Command.EXIT,1),save=new Command("Save battery",Command.SCREEN,2),load=new Command("Load battery",Command.SCREEN,3),sound=new Command("Sound",Command.SCREEN,4),scale=new Command("Scale",Command.SCREEN,5);
    protected App(int p) { profile=p; }
    protected void startApp() {
        if(display==null) {
            display=Display.getDisplay(this);screen=new Screen();
            screen.addCommand(exit);screen.addCommand(save);screen.addCommand(load);screen.addCommand(sound);screen.addCommand(scale);screen.setCommandListener(this);
            runner=new Runner(new Core.Factory() {
                public Core create() throws Exception {
                    String name=getAppProperty("JavaBoy-Core-Factory");
                    if(name==null) throw new Exception("Missing core factory");
                    return ((Core.Factory)Class.forName(name).newInstance()).create();
                }
            },new Store(),this,profile==2?1:0);
        }
        display.setCurrent(screen);runner.resume();
        synchronized(this) { if(stopped) status("Backend stopped; exit and restart the MIDlet"); }
    }
    protected void pauseApp() { if(runner!=null) runner.pause(); }
    protected void destroyApp(boolean force) { synchronized(this) { exiting=true; }if(runner!=null) runner.close(); }
    public void commandAction(Command c,Displayable d) {
        if(c==exit) {
            boolean done;synchronized(this) { exiting=true;done=stopped; }
            if(runner==null||done) notifyDestroyed();else runner.close();
        }else if(c==save) runner.command(1);else if(c==load) runner.command(2);else if(c==sound) runner.command(4);
        else if(c==scale) { screen.scale=screen.scale%3+1;screen.repaint(); }
    }
    private synchronized void post() { if(!queued) { queued=true;display.callSerially(this); } }
    public synchronized void status(String text) { if(!exiting) message=text.length()>512?text.substring(0,512):text;post(); }
    public void frame() { post(); }
    public synchronized void stopped() { stopped=true;post(); }
    public void run() {
        String text;boolean finish;
        synchronized(this) { queued=false;text=message;message=null;finish=exiting&&stopped; }
        if(finish) { notifyDestroyed();return; }
        if(text!=null) { Alert a=new Alert("JavaBoy",text,null,AlertType.INFO);a.setTimeout(Alert.FOREVER);display.setCurrent(a,screen); }
        else screen.repaint();
    }
    private final class Screen extends Canvas {
        private int scale=1;private final int[] base=new int[23040];private int[] enlarged;
        Screen() { setFullScreenMode(true); }
        protected void hideNotify() { if(runner!=null) runner.keys.clear(); }
        protected void sizeChanged(int w,int h) { enlarged=null; }
        protected void paint(Graphics g) {
            g.setColor(0);g.fillRect(0,0,getWidth(),getHeight());if(runner==null) return;runner.frame(base);
            int limit=profile==0?3:(profile==1?2:1),s=Math.max(1,Math.min(scale,Math.min(limit,Math.min(getWidth()/160,getHeight()/144))));
            int w=160*s,h=144*s;if(enlarged==null||enlarged.length!=w*h) enlarged=new int[w*h];
            for(int y=0;y<h;y++) for(int x=0;x<w;x++) enlarged[y*w+x]=base[(y/s)*160+x/s];
            g.drawRGB(enlarged,0,w,(getWidth()-w)/2,(getHeight()-h)/2,w,h,false);
            if("diagnostic".equals(getAppProperty("JavaBoy-Mode"))) {
                g.setColor(0xffffff);g.drawString("DIAGNOSTIC - NOT EMULATION",0,0,Graphics.TOP|Graphics.LEFT);
                g.drawString("Keys: "+runner.keys.mask(),0,getHeight()-g.getFont().getHeight(),Graphics.TOP|Graphics.LEFT);
            }
        }
        private int norm(int k) { return k>='A'&&k<='Z'?k+32:k; }
        private int button(int raw) {
            int k=norm(raw);
            if(k=='2') return Keys.UP;if(k=='8') return Keys.DOWN;if(k=='4') return Keys.LEFT;if(k=='6') return Keys.RIGHT;
            if(k=='5') return Keys.A;if(k=='0') return Keys.B;if(k=='*'||k==' ') return Keys.SELECT;if(k=='#'||k==10||k==13) return Keys.START;
            if(profile==0) { if(k=='w') return Keys.UP;if(k=='s') return Keys.DOWN;if(k=='a') return Keys.LEFT;if(k=='d') return Keys.RIGHT;if(k=='j') return Keys.A;if(k=='k') return Keys.B; }
            if(profile==2) { if(k=='q'||k=='w') return Keys.UP;if(k=='a'||k=='s') return Keys.DOWN;if(k=='o'||k=='p') return Keys.LEFT;if(k=='k'||k=='l') return Keys.RIGHT;if(k=='t'||k=='y') return Keys.A;if(k=='u'||k=='i') return Keys.B; }
            int a;try { a=getGameAction(raw); }catch(IllegalArgumentException e) { return 0; }
            if(a==UP) return Keys.UP;if(a==DOWN) return Keys.DOWN;if(a==LEFT) return Keys.LEFT;if(a==RIGHT) return Keys.RIGHT;if(a==FIRE) return Keys.A;return 0;
        }
        protected void keyPressed(int k) { if(runner!=null) runner.keys.press(norm(k),button(k)); }
        protected void keyRepeated(int k) { keyPressed(k); }
        protected void keyReleased(int k) { if(runner!=null) runner.keys.release(norm(k)); }
    }
}
