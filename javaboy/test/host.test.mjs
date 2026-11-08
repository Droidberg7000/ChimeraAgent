import {test} from 'node:test';import assert from 'node:assert/strict';
import {BootRomBus,saveBoot,restoreBoot,N64Rdram,ExperimentalSlot2Ram,Rumble} from '../host/memory.mjs';
function rom(){const r=new Uint8Array(32768);r[0]=42;r[256]=99;return r;}
test('DMG overlay, exact ROM offsets, one-way unmap and explicit reset',()=>{
 const b=new BootRomBus();b.loadRom(rom());b.loadBoot(new Uint8Array(256).fill(7));b.reset();
 assert.equal(b.read8(0),7);assert.equal(b.read8(256),99);b.write8(0xff50,1);b.write8(0xff50,0);assert.equal(b.read8(0),42);
 b.reset();b.loadBoot(new Uint8Array(256).fill(8));assert.equal(b.read8(0),7);b.reset();assert.equal(b.read8(0),8);
});
test('CGB packed/padded layout preserves header hole',()=>{
 for(const n of [2048,2304]) {const b=new BootRomBus();b.loadRom(rom());b.loadBoot(new Uint8Array(n).fill(12));b.reset();assert.equal(b.read8(256),99);assert.equal(b.read8(512),12);}
});
test('invalid ROM, boot and register input fail closed',()=>{
 const b=new BootRomBus();assert.throws(()=>b.loadRom(new Uint8Array(2)));assert.throws(()=>b.loadBoot(new Uint8Array(2)));
 const r=rom();r[0x147]=1;assert.throws(()=>b.loadRom(r));assert.throws(()=>b.read8(32768));assert.throws(()=>b.write8(0xff50,256));
});
test('persisted bytes revalidated, not trusted metadata',()=>{
 let raw=null;const storage={getItem:()=>raw,setItem:(_,s)=>{raw=s;}};const b=new BootRomBus();saveBoot(storage,new Uint8Array(256).fill(5));assert.equal(restoreBoot(storage,b),true);b.reset();assert.equal(b.read8(0),5);
 raw=JSON.stringify({v:1,bytes:Array(256).fill(300)});assert.throws(()=>restoreBoot(storage,b));
});
test('N64 RAM capacity, big endian and nonwrapping boundaries',()=>{
 const a=new N64Rdram(),b=new N64Rdram(true);assert.equal(a.size,4194304);assert.equal(b.size,8388608);b.write32(0,0x89abcdef);assert.equal(b.read8(0),0x89);assert.equal(b.read32(0),0x89abcdef);assert.throws(()=>b.write32(b.size-2,0));
});
test('Slot-2 buffer explicitly enabled and bounded, not a bus protocol',()=>{
 const r=new ExperimentalSlot2Ram();assert.throws(()=>r.read8(0));r.enable();r.write8(0,9);assert.equal(r.read8(0),9);assert.throws(()=>r.read8(8388608));r.disable();assert.throws(()=>r.read8(0));
});
test('rumble handles refusal and replaces timers',async()=>{
 assert.equal(new Rumble({vibrate:()=>false}).trigger(10),false);const r=new Rumble({vibrate:()=>true});r.trigger(5);r.trigger(80);await new Promise(resolve=>setTimeout(resolve,20));assert.equal(r.active,true);r.stop();assert.equal(r.active,false);
});
