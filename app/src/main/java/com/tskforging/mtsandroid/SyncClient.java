package com.tskforging.mtsandroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SyncClient {
    public interface Callback { void done(int sent, int pending, String error); }
    private final MultiMachineStore store;
    private final SharedPreferences prefs;
    private final ExecutorService pool=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());

    public SyncClient(Context context,MultiMachineStore store){this.store=store;this.prefs=context.getSharedPreferences("mts_v2_config",Context.MODE_PRIVATE);}
    public String serverUrl(){return prefs.getString("server_url","http://192.168.18.145:8765");}
    public void setServerUrl(String value){String s=value==null?"":value.trim();while(s.endsWith("/"))s=s.substring(0,s.length()-1);prefs.edit().putString("server_url",s).apply();}

    public void flush(Callback callback){pool.submit(()->{int sent=0;String error="";try{List<MultiMachineStore.PendingEvent> items=store.pendingEvents(200);for(MultiMachineStore.PendingEvent e:items){post(e.json);store.markSynced(e.eventId);sent++;}}catch(Exception ex){error=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();}int pending=store.pendingCount(),done=sent;String err=error;main.post(()->callback.done(done,pending,err));});}

    private void post(String json)throws Exception{URL url=new URL(serverUrl()+"/api/events");HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(4000);c.setReadTimeout(5000);c.setRequestMethod("POST");c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.setDoOutput(true);byte[] data=json.getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(data.length);try(OutputStream out=c.getOutputStream()){out.write(data);}int code=c.getResponseCode();if(code<200||code>=300)throw new IllegalStateException("Server HTTP "+code);c.disconnect();}
}
