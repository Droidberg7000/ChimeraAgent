package com.chimeratube;
import java.io.*;
import java.util.Vector;

/** Desktop tests of the actual portable core. Does not certify MIDP/MMAPI hardware behavior. */
public final class CoreTest {
 private static int checks;
 private static void check(boolean v,String m){checks++;if(!v)throw new RuntimeException(m);}
 private static byte[] ascii(String s)throws Exception{return s.getBytes("UTF-8");}
 private static String row(String id,String title){return id+"\t"+title+"\tAuthor\t42\tmp4,3gp\n";}
 private static void rejected(String data)throws Exception{boolean failed=false;try{Feed.read(new ByteArrayInputStream(ascii(data)),"catalog");}catch(IOException expected){failed=true;}check(failed,"Bad feed accepted");}
 public static void main(String[] args)throws Exception{
  String id="dQw4w9WgXcQ",text="caf\u00e9 & \u732b";
  check(Feed.encode(text).equals("caf%C3%A9%20%26%20%E7%8C%AB"),"UTF8 query");
  check(Feed.decode(Feed.encode(text)).equals(text),"UTF8 roundtrip");
  Vector v=Feed.read(new ByteArrayInputStream(ascii("CT1\tcatalog\n"+row(id,Feed.encode(text)))),"catalog");
  Feed.Video video=(Feed.Video)v.elementAt(0);
  check(video.id.equals(id),"First ID character lost");check(video.title.equals(text),"Title changed");
  check(video.supports("mp4")&&!video.supports("gp"),"Profile boundaries");
  check(Feed.read(new ByteArrayInputStream(ascii("CT1\tcatalog\n")),"catalog").size()==0,"Empty feed");
  rejected("CT1\tcatalog\n\n"); // Blank-line floods fail at their first row.
  rejected("CT1\tcatalog\n"+row("../bad","title"));
  rejected("CT1\tcatalog\n"+row(id,"%GG"));
  rejected("CT1\tcatalog\n"+row(id,"%FF"));
  rejected("CT1\tcatalog\n"+row(id,"%C0%AF")); // Overlong UTF8.
  rejected("CT1\tcatalog\n"+row(id,"title").replace('\t','x'));
  rejected("CT1\tcatalog\n"+id+"\tt\ta\t1\tmp4\textra\n");
  StringBuffer rows=new StringBuffer("CT1\tcatalog\n");for(int i=0;i<21;i++)rows.append(row("item"+i,"title"));rejected(rows.toString());
  StringBuffer longLine=new StringBuffer("CT1\tcatalog\n");for(int i=0;i<4097;i++)longLine.append('x');rejected(longLine.toString());
  boolean mismatch=false;try{Feed.read(new ByteArrayInputStream(ascii("CT1\tinvidious\n")),"catalog");}catch(IOException expected){mismatch=true;}check(mismatch,"Source header mismatch accepted");
  Feed.Bytes cache=Feed.cache("http://gateway-a:9090","catalog",v);
  check(Feed.cache(cache.data,cache.size,"http://gateway-a:9090","catalog").size()==1,"Cache roundtrip");
  mismatch=false;try{Feed.cache(cache.data,cache.size,"http://gateway-b:9090","catalog");}catch(IOException expected){mismatch=true;}check(mismatch,"Cross-gateway cache accepted");
  mismatch=false;try{Feed.cache(cache.data,cache.size,"http://gateway-a:9090","invidious");}catch(IOException expected){mismatch=true;}check(mismatch,"Cross-source cache accepted");
  Feed.Bytes tiny=new Feed.Bytes(3);tiny.write(new byte[]{1,2,3},0,3);boolean overflow=false;try{tiny.write(4);}catch(IOException expected){overflow=true;}check(overflow&&tiny.size==3,"Cache writer exceeded capacity");
  Feed.Bytes config=Feed.settings("http://gateway-a:9090","catalog");check(Feed.settings(config.data,config.size)[0].equals("http://gateway-a:9090"),"Settings roundtrip");
  cleanup();System.out.println("Portable core checks passed: "+checks);
 }
 private static void cleanup()throws Exception{
  final Object gate=new Object();final boolean[] release={false},started={false};final int[] closed={0};
  Cleanup q=new Cleanup();Cleanup.Ticket[] tickets=new Cleanup.Ticket[4];
  try{
   tickets[0]=q.reserve(new Cleanup.Work(){public void close()throws Exception{synchronized(gate){started[0]=true;gate.notifyAll();while(!release[0])gate.wait();closed[0]++;}}});q.retire(tickets[0]);
   synchronized(gate){long end=System.currentTimeMillis()+3000;while(!started[0]){long wait=end-System.currentTimeMillis();if(wait<=0)throw new RuntimeException("Cleanup worker did not start");gate.wait(wait);}}
   for(int i=1;i<4;i++)tickets[i]=q.reserve(new Cleanup.Work(){public void close(){synchronized(gate){closed[0]++;}}});
   check(q.reserve(new Cleanup.Work(){public void close(){}})==null,"Cleanup backpressure failed");
   for(int i=0;i<4;i++){q.retire(tickets[i]);q.retire(tickets[i]);}
   synchronized(gate){release[0]=true;gate.notifyAll();}
   check(q.awaitEmpty(3000),"Cleanup did not drain");check(closed[0]==4,"Duplicate/missing cleanup");
   for(int i=0;i<4;i++)check(tickets[i].done(),"Ticket retained resource");
  }finally{synchronized(gate){release[0]=true;gate.notifyAll();}for(int i=0;i<4;i++)q.retire(tickets[i]);q.shutdown();q.awaitEmpty(3000);}
 }
}
