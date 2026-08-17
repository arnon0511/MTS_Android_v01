package com.tskforging.mtsandroid;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ConfigStore {
    private final SharedPreferences p;
    private static final String DEFAULT_NG="Burr|Dent/Scratch|Dimension|Setting|Mat.Defect|Weight|Run-Out|Perpendicularity|Other";
    private static final String DEFAULT_STOP="Change Item|NO OT|5S|WAIT RAW MATERIAL|Training|No Worker|SET-UP|Machine Trouble|Blade Change|Other";
    private static final String DEFAULT_SHIFT="No Plan|5S|Other";
    private static final String DEFAULT_CLOSE="No Plan|OT Finish|Other";

    public ConfigStore(Context c){p=c.getSharedPreferences("mts_management",Context.MODE_PRIVATE);}
    public String dayStart(){return p.getString("dayStart","08:00");}
    public String dayClose(){return p.getString("dayClose","17:00");}
    public String nightStart(){return p.getString("nightStart","20:00");}
    public String nightClose(){return p.getString("nightClose","05:00");}
    public int startTolerance(){return p.getInt("startTolerance",60);}
    public int closeEarlyTolerance(){return p.getInt("closeEarlyTolerance",30);}
    public int coffeeMinutes(){return p.getInt("coffeeMinutes",10);}
    public int mealMinutes(){return p.getInt("mealMinutes",60);}
    public int otBreakMinutes(){return p.getInt("otBreakMinutes",20);}
    public String[] ngReasons(){return split(p.getString("ngReasons",DEFAULT_NG));}
    public String[] stopReasons(){return split(p.getString("stopReasons",DEFAULT_STOP));}
    public String[] startReasons(){return split(p.getString("startReasons",DEFAULT_SHIFT));}
    public String[] closeReasons(){return split(p.getString("closeReasons",DEFAULT_CLOSE));}
    public void save(String ds,String dc,String ns,String nc,int startTol,int closeTol,int coffee,int meal,int ot,String ng,String stop,String start,String close){
        p.edit().putString("dayStart",validTime(ds,"08:00")).putString("dayClose",validTime(dc,"17:00"))
                .putString("nightStart",validTime(ns,"20:00")).putString("nightClose",validTime(nc,"05:00"))
                .putInt("startTolerance",Math.max(0,startTol)).putInt("closeEarlyTolerance",Math.max(0,closeTol))
                .putInt("coffeeMinutes",Math.max(0,coffee)).putInt("mealMinutes",Math.max(0,meal)).putInt("otBreakMinutes",Math.max(0,ot))
                .putString("ngReasons",joinClean(ng,DEFAULT_NG)).putString("stopReasons",joinClean(stop,DEFAULT_STOP))
                .putString("startReasons",joinClean(start,DEFAULT_SHIFT)).putString("closeReasons",joinClean(close,DEFAULT_CLOSE)).apply();
    }
    private static String[] split(String s){List<String> out=new ArrayList<>();for(String x:s.split("\\|")){x=x.trim();if(!x.isEmpty())out.add(x);}return out.isEmpty()?new String[]{"Other"}:out.toArray(new String[0]);}
    private static String joinClean(String s,String fallback){return String.join("|",split(s==null?fallback:s.replace(",","|")));}
    private static String validTime(String s,String fallback){if(s!=null&&s.matches("(?:[01]\\d|2[0-3]):[0-5]\\d"))return s;return fallback;}
}
