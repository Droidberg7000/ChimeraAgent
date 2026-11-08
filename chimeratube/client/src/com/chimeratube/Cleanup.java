package com.chimeratube;

/** Four resource tickets maximum, one cleanup worker, no per-click cleanup threads. */
public final class Cleanup implements Runnable {
 public interface Work {void close()throws Exception;}
 public final class Ticket {
  private Work work;private boolean queued,done;
  private Ticket(Work w){work=w;}
  public boolean done(){synchronized(Cleanup.this){return done;}}
 }
 private final Ticket[] queue=new Ticket[4];private int count,allocated;private boolean stopping;
 public Cleanup(){new Thread(this).start();}
 public synchronized Ticket reserve(Work w){if(stopping||allocated==4)return null;allocated++;return new Ticket(w);}
 public synchronized void retire(Ticket t){if(t==null||t.queued||t.done)return;t.queued=true;queue[count++]=t;notifyAll();}
 public synchronized void shutdown(){stopping=true;notifyAll();}
 public synchronized boolean awaitEmpty(long millis)throws InterruptedException{long end=System.currentTimeMillis()+millis;while(allocated!=0){long n=end-System.currentTimeMillis();if(n<=0)return false;wait(n);}return true;}
 public void run(){for(;;){Ticket t;synchronized(this){while(count==0){if(stopping&&allocated==0)return;try{wait();}catch(InterruptedException ignored){}}t=queue[0];for(int i=1;i<count;i++)queue[i-1]=queue[i];queue[--count]=null;}try{t.work.close();}catch(Exception ignored){}finally{synchronized(this){t.work=null;t.done=true;allocated--;notifyAll();}}}}
}
