package com.example.privatebrowser

/** Bundled, auditable scripts only; filter downloads cannot supply JavaScript. */
internal object PageScripts {
    /** Installed at document start on YouTube. It removes only known ad metadata keys. */
    val youtubeDocumentStart = """
        (function(){
          try{if(localStorage.getItem('__privateYoutubeAdblockDisabled')==='1')return;}catch(e){}
          if(window.__privateYoutubeEarly)return;window.__privateYoutubeEarly=1;
          var keys=new Set(['adPlacements','playerAds','adSlots','adBreakHeartbeatParams','adBreakParams']);
          function prune(value){
            if(!value||typeof value!=='object')return value;
            Object.keys(value).forEach(function(k){
              if(keys.has(k)){try{delete value[k];}catch(e){}}
            });
            // YouTube player responses expose ad fields at the root or in one of these wrappers.
            ['playerResponse','player','response','data'].forEach(function(k){
              var child=value[k];if(child&&typeof child==='object')Object.keys(child).forEach(function(ck){
                if(keys.has(ck)){try{delete child[ck];}catch(e){}}
              });
            });
            return value;
          }
          var parse=JSON.parse;
          JSON.parse=function(){var out=parse.apply(this,arguments);try{prune(out);}catch(e){}return out;};
          if(window.Response&&Response.prototype.json){
            var responseJson=Response.prototype.json;
            Response.prototype.json=function(){return responseJson.apply(this,arguments).then(function(v){try{prune(v);}catch(e){}return v;});};
          }
          try{
            var initial=window.ytInitialPlayerResponse;
            if(initial)prune(initial);
            Object.defineProperty(window,'ytInitialPlayerResponse',{configurable:true,get:function(){return initial;},set:function(v){initial=prune(v);}});
          }catch(e){}
        })();
    """.trimIndent()

    fun cosmetic(selectors: Set<String>): String {
        val array = org.json.JSONArray(selectors.take(3000).toList()).toString()
        return """
            (function(){
                var old=document.getElementById('private-browser-ad-style');if(old)old.remove();
                var s=document.createElement('style');s.id='private-browser-ad-style';
                (document.head||document.documentElement).appendChild(s);
                $array.forEach(function(selector){
                    try {s.sheet.insertRule(selector+'{display:none!important}',s.sheet.cssRules.length);}catch(e){}
                });
            })()
        """.trimIndent()
    }
    val youtube = """
        (function(){
            if(!/(^|\.)youtube\.com$/.test(location.hostname)||window.__privateYoutubeAds)return;
            var css=document.createElement('style');css.id='private-youtube-ad-style';
            css.textContent='ytd-ad-slot-renderer,ytd-display-ad-renderer,ytd-promoted-sparkles-web-renderer,ytd-in-feed-ad-layout-renderer,ytm-promoted-sparkles-web-renderer{display:none!important}';
            (document.head||document.documentElement).appendChild(css);
            var lastSeek=0, lastSource='', lastVideo=null, lastTime=-1;
            function tick(){
                if(document.hidden)return;
                var player=document.querySelector('.html5-video-player.ad-showing,.html5-video-player.ad-interrupting');
                if(!player){lastSource='';lastVideo=null;lastTime=-1;return;}
                var button=player.querySelector('.ytp-ad-skip-button-modern,.ytp-skip-ad-button,.ytp-ad-skip-button,.ytp-ad-skip-button-slot button,[id^=skip-button] button');
                if(button&&button.getClientRects().length&&!button.disabled){button.click();return;}
                var video=player.querySelector('video');
                // Only seek an explicitly marked, finite advertisement. Never touch an unmarked main video.
                if(video&&video.readyState>=1&&Number.isFinite(video.duration)&&video.duration>0&&video.duration<=180){
                    var now=Date.now(), source=video.currentSrc;
                    if(now-lastSeek>1500&&(lastVideo!==video||lastSource!==source||video.currentTime<lastTime-1)){
                        lastSource=source;lastVideo=video;lastTime=video.duration;lastSeek=now;
                        try{video.currentTime=video.duration;}catch(e){}
                    }
                }
            }
            var observer=new MutationObserver(tick);observer.observe(document.documentElement,{subtree:true,childList:true,attributes:true,attributeFilter:['class']});
            var timer=setInterval(tick,500);
            function stop(){clearInterval(timer);observer.disconnect();css.remove();delete window.__privateYoutubeAds;}
            window.__privateYoutubeAds={stop:stop};
            addEventListener('pagehide',stop,{once:true});tick();
        })()
    """.trimIndent()
    val stopYoutube = "window.__privateYoutubeAds&&window.__privateYoutubeAds.stop();"

    val desktopViewport = """
        (function(){
          var viewport=document.querySelector('meta[name="viewport"]');
          if(!viewport){viewport=document.createElement('meta');viewport.name='viewport';(document.head||document.documentElement).appendChild(viewport);}
          viewport.setAttribute('content','width=1280, initial-scale=1, minimum-scale=0.1, maximum-scale=10, user-scalable=yes');
          if(document.documentElement)document.documentElement.style.touchAction='pan-x pan-y pinch-zoom';
        })();
    """.trimIndent()

    fun mediaGate(active: Boolean): String = """
        (function(active){
            var old=window.__privateMediaGate;
            if(old&&old.stop)old.stop();
            function media(){return Array.from(document.querySelectorAll('video,audio'));}
            function pauseItem(item){
                if(item&&!item.paused){item.dataset.privateResume='1';try{item.pause();}catch(e){}}
            }
            function pauseInactive(){media().forEach(pauseItem);}
            function onPlay(event){if(!active)pauseItem(event.target);}
            function stop(){document.removeEventListener('play',onPlay,true);delete window.__privateMediaGate;}
            window.__privateMediaGate={stop:stop};
            if(active){media().forEach(function(item){
                if(item.dataset.privateResume==='1'){delete item.dataset.privateResume;var p=item.play();if(p&&p.catch)p.catch(function(){});}
            });}else{pauseInactive();document.addEventListener('play',onPlay,true);}
        })(${if (active) "true" else "false"})
    """.trimIndent()
}
