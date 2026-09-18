package com.tskforging.mtsandroid;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class MultiMachineStore extends SQLiteOpenHelper {
    public static final String UNKNOWN="UNKNOWN", RUNNING="RUNNING", SETUP="SETUP";
    public static final String PLANNED_STOP="PLANNED_STOP", UNPLANNED_STOP="UNPLANNED_STOP", CLOSED="CLOSED";

    public static final class MachineState {
        public String machineId="",groupName="",planGroup="",status=UNKNOWN,employee="",shiftName="";
        public String item="",partNo="",lot="",reason="",detail="",bladeId="";
        public long ok,ng,startMs,stopStartMs,stopSec,updatedMs; public int scheduleType;
    }
    public static final class VerificationState {
        public String machineId="",shiftKey="",orderRaw="",greenRaw="",yellowRaw="",status="";
        public long updatedMs;
    }
    public static final class TagResult {
        public boolean accepted; public String message=""; public long tagQty,previousQty,thisShiftQty,ngQty;
    }
    public static final class PendingEvent {
        public final String eventId,json; PendingEvent(String id,String value){eventId=id;json=value;}
    }

    public MultiMachineStore(Context context){super(context,"mts_multi_v2.db",null,3);}

    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE machine_state(machine_id TEXT PRIMARY KEY,group_name TEXT,plan_group TEXT,status TEXT,employee TEXT,shift_name TEXT,item TEXT,part_no TEXT,lot TEXT,reason TEXT,detail TEXT,blade_id TEXT,ok_qty INTEGER DEFAULT 0,ng_qty INTEGER DEFAULT 0,start_ms INTEGER DEFAULT 0,stop_start_ms INTEGER DEFAULT 0,stop_sec INTEGER DEFAULT 0,updated_ms INTEGER DEFAULT 0,schedule_type INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE tag_history(id INTEGER PRIMARY KEY AUTOINCREMENT,machine_id TEXT,shift_key TEXT,order_no TEXT,process TEXT,item TEXT,part_no TEXT,part_name TEXT,lot TEXT,charge TEXT,tag_qty INTEGER,previous_qty INTEGER,this_qty INTEGER,ng_qty INTEGER DEFAULT 0,duplicate_key TEXT,raw_qr TEXT,confirmed_at INTEGER)");
        db.execSQL("CREATE UNIQUE INDEX idx_tag_shift_dup ON tag_history(machine_id,shift_key,duplicate_key)");
        db.execSQL("CREATE INDEX idx_tag_carry ON tag_history(order_no,process,item,lot)");
        db.execSQL("CREATE TABLE verification_state(machine_id TEXT,shift_key TEXT,order_raw TEXT DEFAULT '',green_raw TEXT DEFAULT '',yellow_raw TEXT DEFAULT '',status TEXT DEFAULT '',updated_ms INTEGER DEFAULT 0,PRIMARY KEY(machine_id,shift_key))");
        db.execSQL("CREATE TABLE events(event_id TEXT PRIMARY KEY,event_type TEXT,machine_id TEXT,group_name TEXT,plan_group TEXT,employee TEXT,shift_name TEXT,event_ms INTEGER,payload_json TEXT,synced INTEGER DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_events_sync ON events(synced,event_ms)"); seedMachines(db);
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion<2){db.execSQL("ALTER TABLE machine_state ADD COLUMN schedule_type INTEGER DEFAULT 0");db.execSQL("ALTER TABLE tag_history ADD COLUMN order_no TEXT DEFAULT ''");db.execSQL("ALTER TABLE tag_history ADD COLUMN ng_qty INTEGER DEFAULT 0");}
        if(oldVersion<3)db.execSQL("CREATE TABLE IF NOT EXISTS verification_state(machine_id TEXT,shift_key TEXT,order_raw TEXT DEFAULT '',green_raw TEXT DEFAULT '',yellow_raw TEXT DEFAULT '',status TEXT DEFAULT '',updated_ms INTEGER DEFAULT 0,PRIMARY KEY(machine_id,shift_key))");
    }
    @Override public void onOpen(SQLiteDatabase db){super.onOpen(db);seedMachines(db);}
    private void seedMachines(SQLiteDatabase db){for(MachineCatalog.Machine m:MachineCatalog.all()){ContentValues v=new ContentValues();v.put("machine_id",m.id);v.put("group_name",m.group);v.put("plan_group",m.planGroup);v.put("status",UNKNOWN);db.insertWithOnConflict("machine_state",null,v,SQLiteDatabase.CONFLICT_IGNORE);}}

    private static final String STATE_COLS="machine_id,group_name,plan_group,status,employee,shift_name,item,part_no,lot,reason,detail,blade_id,ok_qty,ng_qty,start_ms,stop_start_ms,stop_sec,updated_ms,schedule_type";
    public MachineState state(String machineId){try(Cursor c=getReadableDatabase().rawQuery("SELECT "+STATE_COLS+" FROM machine_state WHERE machine_id=?",new String[]{machineId})){if(c.moveToFirst())return readState(c);}return new MachineState();}
    public List<MachineState> states(String group){List<MachineState> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT "+STATE_COLS+" FROM machine_state WHERE group_name=? ORDER BY machine_id",new String[]{group})){while(c.moveToNext())out.add(readState(c));}return out;}
    private MachineState readState(Cursor c){MachineState s=new MachineState();s.machineId=clean(c.getString(0));s.groupName=clean(c.getString(1));s.planGroup=clean(c.getString(2));s.status=clean(c.getString(3));s.employee=clean(c.getString(4));s.shiftName=clean(c.getString(5));s.item=clean(c.getString(6));s.partNo=clean(c.getString(7));s.lot=clean(c.getString(8));s.reason=clean(c.getString(9));s.detail=clean(c.getString(10));s.bladeId=clean(c.getString(11));s.ok=c.getLong(12);s.ng=c.getLong(13);s.startMs=c.getLong(14);s.stopStartMs=c.getLong(15);s.stopSec=c.getLong(16);s.updatedMs=c.getLong(17);s.scheduleType=c.getInt(18);return s;}

    public void assignEmployee(String machineId,String employee,String shift,long now)throws Exception{requireMachine(machineId);ContentValues v=new ContentValues();v.put("employee",clean(employee));v.put("updated_ms",now);getWritableDatabase().update("machine_state",v,"machine_id=?",new String[]{machineId});JSONObject p=new JSONObject();p.put("employee",clean(employee));p.put("assigned_shift",shift);addEvent("EMPLOYEE",machineId,employee,shift,now,p);}
    public void setSchedule(String machineId,int type,String employee,String shift,long now)throws Exception{if(type<1||type>3)throw new IllegalArgumentException("ตารางกะไม่ถูกต้อง");ContentValues v=new ContentValues();v.put("schedule_type",type);v.put("updated_ms",now);getWritableDatabase().update("machine_state",v,"machine_id=?",new String[]{machineId});JSONObject p=new JSONObject();p.put("schedule_type",type);p.put("schedule_name",scheduleName(type));addEvent("SCHEDULE",machineId,employee,shift,now,p);}

    public void setStatus(String machineId,String status,String reason,String detail,String employee,String shift,long now)throws Exception{setStatus(machineId,status,reason,detail,employee,shift,now,now);}
    public void setStatus(String machineId,String status,String reason,String detail,String employee,String shift,long actualMs,long effectiveMs)throws Exception{
        MachineState old=requireMachine(machineId);long stop=old.stopSec,start=old.stopStartMs;
        if(isStopped(old.status)&&start>0&&!isStopped(status)){stop+=Math.max(0,(actualMs-start)/1000);start=0;}
        if(isStopped(status)&&start==0)start=actualMs;
        boolean fresh=RUNNING.equals(status)&&(old.startMs==0||CLOSED.equals(old.status)||!clean(shift).equals(old.shiftName));
        ContentValues v=new ContentValues();v.put("status",status);v.put("reason",clean(reason));v.put("detail",clean(detail));v.put("employee",clean(employee));v.put("shift_name",clean(shift));v.put("updated_ms",actualMs);v.put("stop_start_ms",start);v.put("stop_sec",stop);
        if(fresh){v.put("ok_qty",0);v.put("ng_qty",0);v.put("start_ms",effectiveMs);v.put("stop_sec",0);v.put("stop_start_ms",0);v.put("item","");v.put("part_no","");v.put("lot","");}else if(RUNNING.equals(status)&&old.startMs==0)v.put("start_ms",effectiveMs);
        getWritableDatabase().update("machine_state",v,"machine_id=?",new String[]{machineId});JSONObject p=new JSONObject();p.put("status",status);p.put("reason",clean(reason));p.put("detail",clean(detail));p.put("actual_ms",actualMs);p.put("effective_ms",effectiveMs);addEvent("STATUS",machineId,employee,shift,actualMs,p);
    }

    public VerificationState verification(String machineId,String key){VerificationState v=new VerificationState();v.machineId=machineId;v.shiftKey=key;try(Cursor c=getReadableDatabase().rawQuery("SELECT order_raw,green_raw,yellow_raw,status,updated_ms FROM verification_state WHERE machine_id=? AND shift_key=?",new String[]{machineId,key})){if(c.moveToFirst()){v.orderRaw=clean(c.getString(0));v.greenRaw=clean(c.getString(1));v.yellowRaw=clean(c.getString(2));v.status=clean(c.getString(3));v.updatedMs=c.getLong(4);}}return v;}
    public void saveVerificationScan(String machineId,String key,String kind,String raw,long now){ContentValues v=new ContentValues();v.put("machine_id",machineId);v.put("shift_key",key);v.put(kind+"_raw",clean(raw));v.put("status","PENDING");v.put("updated_ms",now);getWritableDatabase().insertWithOnConflict("verification_state",null,v,SQLiteDatabase.CONFLICT_IGNORE);getWritableDatabase().update("verification_state",v,"machine_id=? AND shift_key=?",new String[]{machineId,key});}
    public void saveVerificationResult(String machineId,String key,boolean pass,String message,String employee,String shift,long now)throws Exception{ContentValues v=new ContentValues();v.put("status",pass?"PASS":"FAIL");v.put("updated_ms",now);getWritableDatabase().update("verification_state",v,"machine_id=? AND shift_key=?",new String[]{machineId,key});JSONObject p=new JSONObject();p.put("result",pass?"PASS":"FAIL");p.put("message",message);addEvent("MATERIAL_VERIFY",machineId,employee,shift,now,p);}

    public TagResult recordTag(String machineId,String employee,String shift,TagParser.ResultTag tag,long ngQty,String ngReason,long now)throws Exception{
        TagResult r=new TagResult();if(tag==null||!tag.isValid()){r.message="ไม่ใช่ WIP/FG Tag ที่รองรับ";return r;}if(ngQty<0){r.message="จำนวน NG ไม่ถูกต้อง";return r;}if(ngQty>0&&clean(ngReason).isEmpty()){r.message="กรุณาระบุสาเหตุ NG";return r;}
        MachineState ms=state(machineId);if(!RUNNING.equals(ms.status)){r.message="กรุณากดเริ่มผลิตก่อนสแกน Tag";return r;}String key=shiftKey(shift,now);long qty=number(tag.qty);long previous=sum("SELECT COALESCE(SUM(this_qty),0) FROM tag_history WHERE order_no=? AND process=? AND item=? AND lot=? AND shift_key<>?",new String[]{tag.order,tag.process,tag.item,tag.lot,key});long thisQty=Math.max(0,qty-previous);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{ContentValues v=new ContentValues();v.put("machine_id",machineId);v.put("shift_key",key);v.put("order_no",tag.order);v.put("process",tag.process);v.put("item",tag.item);v.put("part_no",tag.partNo);v.put("part_name",tag.partName);v.put("lot",tag.lot);v.put("charge",tag.charge);v.put("tag_qty",qty);v.put("previous_qty",previous);v.put("this_qty",thisQty);v.put("ng_qty",ngQty);v.put("duplicate_key",tag.duplicateKey());v.put("raw_qr",tag.raw);v.put("confirmed_at",now);if(db.insert("tag_history",null,v)<0){r.message="Tag ซ้ำในเครื่องและกะนี้";return r;}
            ContentValues u=new ContentValues();u.put("item",tag.item);u.put("part_no",tag.partNo);u.put("lot",tag.lot);u.put("ok_qty",ms.ok+thisQty);u.put("ng_qty",ms.ng+ngQty);u.put("employee",employee);u.put("shift_name",shift);u.put("updated_ms",now);db.update("machine_state",u,"machine_id=?",new String[]{machineId});
            JSONObject p=new JSONObject();p.put("order_no",tag.order);p.put("tag_type",tag.type.name());p.put("process",tag.process);p.put("item",tag.item);p.put("part_no",tag.partNo);p.put("part_name",tag.partName);p.put("lot",tag.lot);p.put("charge",tag.charge);p.put("tag_qty",qty);p.put("previous_qty",previous);p.put("this_qty",thisQty);p.put("ng_qty",ngQty);p.put("raw_qr",tag.raw);addEvent(db,"TAG",machineId,employee,shift,now,p);
            if(ngQty>0){JSONObject n=new JSONObject();n.put("qty",ngQty);n.put("reason",clean(ngReason));n.put("detail","บันทึกพร้อม Tag");addEvent(db,"NG",machineId,employee,shift,now,n);}db.setTransactionSuccessful();r.accepted=true;r.tagQty=qty;r.previousQty=previous;r.thisShiftQty=thisQty;r.ngQty=ngQty;r.message="บันทึกแล้ว";
        }finally{db.endTransaction();}return r;
    }
    public void addNg(String machineId,long qty,String reason,String detail,String employee,String shift,long now)throws Exception{if(qty<=0)throw new IllegalArgumentException("จำนวน NG ต้องมากกว่า 0");MachineState s=state(machineId);ContentValues u=new ContentValues();u.put("ng_qty",s.ng+qty);u.put("updated_ms",now);getWritableDatabase().update("machine_state",u,"machine_id=?",new String[]{machineId});JSONObject p=new JSONObject();p.put("qty",qty);p.put("reason",reason);p.put("detail",detail);addEvent("NG",machineId,employee,shift,now,p);}
    public void changeBlade(String machineId,String bladeId,long toolLifeQty,String reason,String employee,String shift,long now)throws Exception{MachineState s=state(machineId);ContentValues u=new ContentValues();u.put("blade_id",clean(bladeId));u.put("updated_ms",now);getWritableDatabase().update("machine_state",u,"machine_id=?",new String[]{machineId});JSONObject p=new JSONObject();p.put("old_blade",s.bladeId);p.put("new_blade",clean(bladeId));p.put("reason",clean(reason));p.put("tool_life_qty",toolLifeQty);p.put("tool_life_unit","pieces");addEvent("BLADE_CHANGE",machineId,employee,shift,now,p);}
    public void recordAttendance(String shift,long total,long sick,long personal,long vacation,long outside,String outsideReason,long now)throws Exception{JSONObject p=new JSONObject();p.put("total",total);p.put("sick",sick);p.put("personal",personal);p.put("vacation",vacation);p.put("outside",outside);p.put("outside_reason",clean(outsideReason));p.put("available",Math.max(0,total-sick-personal-vacation-outside));addSpecialEvent("ATTENDANCE","SHIFT_ATTENDANCE","","","",shift,now,p);}

    public void closeMachine(String machineId,long lastOk,long lastNg,String ngReason,int coffeeCount,boolean meal,boolean otBreak,String employee,String shift,long actualMs,long effectiveMs,String closeReason,int coffeeMinutes,int mealMinutes,int otBreakMinutes,long standardSec)throws Exception{
        if(lastNg>0&&clean(ngReason).isEmpty())throw new IllegalArgumentException("กรุณาระบุสาเหตุ NG");MachineState s=state(machineId);long finalStop=s.stopSec+(s.stopStartMs>0?Math.max(0,(actualMs-s.stopStartMs)/1000):0);long breaks=Math.max(0,coffeeCount)*(long)Math.max(0,coffeeMinutes)*60L+(meal?(long)Math.max(0,mealMinutes)*60L:0)+(otBreak?(long)Math.max(0,otBreakMinutes)*60L:0);long elapsed=s.startMs>0?Math.max(0,(effectiveMs-s.startMs)/1000):0;long capacity=Math.min(Math.max(0,standardSec),elapsed);long working=Math.max(0,capacity-finalStop-breaks);
        ContentValues u=new ContentValues();u.put("status",CLOSED);u.put("ok_qty",s.ok+Math.max(0,lastOk));u.put("ng_qty",s.ng+Math.max(0,lastNg));u.put("updated_ms",actualMs);u.put("reason",clean(closeReason));u.put("detail","");u.put("stop_start_ms",0);u.put("stop_sec",finalStop);getWritableDatabase().update("machine_state",u,"machine_id=?",new String[]{machineId});JSONObject p=new JSONObject();p.put("last_ok",Math.max(0,lastOk));p.put("last_ng",Math.max(0,lastNg));p.put("ng_reason",clean(ngReason));p.put("break_sec",breaks);p.put("working_sec",working);p.put("stop_sec",finalStop);p.put("standard_sec",standardSec);p.put("schedule_type",s.scheduleType);p.put("total_ok",s.ok+Math.max(0,lastOk));p.put("total_ng",s.ng+Math.max(0,lastNg));p.put("close_reason",clean(closeReason));p.put("actual_close_ms",actualMs);p.put("effective_close_ms",effectiveMs);addEvent("CLOSE_SHIFT",machineId,employee,shift,actualMs,p);
    }

    public List<PendingEvent> pendingEvents(int limit){List<PendingEvent> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT event_id,event_type,machine_id,group_name,plan_group,employee,shift_name,event_ms,payload_json FROM events WHERE synced=0 ORDER BY event_ms LIMIT ?",new String[]{String.valueOf(limit)})){while(c.moveToNext()){try{JSONObject j=new JSONObject();j.put("event_id",c.getString(0));j.put("event_type",c.getString(1));j.put("machine_id",c.getString(2));j.put("group_name",c.getString(3));j.put("plan_group",c.getString(4));j.put("employee",c.getString(5));j.put("shift_name",c.getString(6));j.put("event_ms",c.getLong(7));j.put("payload",new JSONObject(c.getString(8)));out.add(new PendingEvent(c.getString(0),j.toString()));}catch(Exception ignored){}}}return out;}
    public void markSynced(String id){ContentValues v=new ContentValues();v.put("synced",1);getWritableDatabase().update("events",v,"event_id=?",new String[]{id});}
    public int pendingCount(){return (int)sum("SELECT COUNT(*) FROM events WHERE synced=0",new String[]{});}

    private MachineState requireMachine(String id){MachineState s=state(id);if(s.machineId.isEmpty())throw new IllegalArgumentException("ไม่พบเครื่องจักร");return s;}
    private void addEvent(String type,String machineId,String employee,String shift,long now,JSONObject payload)throws Exception{MachineState s=state(machineId);addSpecialEvent(type,machineId,s.groupName,s.planGroup,employee,shift,now,payload);}
    private void addEvent(SQLiteDatabase db,String type,String machineId,String employee,String shift,long now,JSONObject payload)throws Exception{MachineState s=state(machineId);insertEvent(db,type,machineId,s.groupName,s.planGroup,employee,shift,now,payload);}
    private void addSpecialEvent(String type,String machineId,String group,String plan,String employee,String shift,long now,JSONObject payload)throws Exception{insertEvent(getWritableDatabase(),type,machineId,group,plan,employee,shift,now,payload);}
    private void insertEvent(SQLiteDatabase db,String type,String machineId,String group,String plan,String employee,String shift,long now,JSONObject payload)throws Exception{ContentValues v=new ContentValues();v.put("event_id",UUID.randomUUID().toString());v.put("event_type",type);v.put("machine_id",machineId);v.put("group_name",group);v.put("plan_group",plan);v.put("employee",clean(employee));v.put("shift_name",clean(shift));v.put("event_ms",now);v.put("payload_json",payload.toString());v.put("synced",0);db.insertOrThrow("events",null,v);}
    private long sum(String sql,String[] args){try(Cursor c=getReadableDatabase().rawQuery(sql,args)){return c.moveToFirst()?c.getLong(0):0;}}
    private static long number(String s){try{return Math.round(Double.parseDouble(clean(s).replace(",","")));}catch(Exception e){return 0;}}
    private static boolean isStopped(String s){return SETUP.equals(s)||PLANNED_STOP.equals(s)||UNPLANNED_STOP.equals(s);}
    public static String shiftKey(String shift,long now){Calendar c=Calendar.getInstance();c.setTimeInMillis(now);if("NIGHT".equalsIgnoreCase(clean(shift))&&c.get(Calendar.HOUR_OF_DAY)<12)c.add(Calendar.DAY_OF_MONTH,-1);return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(c.getTime())+"|"+shift;}
    public static String scheduleName(int type){return type==1?"ไม่มี OT":type==2?"OT แบบที่ 1":type==3?"OT แบบที่ 2":"ยังไม่เลือก";}
    private static String clean(String s){return s==null?"":s.trim();}
}
