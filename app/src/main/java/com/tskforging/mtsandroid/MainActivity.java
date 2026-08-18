package com.tskforging.mtsandroid;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanIntentResult;
import com.journeyapps.barcodescanner.ScanOptions;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends AppCompatActivity {
    private enum ScanTarget { EMPLOYEE, MACHINE, TAG, BLADE }
    private final int NAVY=Color.rgb(15,43,70), BLUE=Color.rgb(0,82,155), GREEN=Color.rgb(0,112,60);
    private final int RED=Color.rgb(190,25,35), WHITE=Color.WHITE, PALE=Color.rgb(245,248,252);
    private LinearLayout body;
    private MtsDb db;
    private ProductionStore production;
    private ConfigStore config;
    private ScanTarget scanTarget;
    private String employee="", machine="", shift="DAY", shiftId="";
    private long shiftStart=0;
    private TagParser.ResultTag pendingTag;
    private String pendingBladeReason="";
    private TextView timerText;
    private boolean productionScreen=false;
    private final Handler timerHandler=new Handler(Looper.getMainLooper());
    private final Runnable timerTick=new Runnable(){@Override public void run(){if(productionScreen){updateTimer();timerHandler.postDelayed(this,1000);}}};

    private final ActivityResultLauncher<ScanOptions> scanner = registerForActivityResult(
            new ScanContract(), this::onScanResult);

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        db=new MtsDb(this);
        production=new ProductionStore(this);
        config=new ConfigStore(this);
        if(production.hasActiveShift()){
            employee=production.employee();machine=production.machine();shift=production.shiftName();shiftId=production.shiftId();shiftStart=production.startMs();showProductionAuto();
        }else showStartShift();
    }

    private void makeScreen(String title) {
        productionScreen=false;timerHandler.removeCallbacks(timerTick);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PALE);
        TextView header=new TextView(this); header.setText(title); header.setTextColor(Color.WHITE);
        header.setTextSize(22); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(20),0,dp(16),0);
        header.setBackgroundColor(NAVY); root.addView(header,new LinearLayout.LayoutParams(-1,dp(64)));
        ScrollView scroll=new ScrollView(this); body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18),dp(18),dp(18),dp(28)); scroll.addView(body); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }

    private void showStartShift() {
        makeScreen("MTS Android v1.1 MATERIAL & TOOL TEST");
        label("MANUFACTURING TRACEABILITY",26,NAVY,true);
        label("Offline Test Build • Galaxy S25 Ultra",15,Color.DKGRAY,false);
        gap(20);
        label("1. SELECT SHIFT",18,NAVY,true);
        Spinner spinner=new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"DAY","NIGHT"}));
        spinner.setSelection("NIGHT".equals(shift)?1:0);
        body.addView(spinner,new LinearLayout.LayoutParams(-1,dp(56)));
        gap(12);
        action("SCAN EMPLOYEE QR",BLUE,v->{shift=(String)spinner.getSelectedItem(); scan(ScanTarget.EMPLOYEE,"Scan Employee QR");});
        statusCard("Employee",employee.isEmpty()?"Not scanned":employee);
        action("SCAN MACHINE QR",BLUE,v->scan(ScanTarget.MACHINE,"Scan Machine QR"));
        statusCard("Machine",machine.isEmpty()?"Not scanned":machine);
        action("CONFIRM START SHIFT",GREEN,v->{
            shift=(String)spinner.getSelectedItem();
            if(employee.isEmpty()||machine.isEmpty()){toast("Scan Employee and Machine first");return;}
            confirmStartShift();
        });
        gap(10); outline("LOGIC TEST MODE",v->startActivity(new Intent(this,LogicTestActivity.class)));
        outline("TAG HISTORY",v->showHistory());
        outline("ตรวจสอบวัตถุดิบ / MATERIAL VERIFICATION",v->startActivity(new Intent(this,MaterialVerificationActivity.class)));
        outline("MANAGEMENT SETTINGS",v->showManagement());
        ProductionStore.Summary last=production.lastSummary();
        if(!last.shiftId.isEmpty())outline("LAST MACHINE SHIFT SUMMARY",v->showSummary(last,true));
    }

    private void showProductionAuto() {
        makeScreen("PRODUCTION AUTO");
        productionScreen=true;
        statusCard("Shift",shift+"  •  "+fmt(shiftStart));
        statusCard("Employee / Machine",employee+"  /  "+machine);
        timerText=new TextView(this);timerText.setTextSize(18);timerText.setTextColor(NAVY);timerText.setTypeface(null,1);timerText.setPadding(dp(16),dp(13),dp(16),dp(13));timerText.setBackgroundColor(WHITE);
        LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-1,-2);tlp.setMargins(0,dp(6),0,dp(6));body.addView(timerText,tlp);updateTimer();timerHandler.postDelayed(timerTick,1000);
        gap(12);
        action("สแกน TAG ผลิต / SCAN PRODUCTION TAG",BLUE,v->scan(ScanTarget.TAG,"Scan WIP or FG Result Tag"));
        action("ลงงานเสีย / ADD NG",Color.rgb(198,94,0),v->showAddNg());
        if(production.stopRunning())danger("END STOP — "+production.stopReason(),v->{production.endStop(System.currentTimeMillis());toast("Stop Time saved");showProductionAuto();});
        else action("หยุดเครื่อง / STOP M/C",Color.rgb(198,94,0),v->showStartStop());
        gap(8);outline("สรุปผลกะ / MACHINE SHIFT SUMMARY",v->showSummary(production.summary(shiftId),false));
        outline("อายุ TOOL / TOOL LIFE",v->showToolLife());
        outline("ประวัติ TAG / TAG HISTORY",v->showHistory());
        outline("EXPORT TAG HISTORY CSV",v->exportCsv());
        outline("MANAGEMENT SETTINGS",v->showManagement());
        gap(22);danger("ปิดกะ / CLOSE SHIFT",v->showCloseShift());
    }

    private void onScanResult(ScanIntentResult result) {
        if(result.getContents()==null){toast("Scan cancelled");return;}
        String raw=result.getContents().trim();
        if(scanTarget==ScanTarget.EMPLOYEE){employee=readIdentity(raw,"EMP");showStartShift();return;}
        if(scanTarget==ScanTarget.MACHINE){machine=readIdentity(raw,"MC");showStartShift();return;}
        if(scanTarget==ScanTarget.BLADE){
            try{production.changeBlade(readIdentity(raw,"BLADE"),pendingBladeReason,System.currentTimeMillis());pendingBladeReason="";toast("ติดตั้ง Blade ใหม่และ Reset Tool Life แล้ว");showToolLife();}
            catch(Exception e){toast(e.getMessage()==null?"Cannot save Blade":e.getMessage());}
            return;
        }
        pendingTag=TagParser.parse(raw);
        if(!pendingTag.isValid()){showError("UNKNOWN TAG", "Expected WIP 13 fields or FG 14 fields.\n\nRAW:\n"+raw);return;}
        if(db.isDuplicate(pendingTag.duplicateKey())){
            showError("DUPLICATE TAG", "Process: "+pendingTag.process+"\nItem: "+pendingTag.item+"\nLot: "+pendingTag.lot+"\n\nNot recorded.");return;
        }
        showTagConfirm();
    }

    private void showTagConfirm() {
        makeScreen("SCAN RESULT TAG");
        label("PROGRAM CHECK: OK",24,GREEN,true);
        statusCard("Type / Process",pendingTag.type+"  /  "+pendingTag.process);
        statusCard("Item No.",pendingTag.item);
        statusCard("Part No.",pendingTag.partNo);
        statusCard("Part Name",pendingTag.partName);
        long tagQty=production.parsedTagQty(pendingTag.qty),previous=production.previousForItem(pendingTag.item),thisQty=Math.max(0,tagQty-previous);
        statusCard("Lot No.",pendingTag.lot);
        statusCard("Tag Qty (Original)",String.valueOf(tagQty));
        statusCard("Previous Shift Qty",String.valueOf(previous));
        statusCard("This Shift Qty OK",String.valueOf(thisQty));
        statusCard("Charge No.",pendingTag.charge);
        gap(12);
        action("CONFIRM",GREEN,v->{
            long now=System.currentTimeMillis();ProductionStore.QtyResult qty=production.confirmTag(pendingTag,now);
            long id=db.confirm(shiftId,shift,employee,machine,pendingTag,now);
            if(id<0){showError("SAVE ERROR","Tag was not recorded. It may already exist.");}
            else{pendingTag=null;toast("Confirmed • This Shift OK = "+qty.thisShiftQty);showProductionAuto();}
        });
        outline("CANCEL — DO NOT SAVE",v->{pendingTag=null;showProductionAuto();});
    }

    private void showHistory() {
        makeScreen("TAG HISTORY");
        List<MtsDb.HistoryRow> rows=db.list(100);
        label(rows.size()+" confirmed tag(s) • newest first",15,Color.DKGRAY,false);
        gap(8);
        if(rows.isEmpty()) label("No confirmed Tag History",18,Color.GRAY,true);
        for(MtsDb.HistoryRow r:rows){
            MaterialCardView card=new MaterialCardView(this); card.setRadius(dp(12)); card.setCardElevation(dp(1));
            card.setStrokeColor(Color.rgb(205,216,225));card.setStrokeWidth(dp(1));
            TextView t=new TextView(this);t.setPadding(dp(14),dp(12),dp(14),dp(12));t.setTextColor(NAVY);t.setTextSize(15);
            t.setText(r.process+"  •  "+r.item+"\nLot: "+r.lot+"   Qty: "+r.qty+"\n"+fmt(r.confirmedAt));
            card.addView(t); card.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("RAW TAG DETAIL")
                    .setMessage(r.raw).setPositiveButton("CLOSE",null).show());
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(10));body.addView(card,lp);
        }
        gap(8); outline(shiftStart>0?"BACK TO PRODUCTION":"BACK",v->{if(shiftStart>0)showProductionAuto();else showStartShift();});
        outline("EXPORT CSV",v->exportCsv());
        danger("CLEAR HISTORY",v->new AlertDialog.Builder(this).setTitle("CLEAR TAG HISTORY?").setMessage("All confirmed raw Tag History will be deleted. Shift reports are not deleted.")
                .setNegativeButton("CANCEL",null).setPositiveButton("DELETE",(d,w)->{int n=db.clearHistory();toast(n+" tag(s) deleted");showHistory();}).show());
    }

    private void exportCsv(){
        try{
            Uri uri=CsvExporter.export(this,db.list(10000));
            toast("Saved in Downloads/MTS_Exports");
            Intent share=new Intent(Intent.ACTION_SEND);share.setType("text/csv");share.putExtra(Intent.EXTRA_STREAM,uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(share,"Share MTS CSV"));
        }catch(Exception e){showError("EXPORT ERROR",e.getMessage()==null?e.toString():e.getMessage());}
    }

    private void updateTimer(){
        if(timerText==null)return;ProductionStore.Totals t=production.totals();
        String state=production.stopRunning()?"STOP RUNNING: "+production.stopReason():"WORKING";
        timerText.setText(state+"\nOK: "+t.ok+"   NG: "+t.ng+"\nWorking: "+duration(t.workingSec)+"   Stop: "+duration(t.stopSec));
        timerText.setTextColor(production.stopRunning()?RED:GREEN);
    }

    private void showAddNg(){
        if(production.activeItem().isEmpty()){toast("Scan and Confirm a Production Tag first");return;}
        LinearLayout form=dialogForm();EditText qty=numberInput("NG Qty");Spinner reason=spinner(config.ngReasons());
        form.addView(qty);form.addView(reason,new LinearLayout.LayoutParams(-1,dp(56)));
        new AlertDialog.Builder(this).setTitle("ADD NG — "+production.activeItem()+" / "+production.activeLot()).setView(form)
                .setNegativeButton("CANCEL",null).setPositiveButton("CONFIRM",(d,w)->{
                    try{production.addNg(longValue(qty),String.valueOf(reason.getSelectedItem()),System.currentTimeMillis());toast("NG saved");showProductionAuto();}
                    catch(Exception e){toast(e.getMessage()==null?"Cannot save NG":e.getMessage());}
                }).show();
    }

    private void showStartStop(){
        Spinner reason=spinner(config.stopReasons());LinearLayout form=dialogForm();form.addView(reason,new LinearLayout.LayoutParams(-1,dp(58)));
        new AlertDialog.Builder(this).setTitle("STOP M/C REASON").setView(form).setNegativeButton("CANCEL",null)
                .setPositiveButton("CONFIRM",(d,w)->{String selected=String.valueOf(reason.getSelectedItem());
                    if("NO OT".equalsIgnoreCase(selected)){production.markNoOt(System.currentTimeMillis());toast("บันทึก NO OT — ไม่เริ่ม Stop Timer");showProductionAuto();}
                    else if("CHANGE BLADE".equalsIgnoreCase(selected)||"BLADE CHANGE".equalsIgnoreCase(selected))showBladeReason();
                    else{production.startStop(selected,System.currentTimeMillis());showProductionAuto();}
                }).show();
    }

    private void showBladeReason(){
        Spinner reason=spinner(new String[]{"Tool Life Limit","Broken","Chipped","Abnormal Quality","Other"});LinearLayout form=dialogForm();labelFor(form,"เหตุผลที่เปลี่ยน Blade / Change Reason");form.addView(reason,new LinearLayout.LayoutParams(-1,dp(58)));
        new AlertDialog.Builder(this).setTitle("เปลี่ยนใบเลื่อย / CHANGE BLADE").setView(form).setNegativeButton("CANCEL",null).setPositiveButton("SCAN NEW BLADE",(d,w)->{pendingBladeReason=String.valueOf(reason.getSelectedItem());scan(ScanTarget.BLADE,"Scan QR of new Blade");}).show();
    }

    private void showCloseShift(){
        LinearLayout form=dialogForm();TextView active=new TextView(this);active.setText("Last Item: "+emptyDash(production.activeItem())+"\nLast Lot: "+emptyDash(production.activeLot()));active.setTextSize(16);active.setTextColor(NAVY);active.setPadding(0,0,0,dp(8));
        EditText ok=numberInput("0"),ng=numberInput("0");Spinner ngReason=spinner(config.ngReasons());
        Spinner coffee=spinner(new String[]{"SELECT","0 time","1 time","2 times"});Spinner meal=spinner(new String[]{"SELECT","NO BREAK","BREAK"});Spinner otBreak=spinner(new String[]{"SELECT","NO BREAK","BREAK"});
        if(production.noOt()){otBreak.setSelection(1);otBreak.setEnabled(false);}
        form.addView(active);labelFor(form,"จำนวนงานดี Lot สุดท้าย (Last Lot OK)");form.addView(ok);labelFor(form,"จำนวนงานเสีย Lot สุดท้าย (Last Lot NG)");form.addView(ng);labelFor(form,"สาเหตุงานเสีย (NG Reason)");form.addView(ngReason,new LinearLayout.LayoutParams(-1,dp(56)));labelFor(form,"Coffee Break "+config.coffeeMinutes()+" min — select count");form.addView(coffee,new LinearLayout.LayoutParams(-1,dp(56)));labelFor(form,"Meal Break "+config.mealMinutes()+" min");form.addView(meal,new LinearLayout.LayoutParams(-1,dp(56)));labelFor(form,"OT Break "+config.otBreakMinutes()+" min");form.addView(otBreak,new LinearLayout.LayoutParams(-1,dp(56)));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("CLOSE SHIFT").setView(form).setNegativeButton("CANCEL",null).setPositiveButton("CONFIRM CLOSE",null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                boolean needOt=!production.noOt();if(coffee.getSelectedItemPosition()==0||meal.getSelectedItemPosition()==0||(needOt&&otBreak.getSelectedItemPosition()==0)){toast("BREAK CHECK REQUIRED — answer all red fields");coffee.setBackgroundColor(Color.rgb(255,210,210));meal.setBackgroundColor(Color.rgb(255,210,210));if(needOt)otBreak.setBackgroundColor(Color.rgb(255,210,210));return;}
                long actual=System.currentTimeMillis(),scheduled=scheduledClose(shift,actual),effective=actual;String closeReason="";boolean early=actual<scheduled-config.closeEarlyTolerance()*60000L;
                long lastOk=longValue(ok),lastNg=longValue(ng);int coffeeCount=coffee.getSelectedItemPosition()-1,mealTaken=meal.getSelectedItemPosition()==2?1:0,otTaken=needOt&&otBreak.getSelectedItemPosition()==2?1:0;
                if(early){dialog.dismiss();requestCloseReason(lastOk,lastNg,lastNg>0?String.valueOf(ngReason.getSelectedItem()):"",actual,effective,coffeeCount,mealTaken,otTaken);return;}
                if(Math.abs(actual-scheduled)<=config.closeEarlyTolerance()*60000L)effective=scheduled;
                ProductionStore.Summary s=production.closeShift(lastOk,lastNg,lastNg>0?String.valueOf(ngReason.getSelectedItem()):"",actual,effective,closeReason,coffeeCount,mealTaken,otTaken,config.coffeeMinutes(),config.mealMinutes(),config.otBreakMinutes());
                dialog.dismiss();exportExcel(s,false);shiftStart=0;shiftId="";showSummary(s,true);
            }catch(Exception e){toast(e.getMessage()==null?"Cannot close shift":e.getMessage());}
        }));dialog.show();
    }

    private void showSummary(ProductionStore.Summary s,boolean closed){
        makeScreen(closed?"MACHINE SHIFT SUMMARY — CLOSED":"MACHINE SHIFT SUMMARY");
        label(s.machine+" • "+s.shift,20,NAVY,true);label(fmt(s.startMs)+" → "+fmt(s.closeMs),14,Color.DKGRAY,false);gap(10);
        summaryCard("OK",s.ok,GREEN);summaryCard("NG",s.ng,RED);summaryCard("WORKING TIME",duration(s.workingSec),BLUE);summaryCard("STOP TIME",duration(s.stopSec),Color.rgb(198,94,0));summaryCard("OT",duration(s.otSec),Color.rgb(104,50,150));summaryCard("BREAK",duration(s.totalBreakSec),Color.rgb(0,110,120));
        gap(8);outline("EXPORT EXCEL .XLSX",v->exportExcel(s,true));outline("EXPORT SHIFT CSV",v->exportShift(s.shiftId,true));
        if(closed)action("START NEXT SHIFT",GREEN,v->{employee="";machine="";shift="DAY";showStartShift();});
        else outline("BACK TO PRODUCTION",v->showProductionAuto());
    }

    private void showToolLife(){
        makeScreen("TOOL LIFE");label("CURRENT TOOL",18,NAVY,true);statusCard("Code / Type",production.toolCode()+" / "+production.toolType());summaryCard("LIFE QTY",production.toolLife(),GREEN);
        action("INSTALL / CHANGE TOOL",BLUE,v->showInstallTool());outline("BACK TO PRODUCTION",v->showProductionAuto());
    }

    private void showInstallTool(){
        LinearLayout form=dialogForm();EditText code=textInput("Tool Code");Spinner type=spinner(new String[]{"SAW","CHIP","DIE"});form.addView(code);form.addView(type,new LinearLayout.LayoutParams(-1,dp(56)));
        new AlertDialog.Builder(this).setTitle("INSTALL NEW TOOL").setMessage("New Tool Life will reset to 0.").setView(form).setNegativeButton("CANCEL",null)
                .setPositiveButton("INSTALL",(d,w)->{if(code.getText().toString().trim().isEmpty()){toast("Tool Code is required");return;}production.installTool(code.getText().toString(),String.valueOf(type.getSelectedItem()));showToolLife();}).show();
    }

    private void exportShift(String id,boolean share){
        try{Uri uri=CsvExporter.exportShiftReport(this,id,production.reportRows(id));toast("Shift Report saved in Downloads/MTS_Exports");
            if(share){Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/csv");i.putExtra(Intent.EXTRA_STREAM,uri);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Share MTS Shift Report"));}
        }catch(Exception e){toast(e.getMessage()==null?"Export failed":e.getMessage());}
    }

    private void exportExcel(ProductionStore.Summary s,boolean share){
        try{Uri uri=XlsxExporter.export(this,s,db.list(10000),production.reportRows(s.shiftId));toast("Excel saved: Downloads/MTS_Exports");
            if(share){Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");i.putExtra(Intent.EXTRA_STREAM,uri);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Share MTS Excel"));}
        }catch(Exception e){toast("Excel Export Error: "+(e.getMessage()==null?e.toString():e.getMessage()));}
    }

    private void confirmStartShift(){
        long actual=System.currentTimeMillis(),scheduled=scheduledStart(shift,actual),diff=Math.abs(actual-scheduled),tol=config.startTolerance()*60000L;
        if(diff>tol){Spinner reason=spinner(config.startReasons());LinearLayout form=dialogForm();labelFor(form,"Start outside standard time. Reason required");form.addView(reason,new LinearLayout.LayoutParams(-1,dp(56)));
            new AlertDialog.Builder(this).setTitle("START SHIFT REASON").setView(form).setNegativeButton("CANCEL",null).setPositiveButton("START",(d,w)->startShiftNow(actual,actual,String.valueOf(reason.getSelectedItem()))).show();
        }else startShiftNow(actual,scheduled,"");
    }
    private void startShiftNow(long actual,long effective,String reason){shiftStart=effective;production.startShift(shift,employee,machine,actual,effective,reason);shiftId=production.shiftId();showProductionAuto();}

    private void requestCloseReason(long ok,long ng,String ngReason,long actual,long effective,int coffee,int meal,int otBreak){
        Spinner reason=spinner(config.closeReasons());LinearLayout form=dialogForm();labelFor(form,"Closing more than "+config.closeEarlyTolerance()+" minutes early. Reason required");form.addView(reason,new LinearLayout.LayoutParams(-1,dp(56)));
        new AlertDialog.Builder(this).setTitle("CLOSE SHIFT REASON").setView(form).setNegativeButton("BACK",null).setPositiveButton("CONFIRM CLOSE",(d,w)->{
            ProductionStore.Summary s=production.closeShift(ok,ng,ngReason,actual,effective,String.valueOf(reason.getSelectedItem()),coffee,meal,otBreak,config.coffeeMinutes(),config.mealMinutes(),config.otBreakMinutes());exportExcel(s,false);shiftStart=0;shiftId="";showSummary(s,true);
        }).show();
    }

    private long scheduledStart(String name,long now){return scheduleTime(name,"DAY".equals(name)?config.dayStart():config.nightStart(),now,true);}
    private long scheduledClose(String name,long now){return scheduleTime(name,"DAY".equals(name)?config.dayClose():config.nightClose(),now,false);}
    private long scheduleTime(String name,String hm,long now,boolean start){
        String[] p=hm.split(":");Calendar c=Calendar.getInstance();c.setTimeInMillis(now);c.set(Calendar.HOUR_OF_DAY,Integer.parseInt(p[0]));c.set(Calendar.MINUTE,Integer.parseInt(p[1]));c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);
        if("NIGHT".equals(name)){if(start&&c.getTimeInMillis()-now>12*3600000L)c.add(Calendar.DAY_OF_MONTH,-1);if(!start&&now-c.getTimeInMillis()>12*3600000L)c.add(Calendar.DAY_OF_MONTH,1);}
        return c.getTimeInMillis();
    }

    private void showManagement(){
        makeScreen("MANAGEMENT SETTINGS");label("SHIFT TIME (HH:mm)",19,NAVY,true);
        EditText ds=textValue("DAY Start",config.dayStart()),dc=textValue("DAY Close",config.dayClose()),ns=textValue("NIGHT Start",config.nightStart()),nc=textValue("NIGHT Close",config.nightClose());body.addView(ds);body.addView(dc);body.addView(ns);body.addView(nc);
        label("RULES / BREAK MINUTES",19,NAVY,true);EditText st=numberValue("Start tolerance",config.startTolerance()),ct=numberValue("Close early tolerance",config.closeEarlyTolerance()),cf=numberValue("Coffee",config.coffeeMinutes()),ml=numberValue("Meal",config.mealMinutes()),ot=numberValue("OT Break",config.otBreakMinutes());body.addView(st);body.addView(ct);body.addView(cf);body.addView(ml);body.addView(ot);
        label("REASONS — separate with comma",19,NAVY,true);EditText ngr=textValue("NG Reasons",String.join(",",config.ngReasons())),spr=textValue("Stop Reasons",String.join(",",config.stopReasons())),str=textValue("Start Reasons",String.join(",",config.startReasons())),clr=textValue("Close Reasons",String.join(",",config.closeReasons()));ngr.setMinLines(3);spr.setMinLines(3);body.addView(ngr);body.addView(spr);body.addView(str);body.addView(clr);
        action("SAVE SETTINGS",GREEN,v->{config.save(ds.getText().toString(),dc.getText().toString(),ns.getText().toString(),nc.getText().toString(),(int)longValue(st),(int)longValue(ct),(int)longValue(cf),(int)longValue(ml),(int)longValue(ot),ngr.getText().toString(),spr.getText().toString(),str.getText().toString(),clr.getText().toString());toast("Management settings saved");if(production.hasActiveShift())showProductionAuto();else showStartShift();});
        outline("CANCEL",v->{if(production.hasActiveShift())showProductionAuto();else showStartShift();});
    }

    private EditText textValue(String hint,String value){EditText e=textInput(hint);e.setHint(hint);e.setText(value);return e;}
    private EditText numberValue(String hint,int value){EditText e=numberInput(hint);e.setText(String.valueOf(value));e.setHint(hint+" (min)");return e;}
    private void labelFor(LinearLayout parent,String text){TextView t=new TextView(this);t.setText(text);t.setTextColor(NAVY);t.setTextSize(15);t.setTypeface(null,1);t.setPadding(0,dp(8),0,0);parent.addView(t);}

    private LinearLayout dialogForm(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(22),dp(8),dp(22),0);return l;}
    private EditText numberInput(String hint){EditText e=new EditText(this);e.setHint(hint);e.setText("0");e.setTextSize(18);e.setTextColor(NAVY);e.setInputType(InputType.TYPE_CLASS_NUMBER);e.setSelectAllOnFocus(true);e.setMinHeight(dp(56));return e;}
    private EditText textInput(String hint){EditText e=new EditText(this);e.setHint(hint);e.setTextSize(18);e.setTextColor(NAVY);e.setInputType(InputType.TYPE_CLASS_TEXT);e.setMinHeight(dp(56));return e;}
    private Spinner spinner(String[] values){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));return s;}
    private long longValue(EditText e){try{return Long.parseLong(e.getText().toString().trim());}catch(Exception x){return 0;}}
    private void summaryCard(String title,long value,int color){summaryCard(title,String.valueOf(value),color);}
    private void summaryCard(String title,String value,int color){TextView t=new TextView(this);t.setText(title+"\n"+value);t.setTextSize(25);t.setTextColor(color);t.setTypeface(null,1);t.setGravity(Gravity.CENTER);t.setPadding(dp(12),dp(14),dp(12),dp(14));t.setBackgroundColor(WHITE);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));body.addView(t,lp);}
    private String duration(long sec){long h=sec/3600,m=(sec%3600)/60,s=sec%60;return String.format(Locale.US,"%02d:%02d:%02d",h,m,s);}
    private String emptyDash(String s){return s==null||s.isEmpty()?"-":s;}

    private void scan(ScanTarget target,String prompt){
        scanTarget=target;ScanOptions options=new ScanOptions();options.setPrompt(prompt);options.setBeepEnabled(true);
        options.setOrientationLocked(false);options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);scanner.launch(options);
    }
    private String readIdentity(String raw,String prefix){
        String[] f=raw.split("\\|",-1);if(f.length>1&&f[0].trim().equalsIgnoreCase(prefix))return f[f.length-1].trim();
        return raw;
    }
    private void showError(String title,String msg){new AlertDialog.Builder(this).setTitle(title).setMessage(msg)
            .setPositiveButton("SCAN AGAIN",(d,w)->scan(ScanTarget.TAG,"Scan WIP or FG Result Tag"))
            .setNegativeButton("CANCEL",(d,w)->showProductionAuto()).show();}
    private void statusCard(String title,String value){
        TextView t=new TextView(this);t.setText(title+"\n"+value);t.setTextSize(17);t.setTextColor(NAVY);t.setPadding(dp(16),dp(12),dp(16),dp(12));
        t.setBackgroundColor(WHITE);t.setTypeface(null,1);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(6),0,dp(6));body.addView(t,lp);
    }
    private void label(String text,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,1);body.addView(t);}
    private void action(String text,int color,View.OnClickListener click){
        MaterialButton b=new MaterialButton(this);b.setText(text);b.setTextSize(17);b.setTextColor(WHITE);
        b.setTypeface(null,1);b.setAllCaps(false);b.setBackgroundTintList(ColorStateList.valueOf(color));
        b.setRippleColor(ColorStateList.valueOf(Color.argb(60,255,255,255)));b.setOnClickListener(click);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(60));lp.setMargins(0,dp(7),0,dp(7));body.addView(b,lp);
    }
    private void outline(String text,View.OnClickListener click){
        MaterialButton b=new MaterialButton(this);b.setText(text);b.setTextSize(16);b.setTextColor(NAVY);
        b.setTypeface(null,1);b.setAllCaps(false);b.setBackgroundTintList(ColorStateList.valueOf(WHITE));
        b.setStrokeColor(ColorStateList.valueOf(BLUE));b.setStrokeWidth(dp(2));b.setOnClickListener(click);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(56));lp.setMargins(0,dp(6),0,dp(6));body.addView(b,lp);
    }
    private void danger(String text,View.OnClickListener click){action(text,RED,click);}
    private void gap(int px){View v=new View(this);body.addView(v,new LinearLayout.LayoutParams(1,dp(px)));}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private String fmt(long ms){return ms<=0?"-":new SimpleDateFormat("dd/MM/yyyy HH:mm:ss",Locale.US).format(new Date(ms));}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override protected void onResume(){super.onResume();if(productionScreen){timerHandler.removeCallbacks(timerTick);timerHandler.post(timerTick);}}
    @Override protected void onPause(){timerHandler.removeCallbacks(timerTick);super.onPause();}
    @Override public void onBackPressed(){if(production.hasActiveShift())showProductionAuto();else super.onBackPressed();}
}
