package com.chimeratube;
import javax.microedition.midlet.MIDlet;
import javax.microedition.lcdui.*;
import javax.microedition.io.*;
import javax.microedition.media.*;
import javax.microedition.media.control.VideoControl;
import javax.microedition.rms.RecordStore;
import java.io.*;
import java.util.Vector;

/** Bounded MIDP 2.0 client for the CT1 gateway. No native I/O runs in command callbacks. */
public final class ChimeraTubeMIDlet extends MIDlet implements CommandListener,Runnable,PlayerListener {
 private final Object state=new Object();private final Display display=Display.getDisplay(this);
 private final Cleanup cleanup=new Cleanup();private final List list=new List("ChimeraTube",List.IMPLICIT);private final Movie movie=new Movie();
 private final Command exit=new Command("Exit",Command.EXIT,9),settings=new Command("Gateway",Command.SCREEN,3),search=new Command("Search",Command.SCREEN,1),stop=new Command("Stop / Back",Command.BACK,2),save=new Command("Save",Command.OK,1),go=new Command("Go",Command.OK,1),cache=new Command("Cached metadata",Command.SCREEN,4),toggle=new Command("MP4 / 3GP",Command.SCREEN,5);
 private String base="",source="catalog",profile="mp4",itemsBase="",itemsSource="";private Vector items=new Vector();
 private boolean paused=true,dead,loaded,playBusy,uiQueued,hideMovie;private int epoch;
 private Request request;private Config config;private Result pendingResult;private String pendingStatus;private Ready pendingReady;
 private Handle network,media;private Cleanup.Ticket lastPlayer;
 private Form form;private TextField baseField,queryField;private ChoiceGroup sourceField;
 private final Runnable drain=new Runnable(){public void run(){drainUi();}};
 private static final class Config {final String base,source;final int epoch;Config(String b,String s,int e){base=b;source=s;epoch=e;}}
 private static final class Request {final String base,source,query;final int epoch;final boolean cache;Request(String b,String s,String q,int e,boolean c){base=b;source=s;query=q;epoch=e;cache=c;}}
 private static final class Result {final Request req;final Vector videos;Result(Request r,Vector v){req=r;videos=v;}}
 private final class Ready {final Handle h;final Player p;final VideoControl control;final int epoch;boolean done,ok;volatile boolean setup;Ready(Handle a,Player b,VideoControl c,int e){h=a;p=b;control=c;epoch=e;}}
 private final class Handle implements Cleanup.Work {
  HttpConnection conn;InputStream input;Player player;boolean retiring;Cleanup.Ticket ticket;
  synchronized boolean connection(HttpConnection c){if(retiring)return false;conn=c;return true;}
  synchronized boolean input(InputStream s){if(retiring)return false;input=s;return true;}
  synchronized boolean player(Player p){if(retiring)return false;player=p;return true;}
  synchronized boolean owns(Player p){return !retiring&&player==p;}
  void retire(){synchronized(this){if(retiring)return;retiring=true;}cleanup.retire(ticket);}
  public void close(){HttpConnection c;InputStream s;Player p;synchronized(this){retiring=true;c=conn;s=input;p=player;conn=null;input=null;player=null;}if(s!=null)try{s.close();}catch(Exception ignored){}if(c!=null)try{c.close();}catch(Exception ignored){}if(p!=null)try{p.removePlayerListener(ChimeraTubeMIDlet.this);}catch(Exception ignored){}closePlayer(p);}
 }
 public ChimeraTubeMIDlet(){list.addCommand(exit);list.addCommand(settings);list.addCommand(search);list.addCommand(stop);list.addCommand(cache);list.addCommand(toggle);list.setSelectCommand(new Command("Play",Command.ITEM,1));list.setCommandListener(this);movie.addCommand(stop);movie.addCommand(exit);movie.setCommandListener(this);new Thread(this).start();}
 protected void startApp(){synchronized(state){paused=false;}display.setCurrent(list);message(currentEpoch(),"Set gateway, then Search; no automatic request.");}
 protected void pauseApp(){synchronized(state){paused=true;}invalidate(null);}
 protected void destroyApp(boolean unconditional){synchronized(state){dead=true;request=null;config=null;state.notifyAll();}invalidate(null);cleanup.shutdown();}
 private int currentEpoch(){synchronized(state){return epoch;}}
 private boolean current(int e){synchronized(state){return !dead&&!paused&&epoch==e;}}
 /** Only Java references/tickets are changed here. Cleanup performs native close elsewhere. */
 private int invalidate(String text){return invalidate(text,null);}
 private int invalidate(String text,Player expected){Handle n,m;int e;boolean schedule=false;synchronized(state){if(expected!=null&&(media==null||!media.owns(expected)))return -1;e=++epoch;request=null;pendingResult=null;pendingReady=null;pendingStatus=text;hideMovie=true;n=network;network=null;m=media;media=null;movie.clear();state.notifyAll();if(!dead&&!uiQueued){uiQueued=true;schedule=true;}}if(n!=null)n.retire();if(m!=null)m.retire();if(schedule)display.callSerially(drain);return e;}
 private void message(int e,String text){boolean schedule=false;synchronized(state){if(dead||paused||e!=epoch)return;pendingStatus=text;if(!uiQueued){uiQueued=true;schedule=true;}}if(schedule)display.callSerially(drain);}
 private void result(Request r,Vector v){boolean schedule=false;synchronized(state){if(!current(r.epoch)||!base.equals(r.base)||!source.equals(r.source))return;pendingResult=new Result(r,v);pendingStatus="Results / "+profile;if(!uiQueued){uiQueued=true;schedule=true;}}if(schedule)display.callSerially(drain);}
 private void ready(Ready r){boolean schedule=false;synchronized(state){if(!current(r.epoch)||media!=r.h)return;pendingReady=r;if(!uiQueued){uiQueued=true;schedule=true;}}if(schedule)display.callSerially(drain);
 }
 private void drainUi(){Result r;String text;Ready video;boolean hide;int e;synchronized(state){uiQueued=false;e=epoch;r=pendingResult;pendingResult=null;text=pendingStatus;pendingStatus=null;video=pendingReady;pendingReady=null;hide=hideMovie;hideMovie=false;if(dead||paused)return;}
  if(hide){movie.clear();if(display.getCurrent()==movie)display.setCurrent(list);}
  if(r!=null){synchronized(state){if(current(r.req.epoch)&&base.equals(r.req.base)&&source.equals(r.req.source)){items=r.videos;itemsBase=r.req.base;itemsSource=r.req.source;}else r=null;}if(r!=null)showList("Metadata / "+r.req.source);}
  if(text!=null&&current(e))list.setTitle(text.length()>90?text.substring(0,90):text);
  if(video!=null){try{if(current(video.epoch)&&mediaIs(video.h)){display.setCurrent(movie);movie.show(video);if(!current(video.epoch)||!mediaIs(video.h)){movie.clear();signalReady(video,false);}}else signalReady(video,false);}catch(Exception ex){movie.clear();signalReady(video,false);if(current(video.epoch)){display.setCurrent(list);list.setTitle("Video display unavailable");}}}
 }
 private void signalReady(Ready r,boolean ok){synchronized(state){if(r.done)return;r.ok=ok&&current(r.epoch)&&media==r.h;r.done=true;if(movie.waiting==r)movie.waiting=null;state.notifyAll();}}
 private boolean mediaIs(Handle h){synchronized(state){return media==h;}}
 private void showList(String caption){list.deleteAll();for(int i=0;i<items.size();i++){Feed.Video v=(Feed.Video)items.elementAt(i);list.append(v.title+" ["+v.profiles+"]",null);}list.setTitle(caption+" / "+profile);display.setCurrent(list);}
 private static Feed.Bytes load(String name,int max)throws Exception{RecordStore s=RecordStore.openRecordStore(name,false);try{int n=s.getRecordSize(1);if(n<0||n>max)throw new IOException("Stored record too large");Feed.Bytes b=new Feed.Bytes(n);b.size=s.getRecord(1,b.data,0);return b;}finally{s.closeRecordStore();}}
 private static void store(String name,Feed.Bytes b)throws Exception{RecordStore s=RecordStore.openRecordStore(name,true);try{if(s.getNumRecords()==0)s.addRecord(b.data,0,b.size);else s.setRecord(1,b.data,0,b.size);}finally{s.closeRecordStore();}}
 private static String normalize(String s)throws IOException{s=s.trim();while(s.endsWith("/"))s=s.substring(0,s.length()-1);int prefix=s.startsWith("http://")?7:s.startsWith("https://")?8:0;if(prefix==0||s.length()>200||s.length()==prefix)throw new IOException("Use an HTTP(S) base URL");String host=s.substring(prefix);for(int i=0;i<host.length();i++){char c=host.charAt(i);if(!((c>='a'&&c<='z')||(c>='A'&&c<='Z')||(c>='0'&&c<='9')||c=='.'||c=='-'||c==':'))throw new IOException("Host/IPv4 and optional port only");}int colon=host.indexOf(':');if(colon==0||colon!=host.lastIndexOf(':'))throw new IOException("Invalid host/port");if(colon>0)try{int p=Integer.parseInt(host.substring(colon+1));if(p<1||p>65535)throw new NumberFormatException();}catch(NumberFormatException ex){throw new IOException("Invalid port");}return s;}
 private void queueSearch(String query,boolean cached){int e=invalidate("Loading...");synchronized(state){if(!loaded||base.length()==0){message(e,"Set the gateway first; settings may still be loading.");return;}request=new Request(base,source,query,e,cached);state.notifyAll();}}
 public void run(){try{Feed.Bytes b=load("ct-settings",1024);String[] s=Feed.settings(b.data,b.size);String url=normalize(s[0]);if(!s[1].equals("catalog")&&!s[1].equals("invidious"))throw new IOException("Source");synchronized(state){if(epoch==0){base=url;source=s[1];}}}catch(Exception ignored){String prop=getAppProperty("ChimeraTube-Gateway");if(prop!=null)try{String url=normalize(prop);synchronized(state){if(epoch==0)base=url;}}catch(Exception bad){}}synchronized(state){loaded=true;}
  for(;;){Config c;Request r;synchronized(state){while(!dead&&config==null&&request==null)try{state.wait();}catch(InterruptedException ignored){}if(dead)return;c=config;config=null;r=c==null?request:null;if(r!=null)request=null;}
   if(c!=null){try{store("ct-settings",Feed.settings(c.base,c.source));message(c.epoch,"Gateway settings saved");}catch(Exception ex){message(c.epoch,"Settings active but RMS save failed");}continue;}
   if(!current(r.epoch))continue;
   if(r.cache){try{Feed.Bytes b=load("ct-cache",Feed.LIMIT);Vector v=Feed.cache(b.data,b.size,r.base,r.source);result(r,v);message(r.epoch,"Cached METADATA only; playback needs this gateway");}catch(Exception ex){message(r.epoch,"No cache for this gateway/source");}continue;}
   Handle h=new Handle();synchronized(state){if(!current(r.epoch))continue;h.ticket=cleanup.reserve(h);if(h.ticket!=null)network=h;}
   if(h.ticket==null){message(r.epoch,"Cleanup busy; retry after native resources close");continue;}
   try{HttpConnection conn=(HttpConnection)Connector.open(r.base+"/v1/search?q="+Feed.encode(r.query)+"&source="+r.source,Connector.READ,true);if(!h.connection(conn)){conn.close();continue;}conn.setRequestMethod(HttpConnection.GET);if(conn.getResponseCode()!=200)throw new IOException("Gateway HTTP "+conn.getResponseCode());InputStream in=conn.openInputStream();if(!h.input(in)){in.close();continue;}Vector v=Feed.read(in,r.source);h.retire();synchronized(state){if(network==h)network=null;}if(current(r.epoch)){result(r,v);try{store("ct-cache",Feed.cache(r.base,r.source,v));}catch(Exception ex){message(r.epoch,"Results loaded; metadata cache not saved");}}}
   catch(Exception ex){message(r.epoch,"Search failed; no playable fallback invented");}
   finally{synchronized(state){if(network==h)network=null;}h.retire();}
  }
 }
 private static void closePlayer(Player p){if(p==null)return;try{p.stop();}catch(Exception ignored){}finally{try{p.close();}catch(Exception ignored){}}}
 private void play(){int i=list.getSelectedIndex();if(i<0||i>=items.size())return;final Feed.Video item=(Feed.Video)items.elementAt(i);
  synchronized(state){if(dead||paused||playBusy||(lastPlayer!=null&&!lastPlayer.done())){message(epoch,"Stop the current player and wait for cleanup; retry");return;}if(!itemsBase.equals(base)||!itemsSource.equals(source)){message(epoch,"Results belong to another gateway/source");return;}if(!item.supports(profile)){message(epoch,"Prepare this profile on the gateway first");return;}}
  final int e=invalidate("Preparing video...");final String url;final Handle h=new Handle();synchronized(state){if(!current(e))return;h.ticket=cleanup.reserve(h);if(h.ticket==null){message(e,"Cleanup busy; retry");return;}lastPlayer=h.ticket;media=h;playBusy=true;url=base+"/v1/media/"+item.id+"/"+profile;}
  new Thread(new Runnable(){public void run(){boolean keep=false;try{Player p=Manager.createPlayer(url);if(!h.player(p)){closePlayer(p);return;}p.addPlayerListener(ChimeraTubeMIDlet.this);p.realize();p.prefetch();VideoControl c=(VideoControl)p.getControl("VideoControl");if(c==null)throw new IOException("No VideoControl");Ready r=new Ready(h,p,c,e);ready(r);synchronized(state){for(int waits=0;!r.done&&current(e)&&waits<50;waits++)state.wait(200);if(!r.done||!r.ok||!current(e)||media!=h)return;}p.start();keep=current(e)&&mediaIs(h);}
   catch(Exception ex){message(e,"Playback/display failed; try another prepared profile");}
   finally{if(!keep){synchronized(state){if(media==h)media=null;if(epoch==e){movie.clear();hideMovie=true;}}h.retire();message(e,"Playback unavailable; try another prepared profile");}synchronized(state){playBusy=false;}}
  }}).start();
 }
 public void playerUpdate(Player p,String event,Object data){if(!PlayerListener.END_OF_MEDIA.equals(event)&&!PlayerListener.ERROR.equals(event))return;invalidate(PlayerListener.ERROR.equals(event)?"Player reported an error":"Playback ended",p);}
 public void commandAction(Command c,Displayable d){if(c==exit){destroyApp(true);notifyDestroyed();}else if(c==stop){invalidate("Cancelled");display.setCurrent(list);}else if(c==toggle){profile=profile.equals("mp4")?"3gp":"mp4";showList("Selected profile");}else if(c==cache)queueSearch("",true);else if(c==search){form=new Form("Search");queryField=new TextField("Query","",200,TextField.ANY);form.append(queryField);form.addCommand(go);form.addCommand(stop);form.setCommandListener(this);display.setCurrent(form);}else if(c==go){display.setCurrent(list);queueSearch(queryField.getString(),false);}else if(c==settings){form=new Form("Trusted gateway");baseField=new TextField("Base URL",base,200,TextField.URL);sourceField=new ChoiceGroup("Source",Choice.EXCLUSIVE,new String[]{"catalog","invidious"},null);sourceField.setSelectedIndex(source.equals("invidious")?1:0,true);form.append(baseField);form.append(sourceField);form.addCommand(save);form.addCommand(stop);form.setCommandListener(this);display.setCurrent(form);}else if(c==save){try{String b=normalize(baseField.getString()),s=sourceField.getString(sourceField.getSelectedIndex());int e=invalidate("Gateway changed; old results cleared");synchronized(state){base=b;source=s;loaded=true;items=new Vector();itemsBase="";itemsSource="";config=new Config(b,s,e);state.notifyAll();}showList("Gateway active; save queued");}catch(Exception ex){Alert a=new Alert("Gateway error",ex.toString(),null,AlertType.ERROR);a.setTimeout(Alert.FOREVER);display.setCurrent(a,form);}}else if(d==list)play();}
 private final class Movie extends Canvas{volatile VideoControl control;volatile boolean visible,shown;volatile Ready waiting;
  void clear(){waiting=null;control=null;visible=false;}
  protected void showNotify(){shown=true;Ready r=waiting;if(r!=null&&r.setup)signalReady(r,true);}
  protected void hideNotify(){shown=false;}
  void show(Ready r){waiting=r;VideoControl c=r.control;control=c;c.initDisplayMode(VideoControl.USE_DIRECT_VIDEO,this);resize();c.setVisible(true);visible=true;r.setup=true;if(shown)signalReady(r,true);}
  protected void paint(Graphics g){if(!visible){g.setColor(0);g.fillRect(0,0,getWidth(),getHeight());}}
  protected void sizeChanged(int w,int h){resize();}
  private void resize(){VideoControl c=control;if(c==null)return;try{int sw=c.getSourceWidth(),sh=c.getSourceHeight();if(sw<=0||sh<=0)return;int w=getWidth(),h=w*sh/sw;if(h>getHeight()){h=getHeight();w=h*sw/sh;}if(w>0&&h>0){c.setDisplayLocation((getWidth()-w)/2,(getHeight()-h)/2);c.setDisplaySize(w,h);}}catch(Exception ignored){}}
 }
}
