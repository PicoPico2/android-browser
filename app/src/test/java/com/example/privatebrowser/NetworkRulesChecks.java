package com.example.privatebrowser;

public final class NetworkRulesChecks {
    private static int checks;
    private static void expect(boolean condition) { checks++; if (!condition) throw new AssertionError("check " + checks); }
    public static void runAll() {
        checks=0;
        NetworkRules r=new NetworkRules();
        expect(r.add("||ads.example.com^"));
        expect(r.add("@@||safe.ads.example.com^"));
        expect(r.decision("https://ads.example.com/x", "site.test", "image")==1);
        expect(r.decision("https://cdn.ads.example.com/x", "site.test", "image")==1);
        expect(r.decision("https://ads.example.com.evil.test/x", "site.test", "image")==0);
        expect(r.decision("https://notads.example.com/x", "site.test", "image")==0);
        expect(r.decision("https://safe.ads.example.com/x", "site.test", "image")==-1);
        expect(r.decision("https://ADS.EXAMPLE.COM./x", "site.test", "image")==1);
        expect(!r.add("||test.example^$third-party"));
        expect(!r.add("||test.example^$redirect=noopjs"));
        expect(!r.add("||test.example^$unknown"));
        expect(!r.add("/unsafe.*regex/"));
        expect(!r.add("@@||test.example^$document"));
        NetworkRules scoped=new NetworkRules();
        expect(scoped.add("||cdn.example.com/ads/*$script,domain=site.test|~safe.site.test"));
        expect(scoped.decision("https://cdn.example.com/ads/a.js", "www.site.test", "script")==1);
        expect(scoped.decision("https://cdn.example.com/ads/a.js", "safe.site.test", "script")==0);
        expect(scoped.decision("https://cdn.example.com/app/a.js", "site.test", "script")==0);
        expect(scoped.decision("https://cdn.example.com/ads/a.js", "site.test", "image")==0);
        expect(scoped.decision("https://cdn.example.com/ads/a.js", "site.test", "")==0);
        expect(scoped.decision("https://cdn.example.com/ads/a.js", "", "script")==0);
        NetworkRules generic=new NetworkRules();
        expect(generic.add("/advertisement/*"));
        expect(generic.decision("https://site.test/advertisement/banner", "site.test", "image")==1);
        expect(generic.decision("https://site.test/article", "site.test", "image")==0);
        expect(generic.add("@@/advertisement/allowed|"));
        expect(generic.decision("https://site.test/advertisement/allowed", "site.test", "image")==-1);
        expect(generic.decision("https://site.test/advertisement/allowedmore", "site.test", "image")==1);
        NetworkRules caseRule=new NetworkRules();
        expect(caseRule.add("|https://example.com/Ad|$match-case"));
        expect(caseRule.decision("https://example.com/Ad", "example.com", "image")==1);
        expect(caseRule.decision("https://example.com/ad", "example.com", "image")==0);
        expect(r.decision("javascript:alert(1)", "", "")==0);
        NetworkRules indexed=new NetworkRules();
        for(int i=0;i<20000;i++) expect(indexed.add("||ads"+i+".example.com^"));
        expect(indexed.decision("https://ads19999.example.com/x", "site.test", "image")==1);
        expect(indexed.decision("https://media.example.com/video", "site.test", "media")==0);
        System.out.println("NetworkRules: " + checks + " assertions passed");
    }
    public static void main(String[] args) { runAll(); }
}
