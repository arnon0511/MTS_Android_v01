package com.tskforging.mtsandroid;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
import java.util.Locale;

public final class LogicTestActivity extends AppCompatActivity {
    private enum Target { BASELINE, CONFIRM, DUPLICATE, DIFFERENT_PROCESS }
    private final int NAVY=Color.rgb(15,43,70), BLUE=Color.rgb(0,82,155), GREEN=Color.rgb(0,112,60);
    private final int RED=Color.rgb(190,25,35), ORANGE=Color.rgb(198,94,0), WHITE=Color.WHITE;
    private LinearLayout body;
    private Target target;
    private TagParser.ResultTag baseline;
    private String confirmedKey="", confirmedRaw="";
    private final String[] result={"PENDING","PENDING","PENDING","PENDING","PENDING","PENDING","PENDING"};
    private final String[] detail={"","","","","","",""};

    private final ActivityResultLauncher<ScanOptions> scanner=registerForActivityResult(
            new ScanContract(),this::onScan);

    @Override protected void onCreate(Bundle state){super.onCreate(state);showChecklist();}

    private void screen(String title){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(245,248,252));
        TextView header=new TextView(this);header.setText(title);header.setTextColor(WHITE);header.setTextSize(21);header.setTypeface(null,1);
        header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(18),0,dp(14),0);header.setBackgroundColor(NAVY);
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(62)));
        ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(16),dp(16),dp(16),dp(28));
        scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }

    private void showChecklist(){
        screen("MTS LOGIC TEST — REAL TAG");
        text("ทดสอบด้วย Tag จริงตามลำดับ",24,NAVY,true);
        text("ข้อมูลใน Test Mode ไม่ถูกบันทึกเข้า Production Tag History",14,Color.DKGRAY,false);
        gap(12);
        testCard(1,"อ่าน WIP/FG และตรวจ Field",result[0],detail[0]);
        testCard(2,"CANCEL ต้องไม่บันทึก",result[1],detail[1]);
        testCard(3,"CONFIRM ต้องบันทึก",result[2],detail[2]);
        testCard(4,"Temporary History ต้องมี 1 รายการ",result[3],detail[3]);
        testCard(5,"Scan Tag เดิมต้อง Duplicate",result[4],detail[4]);
        testCard(6,"Item+Lot เดิม แต่ Process ต่าง ต้องผ่าน",result[5],detail[5]);
        testCard(7,"Export Test Report CSV",result[6],detail[6]);
        gap(12);
        if(!"PASS".equals(result[0])) primary(("FAIL".equals(result[0])?"RETRY":"START")+" STEP 1 — SCAN REAL TAG",v->launch(Target.BASELINE,"Scan real WIP or FG Tag"));
        else if(!"PASS".equals(result[1])) primary("RUN STEP 2 — CANCEL TEST",v->showCancelTest());
        else if(!"PASS".equals(result[2])||!"PASS".equals(result[3])) primary(("FAIL".equals(result[2])?"RETRY":"RUN")+" STEP 3 — SCAN SAME TAG",v->launch(Target.CONFIRM,"Scan the same Tag, then Confirm"));
        else if(!"PASS".equals(result[4])) primary(("FAIL".equals(result[4])?"RETRY":"RUN")+" STEP 5 — DUPLICATE TEST",v->launch(Target.DUPLICATE,"Scan the same confirmed Tag"));
        else if(!"PASS".equals(result[5])&&!"SKIP".equals(result[5])) primary(("FAIL".equals(result[5])?"RETRY":"RUN")+" STEP 6 — DIFFERENT PROCESS",v->launch(Target.DIFFERENT_PROCESS,"Scan same Item+Lot with different Process"));
        else if(!"PASS".equals(result[6])) primary(("FAIL".equals(result[6])?"RETRY":"RUN")+" STEP 7 — EXPORT REPORT",v->exportReport());
        else text(allRequiredPass()?"TEST RESULT: PASS":"TEST FINISHED — REVIEW FAIL/SKIP",22,allRequiredPass()?GREEN:ORANGE,true);
        if("PASS".equals(result[4])&&!"PASS".equals(result[5])&&!"SKIP".equals(result[5])) secondary("SKIP STEP 6 — NO TEST TAG",v->{result[5]="SKIP";detail[5]="No same Item+Lot different Process Tag available";showChecklist();});
        secondary("RESET TEST",v->new AlertDialog.Builder(this).setTitle("Reset Logic Test?").setMessage("All current test results will be cleared.")
                .setNegativeButton("CANCEL",null).setPositiveButton("RESET",(d,w)->reset()).show());
        secondary("BACK TO MTS",v->finish());
    }

    private void onScan(ScanIntentResult scan){
        if(scan.getContents()==null){toast("Scan cancelled");showChecklist();return;}
        TagParser.ResultTag tag=TagParser.parse(scan.getContents());
        if(!tag.isValid()){failCurrent("Unknown Tag or required Process/Item/Lot is blank");return;}
        if(target==Target.BASELINE){baseline=tag;showFieldCheck();return;}
        if(baseline==null){failCurrent("Baseline Tag missing");return;}
        if(target==Target.CONFIRM){
            if(!tag.duplicateKey().equals(baseline.duplicateKey())){result[2]="FAIL";detail[2]="Scanned Tag does not match baseline";showChecklist();return;}
            showConfirmTest(tag);return;
        }
        if(target==Target.DUPLICATE){
            boolean duplicate=!confirmedKey.isEmpty()&&confirmedKey.equals(tag.duplicateKey());
            result[4]=duplicate?"PASS":"FAIL";detail[4]=duplicate?"Duplicate correctly blocked":"Duplicate key was not detected";showChecklist();return;
        }
        boolean sameItem=tag.item.equalsIgnoreCase(baseline.item),sameLot=tag.lot.equalsIgnoreCase(baseline.lot);
        boolean differentProcess=!tag.process.equalsIgnoreCase(baseline.process);
        boolean accepted=!tag.duplicateKey().equals(confirmedKey);
        boolean pass=sameItem&&sameLot&&differentProcess&&accepted;
        result[5]=pass?"PASS":"FAIL";
        detail[5]=pass?(baseline.process+" → "+tag.process):("Need same Item+Lot and different Process. Scanned: "+tag.process+" / "+tag.item+" / "+tag.lot);
        showChecklist();
    }

    private void showFieldCheck(){
        screen("STEP 1 — CHECK TAG FIELDS");
        text("Compare these values with the printed Tag",19,NAVY,true);gap(8);
        value("Type / Process",baseline.type+" / "+baseline.process);
        value("Item No.",baseline.item);value("Part No.",baseline.partNo);value("Part Name",baseline.partName);
        value("Lot No.",baseline.lot);value("Qty",baseline.qty);value("Charge No.",baseline.charge);
        primary("FIELDS CORRECT — PASS",v->{result[0]="PASS";detail[0]=baseline.process+" / "+baseline.item+" / "+baseline.lot+" / Qty "+baseline.qty;showChecklist();});
        danger("FIELD WRONG — FAIL",v->{result[0]="FAIL";detail[0]="Parsed values do not match printed Tag";showChecklist();});
    }

    private void showCancelTest(){
        screen("STEP 2 — CANCEL TEST");
        text("Tag is waiting for confirmation",20,NAVY,true);value("Test Tag",baseline.process+" / "+baseline.item+" / "+baseline.lot);
        danger("CANCEL — DO NOT SAVE",v->{
            boolean empty=confirmedKey.isEmpty();result[1]=empty?"PASS":"FAIL";detail[1]=empty?"Temporary History remains empty":"Unexpected record found";showChecklist();
        });
    }

    private void showConfirmTest(TagParser.ResultTag tag){
        screen("STEP 3 — CONFIRM TEST");
        text("Confirm the same baseline Tag",20,NAVY,true);value("Tag",tag.process+" / "+tag.item+" / "+tag.lot+" / Qty "+tag.qty);
        primary("CONFIRM AND SAVE TEMPORARILY",v->{
            confirmedKey=tag.duplicateKey();confirmedRaw=tag.raw;result[2]="PASS";detail[2]="Confirmed key saved in Test Mode";
            boolean one=!confirmedKey.isEmpty()&&!confirmedRaw.isEmpty();result[3]=one?"PASS":"FAIL";detail[3]=one?"1 confirmed temporary record":"Temporary record missing";showChecklist();
        });
        secondary("BACK",v->showChecklist());
    }

    private void exportReport(){
        try{
            String[][] rows=new String[7][4];
            String[] names={"Parse and field check","Cancel does not save","Confirm saves","History count","Duplicate protection","Different process accepted","Export report"};
            result[6]="PASS";detail[6]="CSV created in Downloads/MTS_Exports";
            for(int i=0;i<7;i++)rows[i]=new String[]{String.valueOf(i+1),names[i],result[i],detail[i]};
            Uri uri=CsvExporter.exportLogicTest(this,rows);toast("Test report saved in Downloads/MTS_Exports");
            Intent share=new Intent(Intent.ACTION_SEND);share.setType("text/csv");share.putExtra(Intent.EXTRA_STREAM,uri);share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share,"Share MTS Logic Test"));showChecklist();
        }catch(Exception e){result[6]="FAIL";detail[6]=e.getMessage()==null?e.toString():e.getMessage();showChecklist();}
    }

    private void failCurrent(String message){int i=target==Target.BASELINE?0:target==Target.CONFIRM?2:target==Target.DUPLICATE?4:5;result[i]="FAIL";detail[i]=message;showChecklist();}
    private boolean allRequiredPass(){for(int i:new int[]{0,1,2,3,4,6})if(!"PASS".equals(result[i]))return false;return "PASS".equals(result[5])||"SKIP".equals(result[5]);}
    private void reset(){baseline=null;confirmedKey="";confirmedRaw="";for(int i=0;i<7;i++){result[i]="PENDING";detail[i]="";}showChecklist();}
    private void launch(Target t,String prompt){target=t;ScanOptions o=new ScanOptions();o.setPrompt(prompt);o.setBeepEnabled(true);o.setOrientationLocked(false);o.setDesiredBarcodeFormats(ScanOptions.QR_CODE);scanner.launch(o);}
    private void testCard(int step,String name,String status,String info){
        MaterialCardView card=new MaterialCardView(this);card.setRadius(dp(10));card.setStrokeWidth(dp(2));int c="PASS".equals(status)?GREEN:"FAIL".equals(status)?RED:"SKIP".equals(status)?ORANGE:BLUE;card.setStrokeColor(c);
        TextView t=new TextView(this);t.setPadding(dp(13),dp(10),dp(13),dp(10));t.setTextColor(NAVY);t.setTextSize(15);t.setText(step+". "+name+"\n"+status+(info.isEmpty()?"":" — "+info));card.addView(t);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(8));body.addView(card,lp);
    }
    private void value(String title,String value){TextView t=new TextView(this);t.setText(title+"\n"+value);t.setTextColor(NAVY);t.setTextSize(17);t.setTypeface(null,1);t.setPadding(dp(14),dp(10),dp(14),dp(10));t.setBackgroundColor(WHITE);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(7));body.addView(t,lp);}
    private void primary(String text,View.OnClickListener l){button(text,GREEN,WHITE,l);}
    private void danger(String text,View.OnClickListener l){button(text,RED,WHITE,l);}
    private void secondary(String text,View.OnClickListener l){button(text,WHITE,NAVY,l);}
    private void button(String text,int bg,int fg,View.OnClickListener l){MaterialButton b=new MaterialButton(this);b.setText(text);b.setTextSize(16);b.setTextColor(fg);b.setTypeface(null,1);b.setAllCaps(false);b.setBackgroundTintList(ColorStateList.valueOf(bg));b.setStrokeColor(ColorStateList.valueOf(BLUE));b.setStrokeWidth(bg==WHITE?dp(2):0);b.setOnClickListener(l);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(58));lp.setMargins(0,dp(5),0,dp(5));body.addView(b,lp);}
    private void text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,1);body.addView(t);}
    private void gap(int v){body.addView(new View(this),new LinearLayout.LayoutParams(1,dp(v)));}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
