const vm = require('vm');
const assert = require('assert');
const fs = require('fs');
const path = require('path');
const kotlin = fs.readFileSync(path.join(__dirname, '../app/src/main/java/com/example/privatebrowser/PageScripts.kt'), 'utf8');
const source = kotlin.match(/val youtube = """([\s\S]*?)"""\.trimIndent/)[1];
let cases=0;
function run({host='www.youtube.com',ad=false,duration=30,skip=false,hidden=false}={}){
 let seeks=0,clicks=0,intervals=0,clears=0,removed=0,position=0;
 const video={readyState:2,duration,currentSrc:'ad-video',get currentTime(){return position},set currentTime(v){seeks++;position=v}};
 const button=skip?{getClientRects:()=>[{}],disabled:false,click:()=>clicks++}:null;
 const player={querySelector:q=>q==='video'?video:button};
 const context={location:{hostname:host},window:{},document:{hidden,head:{appendChild(){}},createElement:()=>({remove:()=>removed++}),querySelector:()=>ad?player:null},setInterval:fn=>{intervals++;return 1},clearInterval:()=>clears++,addEventListener(){},Date,Number};
 vm.runInNewContext(source,context);
 return {seeks,clicks,intervals,context,stop(){context.window.__privateYoutubeAds.stop();assert.equal(clears,1);assert.equal(removed,1)}};
}
let r=run({ad:false});assert.equal(r.seeks,0);cases++;
r=run({ad:true});assert.equal(r.seeks,1);r.stop();cases++;
r=run({ad:true,duration:Infinity});assert.equal(r.seeks,0);cases++;
r=run({ad:true,duration:500});assert.equal(r.seeks,0);cases++;
r=run({ad:true,skip:true});assert.equal(r.clicks,1);assert.equal(r.seeks,0);cases++;
r=run({ad:true,hidden:true});assert.equal(r.seeks,0);cases++;
r=run({host:'youtube.com.evil.test',ad:true});assert.equal(r.intervals,0);cases++;
r=run({host:'www.nicovideo.jp',ad:true});assert.equal(r.intervals,0);cases++;
r=run();vm.runInNewContext(source,r.context);assert.equal(r.context.window.__privateYoutubeAds!==undefined,true);cases++;
console.log('YouTube script: '+cases+' simulated cases passed (not a live YouTube test)');
