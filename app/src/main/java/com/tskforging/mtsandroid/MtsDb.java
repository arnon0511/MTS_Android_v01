package com.tskforging.mtsandroid;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public final class MtsDb extends SQLiteOpenHelper {
    public static final class HistoryRow {
        public long id, confirmedAt;
        public String shiftId, shift, employee, machine, type, process, item, partNo, partName;
        public String qty, lot, charge, raw;
    }

    public MtsDb(Context context) { super(context, "mts_android.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tag_history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "shift_id TEXT NOT NULL,shift_name TEXT,employee TEXT,machine TEXT," +
                "doc_type TEXT,process TEXT,item TEXT,part_no TEXT,part_name TEXT," +
                "qty TEXT,lot TEXT,charge TEXT,duplicate_key TEXT NOT NULL UNIQUE," +
                "raw_qr TEXT NOT NULL,confirmed_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_tag_history_time ON tag_history(confirmed_at DESC)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    public boolean isDuplicate(String key) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM tag_history WHERE duplicate_key=? LIMIT 1", new String[]{key})) {
            return c.moveToFirst();
        }
    }

    public long confirm(String shiftId, String shift, String employee, String machine,
                        TagParser.ResultTag tag, long confirmedAt) {
        ContentValues v = new ContentValues();
        v.put("shift_id", shiftId); v.put("shift_name", shift); v.put("employee", employee);
        v.put("machine", machine); v.put("doc_type", tag.type.name()); v.put("process", tag.process);
        v.put("item", tag.item); v.put("part_no", tag.partNo); v.put("part_name", tag.partName);
        v.put("qty", tag.qty); v.put("lot", tag.lot); v.put("charge", tag.charge);
        v.put("duplicate_key", tag.duplicateKey()); v.put("raw_qr", tag.raw);
        v.put("confirmed_at", confirmedAt);
        return getWritableDatabase().insert("tag_history", null, v);
    }

    public List<HistoryRow> list(int limit) {
        List<HistoryRow> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id,shift_id,shift_name,employee,machine,doc_type,process,item,part_no," +
                        "part_name,qty,lot,charge,raw_qr,confirmed_at FROM tag_history " +
                        "ORDER BY confirmed_at DESC LIMIT ?", new String[]{String.valueOf(limit)})) {
            while (c.moveToNext()) {
                HistoryRow r = new HistoryRow();
                r.id=c.getLong(0); r.shiftId=c.getString(1); r.shift=c.getString(2);
                r.employee=c.getString(3); r.machine=c.getString(4); r.type=c.getString(5);
                r.process=c.getString(6); r.item=c.getString(7); r.partNo=c.getString(8);
                r.partName=c.getString(9); r.qty=c.getString(10); r.lot=c.getString(11);
                r.charge=c.getString(12); r.raw=c.getString(13); r.confirmedAt=c.getLong(14);
                out.add(r);
            }
        }
        return out;
    }
}
