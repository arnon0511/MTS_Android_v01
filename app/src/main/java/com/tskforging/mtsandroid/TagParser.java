package com.tskforging.mtsandroid;

import java.util.Locale;

public final class TagParser {
    public enum Type { WIP, FG, UNKNOWN }

    public static final class ResultTag {
        public final Type type;
        public final String process, item, partNo, partName, qty, lot, charge, raw;

        ResultTag(Type type, String process, String item, String partNo, String partName,
                  String qty, String lot, String charge, String raw) {
            this.type = type;
            this.process = clean(process);
            this.item = clean(item);
            this.partNo = clean(partNo);
            this.partName = clean(partName);
            this.qty = clean(qty);
            this.lot = clean(lot);
            this.charge = clean(charge);
            this.raw = raw == null ? "" : raw.trim();
        }

        public boolean isValid() {
            return type != Type.UNKNOWN && !process.isEmpty() && !item.isEmpty() && !lot.isEmpty();
        }

        public String duplicateKey() {
            return normalize(process) + "|" + normalize(item) + "|" + normalize(lot);
        }
    }

    private TagParser() {}

    public static ResultTag parse(String raw) {
        if (raw == null) return unknown("");
        String[] f = raw.trim().split("\\|", -1);
        if (f.length == 13 && looksLikeProcess(get(f, 1))) {
            return new ResultTag(Type.WIP, get(f, 1), get(f, 2), get(f, 4), get(f, 5),
                    get(f, 6), get(f, 11), get(f, 12), raw);
        }
        if (f.length == 14 && startsWith(get(f, 1), "FP")) {
            return new ResultTag(Type.FG, "FG", get(f, 1), get(f, 3), get(f, 4),
                    get(f, 5), get(f, 10), get(f, 11), raw);
        }
        return unknown(raw);
    }

    private static ResultTag unknown(String raw) {
        return new ResultTag(Type.UNKNOWN, "", "", "", "", "", "", "", raw);
    }

    private static String get(String[] f, int index) {
        return index >= 0 && index < f.length ? f[index] : "";
    }

    private static boolean startsWith(String value, String prefix) {
        return normalize(value).startsWith(prefix);
    }

    private static boolean looksLikeProcess(String value) {
        String s = normalize(value);
        return s.contains("#") || s.contains("CUTTING") || s.contains("CHAMFER")
                || s.contains("PRESS") || s.contains("DRAWING");
    }

    private static String normalize(String value) {
        return clean(value).toUpperCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
