package com.example.privatebrowser;

import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

/** Indexed ABP URL rules. Unsupported modifiers are rejected, never broadened.
 * No remote scripts, redirect resources, response rewriting or browser-extension API emulation.
 */
public final class NetworkRules {
    private final Map<String,List<Rule>> hosts = new HashMap<>();
    private final Set<String> directDeny = new HashSet<>(), directAllow = new HashSet<>();
    private final List<Rule> generic = new ArrayList<>();
    private int size;
    private static final Pattern TRAILING_HOST_DOT = Pattern.compile("(://[^/?#:]+)\\.(?=[:/?#]|$)", Pattern.CASE_INSENSITIVE);
    private static final Set<String> TYPES = new HashSet<>(Arrays.asList(
        "script", "image", "stylesheet", "font", "media", "subdocument", "document", "xmlhttprequest", "other"));
    public int size() { return size; }
    public boolean add(String input) {
        boolean exception = input.startsWith("@@");
        String value = exception ? input.substring(2) : input;
        String[] parts = value.split("\\$", 2);
        String pattern = parts[0];
        if (pattern.chars().filter(c -> c == '*').count() > 3 || pattern.isEmpty() || pattern.length() > 512 || pattern.startsWith("/") && pattern.endsWith("/")) return false;
        Set<String> include = new HashSet<>(), exclude = new HashSet<>(), types = new HashSet<>(), excludedTypes = new HashSet<>();
        boolean matchCase = false;
        if (parts.length == 2) for (String option : parts[1].split(",")) {
            if (option.equals("match-case")) matchCase = true;
            else if (option.startsWith("domain=")) {
                for (String domain : option.substring(7).split("\\|")) {
                    boolean negative = domain.startsWith("~");
                    String host = negative ? domain.substring(1) : domain;
                    if (!host.matches("[a-zA-Z0-9.-]+") || host.isEmpty()) return false;
                    (negative ? exclude : include).add(host.toLowerCase(Locale.ROOT));
                }
            } else if (TYPES.contains(option)) types.add(option);
            else if (option.startsWith("~") && TYPES.contains(option.substring(1))) excludedTypes.add(option.substring(1));
            else return false; // e.g. third-party needs a full PSL + frame origin, so do not guess.
        }
        // ABP's exception $document disables filtering for the whole page; request-only
        // matching cannot implement that semantic. Reject it rather than misinterpret it.
        if (exception && types.contains("document")) return false;
        // Host-only lists can contain hundreds of thousands of entries. Keep them as
        // suffix sets, not one compiled regular expression per hostname.
        if (parts.length == 1 && pattern.matches("\\|\\|[a-zA-Z0-9.-]+\\^")) {
            String host = pattern.substring(2, pattern.length()-1).toLowerCase(Locale.ROOT);
            if (!host.contains(".")) return false;
            if ((exception ? directAllow : directDeny).add(host)) size++;
            return true;
        }
        String hostKey = "";
        String expression;
        if (pattern.startsWith("||")) {
            String tail = pattern.substring(2);
            int i = 0;
            while (i < tail.length() && (Character.isLetterOrDigit(tail.charAt(i)) || tail.charAt(i)=='.' || tail.charAt(i)=='-')) i++;
            hostKey = tail.substring(0, i).toLowerCase(Locale.ROOT);
            if (!hostKey.contains(".") || (i < tail.length() && "^/:|".indexOf(tail.charAt(i)) < 0)) return false;
            // Require an actual hostname boundary; never match example.com.evil.test.
            expression = "^https?://(?:[^/?#:.]+\\.)*" + Pattern.quote(hostKey) + "(?=[:/?#]|$)" + compileGlob(tail.substring(i));
        } else {
            boolean left = pattern.startsWith("|");
            expression = (left ? "^" : "") + compileGlob(left ? pattern.substring(1) : pattern);
        }
        try {
            Rule rule = new Rule(Pattern.compile(expression, matchCase ? 0 : Pattern.CASE_INSENSITIVE), exception, include, exclude, types, excludedTypes, pattern);
            if (hostKey.isEmpty()) {
                // Bound generic scans, counted as unsupported if capacity is exceeded.
                if (generic.size() >= 4096 && !exception) return false;
                generic.add(rule);
            } else hosts.computeIfAbsent(hostKey, ignored -> new ArrayList<>()).add(rule);
            size++;
            return true;
        } catch (RuntimeException ignored) { return false; }
    }
    private static String compileGlob(String pattern) {
        StringBuilder out = new StringBuilder();
        for (int i=0; i<pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c=='*') {
                if (i==0 || pattern.charAt(i-1)!='*') out.append(".*");
            } else if (c=='^') out.append("(?:[^a-zA-Z0-9_.%-]|$)");
            else if (c=='|' && i==pattern.length()-1) out.append('$');
            else out.append(Pattern.quote(String.valueOf(c)));
        }
        return out.toString();
    }
    public int decision(String url, String pageHost, String type) {
        if (url.length() > 16384) return 0;
        String host;
        try { URI uri = new URI(url); host = uri.getHost(); if (host==null || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) return 0; }
        catch (Exception ignored) { return 0; }
        host = host.toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) host = host.substring(0, host.length()-1);
        // Canonicalize a trailing DNS dot for matching while preserving path/query case.
        url = TRAILING_HOST_DOT.matcher(url).replaceFirst("$1");
        boolean blocked = false;
        String lowerUrl = url.toLowerCase(Locale.ROOT);
        String suffix = host;
        while (!suffix.isEmpty()) {
            if (directAllow.contains(suffix)) return -1;
            if (directDeny.contains(suffix)) blocked = true;
            List<Rule> candidates = hosts.get(suffix);
            if (candidates != null) for (Rule rule : candidates) if (rule.matches(url, lowerUrl, pageHost, type)) {
                if (rule.exception) return -1;
                blocked = true;
            }
            int dot=suffix.indexOf('.'); suffix=dot<0 ? "" : suffix.substring(dot+1);
        }
        for (Rule rule : generic) if (rule.matches(url,lowerUrl,pageHost,type)) {
            if (rule.exception) return -1;
            blocked=true;
        }
        return blocked ? 1 : 0;
    }
    private static boolean matchesDomains(String host, Set<String> domains) {
        for (String d:domains) if (host.equals(d) || host.endsWith("."+d)) return true;
        return false;
    }
    private static final class Rule {
        final Pattern pattern; final boolean exception; final String needle;
        final Set<String> include, exclude, types, excludedTypes;
        Rule(Pattern p, boolean e, Set<String> i, Set<String> x, Set<String> t, Set<String> xt, String source) {
            pattern=p;exception=e;include=i;exclude=x;types=t;excludedTypes=xt;
            String longest="";
            for (String token:source.split("[|*^]")) if (token.length()>longest.length()) longest=token;
            needle=longest.toLowerCase(Locale.ROOT);
        }
        boolean matches(String url,String lowerUrl,String pageHost,String type) {
            if (!lowerUrl.contains(needle)) return false;
            if ((!include.isEmpty() || !exclude.isEmpty()) && pageHost.isEmpty()) return false;
            if (!include.isEmpty() && !matchesDomains(pageHost,include) || matchesDomains(pageHost,exclude)) return false;
            // Unknown request type must not turn a constrained rule into an unconditional rule.
            if ((!types.isEmpty() || !excludedTypes.isEmpty()) && type.isEmpty()) return false;
            if (!types.isEmpty() && !types.contains(type) || excludedTypes.contains(type)) return false;
            return pattern.matcher(url).find();
        }
    }
}
