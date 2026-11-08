package com.javaboy.chimera;
import javax.microedition.rms.RecordStore;
/** Versioned RMS battery storage. Called only from Runner. No power-loss guarantee. */
public final class Store implements Runner.Battery {
    private String name(String key) throws Exception {
        if(key==null||key.length()==0||key.length()>24) throw new Exception("Invalid cartridge fingerprint");
        for(int i=0;i<key.length();i++) {
            char c=key.charAt(i);
            if(!((c>='a'&&c<='z')||(c>='A'&&c<='Z')||(c>='0'&&c<='9')||c=='_'||c=='-')) throw new Exception("Invalid fingerprint");
        }
        return "jbc-"+key;
    }
    private int crc(byte[] b) {
        int n=65535;for(int i=6;i<b.length;i++) { n^=(b[i]&255)<<8;for(int j=0;j<8;j++) n=((n&32768)!=0?(n<<1)^4129:n<<1)&65535; }return n;
    }
    public void save(String key,byte[] data) throws Exception {
        if(data==null) return;if(data.length>131072) throw new Exception("Battery too large");
        byte[] b=new byte[data.length+6];b[0]='J';b[1]='B';b[2]='C';b[3]=1;System.arraycopy(data,0,b,6,data.length);
        int sum=crc(b);b[4]=(byte)(sum>>8);b[5]=(byte)sum;
        RecordStore rs=RecordStore.openRecordStore(name(key),true);
        try { if(rs.getNumRecords()==0) rs.addRecord(b,0,b.length);else rs.setRecord(1,b,0,b.length); }finally { rs.closeRecordStore(); }
    }
    public byte[] load(String key) throws Exception {
        RecordStore rs=RecordStore.openRecordStore(name(key),true);
        try {
            if(rs.getNumRecords()==0) return null;int n=rs.getRecordSize(1);
            if(n<6||n>131078) throw new Exception("Invalid battery size");byte[] b=rs.getRecord(1);
            if(b[0]!='J'||b[1]!='B'||b[2]!='C'||b[3]!=1||crc(b)!=(((b[4]&255)<<8)|(b[5]&255))) throw new Exception("Battery checksum/version");
            byte[] data=new byte[n-6];System.arraycopy(b,6,data,0,data.length);return data;
        }finally { rs.closeRecordStore(); }
    }
}
