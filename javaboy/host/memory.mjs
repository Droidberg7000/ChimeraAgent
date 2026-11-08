// Host-side components, not console engines. No BIOS, ROM, singleton RAM or synthetic CPU.
export class BootRomBus {
  #rom=new Uint8Array(32768);#pending=null;#boot=null;#mapped=false;
  loadRom(data) {
    if(!(data instanceof Uint8Array)||data.length!==32768||data[0x147]||data[0x148]||data[0x149]) throw Error('Only ROM-only 32 KiB cartridges supported');
    this.#rom=data.slice();
  }
  loadBoot(data) {
    if(!(data instanceof Uint8Array)||![256,2048,2304].includes(data.length)) throw Error('Invalid boot layout');
    const bytes=new Uint8Array(2304);
    if(data.length===2048) {bytes.set(data.subarray(0,256));bytes.set(data.subarray(256),512);}else bytes.set(data);
    this.#pending={bytes,cgb:data.length!==256};
  }
  reset() {this.#boot=this.#pending;this.#mapped=!!this.#boot;}
  clearBoot() {this.#pending=this.#boot=null;this.#mapped=false;}
  read8(a) {
    if(!Number.isInteger(a)||a<0||a>65535) throw Error('Address');
    if(a===0xff50) return this.#mapped?0:1;
    if(a>=32768) throw Error('Not a complete MMU');
    if(this.#mapped&&(a<256||(this.#boot.cgb&&a>=512&&a<2304))) return this.#boot.bytes[a];
    return this.#rom[a];
  }
  write8(a,v) {
    if(a!==0xff50||!Number.isInteger(v)||v<0||v>255) throw Error('Register/value');
    if(v) this.#mapped=false;
  }
}
export function saveBoot(storage,data) {
  new BootRomBus().loadBoot(data);storage.setItem('JAVABOY_BOOT_V1',JSON.stringify({v:1,bytes:Array.from(data)}));
}
export function restoreBoot(storage,bus) {
  const raw=storage.getItem('JAVABOY_BOOT_V1');if(raw===null) return false;
  if(raw.length>10000) throw Error('Record too large');const r=JSON.parse(raw);
  if(r.v!==1||!Array.isArray(r.bytes)||![256,2048,2304].includes(r.bytes.length)||!r.bytes.every(v=>Number.isInteger(v)&&v>=0&&v<=255)) throw Error('Invalid stored boot');
  bus.loadBoot(Uint8Array.from(r.bytes));return true;
}
function span(a,n,size) {if(!Number.isSafeInteger(a)||a<0||a+n>size) throw Error('RAM bounds');}
export class N64Rdram {
  #ram;constructor(expanded=false) {this.#ram=new Uint8Array((expanded?8:4)*1024*1024);}
  get size() {return this.#ram.length;}
  read8(a) {span(a,1,this.size);return this.#ram[a];}
  write8(a,v) {span(a,1,this.size);this.#ram[a]=v;}
  read32(a) {span(a,4,this.size);return ((this.read8(a)*16777216)+(this.read8(a+1)<<16)+(this.read8(a+2)<<8)+this.read8(a+3))>>>0;}
  write32(a,v) {span(a,4,this.size);for(let i=0;i<4;i++) this.write8(a+i,v>>>(24-i*8));}
}
export class ExperimentalSlot2Ram {
  #ram=null;enable() {this.#ram??=new Uint8Array(8*1024*1024);}disable() {this.#ram=null;}
  read8(a) {if(!this.#ram) throw Error('Disabled');span(a,1,this.#ram.length);return this.#ram[a];}
  write8(a,v) {if(!this.#ram) throw Error('Disabled');span(a,1,this.#ram.length);this.#ram[a]=v;}
}
export class Rumble {
  #host;#timer=null;#active=false;
  constructor(host=globalThis.navigator) {this.#host=host;}
  get active() {return this.#active;}
  stop() {clearTimeout(this.#timer);this.#timer=null;this.#active=false;try {this.#host?.vibrate?.(0);}catch {}}
  trigger(ms=200) {
    if(!Number.isInteger(ms)||ms<1||ms>1000) throw Error('Duration');this.stop();
    try {if(this.#host?.vibrate?.(ms)!==true) return false;}catch {return false;}
    this.#active=true;this.#timer=setTimeout(()=>{this.#active=false;this.#timer=null;},ms);return true;
  }
}
