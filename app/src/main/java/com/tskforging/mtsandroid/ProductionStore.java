package com.tskforging.mtsandroid;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ProductionStore extends SQLiteOpenHelper {
    public static final class QtyResult {
        public long tagQty, previousQty, thisShiftQty;
        public String item, lot, process;
    }
    public static final class Totals { public long ok,ng,workingSec,stopSec; }
    public static final class Summary {
        public String shiftId="",shift="",employee="",machine="";
        public String startReason="",closeReason="";
        public long startMs,actualStartMs,closeMs,actualCloseMs,ok,ng,workingSec,stopSec,otSec,totalBreakSec;
        public int coffeeCount,mealTaken,otBreakTaken;
    }

    private final SharedPreferences prefs;

    public ProductionStore(Context context){
        super(context,"mts_production.db",null,3);
        prefs=context.getSharedPreferences("mts_production_state",Context.MODE_PRIVATE);
    }

    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE shifts(id TEXT PRIMARY KEY,shift_name TEXT,employee TEXT,machine TEXT,start_ms INTEGER,actual_start_ms INTEGER,close_ms INTEGER,actual_close_ms INTEGER,status TEXT,start_reason TEXT,close_reason TEXT,coffee_count INTEGER DEFAULT 0,meal_taken INTEGER DEFAULT 0,ot_break_taken INTEGER DEFAULT 0,total_break_sec INTEGER DEFAULT 0,ot_sec INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE lots(id INTEGER PRIMARY KEY AUTOINCREMENT,shift_id TEXT,process TEXT,item TEXT,lot TEXT,tag_qty INTEGER,previous_qty INTEGER,this_qty INTEGER,raw_qr TEXT,confirmed_at INTEGER)");
        db.execSQL("CREATE TABLE ng_events(id INTEGER PRIMARY KEY AUTOINCREMENT,shift_id TEXT,item TEXT,lot TEXT,qty INTEGER,reason TEXT,event_ms INTEGER)");
        db.execSQL("CREATE TABLE stop_events(id INTEGER PRIMARY KEY AUTOINCREMENT,shift_id TEXT,reason TEXT,start_ms INTEGER,end_ms INTEGER,duration_sec INTEGER)");
        db.execSQL("CREATE TABLE carryover(item TEXT PRIMARY KEY,lot TEXT,previous_ok INTEGER,updated_ms INTEGER)");
        db.execSQL("CREATE TABLE tool_events(id INTEGER PRIMARY KEY AUTOINCREMENT,shift_id TEXT,machine TEXT,old_tool TEXT,new_tool TEXT,old_life INTEGER,reason TEXT,event_ms INTEGER)");
        db.execSQL("CREATE INDEX idx_lots_shift ON lots(shift_id)");
        db.execSQL("CREATE INDEX idx_ng_shift ON ng_events(shift_id)");
        db.execSQL("CREATE INDEX idx_stop_shift ON stop_events(shift_id)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion<2){
            String[] sql={"ALTER TABLE shifts ADD COLUMN actual_start_ms INTEGER DEFAULT 0","ALTER TABLE shifts ADD COLUMN actual_close_ms INTEGER DEFAULT 0","ALTER TABLE shifts ADD COLUMN start_reason TEXT DEFAULT ''","ALTER TABLE shifts ADD COLUMN close_reason TEXT DEFAULT ''","ALTER TABLE shifts ADD COLUMN coffee_count INTEGER DEFAULT 0","ALTER TABLE shifts ADD COLUMN meal_taken INTEGER DEFAULT 0","ALTER TABLE shifts ADD COLUMN ot_break_taken INTEGER DEFAULT 0","ALTER TABLE shifts ADD COLUMN total_break_sec INTEGER DEFAULT 0","ALTER TABLE shifts ADD COLUMN ot_sec INTEGER DEFAULT 0"};
            for(String q:sql)db.execSQL(q);
        }
        if(oldVersion<3)db.execSQL("CREATE TABLE IF NOT EXISTS tool_events(id INTEGER PRIMARY KEY AUTOINCREMENT,shift_id TEXT,machine TEXT,old_tool TEXT,new_tool TEXT,old_life INTEGER,reason TEXT,event_ms INTEGER)");
    }

    public boolean hasActiveShift(){return prefs.getBoolean("active",false);}
    public String shiftId(){return prefs.getString("shiftId","");}
    public String shiftName(){return prefs.getString("shiftName","DAY");}
    public String employee(){return prefs.getString("employee","");}
    public String machine(){return prefs.getString("machine","");}
    public long startMs(){return prefs.getLong("startMs",0);}
    public String activeItem(){return prefs.getString("activeItem","");}
    public String activeLot(){return prefs.getString("activeLot","");}
    public boolean stopRunning(){return prefs.getLong("stopStart",0)>0;}
    public String stopReason(){return prefs.getString("stopReason","");}
    public long stopStart(){return prefs.getLong("stopStart",0);}

    public void startShift(String shift,String employee,String machine,long actual,long effective,String reason){
        String id=new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date(actual));
        ContentValues v=new ContentValues();v.put("id",id);v.put("shift_name",shift);v.put("employee",employee);v.put("machine",machine);
        v.put("start_ms",effective);v.put("actual_start_ms",actual);v.put("close_ms",0);v.put("actual_close_ms",0);v.put("start_reason",reason);v.put("status","OPEN");getWritableDatabase().insertOrThrow("shifts",null,v);
        prefs.edit().putBoolean("active",true).putString("shiftId",id).putString("shiftName",shift).putString("employee",employee)
                .putString("machine",machine).putLong("startMs",effective).putLong("actualStartMs",actual).putString("activeItem","").putString("activeLot","")
                .putLong("stopStart",0).putString("stopReason","").putBoolean("noOt",false).apply();
    }

    public void startShift(String shift,String employee,String machine,long now){startShift(shift,employee,machine,now,now,"");}

    public QtyResult confirmTag(TagParser.ResultTag tag,long now){
        QtyResult r=new QtyResult();r.item=tag.item;r.lot=tag.lot;r.process=tag.process;r.tagQty=parseQty(tag.qty);
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            long previous=0;
            try(Cursor c=db.rawQuery("SELECT previous_ok FROM carryover WHERE UPPER(TRIM(item))=UPPER(TRIM(?)) LIMIT 1",new String[]{tag.item})){
                if(c.moveToFirst())previous=Math.max(0,c.getLong(0));
            }
            r.previousQty=previous;r.thisShiftQty=Math.max(0,r.tagQty-previous);
            ContentValues v=new ContentValues();v.put("shift_id",shiftId());v.put("process",tag.process);v.put("item",tag.item);v.put("lot",tag.lot);
            v.put("tag_qty",r.tagQty);v.put("previous_qty",previous);v.put("this_qty",r.thisShiftQty);v.put("raw_qr",tag.raw);v.put("confirmed_at",now);
            db.insertOrThrow("lots",null,v);
            if(previous>0)db.delete("carryover","UPPER(TRIM(item))=UPPER(TRIM(?))",new String[]{tag.item});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        prefs.edit().putString("activeItem",tag.item).putString("activeLot",tag.lot).apply();
        addToolLife(r.thisShiftQty);
        return r;
    }

    public long previousForItem(String item){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT previous_ok FROM carryover WHERE UPPER(TRIM(item))=UPPER(TRIM(?)) LIMIT 1",new String[]{item})){
            return c.moveToFirst()?Math.max(0,c.getLong(0)):0;
        }
    }

    public long parsedTagQty(String value){return parseQty(value);}

    public void addNg(long qty,String reason,long now){
        if(qty<=0)throw new IllegalArgumentException("จำนวน NG ต้องมากกว่า 0 / NG Qty must be greater than zero");
        ContentValues v=new ContentValues();v.put("shift_id",shiftId());v.put("item",activeItem());v.put("lot",activeLot());v.put("qty",qty);v.put("reason",reason);v.put("event_ms",now);
        getWritableDatabase().insertOrThrow("ng_events",null,v);
    }

    public void startStop(String reason,long now){
        if(stopRunning())return;
        prefs.edit().putLong("stopStart",now).putString("stopReason",reason).apply();
    }
    public boolean noOt(){return prefs.getBoolean("noOt",false);}
    public void markNoOt(long now){
        ContentValues v=new ContentValues();v.put("shift_id",shiftId());v.put("reason","NO OT");v.put("start_ms",now);v.put("end_ms",now);v.put("duration_sec",0);getWritableDatabase().insertOrThrow("stop_events",null,v);
        prefs.edit().putBoolean("noOt",true).apply();
    }
    public void endStop(long now){
        long start=stopStart();if(start<=0)return;
        ContentValues v=new ContentValues();v.put("shift_id",shiftId());v.put("reason",stopReason());v.put("start_ms",start);v.put("end_ms",now);v.put("duration_sec",Math.max(0,(now-start)/1000));
        getWritableDatabase().insertOrThrow("stop_events",null,v);prefs.edit().putLong("stopStart",0).putString("stopReason","").apply();
    }

    public Totals totals(){
        Totals t=new Totals();String id=shiftId();if(id.isEmpty())return t;
        t.ok=sum("SELECT COALESCE(SUM(this_qty),0) FROM lots WHERE shift_id=?",id);
        t.ng=sum("SELECT COALESCE(SUM(qty),0) FROM ng_events WHERE shift_id=?",id);
        t.stopSec=sum("SELECT COALESCE(SUM(duration_sec),0) FROM stop_events WHERE shift_id=?",id);
        if(stopRunning())t.stopSec+=Math.max(0,(System.currentTimeMillis()-stopStart())/1000);
        t.workingSec=Math.max(0,(System.currentTimeMillis()-startMs())/1000-t.stopSec);
        return t;
    }

    public Summary closeShift(long lastOk,long lastNg,String ngReason,long actualClose,long effectiveClose,String closeReason,int coffeeCount,int mealTaken,int otBreakTaken,int coffeeMin,int mealMin,int otBreakMin){
        if(stopRunning())endStop(actualClose);
        String item=activeItem(),lot=activeLot(),id=shiftId();
        if((lastOk>0||lastNg>0)&&(item.isEmpty()||lot.isEmpty()))throw new IllegalStateException("กรุณาสแกน Tag ก่อนระบุจำนวน Lot สุดท้าย / Scan a Tag before entering Last Lot Qty");
        if(lastOk>0){
            ContentValues v=new ContentValues();v.put("shift_id",id);v.put("process","LAST LOT");v.put("item",item);v.put("lot",lot);v.put("tag_qty",lastOk);v.put("previous_qty",0);v.put("this_qty",lastOk);v.put("raw_qr","");v.put("confirmed_at",actualClose);
            getWritableDatabase().insertOrThrow("lots",null,v);
            ContentValues c=new ContentValues();c.put("item",item);c.put("lot",lot);c.put("previous_ok",lastOk);c.put("updated_ms",actualClose);
            getWritableDatabase().insertWithOnConflict("carryover",null,c,SQLiteDatabase.CONFLICT_REPLACE);addToolLife(lastOk);
        }
        if(lastNg>0){if(ngReason==null||ngReason.trim().isEmpty())throw new IllegalArgumentException("กรุณาระบุสาเหตุ NG / NG Reason is required");addNg(lastNg,ngReason,actualClose);}
        long breakSec=(long)Math.max(0,coffeeCount)*coffeeMin*60L+(mealTaken==1?mealMin*60L:0)+(otBreakTaken==1?otBreakMin*60L:0);
        long otSec=noOt()?0:Math.max(0,(effectiveClose-startMs())/1000-9*3600L);
        ContentValues u=new ContentValues();u.put("close_ms",effectiveClose);u.put("actual_close_ms",actualClose);u.put("close_reason",closeReason);u.put("coffee_count",coffeeCount);u.put("meal_taken",mealTaken);u.put("ot_break_taken",otBreakTaken);u.put("total_break_sec",breakSec);u.put("ot_sec",otSec);u.put("status","CLOSED");getWritableDatabase().update("shifts",u,"id=?",new String[]{id});
        Summary s=summary(id);saveLastSummary(s);prefs.edit().putBoolean("active",false).putLong("stopStart",0).apply();return s;
    }

    public Summary closeShift(long lastOk,long lastNg,String ngReason,long now){return closeShift(lastOk,lastNg,ngReason,now,now,"",0,0,0,10,60,20);}

    public Summary summary(String id){
        Summary s=new Summary();s.shiftId=id;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT shift_name,employee,machine,start_ms,actual_start_ms,close_ms,actual_close_ms,start_reason,close_reason,coffee_count,meal_taken,ot_break_taken,total_break_sec,ot_sec FROM shifts WHERE id=?",new String[]{id})){
            if(c.moveToFirst()){s.shift=c.getString(0);s.employee=c.getString(1);s.machine=c.getString(2);s.startMs=c.getLong(3);s.actualStartMs=c.getLong(4);s.closeMs=c.getLong(5);s.actualCloseMs=c.getLong(6);s.startReason=c.getString(7);s.closeReason=c.getString(8);s.coffeeCount=c.getInt(9);s.mealTaken=c.getInt(10);s.otBreakTaken=c.getInt(11);s.totalBreakSec=c.getLong(12);s.otSec=c.getLong(13);}
        }
        s.ok=sum("SELECT COALESCE(SUM(this_qty),0) FROM lots WHERE shift_id=?",id);s.ng=sum("SELECT COALESCE(SUM(qty),0) FROM ng_events WHERE shift_id=?",id);
        s.stopSec=sum("SELECT COALESCE(SUM(duration_sec),0) FROM stop_events WHERE shift_id=?",id);long end=s.closeMs>0?s.closeMs:System.currentTimeMillis();s.workingSec=Math.max(0,(end-s.startMs)/1000-s.stopSec-s.totalBreakSec);return s;
    }

    public Summary lastSummary(){
        String id=prefs.getString("lastShiftId","");return id.isEmpty()?new Summary():summary(id);
    }
    private void saveLastSummary(Summary s){prefs.edit().putString("lastShiftId",s.shiftId).apply();}

    public long toolLife(){return prefs.getLong("toolLife",0);}
    public String toolCode(){return prefs.getString("toolCode","ยังไม่ตั้งค่า / NOT SET");}
    public String toolType(){return prefs.getString("toolType","SAW");}
    public void installTool(String code,String type){prefs.edit().putString("toolCode",code.trim()).putString("toolType",type.trim()).putLong("toolLife",0).apply();}
    public void changeBlade(String newCode,String reason,long now){
        if(newCode==null||newCode.trim().isEmpty())throw new IllegalArgumentException("กรุณาสแกน QR Blade / Blade QR is required");
        ContentValues v=new ContentValues();v.put("shift_id",shiftId());v.put("machine",machine());v.put("old_tool",toolCode());v.put("new_tool",newCode.trim());v.put("old_life",toolLife());v.put("reason",reason);v.put("event_ms",now);getWritableDatabase().insertOrThrow("tool_events",null,v);
        installTool(newCode,"BLADE");
    }
    private void addToolLife(long qty){prefs.edit().putLong("toolLife",toolLife()+Math.max(0,qty)).apply();}

    public List<String[]> reportRows(String id){
        List<String[]> rows=new ArrayList<>();Summary s=summary(id);String base=s.shift+"|"+s.employee+"|"+s.machine;
        rows.add(new String[]{"SHIFT",fmt(s.actualCloseMs>0?s.actualCloseMs:s.closeMs),id,base,"","","",String.valueOf(s.ok),"OK="+s.ok+"; NG="+s.ng+"; WorkingSec="+s.workingSec+"; StopSec="+s.stopSec+"; OTSec="+s.otSec+"; BreakSec="+s.totalBreakSec+"; Coffee="+s.coffeeCount+"; Meal="+s.mealTaken+"; OTBreak="+s.otBreakTaken+"; StartReason="+s.startReason+"; CloseReason="+s.closeReason,"",""});
        try(Cursor c=getReadableDatabase().rawQuery("SELECT confirmed_at,process,item,lot,tag_qty,previous_qty,this_qty,raw_qr FROM lots WHERE shift_id=? ORDER BY confirmed_at",new String[]{id})){
            while(c.moveToNext())rows.add(new String[]{"TAG",fmt(c.getLong(0)),id,base,c.getString(2),c.getString(3),c.getString(1),String.valueOf(c.getLong(6)),"TagQty="+c.getLong(4)+"; Previous="+c.getLong(5),"",c.getString(7)});
        }
        try(Cursor c=getReadableDatabase().rawQuery("SELECT event_ms,item,lot,qty,reason FROM ng_events WHERE shift_id=? ORDER BY event_ms",new String[]{id})){
            while(c.moveToNext())rows.add(new String[]{"NG",fmt(c.getLong(0)),id,base,c.getString(1),c.getString(2),"",String.valueOf(c.getLong(3)),c.getString(4),"",""});
        }
        try(Cursor c=getReadableDatabase().rawQuery("SELECT start_ms,reason,duration_sec FROM stop_events WHERE shift_id=? ORDER BY start_ms",new String[]{id})){
            while(c.moveToNext())rows.add(new String[]{"STOP",fmt(c.getLong(0)),id,base,"","","","",c.getString(1),String.valueOf(c.getLong(2)),""});
        }
        try(Cursor c=getReadableDatabase().rawQuery("SELECT event_ms,old_tool,new_tool,old_life,reason FROM tool_events WHERE shift_id=? ORDER BY event_ms",new String[]{id})){
            while(c.moveToNext())rows.add(new String[]{"TOOL_CHANGE",fmt(c.getLong(0)),id,base,"","",c.getString(2),String.valueOf(c.getLong(3)),"Old="+c.getString(1)+"; Reason="+c.getString(4),"",""});
        }
        return rows;
    }

    private long sum(String sql,String id){try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{id})){return c.moveToFirst()?c.getLong(0):0;}}
    private static long parseQty(String value){try{return Math.max(0,Math.round(Double.parseDouble(value.replace(",","").trim())));}catch(Exception e){return 0;}}
    private static String fmt(long ms){return ms<=0?"":new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date(ms));}
}
