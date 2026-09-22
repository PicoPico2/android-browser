package com.example.privatebrowser

/** Bundled, auditable scripts only; filter downloads cannot supply JavaScript. */
internal object PageScripts {
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
                var player=document.querySelector('.html5-video-player.ad-showing');
                if(!player){lastSource='';lastVideo=null;lastTime=-1;return;}
                var button=player.querySelector('.ytp-skip-ad-button,.ytp-ad-skip-button,.ytp-ad-skip-button-modern');
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
            var timer=setInterval(tick,750);
            function stop(){clearInterval(timer);css.remove();delete window.__privateYoutubeAds;}
            window.__privateYoutubeAds={stop:stop};
            addEventListener('pagehide',stop,{once:true});tick();
        })()
    """.trimIndent()
    val stopYoutube = "window.__privateYoutubeAds&&window.__privateYoutubeAds.stop();"
}
