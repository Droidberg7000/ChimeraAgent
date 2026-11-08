package com.javaboy.chimera;
public final class Keys {
    public static final int UP=1,DOWN=2,LEFT=4,RIGHT=8,A=16,B=32,START=64,SELECT=128;
    private final int[] codes=new int[32],bits=new int[32];
    public synchronized void press(int code,int bit) {
        if(bit==0) return;int free=-1;
        for(int i=0;i<32;i++) { if(bits[i]!=0&&codes[i]==code) return;if(bits[i]==0) free=i; }
        if(free>=0) { codes[free]=code;bits[free]=bit; }
    }
    public synchronized void release(int code) { for(int i=0;i<32;i++) if(codes[i]==code) bits[i]=0; }
    public synchronized void clear() { for(int i=0;i<32;i++) bits[i]=0; }
    public synchronized int mask() { int m=0;for(int i=0;i<32;i++) m|=bits[i];return m; }
}
