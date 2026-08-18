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
        makeScreen("MTS Android v1.1.2 — ทดสอบวัตถุดิบและ Tool");
        label("ระบบตรวจสอบย้อนกลับการผลิต / MANUFACTURING TRACEABILITY",24,NAVY,true);
        label("รุ่นทดสอบ Offline • Galaxy S25 Ultra",15,Color.DKGRAY,false);
        gap(20);
        label("1. เลือกกะ / SELECT SHIFT",18,NAVY,true);
        Spinner spinner=new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"กะกลางวัน / DAY","กะกลางคืน / NIGHT"}));
        spinner.setSelection("NIGHT".equals(shift)?1:0);
        body.addView(spinner,new LinearLayout.LayoutParams(-1,dp(56)));
        gap(12);
        action("สแกน QR พนักงาน / SCAN EMPLOYEE QR",BLUE,v->{shift=spinner.getSelectedItemPosition()==1?"NIGHT":"DAY"; scan(ScanTarget.EMPLOYEE,"สแกน QR พนักงาน / Scan Employee QR");});
        statusCard("พนักงาน / Employee",employee.isEmpty()?"ยังไม่สแกน / Not scanned":employee);
        action("สแกน QR เครื่องจักร / SCAN MACHINE QR",BLUE,v->scan(ScanTarget.MACHINE,"สแกน QR เครื่องจักร / Scan Machine QR"));
        statusCard("เครื่องจักร / Machine",machine.isEmpty()?"ยังไม่สแกน / Not scanned":machine);
        action("ยืนยันเริ่มกะ / CONFIRM START SHIFT",GREEN,v->{
            shift=spinner.getSelectedItemPosition()==1?"NIGHT":"DAY";
            if(employee.isEmpty()||machine.isEmpty()){toast("กรุณาสแกนพนักงานและเครื่องจักรก่อน / Scan Employee and Machine first");return;}
            confirmStartShift();
        });
        gap(10); outline("โหมดทดสอบ Logic / LOGIC TEST MODE",v->startActivity(new Intent(this,LogicTestActivity.class)));
        outline("ประวัติ TAG / TAG HISTORY",v->showHistory());
        outline("ตรวจสอบวัตถุดิบ / MATERIAL VERIFICATION",v->startActivity(new Intent(this,MaterialVerificationActivity.class)));
        outline("ตั้งค่าระบบ / MANAGEMENT SETTINGS",v->showManagement());
        ProductionStore.Summary last=production.lastSummary();
        if(!last.shiftId.isEmpty())outline("สรุปกะล่าสุด / LAST MACHINE SHIFT SUMMARY",v->showSummary(last,true));
    }

    private void showProductionAuto() {
        makeScreen("ผลิตอัตโนมัติ / PRODUCTION AUTO");
        productionScreen=true;
        statusCard("กะ / Shift",shift+"  •  "+fmt(shiftStart));
        statusCard("พนักงาน / เครื่องจักร — Employee / Machine",employee+"  /  "+machine);
        timerText=new TextView(this);timerText.setTextSize(18);timerText.setTextColor(NAVY);timerText.setTypeface(null,1);timerText.setPadding(dp(16),dp(13),dp(16),dp(13));timerText.setBackgroundColor(WHITE);
        LinearLayout.LayoutParams tlp=new LinearLayout.LayoutParams(-1,-2);tlp.setMargins(0,dp(6),0,dp(6));body.addView(timerText,tlp);updateTimer();timerHandler.postDelayed(timerTick,1000);
        gap(12);
        action("สแกน TAG ผลิต / SCAN PRODUCTION TAG",BLUE,v->scan(ScanTarget.TAG,"สแกน TAG ผลิต WIP หรือ FG / Scan WIP or FG Result Tag"));
        action("ลงงานเสีย / ADD NG",Color.rgb(198,94,0),v->showAddNg());
        if(production.stopRunning())danger("จบการหยุด / END STOP — "+production.stopReason(),v->{production.endStop(System.currentTimeMillis());toast("บันทึกเวลาหยุดแล้ว / Stop Time saved");showProductionAuto();});
        else action("หยุดเครื่อง / STOP M/C",Color.rgb(198,94,0),v->showStartStop());
        gap(8);outline("สรุปผลกะ / MACHINE SHIFT SUMMARY",v->showSummary(production.summary(shiftId),false));
        outline("อายุ TOOL / TOOL LIFE",v->showToolLife());
        outline("ประวัติ TAG / TAG HISTORY",v->showHistory());
        outline("ส่งออกประวัติ TAG เป็น CSV / EXPORT TAG HISTORY CSV",v->exportCsv());
        outline("ตั้งค่าระบบ / MANAGEMENT SETTINGS",v->showManagement());
        gap(22);danger("ปิดกะ / CLOSE SHIFT",v->showCloseShift());
    }

    private void onScanResult(ScanIntentResult result) {
        if(result.getContents()==null){toast("ยกเลิกการสแกน / Scan cancelled");return;}
        String raw=result.getContents().trim();
        if(scanTarget==ScanTarget.EMPLOYEE){employee=readIdentity(raw,"EMP");showStartShift();return;}
        if(scanTarget==ScanTarget.MACHINE){machine=readIdentity(raw,"MC");showStartShift();return;}
        if(scanTarget==ScanTarget.BLADE){
            try{production.changeBlade(readIdentity(raw,"BLADE"),pendingBladeReason,System.currentTimeMillis());pendingBladeReason="";toast("ติดตั้ง Blade ใหม่และรีเซ็ตอายุ Tool แล้ว / Blade installed and Tool Life reset");showToolLife();}
            catch(Exception e){toast(e.getMessage()==null?"บันทึก Blade ไม่สำเร็จ / Cannot save Blade":e.getMessage());}
            return;
        }
        pendingTag=TagParser.parse(raw);
        if(!pendingTag.isValid()){showError("ไม่รู้จัก TAG / UNKNOWN TAG", "ต้องเป็น WIP 13 ช่อง หรือ FG 14 ช่อง / Expected WIP 13 fields or FG 14 fields.\n\nข้อมูลดิบ / RAW:\n"+raw);return;}
        if(db.isDuplicate(pendingTag.duplicateKey())){
            showError("TAG ซ้ำ / DUPLICATE TAG", "กระบวนการ / Process: "+pendingTag.process+"\nรหัส Item / Item: "+pendingTag.item+"\nเลข Lot / Lot: "+pendingTag.lot+"\n\nไม่บันทึกข้อมูล / Not recorded.");return;
        }
        showTagConfirm();
    }

    private void showTagConfirm() {
        makeScreen("ผลการสแกน TAG / SCAN RESULT TAG");
        label("โปรแกรมตรวจสอบ: ผ่าน / PROGRAM CHECK: OK",22,GREEN,true);
        statusCard("ประเภท / กระบวนการ — Type / Process",pendingTag.type+"  /  "+pendingTag.process);
        statusCard("รหัส Item / Item No.",pendingTag.item);
        statusCard("รหัสชิ้นงาน / Part No.",pendingTag.partNo);
        statusCard("ชื่อชิ้นงาน / Part Name",pendingTag.partName);
        long tagQty=production.parsedTagQty(pendingTag.qty),previous=production.previousForItem(pendingTag.item),thisQty=Math.max(0,tagQty-previous);
        statusCard("เลข Lot / Lot No.",pendingTag.lot);
        statusCard("จำนวนเดิมใน Tag / Tag Qty (Original)",String.valueOf(tagQty));
        statusCard("จำนวนคงเหลือจากกะก่อน / Previous Shift Qty",String.valueOf(previous));
        statusCard("จำนวน OK กะนี้ / This Shift Qty OK",String.valueOf(thisQty));
        statusCard("เลข Charge / Charge No.",pendingTag.charge);
        gap(12);
        action("ยืนยัน / CONFIRM",GREEN,v->{
            long now=System.currentTimeMillis();ProductionStore.QtyResult qty=production.confirmTag(pendingTag,now);
            long id=db.confirm(shiftId,shift,employee,machine,pendingTag,now);
            if(id<0){showError("บันทึกไม่สำเร็จ / SAVE ERROR","ไม่ได้บันทึก Tag อาจมีข้อมูลนี้อยู่แล้ว / Tag was not recorded. It may already exist.");}
            else{pendingTag=null;toast("ยืนยันแล้ว • OK กะนี้ = "+qty.thisShiftQty+" / Confirmed");showProductionAuto();}
        });
        outline("ยกเลิก — ไม่บันทึก / CANCEL — DO NOT SAVE",v->{pendingTag=null;showProductionAuto();});
    }

    private void showHistory() {
        makeScreen("ประวัติ TAG / TAG HISTORY");
        List<MtsDb.HistoryRow> rows=db.list(100);
        label("ยืนยันแล้ว "+rows.size()+" Tag • รายการใหม่อยู่บน / confirmed tag(s) • newest first",15,Color.DKGRAY,false);
        gap(8);
        if(rows.isEmpty()) label("ยังไม่มีประวัติ Tag ที่ยืนยัน / No confirmed Tag History",18,Color.GRAY,true);
        for(MtsDb.HistoryRow r:rows){
            MaterialCardView card=new MaterialCardView(this); card.setRadius(dp(12)); card.setCardElevation(dp(1));
            card.setStrokeColor(Color.rgb(205,216,225));card.setStrokeWidth(dp(1));
            TextView t=new TextView(this);t.setPadding(dp(14),dp(12),dp(14),dp(12));t.setTextColor(NAVY);t.setTextSize(15);
            t.setText(r.process+"  •  "+r.item+"\nLot: "+r.lot+"   จำนวน / Qty: "+r.qty+"\n"+fmt(r.confirmedAt));
            card.addView(t); card.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("รายละเอียด TAG ดิบ / RAW TAG DETAIL")
                    .setMessage(r.raw).setPositiveButton("ปิด / CLOSE",null).show());
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(10));body.addView(card,lp);
        }
        gap(8); outline(shiftStart>0?"กลับหน้าผลิต / BACK TO PRODUCTION":"กลับ / BACK",v->{if(shiftStart>0)showProductionAuto();else showStartShift();});
        outline("ส่งออก CSV / EXPORT CSV",v->exportCsv());
        danger("ล้างประวัติ / CLEAR HISTORY",v->new AlertDialog.Builder(this).setTitle("ล้างประวัติ TAG? / CLEAR TAG HISTORY?").setMessage("ประวัติ Tag ดิบที่ยืนยันทั้งหมดจะถูกลบ แต่รายงานกะจะไม่ถูกลบ / All confirmed raw Tag History will be deleted. Shift reports are not deleted.")
                .setNegativeButton("ยกเลิก / CANCEL",null).setPositiveButton("ลบ / DELETE",(d,w)->{int n=db.clearHistory();toast("ลบแล้ว "+n+" Tag / tag(s) deleted");showHistory();}).show());
    }

    private void exportCsv(){
        try{
            Uri uri=CsvExporter.export(this,db.list(10000));
            toast("บันทึกแล้วใน Downloads/MTS_Exports / Saved");
            Intent share=new Intent(Intent.ACTION_SEND);share.setType("text/csv");share.putExtra(Intent.EXTRA_STREAM,uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(share,"แชร์ MTS CSV / Share MTS CSV"));
        }catch(Exception e){showError("ส่งออกไม่สำเร็จ / EXPORT ERROR",e.getMessage()==null?e.toString():e.getMessage());}
    }

    private void updateTimer(){
        if(timerText==null)return;ProductionStore.Totals t=production.totals();
        String state=production.stopRunning()?"กำลังหยุด / STOP RUNNING: "+production.stopReason():"กำลังผลิต / WORKING";
        timerText.setText(state+"\nOK: "+t.ok+"   NG: "+t.ng+"\nเวลาผลิต / Working: "+duration(t.workingSec)+"   เวลาหยุด / Stop: "+duration(t.stopSec));
        timerText.setTextColor(production.stopRunning()?RED:GREEN);
    }

    private void showAddNg(){
        if(production.activeItem().isEmpty()){toast("กรุณาสแกนและยืนยัน Tag ผลิตก่อน / Scan and Confirm a Production Tag first");return;}
        LinearLayout form=dialogForm();EditText qty=numberInput("จำนวน NG / NG Qty");Spinner reason=spinner(config.ngReasons());
        form.addView(qty);form.addView(reason,new LinearLayout.LayoutParams(-1,dp(56)));
        new AlertDialog.Builder(this).setTitle("ลงงานเสีย / ADD NG — "+production.activeItem()+" / "+production.activeLot()).setView(form)
                .setNegativeButton("ยกเลิก / CANCEL",null).setPositiveButton("ยืนยัน / CONFIRM",(d,w)->{
                    try{production.addNg(longValue(qty),String.valueOf(reason.getSelectedItem()),System.currentTimeMillis());toast("บันทึก NG แล้ว / NG saved");showProductionAuto();}
                    catch(Exception e){toast(e.getMessage()==null?"บันทึก NG ไม่สำเร็จ / Cannot save NG":e.getMessage());}
                }).show();
    }

    private void showStartStop(){
        Spinner reason=spinner(config.stopReasons());LinearLayout form=dialogForm();form.addView(reason,new LinearLayout.LayoutParams(-1,dp(58)));
        new AlertDialog.Builder(this).setTitle("สาเหตุหยุดเครื่อง / STOP M/C REASON").setView(form).setNegativeButton("ยกเลิก / CANCEL",null)
                .setPositiveButton("ยืนยัน / CONFIRM",(d,w)->{String selected=String.valueOf(reason.getSelectedItem());
                    if(selected.toUpperCase(Locale.US).contains("NO OT")){production.markNoOt(System.currentTimeMillis());toast("บันทึกไม่มี OT แล้ว — ไม่เริ่มเวลาหยุด / NO OT saved");showProductionAuto();}
                    else if(selected.toUpperCase(Locale.US).contains("CHANGE BLADE")||selected.toUpperCase(Locale.US).contains("BLADE CHANGE"))showBladeReason();
                    else{production.startStop(selected,System.currentTimeMillis());showProductionAuto();}
                }).show();
    }

    private void showBladeReason(){
        Spinner reason=spinner(new String[]{"ครบอายุ Tool / Tool Life Limit","แตกหัก / Broken","บิ่น / Chipped","คุณภาพผิดปกติ / Abnormal Quality","อื่น ๆ / Other"});LinearLayout form=dialogForm();labelFor(form,"เหตุผลที่เปลี่ยน Blade / Change Reason");form.addView(reason,new LinearLayout.LayoutParams(-1,dp(58)));
        new AlertDialog.Builder(this).setTitle("เปลี่ยนใบเลื่อย / CHANGE BLADE").setView(form).setNegativeButton("ยกเลิก / CANCEL",null).setPositiveButton("สแกน BLADE ใหม่ / SCAN NEW BLADE",(d,w)->{pendingBladeReason=String.valueOf(reason.getSelectedItem());scan(ScanTarget.BLADE,"สแกน QR Blade ใหม่ / Scan QR of new Blade");}).show();
    }

    private void showCloseShift(){
        LinearLayout form=dialogForm();TextView active=new TextView(this);active.setText("Item ล่าสุด / Last Item: "+emptyDash(production.activeItem())+"\nLot ล่าสุด / Last Lot: "+emptyDash(production.activeLot()));active.setTextSize(16);active.setTextColor(NAVY);active.setPadding(0,0,0,dp(8));
        EditText ok=numberInput("0"),ng=numberInput("0");Spinner ngReason=spinner(config.ngReasons());
        Spinner coffee=spinner(new String[]{"เลือก / SELECT","0 ครั้ง / 0 time","1 ครั้ง / 1 time","2 ครั้ง / 2 times"});Spinner meal=spinner(new String[]{"เลือก / SELECT","ไม่ได้พัก / NO BREAK","พัก / BREAK"});Spinner otBreak=spinner(new String[]{"เลือก / SELECT","ไม่ได้พัก / NO BREAK","พัก / BREAK"});
        if(production.noOt()){otBreak.setSelection(1);otBreak.setEnabled(false);}
        form.addView(active);labelFor(form,"จำนวนงานดี Lot สุดท้าย / Last Lot OK");form.addView(ok);labelFor(form,"จำนวนงานเสีย Lot สุดท้าย / Last Lot NG");form.addView(ng);labelFor(form,"สาเหตุงานเสีย / NG Reason");form.addView(ngReason,new LinearLayout.LayoutParams(-1,dp(60)));labelFor(form,"พักกาแฟ / Coffee Break "+config.coffeeMinutes()+" นาที — เลือกจำนวนครั้ง");form.addView(coffee,new LinearLayout.LayoutParams(-1,dp(60)));labelFor(form,"พักอาหาร / Meal Break "+config.mealMinutes()+" นาที");form.addView(meal,new LinearLayout.LayoutParams(-1,dp(60)));labelFor(form,"พัก OT / OT Break "+config.otBreakMinutes()+" นาที — เลือก พัก หรือ ไม่ได้พัก");form.addView(otBreak,new LinearLayout.LayoutParams(-1,dp(60)));gapFor(form,dp(20));
        ScrollView dialogScroll=new ScrollView(this);dialogScroll.setFillViewport(false);dialogScroll.setVerticalScrollBarEnabled(true);dialogScroll.addView(form,new ScrollView.LayoutParams(-1,-2));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("ปิดกะ / CLOSE SHIFT").setView(dialogScroll).setNegativeButton("ยกเลิก / CANCEL",null).setPositiveButton("ยืนยันปิดกะ / CONFIRM CLOSE",null).create();
        dialog.setOnShowListener(x->{
            if(dialog.getWindow()!=null)dialog.getWindow().setLayout(-1,(int)(getResources().getDisplayMetrics().heightPixels*0.90f));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                boolean needOt=!production.noOt();if(coffee.getSelectedItemPosition()==0||meal.getSelectedItemPosition()==0||(needOt&&otBreak.getSelectedItemPosition()==0)){toast("ต้องตอบข้อมูลการพักให้ครบทุกช่องสีแดง / BREAK CHECK REQUIRED");coffee.setBackgroundColor(Color.rgb(255,210,210));meal.setBackgroundColor(Color.rgb(255,210,210));if(needOt){otBreak.setBackgroundColor(Color.rgb(255,210,210));dialogScroll.post(()->dialogScroll.smoothScrollTo(0,otBreak.getBottom()));}return;}
                long actual=System.currentTimeMillis(),scheduled=scheduledClose(shift,actual),effective=actual;String closeReason="";boolean early=actual<scheduled-config.closeEarlyTolerance()*60000L;
                long lastOk=longValue(ok),lastNg=longValue(ng);int coffeeCount=coffee.getSelectedItemPosition()-1,mealTaken=meal.getSelectedItemPosition()==2?1:0,otTaken=needOt&&otBreak.getSelectedItemPosition()==2?1:0;
                if(early){dialog.dismiss();requestCloseReason(lastOk,lastNg,lastNg>0?String.valueOf(ngReason.getSelectedItem()):"",actual,effective,coffeeCount,mealTaken,otTaken);return;}
                if(Math.abs(actual-scheduled)<=config.closeEarlyTolerance()*60000L)effective=scheduled;
                ProductionStore.Summary s=production.closeShift(lastOk,lastNg,lastNg>0?String.valueOf(ngReason.getSelectedItem()):"",actual,effective,closeReason,coffeeCount,mealTaken,otTaken,config.coffeeMinutes(),config.mealMinutes(),config.otBreakMinutes());
                dialog.dismiss();exportExcel(s,false);shiftStart=0;shiftId="";showSummary(s,true);
            }catch(Exception e){toast(e.getMessage()==null?"ปิดกะไม่สำเร็จ / Cannot close shift":e.getMessage());}
            });
        });dialog.show();
    }

    private void showSummary(ProductionStore.Summary s,boolean closed){
        makeScreen(closed?"สรุปกะเครื่องจักร — ปิดแล้ว / SHIFT SUMMARY — CLOSED":"สรุปกะเครื่องจักร / MACHINE SHIFT SUMMARY");
        label(s.machine+" • "+s.shift,20,NAVY,true);label(fmt(s.startMs)+" → "+fmt(s.closeMs),14,Color.DKGRAY,false);gap(10);
        summaryCard("งานดี / OK",s.ok,GREEN);summaryCard("งานเสีย / NG",s.ng,RED);summaryCard("เวลาผลิต / WORKING TIME",duration(s.workingSec),BLUE);summaryCard("เวลาหยุด / STOP TIME",duration(s.stopSec),Color.rgb(198,94,0));summaryCard("ล่วงเวลา / OT",duration(s.otSec),Color.rgb(104,50,150));summaryCard("เวลาพัก / BREAK",duration(s.totalBreakSec),Color.rgb(0,110,120));
        gap(8);outline("ส่งออก EXCEL .XLSX / EXPORT EXCEL",v->exportExcel(s,true));outline("ส่งออก CSV กะ / EXPORT SHIFT CSV",v->exportShift(s.shiftId,true));
        if(closed)action("เริ่มกะถัดไป / START NEXT SHIFT",GREEN,v->{employee="";machine="";shift="DAY";showStartShift();});
        else outline("กลับหน้าผลิต / BACK TO PRODUCTION",v->showProductionAuto());
    }

    private void showToolLife(){
        makeScreen("อายุ TOOL / TOOL LIFE");label("TOOL ปัจจุบัน / CURRENT TOOL",18,NAVY,true);statusCard("รหัส / ประเภท — Code / Type",production.toolCode()+" / "+production.toolType());summaryCard("จำนวนอายุใช้งาน / LIFE QTY",production.toolLife(),GREEN);
        action("ติดตั้ง / เปลี่ยน TOOL — INSTALL / CHANGE",BLUE,v->showInstallTool());outline("กลับหน้าผลิต / BACK TO PRODUCTION",v->showProductionAuto());
    }

    private void showInstallTool(){
        LinearLayout form=dialogForm();EditText code=textInput("รหัส Tool / Tool Code");Spinner type=spinner(new String[]{"ใบเลื่อย / SAW","Chip / CHIP","แม่พิมพ์ / DIE"});form.addView(code);form.addView(type,new LinearLayout.LayoutParams(-1,dp(56)));
        new AlertDialog.Builder(this).setTitle("ติดตั้ง TOOL ใหม่ / INSTALL NEW TOOL").setMessage("อายุ Tool ใหม่จะเริ่มที่ 0 / New Tool Life will reset to 0.").setView(form).setNegativeButton("ยกเลิก / CANCEL",null)
                .setPositiveButton("ติดตั้ง / INSTALL",(d,w)->{if(code.getText().toString().trim().isEmpty()){toast("กรุณาระบุรหัส Tool / Tool Code is required");return;}production.installTool(code.getText().toString(),String.valueOf(type.getSelectedItem()));showToolLife();}).show();
    }

    private void exportShift(String id,boolean share){
        try{Uri uri=CsvExporter.exportShiftReport(this,id,production.reportRows(id));toast("บันทึกรายงานกะแล้วใน Downloads/MTS_Exports / Shift Report saved");
            if(share){Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/csv");i.putExtra(Intent.EXTRA_STREAM,uri);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"แชร์รายงานกะ MTS / Share MTS Shift Report"));}
        }catch(Exception e){toast(e.getMessage()==null?"ส่งออกไม่สำเร็จ / Export failed":e.getMessage());}
    }

    private void exportExcel(ProductionStore.Summary s,boolean share){
        try{Uri uri=XlsxExporter.export(this,s,db.list(10000),production.reportRows(s.shiftId));toast("บันทึก Excel แล้วใน Downloads/MTS_Exports / Excel saved");
            if(share){Intent i=new Intent(Intent.ACTION_SEND);i.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");i.putExtra(Intent.EXTRA_STREAM,uri);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"แชร์ MTS Excel / Share MTS Excel"));}
        }catch(Exception e){toast("ส่งออก Excel ไม่สำเร็จ / Excel Export Error: "+(e.getMessage()==null?e.toString():e.getMessage()));}
    }

    private void confirmStartShift(){
        long actual=System.currentTimeMillis(),scheduled=scheduledStart(shift,actual),diff=Math.abs(actual-scheduled),tol=config.startTolerance()*60000L;
        if(diff>tol){Spinner reason=spinner(config.startReasons());LinearLayout form=dialogForm();labelFor(form,"เริ่มนอกเวลามาตรฐาน ต้องระบุสาเหตุ / Start outside standard time. Reason required");form.addView(reason,new LinearLayout.LayoutParams(-1,dp(56)));
            new AlertDialog.Builder(this).setTitle("สาเหตุเริ่มกะ / START SHIFT REASON").setView(form).setNegativeButton("ยกเลิก / CANCEL",null).setPositiveButton("เริ่มกะ / START",(d,w)->startShiftNow(actual,actual,String.valueOf(reason.getSelectedItem()))).show();
        }else startShiftNow(actual,scheduled,"");
    }
    private void startShiftNow(long actual,long effective,String reason){shiftStart=effective;production.startShift(shift,employee,machine,actual,effective,reason);shiftId=production.shiftId();showProductionAuto();}

    private void requestCloseReason(long ok,long ng,String ngReason,long actual,long effective,int coffee,int meal,int otBreak){
        Spinner reason=spinner(config.closeReasons());LinearLayout form=dialogForm();labelFor(form,"ปิดก่อนเวลาเกิน "+config.closeEarlyTolerance()+" นาที ต้องระบุสาเหตุ / Closing early. Reason required");form.addView(reason,new LinearLayout.LayoutParams(-1,dp(56)));
        new AlertDialog.Builder(this).setTitle("สาเหตุปิดกะ / CLOSE SHIFT REASON").setView(form).setNegativeButton("กลับ / BACK",null).setPositiveButton("ยืนยันปิดกะ / CONFIRM CLOSE",(d,w)->{
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
        makeScreen("ตั้งค่าระบบ / MANAGEMENT SETTINGS");label("เวลากะ / SHIFT TIME (HH:mm)",19,NAVY,true);
        EditText ds=textValue("เริ่มกะ DAY / DAY Start",config.dayStart()),dc=textValue("ปิดกะ DAY / DAY Close",config.dayClose()),ns=textValue("เริ่มกะ NIGHT / NIGHT Start",config.nightStart()),nc=textValue("ปิดกะ NIGHT / NIGHT Close",config.nightClose());body.addView(ds);body.addView(dc);body.addView(ns);body.addView(nc);
        label("กฎและเวลาพัก / RULES & BREAK MINUTES",19,NAVY,true);EditText st=numberValue("ช่วงยอมรับเวลาเริ่ม / Start tolerance",config.startTolerance()),ct=numberValue("ช่วงยอมรับปิดก่อน / Close early tolerance",config.closeEarlyTolerance()),cf=numberValue("พักกาแฟ / Coffee",config.coffeeMinutes()),ml=numberValue("พักอาหาร / Meal",config.mealMinutes()),ot=numberValue("พัก OT / OT Break",config.otBreakMinutes());body.addView(st);body.addView(ct);body.addView(cf);body.addView(ml);body.addView(ot);
        label("รายการสาเหตุ — คั่นด้วยจุลภาค / REASONS — comma separated",19,NAVY,true);EditText ngr=textValue("สาเหตุ NG / NG Reasons",String.join(",",config.ngReasons())),spr=textValue("สาเหตุหยุด / Stop Reasons",String.join(",",config.stopReasons())),str=textValue("สาเหตุเริ่มกะ / Start Reasons",String.join(",",config.startReasons())),clr=textValue("สาเหตุปิดกะ / Close Reasons",String.join(",",config.closeReasons()));ngr.setMinLines(3);spr.setMinLines(3);body.addView(ngr);body.addView(spr);body.addView(str);body.addView(clr);
        action("บันทึกการตั้งค่า / SAVE SETTINGS",GREEN,v->{config.save(ds.getText().toString(),dc.getText().toString(),ns.getText().toString(),nc.getText().toString(),(int)longValue(st),(int)longValue(ct),(int)longValue(cf),(int)longValue(ml),(int)longValue(ot),ngr.getText().toString(),spr.getText().toString(),str.getText().toString(),clr.getText().toString());toast("บันทึกการตั้งค่าแล้ว / Management settings saved");if(production.hasActiveShift())showProductionAuto();else showStartShift();});
        outline("ยกเลิก / CANCEL",v->{if(production.hasActiveShift())showProductionAuto();else showStartShift();});
    }

    private EditText textValue(String hint,String value){EditText e=textInput(hint);e.setHint(hint);e.setText(value);return e;}
    private EditText numberValue(String hint,int value){EditText e=numberInput(hint);e.setText(String.valueOf(value));e.setHint(hint+" (min)");return e;}
    private void labelFor(LinearLayout parent,String text){TextView t=new TextView(this);t.setText(text);t.setTextColor(NAVY);t.setTextSize(15);t.setTypeface(null,1);t.setPadding(0,dp(8),0,0);parent.addView(t);}
    private void gapFor(LinearLayout parent,int px){parent.addView(new View(this),new LinearLayout.LayoutParams(1,px));}

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
            .setPositiveButton("สแกนอีกครั้ง / SCAN AGAIN",(d,w)->scan(ScanTarget.TAG,"สแกน TAG ผลิต WIP หรือ FG / Scan WIP or FG Result Tag"))
            .setNegativeButton("ยกเลิก / CANCEL",(d,w)->showProductionAuto()).show();}
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
