package com.tskforging.mtsandroid;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public final class VerificationDb extends SQLiteOpenHelper {
    public VerificationDb(Context c){super(c,"mts_material_verification.db",null,1);}
    @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE verification_history(id INTEGER PRIMARY KEY AUTOINCREMENT,event_ms INTEGER,result TEXT,order_raw TEXT,green_raw TEXT,yellow_raw TEXT,detail TEXT)");}
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){}
    public void save(long ms,String result,String order,String green,String yellow,String detail){ContentValues v=new ContentValues();v.put("event_ms",ms);v.put("result",result);v.put("order_raw",order);v.put("green_raw",green);v.put("yellow_raw",yellow);v.put("detail",detail);getWritableDatabase().insertOrThrow("verification_history",null,v);}
}
