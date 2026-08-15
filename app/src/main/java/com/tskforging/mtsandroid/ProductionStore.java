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
    public static final String[] NG_REASONS={"Burr","Dent/Scratch","Dimension","Setting","Mat.Defect","Weight","Run-Out","Perpendicularity","Other"};
    public static final String[] STOP_REASONS={"Change Item","NO OT","5S","WAIT RAW MATERIAL","Training","No Worker","SET-UP","Machine Trouble","Blade Change","Other"};

    public static final class QtyResult {
        public long tagQty, previousQty, thisShiftQty;
        public String item, lot, process;
    }
    public static final class Totals { public long ok,ng,workingSec,stopSec; }
    public static final class Summary {
        public String shiftId="",shift="",employee="",machine="";
        public long startMs,closeMs,ok,ng,workingSec,stopSec;
    }

    private final SharedPreferences prefs;

    public ProductionStore(Context context){
        super(context,"mts_production.db",null,1);
        prefs=context.getSharedPreferences("mts_production_state",Context.MODE_PRIVATE);
    }

    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE shifts(id TEXT PRIMARY KEY,shift_name TEXT,employee TEXT,machine TEXT,start_ms INTEGER,close_ms INTEGER,status TEXT)");
        db.execSQL("CREATE TABLE lots(id INTEGER PRIMARY KEY AUTOINCREMENT,shift_id TEXT,process TEXT,item TEXT,lot TEXT,tag_qty INTEGER,previous_qty INTEGER,this_qty INTEGER,raw_qr TEXT,confirmed_at INTEGER)");
        db.execSQL("CREATE TABLE ng_events(id INTEGER PRIMARY KEY AUTOINCREMENT,shift_id TEXT,item TEXT,lot TEXT,qty INTEGER,reason TEXT,event_ms INTEGER)");
        db.execSQL("CREATE TABLE stop_events(id INTEGER PRIMARY KEY AUTOINCREMENT,shift_id TEXT,reason TEXT,start_ms INTEGER,end_ms INTEGER,duration_sec INTEGER)");
        db.execSQL("CREATE TABLE carryover(item TEXT PRIMARY KEY,lot TEXT,previous_ok INTEGER,updated_ms INTEGER)");
        db.execSQL("CREATE INDEX idx_lots_shift ON lots(shift_id)");
        db.execSQL("CREATE INDEX idx_ng_shift ON ng_events(shift_id)");
        db.execSQL("CREATE INDEX idx_stop_shift ON stop_events(shift_id)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){}

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

    public void startShift(String shift,String employee,String machine,long now){
        String id=new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date(now));
        ContentValues v=new ContentValues();v.put("id",id);v.put("shift_name",shift);v.put("employee",employee);v.put("machine",machine);
        v.put("start_ms",now);v.put("close_ms",0);v.put("status","OPEN");getWritableDatabase().insertOrThrow("shifts",null,v);
        prefs.edit().putBoolean("active",true).putString("shiftId",id).putString("shiftName",shift).putString("employee",employee)
                .putString("machine",machine).putLong("startMs",now).putString("activeItem","").putString("activeLot","")
                .putLong("stopStart",0).putString("stopReason","").apply();
    }

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
        if(qty<=0)throw new IllegalArgumentException("NG Qty must be greater than zero");
        ContentValues v=new ContentValues();v.put("shift_id",shiftId());v.put("item",activeItem());v.put("lot",activeLot());v.put("qty",qty);v.put("reason",reason);v.put("event_ms",now);
        getWritableDatabase().insertOrThrow("ng_events",null,v);
    }

    public void startStop(String reason,long now){
        if(stopRunning())return;
        prefs.edit().putLong("stopStart",now).putString("stopReason",reason).apply();
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

    public Summary closeShift(long lastOk,long lastNg,String ngReason,long now){
        if(stopRunning())endStop(now);
        String item=activeItem(),lot=activeLot(),id=shiftId();
        if((lastOk>0||lastNg>0)&&(item.isEmpty()||lot.isEmpty()))throw new IllegalStateException("Scan a Tag before entering Last Lot Qty");
        if(lastOk>0){
            ContentValues v=new ContentValues();v.put("shift_id",id);v.put("process","LAST LOT");v.put("item",item);v.put("lot",lot);v.put("tag_qty",lastOk);v.put("previous_qty",0);v.put("this_qty",lastOk);v.put("raw_qr","");v.put("confirmed_at",now);
            getWritableDatabase().insertOrThrow("lots",null,v);
            ContentValues c=new ContentValues();c.put("item",item);c.put("lot",lot);c.put("previous_ok",lastOk);c.put("updated_ms",now);
            getWritableDatabase().insertWithOnConflict("carryover",null,c,SQLiteDatabase.CONFLICT_REPLACE);addToolLife(lastOk);
        }
        if(lastNg>0){if(ngReason==null||ngReason.trim().isEmpty())throw new IllegalArgumentException("NG Reason is required");addNg(lastNg,ngReason,now);}
        ContentValues u=new ContentValues();u.put("close_ms",now);u.put("status","CLOSED");getWritableDatabase().update("shifts",u,"id=?",new String[]{id});
        Summary s=summary(id);saveLastSummary(s);prefs.edit().putBoolean("active",false).putLong("stopStart",0).apply();return s;
    }

    public Summary summary(String id){
        Summary s=new Summary();s.shiftId=id;
        try(Cursor c=getReadableDatabase().rawQuery("SELECT shift_name,employee,machine,start_ms,close_ms FROM shifts WHERE id=?",new String[]{id})){
            if(c.moveToFirst()){s.shift=c.getString(0);s.employee=c.getString(1);s.machine=c.getString(2);s.startMs=c.getLong(3);s.closeMs=c.getLong(4);}
        }
        s.ok=sum("SELECT COALESCE(SUM(this_qty),0) FROM lots WHERE shift_id=?",id);s.ng=sum("SELECT COALESCE(SUM(qty),0) FROM ng_events WHERE shift_id=?",id);
        s.stopSec=sum("SELECT COALESCE(SUM(duration_sec),0) FROM stop_events WHERE shift_id=?",id);long end=s.closeMs>0?s.closeMs:System.currentTimeMillis();s.workingSec=Math.max(0,(end-s.startMs)/1000-s.stopSec);return s;
    }

    public Summary lastSummary(){
        String id=prefs.getString("lastShiftId","");return id.isEmpty()?new Summary():summary(id);
    }
    private void saveLastSummary(Summary s){prefs.edit().putString("lastShiftId",s.shiftId).apply();}

    public long toolLife(){return prefs.getLong("toolLife",0);}
    public String toolCode(){return prefs.getString("toolCode","NOT SET");}
    public String toolType(){return prefs.getString("toolType","SAW");}
    public void installTool(String code,String type){prefs.edit().putString("toolCode",code.trim()).putString("toolType",type.trim()).putLong("toolLife",0).apply();}
    private void addToolLife(long qty){prefs.edit().putLong("toolLife",toolLife()+Math.max(0,qty)).apply();}

    public List<String[]> reportRows(String id){
        List<String[]> rows=new ArrayList<>();Summary s=summary(id);String base=s.shift+"|"+s.employee+"|"+s.machine;
        rows.add(new String[]{"SHIFT",fmt(s.closeMs),id,base,"","","",String.valueOf(s.ok),"OK="+s.ok+"; NG="+s.ng+"; WorkingSec="+s.workingSec+"; StopSec="+s.stopSec,"",""});
        try(Cursor c=getReadableDatabase().rawQuery("SELECT confirmed_at,process,item,lot,tag_qty,previous_qty,this_qty,raw_qr FROM lots WHERE shift_id=? ORDER BY confirmed_at",new String[]{id})){
            while(c.moveToNext())rows.add(new String[]{"TAG",fmt(c.getLong(0)),id,base,c.getString(2),c.getString(3),c.getString(1),String.valueOf(c.getLong(6)),"TagQty="+c.getLong(4)+"; Previous="+c.getLong(5),"",c.getString(7)});
        }
        try(Cursor c=getReadableDatabase().rawQuery("SELECT event_ms,item,lot,qty,reason FROM ng_events WHERE shift_id=? ORDER BY event_ms",new String[]{id})){
            while(c.moveToNext())rows.add(new String[]{"NG",fmt(c.getLong(0)),id,base,c.getString(1),c.getString(2),"",String.valueOf(c.getLong(3)),c.getString(4),"",""});
        }
        try(Cursor c=getReadableDatabase().rawQuery("SELECT start_ms,reason,duration_sec FROM stop_events WHERE shift_id=? ORDER BY start_ms",new String[]{id})){
            while(c.moveToNext())rows.add(new String[]{"STOP",fmt(c.getLong(0)),id,base,"","","","",c.getString(1),String.valueOf(c.getLong(2)),""});
        }
        return rows;
    }

    private long sum(String sql,String id){try(Cursor c=getReadableDatabase().rawQuery(sql,new String[]{id})){return c.moveToFirst()?c.getLong(0):0;}}
    private static long parseQty(String value){try{return Math.max(0,Math.round(Double.parseDouble(value.replace(",","").trim())));}catch(Exception e){return 0;}}
    private static String fmt(long ms){return ms<=0?"":new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date(ms));}
}
