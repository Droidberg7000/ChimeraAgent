package com.chimeratube;
import java.io.*;
import java.util.Vector;

/** CLDC 1.1: bounded, incremental CT1 parsing; no whole-feed String or split vectors. */
public final class Feed {
 public static final int LIMIT=32768, ROW=4096, ITEMS=20;
 public static final class Video {
  public final String id,title,author,profiles; public final int seconds;
  public Video(String i,String t,String a,int n,String p)throws IOException{
   if(!safeId(i)||n<0||t.length()>320||a.length()>160||!profiles(p))throw new IOException("Invalid video");
   id=i;title=clean(t);author=clean(a);seconds=n;profiles=p;
  }
  public boolean supports(String p){return (","+profiles+",").indexOf(","+p+",")>=0;}
 }
 private static String clean(String s){StringBuffer b=new StringBuffer();for(int i=0;i<s.length();i++){char c=s.charAt(i);b.append(c<32||c==127?' ':c);}return b.toString();}
 private static boolean profiles(String s){return s.equals("-")||s.equals("mp4")||s.equals("3gp")||s.equals("mp4,3gp")||s.equals("3gp,mp4");}
 public static boolean safeId(String s){if(s.length()<1||s.length()>64)return false;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(!((c>='a'&&c<='z')||(c>='A'&&c<='Z')||(c>='0'&&c<='9')||c=='_'||c=='-'))return false;}return true;}
 public static String encode(String s)throws IOException{byte[] a=s.getBytes("UTF-8");StringBuffer b=new StringBuffer();String h="0123456789ABCDEF";for(int i=0;i<a.length;i++){int n=a[i]&255;if((n>=65&&n<=90)||(n>=97&&n<=122)||(n>=48&&n<=57)||n==45||n==46||n==95||n==126)b.append((char)n);else{b.append('%');b.append(h.charAt(n>>4));b.append(h.charAt(n&15));}}return b.toString();}
 private static int hex(char c){if(c>='0'&&c<='9')return c-48;if(c>='A'&&c<='F')return c-55;if(c>='a'&&c<='f')return c-87;return -1;}
 private static String utf8(byte[] a,int n)throws IOException{
  for(int i=0;i<n;){int x=a[i++]&255;if(x<128)continue;int count,lo=128,hi=191;
   if(x>=194&&x<=223)count=1;else if(x>=224&&x<=239){count=2;if(x==224)lo=160;if(x==237)hi=159;}
   else if(x>=240&&x<=244){count=3;if(x==240)lo=144;if(x==244)hi=143;}else throw new IOException("Invalid UTF-8");
   if(i+count>n)throw new IOException("Incomplete UTF-8");int y=a[i++]&255;if(y<lo||y>hi)throw new IOException("Invalid UTF-8");
   for(int j=1;j<count;j++){y=a[i++]&255;if(y<128||y>191)throw new IOException("Invalid UTF-8");}
  }return new String(a,0,n,"UTF-8");
 }
 public static String decode(String s)throws IOException{byte[] a=new byte[s.length()];int n=0;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c=='%'){if(i+2>=s.length())throw new IOException("Incomplete escape");int x=hex(s.charAt(++i)),y=hex(s.charAt(++i));if(x<0||y<0)throw new IOException("Bad escape");a[n++]=(byte)((x<<4)|y);}else{if(c>127)throw new IOException("Non-ASCII wire");a[n++]=(byte)c;}}return utf8(a,n);}
 private static final class Lines {
  final InputStream in;final byte[] net=new byte[512],row=new byte[ROW];int pos,end,total;
  Lines(InputStream s){in=s;}
  int nextByte()throws IOException{if(pos==end){end=in.read(net);pos=0;if(end<0)return -1;if(end==0){int c=in.read();if(c<0)return -1;net[0]=(byte)c;end=1;}total+=end;if(total>LIMIT)throw new IOException("Feed exceeds 32 KB");}return net[pos++]&255;}
  String next(int max)throws IOException{int n=0;for(;;){int c=nextByte();if(c<0)return n==0?null:new String(row,0,n,"UTF-8");if(c==10)return new String(row,0,n,"UTF-8");if(c!=9&&(c<32||c>126))throw new IOException("Invalid wire character");if(n==max)throw new IOException("Line too long");row[n++]=(byte)c;}}
 }
 public static Vector read(InputStream in,String source)throws IOException{
  Lines r=new Lines(in);String header=r.next(32);if(!("CT1\t"+source).equals(header)||!(source.equals("catalog")||source.equals("invidious")))throw new IOException("Wrong feed source/header");
  Vector result=new Vector(ITEMS);String line;
  while((line=r.next(ROW))!=null){if(line.length()==0||result.size()==ITEMS)throw new IOException("Blank/excess row");
   int[] p=new int[4];int start=0;for(int i=0;i<4;i++){p[i]=line.indexOf('\t',start);if(p[i]<0)throw new IOException("Missing field");start=p[i]+1;}if(line.indexOf('\t',start)>=0)throw new IOException("Extra field");
   int seconds;try{seconds=Integer.parseInt(decode(line.substring(p[2]+1,p[3])));}catch(NumberFormatException e){throw new IOException("Invalid duration");}
   result.addElement(new Video(decode(line.substring(0,p[0])),decode(line.substring(p[0]+1,p[1])),decode(line.substring(p[1]+1,p[2])),seconds,decode(line.substring(p[3]+1))));
  }return result;
 }
 /** Fixed backing array; RMS can write data[0..size) without toByteArray copying. */
 public static final class Bytes extends OutputStream {
  public final byte[] data;public int size;
  public Bytes(int capacity){data=new byte[capacity];}
  public void write(int b)throws IOException{if(size==data.length)throw new IOException("Record too large");data[size++]=(byte)b;}
  public void write(byte[] a,int off,int len)throws IOException{if(len<0||off<0||off>a.length-len||len>data.length-size)throw new IOException("Record too large");System.arraycopy(a,off,data,size,len);size+=len;}
 }
 private static void field(DataOutputStream out,String s,int max)throws IOException{byte[] a=s.getBytes("UTF-8");if(a.length>max)throw new IOException("Field too long");out.writeShort(a.length);out.write(a);}
 private static String field(DataInputStream in,int max)throws IOException{int n=in.readUnsignedShort();if(n>max||n>in.available())throw new IOException("Invalid cached field");byte[] a=new byte[n];in.readFully(a);return utf8(a,n);}
 public static Bytes settings(String base,String source)throws IOException{Bytes b=new Bytes(1024);DataOutputStream out=new DataOutputStream(b);out.writeInt(1);field(out,base,600);field(out,source,16);return b;}
 public static String[] settings(byte[] data,int size)throws IOException{DataInputStream in=new DataInputStream(new ByteArrayInputStream(data,0,size));if(in.readInt()!=1)throw new IOException("Settings version");String base=field(in,600),source=field(in,16);if(in.available()!=0)throw new IOException("Trailing settings");return new String[]{base,source};}
 public static Bytes cache(String base,String source,Vector videos)throws IOException{if(videos.size()>ITEMS)throw new IOException("Too many entries");Bytes b=new Bytes(LIMIT);DataOutputStream out=new DataOutputStream(b);out.writeInt(1);field(out,base,600);field(out,source,16);out.writeByte(videos.size());for(int i=0;i<videos.size();i++){Video v=(Video)videos.elementAt(i);field(out,v.id,64);field(out,v.title,1280);field(out,v.author,640);out.writeInt(v.seconds);field(out,v.profiles,16);}return b;}
 public static Vector cache(byte[] data,int size,String base,String source)throws IOException{DataInputStream in=new DataInputStream(new ByteArrayInputStream(data,0,size));if(in.readInt()!=1||!field(in,600).equals(base)||!field(in,16).equals(source))throw new IOException("Cache belongs to another gateway/source");int count=in.readUnsignedByte();if(count>ITEMS)throw new IOException("Too many cached entries");Vector v=new Vector(count);for(int i=0;i<count;i++)v.addElement(new Video(field(in,64),field(in,1280),field(in,640),in.readInt(),field(in,16)));if(in.available()!=0)throw new IOException("Trailing cache bytes");return v;}
}
