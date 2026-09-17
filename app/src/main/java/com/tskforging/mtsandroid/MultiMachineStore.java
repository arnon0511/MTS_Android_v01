package com.tskforging.mtsandroid;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class MultiMachineStore extends SQLiteOpenHelper {
    public static final String UNKNOWN = "UNKNOWN";
    public static final String RUNNING = "RUNNING";
    public static final String SETUP = "SETUP";
    public static final String PLANNED_STOP = "PLANNED_STOP";
    public static final String UNPLANNED_STOP = "UNPLANNED_STOP";
    public static final String CLOSED = "CLOSED";

    public static final class MachineState {
        public String machineId = "", groupName = "", planGroup = "", status = UNKNOWN;
        public String employee = "", shiftName = "", item = "", partNo = "", lot = "";
        public String reason = "", detail = "", bladeId = "";
        public long ok, ng, startMs, stopStartMs, stopSec, updatedMs;
    }

    public static final class TagResult {
        public boolean accepted;
        public String message = "";
        public long tagQty, previousQty, thisShiftQty;
    }

    public static final class PendingEvent {
        public final String eventId;
        public final String json;
        PendingEvent(String eventId, String json) { this.eventId = eventId; this.json = json; }
    }

    public MultiMachineStore(Context context) { super(context, "mts_multi_v2.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE machine_state(machine_id TEXT PRIMARY KEY,group_name TEXT,plan_group TEXT,status TEXT,employee TEXT,shift_name TEXT,item TEXT,part_no TEXT,lot TEXT,reason TEXT,detail TEXT,blade_id TEXT,ok_qty INTEGER DEFAULT 0,ng_qty INTEGER DEFAULT 0,start_ms INTEGER DEFAULT 0,stop_start_ms INTEGER DEFAULT 0,stop_sec INTEGER DEFAULT 0,updated_ms INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE tag_history(id INTEGER PRIMARY KEY AUTOINCREMENT,machine_id TEXT,shift_key TEXT,process TEXT,item TEXT,part_no TEXT,part_name TEXT,lot TEXT,charge TEXT,tag_qty INTEGER,previous_qty INTEGER,this_qty INTEGER,duplicate_key TEXT,raw_qr TEXT,confirmed_at INTEGER)");
        db.execSQL("CREATE UNIQUE INDEX idx_tag_shift_dup ON tag_history(machine_id,shift_key,duplicate_key)");
        db.execSQL("CREATE INDEX idx_tag_carry ON tag_history(process,item,lot)");
        db.execSQL("CREATE TABLE events(event_id TEXT PRIMARY KEY,event_type TEXT,machine_id TEXT,group_name TEXT,plan_group TEXT,employee TEXT,shift_name TEXT,event_ms INTEGER,payload_json TEXT,synced INTEGER DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_events_sync ON events(synced,event_ms)");
        seedMachines(db);
    }

    private void seedMachines(SQLiteDatabase db) {
        for (MachineCatalog.Machine m : MachineCatalog.all()) {
            ContentValues v = new ContentValues();
            v.put("machine_id", m.id); v.put("group_name", m.group); v.put("plan_group", m.planGroup);
            v.put("status", UNKNOWN); db.insertWithOnConflict("machine_state", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    @Override public void onOpen(SQLiteDatabase db) { super.onOpen(db); seedMachines(db); }

    public MachineState state(String machineId) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT machine_id,group_name,plan_group,status,employee,shift_name,item,part_no,lot,reason,detail,blade_id,ok_qty,ng_qty,start_ms,stop_start_ms,stop_sec,updated_ms FROM machine_state WHERE machine_id=?", new String[]{machineId})) {
            if (c.moveToFirst()) return readState(c);
        }
        return new MachineState();
    }

    public List<MachineState> states(String group) {
        List<MachineState> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT machine_id,group_name,plan_group,status,employee,shift_name,item,part_no,lot,reason,detail,blade_id,ok_qty,ng_qty,start_ms,stop_start_ms,stop_sec,updated_ms FROM machine_state WHERE group_name=? ORDER BY machine_id", new String[]{group})) {
            while (c.moveToNext()) out.add(readState(c));
        }
        return out;
    }

    private MachineState readState(Cursor c) {
        MachineState s = new MachineState();
        s.machineId=clean(c.getString(0));s.groupName=clean(c.getString(1));s.planGroup=clean(c.getString(2));s.status=clean(c.getString(3));
        s.employee=clean(c.getString(4));s.shiftName=clean(c.getString(5));s.item=clean(c.getString(6));s.partNo=clean(c.getString(7));s.lot=clean(c.getString(8));
        s.reason=clean(c.getString(9));s.detail=clean(c.getString(10));s.bladeId=clean(c.getString(11));s.ok=c.getLong(12);s.ng=c.getLong(13);
        s.startMs=c.getLong(14);s.stopStartMs=c.getLong(15);s.stopSec=c.getLong(16);s.updatedMs=c.getLong(17); return s;
    }

    public void assignEmployee(String machineId,String employee,String shift,long now)throws Exception{
        MachineState s=state(machineId);if(s.machineId.isEmpty())throw new IllegalArgumentException("ไม่พบเครื่องจักร / Unknown machine");
        ContentValues v=new ContentValues();v.put("employee",clean(employee));v.put("updated_ms",now);getWritableDatabase().update("machine_state",v,"machine_id=?",new String[]{machineId});
        JSONObject p=new JSONObject();p.put("employee",clean(employee));p.put("assigned_shift",clean(shift));addEvent("EMPLOYEE",machineId,employee,shift,now,p);
    }

    public void setStatus(String machineId, String status, String reason, String detail,
                          String employee, String shift, long now) throws Exception {
        setStatus(machineId,status,reason,detail,employee,shift,now,now);
    }

    public void setStatus(String machineId, String status, String reason, String detail,
                          String employee, String shift, long actualMs, long effectiveMs) throws Exception {
        MachineState old = state(machineId);
        if (old.machineId.isEmpty()) throw new IllegalArgumentException("ไม่พบเครื่องจักร / Unknown machine");
        long stopSec = old.stopSec;
        long stopStart = old.stopStartMs;
        if (isStopped(old.status) && stopStart > 0 && !isStopped(status)) {
            stopSec += Math.max(0, (actualMs-stopStart)/1000); stopStart=0;
        }
        if (isStopped(status) && stopStart == 0) stopStart=actualMs;
        boolean newShift = RUNNING.equals(status) && (old.startMs==0 || CLOSED.equals(old.status) || !clean(shift).equals(old.shiftName));
        ContentValues v = new ContentValues();
        v.put("status",status);v.put("reason",clean(reason));v.put("detail",clean(detail));v.put("employee",clean(employee));v.put("shift_name",clean(shift));
        v.put("updated_ms",actualMs);v.put("stop_start_ms",stopStart);v.put("stop_sec",stopSec);
        if (newShift) {
            v.put("ok_qty",0);v.put("ng_qty",0);v.put("start_ms",effectiveMs);v.put("stop_sec",0);v.put("stop_start_ms",0);
            v.put("item","");v.put("part_no","");v.put("lot","");
        } else if (RUNNING.equals(status) && old.startMs == 0) v.put("start_ms",effectiveMs);
        getWritableDatabase().update("machine_state",v,"machine_id=?",new String[]{machineId});
        JSONObject p=new JSONObject();p.put("status",status);p.put("reason",clean(reason));p.put("detail",clean(detail));p.put("actual_ms",actualMs);p.put("effective_ms",effectiveMs);
        addEvent("STATUS",machineId,employee,shift,actualMs,p);
    }

    public TagResult recordTag(String machineId, String employee, String shift, TagParser.ResultTag tag, long now) throws Exception {
        TagResult result=new TagResult();
        if (tag==null || !tag.isValid()) { result.message="ไม่ใช่ WIP/FG Tag ที่รองรับ / Unsupported Tag";return result; }
        MachineState ms=state(machineId); String shiftKey=shiftKey(shift,now); long qty=number(tag.qty);
        if (!RUNNING.equals(ms.status) && !SETUP.equals(ms.status)) { result.message="กรุณากดเริ่มผลิตก่อนสแกน Tag / Start work first";return result; }
        long previous=sum("SELECT COALESCE(SUM(this_qty),0) FROM tag_history WHERE process=? AND item=? AND lot=? AND shift_key<>?",new String[]{tag.process,tag.item,tag.lot,shiftKey});
        long thisQty=Math.max(0,qty-previous);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            ContentValues v=new ContentValues();v.put("machine_id",machineId);v.put("shift_key",shiftKey);v.put("process",tag.process);v.put("item",tag.item);v.put("part_no",tag.partNo);v.put("part_name",tag.partName);v.put("lot",tag.lot);v.put("charge",tag.charge);v.put("tag_qty",qty);v.put("previous_qty",previous);v.put("this_qty",thisQty);v.put("duplicate_key",tag.duplicateKey());v.put("raw_qr",tag.raw);v.put("confirmed_at",now);
            long id=db.insert("tag_history",null,v); if(id<0){result.message="Tag ซ้ำในเครื่องและกะนี้ / Duplicate Tag";return result;}
            ContentValues u=new ContentValues();u.put("item",tag.item);u.put("part_no",tag.partNo);u.put("lot",tag.lot);u.put("ok_qty",ms.ok+thisQty);u.put("employee",employee);u.put("shift_name",shift);u.put("status",RUNNING);u.put("updated_ms",now);if(ms.startMs==0)u.put("start_ms",now);
            db.update("machine_state",u,"machine_id=?",new String[]{machineId});
            JSONObject p=new JSONObject();p.put("tag_type",tag.type.name());p.put("process",tag.process);p.put("item",tag.item);p.put("part_no",tag.partNo);p.put("part_name",tag.partName);p.put("lot",tag.lot);p.put("charge",tag.charge);p.put("tag_qty",qty);p.put("previous_qty",previous);p.put("this_qty",thisQty);p.put("raw_qr",tag.raw);
            addEvent(db,"TAG",machineId,employee,shift,now,p);db.setTransactionSuccessful();
            result.accepted=true;result.tagQty=qty;result.previousQty=previous;result.thisShiftQty=thisQty;result.message="บันทึกแล้ว / Saved";
        } finally { db.endTransaction(); }
        return result;
    }

    public void addNg(String machineId,long qty,String reason,String detail,String employee,String shift,long now)throws Exception{
        if(qty<=0)throw new IllegalArgumentException("จำนวน NG ต้องมากกว่า 0");MachineState s=state(machineId);
        ContentValues u=new ContentValues();u.put("ng_qty",s.ng+qty);u.put("updated_ms",now);getWritableDatabase().update("machine_state",u,"machine_id=?",new String[]{machineId});
        JSONObject p=new JSONObject();p.put("qty",qty);p.put("reason",reason);p.put("detail",detail);addEvent("NG",machineId,employee,shift,now,p);
    }

    public void changeBlade(String machineId,String bladeId,String reason,String employee,String shift,long now)throws Exception{
        MachineState s=state(machineId);ContentValues u=new ContentValues();u.put("blade_id",clean(bladeId));u.put("updated_ms",now);getWritableDatabase().update("machine_state",u,"machine_id=?",new String[]{machineId});
        JSONObject p=new JSONObject();p.put("old_blade",s.bladeId);p.put("new_blade",clean(bladeId));p.put("reason",clean(reason));p.put("life_ok_qty",s.ok);addEvent("BLADE_CHANGE",machineId,employee,shift,now,p);
    }

    public void closeMachine(String machineId,long lastOk,long lastNg,String ngReason,int coffeeCount,boolean meal,boolean otBreak,String employee,String shift,long actualMs,long effectiveMs,String closeReason,int coffeeMinutes,int mealMinutes,int otBreakMinutes)throws Exception{
        if(lastNg>0 && clean(ngReason).isEmpty())throw new IllegalArgumentException("กรุณาระบุสาเหตุ NG");MachineState s=state(machineId);
        long finalStop=s.stopSec+(s.stopStartMs>0?Math.max(0,(actualMs-s.stopStartMs)/1000):0);
        ContentValues u=new ContentValues();u.put("status",CLOSED);u.put("ok_qty",s.ok+Math.max(0,lastOk));u.put("ng_qty",s.ng+Math.max(0,lastNg));u.put("updated_ms",actualMs);u.put("reason",clean(closeReason));u.put("detail","");u.put("stop_start_ms",0);u.put("stop_sec",finalStop);getWritableDatabase().update("machine_state",u,"machine_id=?",new String[]{machineId});
        long breakSec=Math.max(0,coffeeCount)*(long)Math.max(0,coffeeMinutes)*60L+(meal?(long)Math.max(0,mealMinutes)*60L:0)+(otBreak?(long)Math.max(0,otBreakMinutes)*60L:0);
        long elapsed=s.startMs>0?Math.max(0,(effectiveMs-s.startMs)/1000):0;
        long workingSec=Math.max(0,elapsed-finalStop-breakSec);long otSec=Math.max(0,elapsed-9L*3600L);
        JSONObject p=new JSONObject();p.put("last_ok",Math.max(0,lastOk));p.put("last_ng",Math.max(0,lastNg));p.put("ng_reason",clean(ngReason));p.put("coffee_count",coffeeCount);p.put("meal_taken",meal);p.put("ot_break_taken",otBreak);p.put("break_sec",breakSec);p.put("working_sec",workingSec);p.put("ot_sec",otSec);p.put("total_ok",s.ok+Math.max(0,lastOk));p.put("total_ng",s.ng+Math.max(0,lastNg));p.put("stop_sec",finalStop);p.put("close_reason",clean(closeReason));p.put("actual_close_ms",actualMs);p.put("effective_close_ms",effectiveMs);addEvent("CLOSE_SHIFT",machineId,employee,shift,actualMs,p);
    }

    public List<PendingEvent> pendingEvents(int limit){List<PendingEvent> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT event_id,event_type,machine_id,group_name,plan_group,employee,shift_name,event_ms,payload_json FROM events WHERE synced=0 ORDER BY event_ms LIMIT ?",new String[]{String.valueOf(limit)})){while(c.moveToNext()){try{JSONObject j=new JSONObject();j.put("event_id",c.getString(0));j.put("event_type",c.getString(1));j.put("machine_id",c.getString(2));j.put("group_name",c.getString(3));j.put("plan_group",c.getString(4));j.put("employee",c.getString(5));j.put("shift_name",c.getString(6));j.put("event_ms",c.getLong(7));j.put("payload",new JSONObject(c.getString(8)));out.add(new PendingEvent(c.getString(0),j.toString()));}catch(Exception ignored){}}}return out;}
    public void markSynced(String eventId){ContentValues v=new ContentValues();v.put("synced",1);getWritableDatabase().update("events",v,"event_id=?",new String[]{eventId});}
    public int pendingCount(){return(int)sum("SELECT COUNT(*) FROM events WHERE synced=0",new String[]{});}

    private void addEvent(String type,String machineId,String employee,String shift,long now,JSONObject payload)throws Exception{addEvent(getWritableDatabase(),type,machineId,employee,shift,now,payload);}
    private void addEvent(SQLiteDatabase db,String type,String machineId,String employee,String shift,long now,JSONObject payload)throws Exception{MachineState s=state(machineId);ContentValues v=new ContentValues();v.put("event_id",UUID.randomUUID().toString());v.put("event_type",type);v.put("machine_id",machineId);v.put("group_name",s.groupName);v.put("plan_group",s.planGroup);v.put("employee",clean(employee));v.put("shift_name",clean(shift));v.put("event_ms",now);v.put("payload_json",payload.toString());v.put("synced",0);db.insertOrThrow("events",null,v);}
    private long sum(String sql,String[] args){try(Cursor c=getReadableDatabase().rawQuery(sql,args)){return c.moveToFirst()?c.getLong(0):0;}}
    private static long number(String text){try{return Math.round(Double.parseDouble(clean(text).replace(",","")));}catch(Exception e){return 0;}}
    private static boolean isStopped(String status){return SETUP.equals(status)||PLANNED_STOP.equals(status)||UNPLANNED_STOP.equals(status);}
    private static String shiftKey(String shift,long now){java.util.Calendar c=java.util.Calendar.getInstance();c.setTimeInMillis(now);if("NIGHT".equalsIgnoreCase(clean(shift))&&c.get(java.util.Calendar.HOUR_OF_DAY)<12)c.add(java.util.Calendar.DAY_OF_MONTH,-1);java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US);return f.format(c.getTime())+"|"+shift;}
    private static String clean(String s){return s==null?"":s.trim();}
}
