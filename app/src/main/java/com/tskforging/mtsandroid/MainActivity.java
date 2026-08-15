package com.tskforging.mtsandroid;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
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
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends AppCompatActivity {
    private enum ScanTarget { EMPLOYEE, MACHINE, TAG }
    private final int NAVY=Color.rgb(23,50,77), BLUE=Color.rgb(31,78,120), GREEN=Color.rgb(46,125,50);
    private LinearLayout body;
    private MtsDb db;
    private ScanTarget scanTarget;
    private String employee="", machine="", shift="DAY", shiftId="";
    private long shiftStart=0;
    private TagParser.ResultTag pendingTag;

    private final ActivityResultLauncher<ScanOptions> scanner = registerForActivityResult(
            new ScanContract(), this::onScanResult);

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        db=new MtsDb(this);
        showStartShift();
    }

    private void makeScreen(String title) {
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245,247,250));
        TextView header=new TextView(this); header.setText(title); header.setTextColor(Color.WHITE);
        header.setTextSize(22); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(20),0,dp(16),0);
        header.setBackgroundColor(NAVY); root.addView(header,new LinearLayout.LayoutParams(-1,dp(64)));
        ScrollView scroll=new ScrollView(this); body=new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18),dp(18),dp(18),dp(28)); scroll.addView(body); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }

    private void showStartShift() {
        makeScreen("MTS Android v0.1");
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
            shiftStart=System.currentTimeMillis(); shiftId=new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date(shiftStart));
            showProductionAuto();
        });
        gap(10); outline("TAG HISTORY",v->showHistory());
    }

    private void showProductionAuto() {
        makeScreen("PRODUCTION AUTO");
        statusCard("Shift",shift+"  •  "+fmt(shiftStart));
        statusCard("Employee / Machine",employee+"  /  "+machine);
        gap(12);
        action("SCAN PRODUCTION TAG",BLUE,v->scan(ScanTarget.TAG,"Scan WIP or FG Result Tag"));
        gap(8); outline("TAG HISTORY",v->showHistory());
        outline("EXPORT CSV",v->exportCsv());
        gap(28); danger("CLOSE SHIFT",v->new AlertDialog.Builder(this).setTitle("Close Shift?")
                .setMessage("v0.1 will close the current test shift. Tag History remains saved.")
                .setNegativeButton("CANCEL",null).setPositiveButton("CLOSE",(d,w)->{
                    employee="";machine="";shiftId="";shiftStart=0;showStartShift();
                }).show());
    }

    private void onScanResult(ScanIntentResult result) {
        if(result.getContents()==null){toast("Scan cancelled");return;}
        String raw=result.getContents().trim();
        if(scanTarget==ScanTarget.EMPLOYEE){employee=readIdentity(raw,"EMP");showStartShift();return;}
        if(scanTarget==ScanTarget.MACHINE){machine=readIdentity(raw,"MC");showStartShift();return;}
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
        statusCard("Lot / Qty",pendingTag.lot+"  /  "+pendingTag.qty);
        statusCard("Charge No.",pendingTag.charge);
        gap(12);
        action("CONFIRM",GREEN,v->{
            long id=db.confirm(shiftId,shift,employee,machine,pendingTag,System.currentTimeMillis());
            if(id<0){showError("SAVE ERROR","Tag was not recorded. It may already exist.");}
            else{pendingTag=null;toast("Confirmed and saved to Tag History");showProductionAuto();}
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
    }

    private void exportCsv(){
        try{
            Uri uri=CsvExporter.export(this,db.list(10000));
            toast("Saved in Downloads/MTS_Exports");
            Intent share=new Intent(Intent.ACTION_SEND);share.setType("text/csv");share.putExtra(Intent.EXTRA_STREAM,uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(share,"Share MTS CSV"));
        }catch(Exception e){showError("EXPORT ERROR",e.getMessage()==null?e.toString():e.getMessage());}
    }

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
        t.setBackgroundColor(Color.WHITE);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(6),0,dp(6));body.addView(t,lp);
    }
    private void label(String text,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,1);body.addView(t);}
    private void action(String text,int color,View.OnClickListener click){MaterialButton b=new MaterialButton(this);b.setText(text);b.setTextSize(17);b.setTextColor(Color.WHITE);b.setBackgroundColor(color);b.setOnClickListener(click);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(58));lp.setMargins(0,dp(7),0,dp(7));body.addView(b,lp);}
    private void outline(String text,View.OnClickListener click){Button b=new Button(this);b.setText(text);b.setTextSize(16);b.setTextColor(BLUE);b.setOnClickListener(click);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(54));lp.setMargins(0,dp(6),0,dp(6));body.addView(b,lp);}
    private void danger(String text,View.OnClickListener click){action(text,Color.rgb(198,40,40),click);}
    private void gap(int px){View v=new View(this);body.addView(v,new LinearLayout.LayoutParams(1,dp(px)));}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private String fmt(long ms){return ms<=0?"-":new SimpleDateFormat("dd/MM/yyyy HH:mm:ss",Locale.US).format(new Date(ms));}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    @Override public void onBackPressed(){if(shiftStart>0)showProductionAuto();else super.onBackPressed();}
}
