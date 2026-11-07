package com.ashen.prototype;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON parser (objects, arrays, strings, numbers, booleans, null). Supports // line comments. Pure Java. */
final class Json {
    private final String s;
    private int p;

    private Json(String s) { this.s = s; }

    static Object parse(String text) { return new Json(text).val(); }

    private void ws() {
        while (p < s.length()) {
            char c = s.charAt(p);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') p++;
            else if (c == '/' && p + 1 < s.length() && s.charAt(p + 1) == '/') { while (p < s.length() && s.charAt(p) != '\n') p++; }
            else break;
        }
    }

    private Object val() {
        ws();
        char c = s.charAt(p);
        if (c == '{') {
            p++;
            Map<String, Object> m = new LinkedHashMap<>();
            ws();
            if (s.charAt(p) == '}') { p++; return m; }
            while (true) {
                ws(); String k = readStr(); ws(); p++; // ':'
                m.put(k, val()); ws();
                if (s.charAt(p++) == '}') return m;
            }
        }
        if (c == '[') {
            p++;
            List<Object> l = new ArrayList<>();
            ws();
            if (s.charAt(p) == ']') { p++; return l; }
            while (true) {
                l.add(val()); ws();
                if (s.charAt(p++) == ']') return l;
            }
        }
        if (c == '"') return readStr();
        if (s.startsWith("true", p)) { p += 4; return Boolean.TRUE; }
        if (s.startsWith("false", p)) { p += 5; return Boolean.FALSE; }
        if (s.startsWith("null", p)) { p += 4; return null; }
        int st = p;
        while (p < s.length() && "+-0123456789.eE".indexOf(s.charAt(p)) >= 0) p++;
        return Double.parseDouble(s.substring(st, p));
    }

    private String readStr() {
        p++;
        StringBuilder b = new StringBuilder();
        while (s.charAt(p) != '"') {
            char c = s.charAt(p++);
            if (c == '\\') {
                char e = s.charAt(p++);
                switch (e) {
                    case 'n': b.append('\n'); break;
                    case 't': b.append('\t'); break;
                    case 'u': b.append((char) Integer.parseInt(s.substring(p, p + 4), 16)); p += 4; break;
                    default: b.append(e);
                }
            } else b.append(c);
        }
        p++;
        return b.toString();
    }

    // ---- typed helpers ----
    @SuppressWarnings("unchecked")
    static Map<String, Object> obj(Object o) { return o == null ? new LinkedHashMap<String, Object>() : (Map<String, Object>) o; }

    @SuppressWarnings("unchecked")
    static List<Object> arr(Object o) { return o == null ? new ArrayList<Object>() : (List<Object>) o; }

    static double getNum(Map<String, Object> m, String k, double d) {
        Object v = m.get(k);
        return v instanceof Number ? ((Number) v).doubleValue() : d;
    }

    static String getStr(Map<String, Object> m, String k, String d) {
        Object v = m.get(k);
        return v instanceof String ? (String) v : d;
    }

    static boolean getBool(Map<String, Object> m, String k, boolean d) {
        Object v = m.get(k);
        return v instanceof Boolean ? (Boolean) v : d;
    }
}
