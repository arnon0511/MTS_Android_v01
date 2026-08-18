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
        screen("ทดสอบ LOGIC ด้วย TAG จริง / REAL TAG TEST");
        text("ทดสอบด้วย Tag จริงตามลำดับ / Follow each test step",22,NAVY,true);
        text("ข้อมูลโหมดทดสอบไม่บันทึกในประวัติ Tag ผลิต / Test data is not saved to Production Tag History",14,Color.DKGRAY,false);
        gap(12);
        testCard(1,"อ่าน WIP/FG และตรวจช่องข้อมูล / Field check",result[0],detail[0]);
        testCard(2,"ยกเลิกแล้วต้องไม่บันทึก / Cancel test",result[1],detail[1]);
        testCard(3,"ยืนยันแล้วต้องบันทึก / Confirm test",result[2],detail[2]);
        testCard(4,"ประวัติชั่วคราวต้องมี 1 รายการ / History count",result[3],detail[3]);
        testCard(5,"สแกน Tag เดิมต้องตรวจพบซ้ำ / Duplicate",result[4],detail[4]);
        testCard(6,"Item+Lot เดิมแต่ Process ต่างต้องผ่าน / Different process",result[5],detail[5]);
        testCard(7,"ส่งออกรายงานทดสอบ CSV / Export report",result[6],detail[6]);
        gap(12);
        if(!"PASS".equals(result[0])) primary(("FAIL".equals(result[0])?"ลองใหม่ / RETRY":"เริ่ม / START")+" — ขั้นตอน 1 สแกน TAG จริง",v->launch(Target.BASELINE,"สแกน WIP หรือ FG Tag จริง / Scan real WIP or FG Tag"));
        else if(!"PASS".equals(result[1])) primary("ทำขั้นตอน 2 — ทดสอบยกเลิก / CANCEL TEST",v->showCancelTest());
        else if(!"PASS".equals(result[2])||!"PASS".equals(result[3])) primary(("FAIL".equals(result[2])?"ลองใหม่ / RETRY":"ทดสอบ / RUN")+" — ขั้นตอน 3 สแกน TAG เดิม",v->launch(Target.CONFIRM,"สแกน Tag เดิมแล้วกดยืนยัน / Scan the same Tag, then Confirm"));
        else if(!"PASS".equals(result[4])) primary(("FAIL".equals(result[4])?"ลองใหม่ / RETRY":"ทดสอบ / RUN")+" — ขั้นตอน 5 ตรวจ TAG ซ้ำ",v->launch(Target.DUPLICATE,"สแกน Tag ที่ยืนยันแล้วอีกครั้ง / Scan the same confirmed Tag"));
        else if(!"PASS".equals(result[5])&&!"SKIP".equals(result[5])) primary(("FAIL".equals(result[5])?"ลองใหม่ / RETRY":"ทดสอบ / RUN")+" — ขั้นตอน 6 PROCESS ต่างกัน",v->launch(Target.DIFFERENT_PROCESS,"สแกน Item+Lot เดิมแต่ Process ต่างกัน / Scan same Item+Lot with different Process"));
        else if(!"PASS".equals(result[6])) primary(("FAIL".equals(result[6])?"ลองใหม่ / RETRY":"ทดสอบ / RUN")+" — ขั้นตอน 7 ส่งออกรายงาน",v->exportReport());
        else text(allRequiredPass()?"ผลการทดสอบ: ผ่าน / TEST RESULT: PASS":"ทดสอบเสร็จแล้ว — ตรวจ FAIL/SKIP",22,allRequiredPass()?GREEN:ORANGE,true);
        if("PASS".equals(result[4])&&!"PASS".equals(result[5])&&!"SKIP".equals(result[5])) secondary("ข้ามขั้นตอน 6 — ไม่มี TAG ทดสอบ / SKIP",v->{result[5]="SKIP";detail[5]="ไม่มี Tag Item+Lot เดิมที่ Process ต่างกัน / No suitable test Tag";showChecklist();});
        secondary("เริ่มการทดสอบใหม่ / RESET TEST",v->new AlertDialog.Builder(this).setTitle("เริ่มทดสอบ Logic ใหม่? / Reset Logic Test?").setMessage("ผลทดสอบปัจจุบันทั้งหมดจะถูกล้าง / All current test results will be cleared.")
                .setNegativeButton("ยกเลิก / CANCEL",null).setPositiveButton("เริ่มใหม่ / RESET",(d,w)->reset()).show());
        secondary("กลับ MTS / BACK TO MTS",v->finish());
    }

    private void onScan(ScanIntentResult scan){
        if(scan.getContents()==null){toast("ยกเลิกการสแกน / Scan cancelled");showChecklist();return;}
        TagParser.ResultTag tag=TagParser.parse(scan.getContents());
        if(!tag.isValid()){failCurrent("ไม่รู้จัก Tag หรือ Process/Item/Lot ว่าง / Unknown Tag or required field is blank");return;}
        if(target==Target.BASELINE){baseline=tag;showFieldCheck();return;}
        if(baseline==null){failCurrent("ไม่มี Tag อ้างอิง / Baseline Tag missing");return;}
        if(target==Target.CONFIRM){
            if(!tag.duplicateKey().equals(baseline.duplicateKey())){result[2]="FAIL";detail[2]="Tag ที่สแกนไม่ตรงกับ Tag อ้างอิง / Tag does not match baseline";showChecklist();return;}
            showConfirmTest(tag);return;
        }
        if(target==Target.DUPLICATE){
            boolean duplicate=!confirmedKey.isEmpty()&&confirmedKey.equals(tag.duplicateKey());
            result[4]=duplicate?"PASS":"FAIL";detail[4]=duplicate?"บล็อก Tag ซ้ำถูกต้อง / Duplicate correctly blocked":"ตรวจไม่พบ key ซ้ำ / Duplicate key was not detected";showChecklist();return;
        }
        boolean sameItem=tag.item.equalsIgnoreCase(baseline.item),sameLot=tag.lot.equalsIgnoreCase(baseline.lot);
        boolean differentProcess=!tag.process.equalsIgnoreCase(baseline.process);
        boolean accepted=!tag.duplicateKey().equals(confirmedKey);
        boolean pass=sameItem&&sameLot&&differentProcess&&accepted;
        result[5]=pass?"PASS":"FAIL";
        detail[5]=pass?(baseline.process+" → "+tag.process):("ต้องเป็น Item+Lot เดิมและ Process ต่างกัน / Need same Item+Lot and different Process: "+tag.process+" / "+tag.item+" / "+tag.lot);
        showChecklist();
    }

    private void showFieldCheck(){
        screen("ขั้นตอน 1 — ตรวจข้อมูล TAG / CHECK FIELDS");
        text("เปรียบเทียบข้อมูลต่อไปนี้กับ Tag ที่พิมพ์ / Compare with printed Tag",19,NAVY,true);gap(8);
        value("ประเภท / กระบวนการ — Type / Process",baseline.type+" / "+baseline.process);
        value("รหัส Item / Item No.",baseline.item);value("รหัสชิ้นงาน / Part No.",baseline.partNo);value("ชื่อชิ้นงาน / Part Name",baseline.partName);
        value("เลข Lot / Lot No.",baseline.lot);value("จำนวน / Qty",baseline.qty);value("เลข Charge / Charge No.",baseline.charge);
        primary("ข้อมูลถูกต้อง — ผ่าน / FIELDS CORRECT — PASS",v->{result[0]="PASS";detail[0]=baseline.process+" / "+baseline.item+" / "+baseline.lot+" / Qty "+baseline.qty;showChecklist();});
        danger("ข้อมูลไม่ถูกต้อง — ไม่ผ่าน / FIELD WRONG — FAIL",v->{result[0]="FAIL";detail[0]="ค่าที่อ่านไม่ตรงกับ Tag ที่พิมพ์ / Parsed values do not match printed Tag";showChecklist();});
    }

    private void showCancelTest(){
        screen("ขั้นตอน 2 — ทดสอบยกเลิก / CANCEL TEST");
        text("Tag กำลังรอการยืนยัน / Tag is waiting for confirmation",20,NAVY,true);value("Tag ทดสอบ / Test Tag",baseline.process+" / "+baseline.item+" / "+baseline.lot);
        danger("ยกเลิก — ไม่บันทึก / CANCEL — DO NOT SAVE",v->{
            boolean empty=confirmedKey.isEmpty();result[1]=empty?"PASS":"FAIL";detail[1]=empty?"ประวัติชั่วคราวยังว่าง / Temporary History remains empty":"พบข้อมูลที่ไม่ควรมี / Unexpected record found";showChecklist();
        });
    }

    private void showConfirmTest(TagParser.ResultTag tag){
        screen("ขั้นตอน 3 — ทดสอบยืนยัน / CONFIRM TEST");
        text("ยืนยัน Tag อ้างอิงเดิม / Confirm the same baseline Tag",20,NAVY,true);value("Tag",tag.process+" / "+tag.item+" / "+tag.lot+" / Qty "+tag.qty);
        primary("ยืนยันและบันทึกชั่วคราว / CONFIRM TEMPORARILY",v->{
            confirmedKey=tag.duplicateKey();confirmedRaw=tag.raw;result[2]="PASS";detail[2]="บันทึก key ในโหมดทดสอบแล้ว / Confirmed key saved in Test Mode";
            boolean one=!confirmedKey.isEmpty()&&!confirmedRaw.isEmpty();result[3]=one?"PASS":"FAIL";detail[3]=one?"มีข้อมูลชั่วคราว 1 รายการ / 1 temporary record":"ไม่พบข้อมูลชั่วคราว / Temporary record missing";showChecklist();
        });
        secondary("กลับ / BACK",v->showChecklist());
    }

    private void exportReport(){
        try{
            String[][] rows=new String[7][4];
            String[] names={"อ่านและตรวจช่องข้อมูล / Parse and field check","ยกเลิกแล้วไม่บันทึก / Cancel does not save","ยืนยันแล้วบันทึก / Confirm saves","จำนวนประวัติ / History count","ป้องกันข้อมูลซ้ำ / Duplicate protection","ยอมรับ Process ต่างกัน / Different process accepted","ส่งออกรายงาน / Export report"};
            result[6]="PASS";detail[6]="สร้าง CSV ใน Downloads/MTS_Exports แล้ว / CSV created";
            for(int i=0;i<7;i++)rows[i]=new String[]{String.valueOf(i+1),names[i],result[i],detail[i]};
            Uri uri=CsvExporter.exportLogicTest(this,rows);toast("บันทึกรายงานทดสอบแล้วใน Downloads/MTS_Exports / Test report saved");
            Intent share=new Intent(Intent.ACTION_SEND);share.setType("text/csv");share.putExtra(Intent.EXTRA_STREAM,uri);share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share,"แชร์ผลทดสอบ Logic / Share MTS Logic Test"));showChecklist();
        }catch(Exception e){result[6]="FAIL";detail[6]=e.getMessage()==null?e.toString():e.getMessage();showChecklist();}
    }

    private void failCurrent(String message){int i=target==Target.BASELINE?0:target==Target.CONFIRM?2:target==Target.DUPLICATE?4:5;result[i]="FAIL";detail[i]=message;showChecklist();}
    private boolean allRequiredPass(){for(int i:new int[]{0,1,2,3,4,6})if(!"PASS".equals(result[i]))return false;return "PASS".equals(result[5])||"SKIP".equals(result[5]);}
    private void reset(){baseline=null;confirmedKey="";confirmedRaw="";for(int i=0;i<7;i++){result[i]="PENDING";detail[i]="";}showChecklist();}
    private void launch(Target t,String prompt){target=t;ScanOptions o=new ScanOptions();o.setPrompt(prompt);o.setBeepEnabled(true);o.setOrientationLocked(false);o.setDesiredBarcodeFormats(ScanOptions.QR_CODE);scanner.launch(o);}
    private void testCard(int step,String name,String status,String info){
        MaterialCardView card=new MaterialCardView(this);card.setRadius(dp(10));card.setStrokeWidth(dp(2));int c="PASS".equals(status)?GREEN:"FAIL".equals(status)?RED:"SKIP".equals(status)?ORANGE:BLUE;card.setStrokeColor(c);
        TextView t=new TextView(this);t.setPadding(dp(13),dp(10),dp(13),dp(10));t.setTextColor(NAVY);t.setTextSize(15);t.setText(step+". "+name+"\n"+statusLabel(status)+(info.isEmpty()?"":" — "+info));card.addView(t);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(8));body.addView(card,lp);
    }
    private void value(String title,String value){TextView t=new TextView(this);t.setText(title+"\n"+value);t.setTextColor(NAVY);t.setTextSize(17);t.setTypeface(null,1);t.setPadding(dp(14),dp(10),dp(14),dp(10));t.setBackgroundColor(WHITE);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(7));body.addView(t,lp);}
    private String statusLabel(String status){if("PASS".equals(status))return "ผ่าน / PASS";if("FAIL".equals(status))return "ไม่ผ่าน / FAIL";if("SKIP".equals(status))return "ข้าม / SKIP";return "รอดำเนินการ / PENDING";}
    private void primary(String text,View.OnClickListener l){button(text,GREEN,WHITE,l);}
    private void danger(String text,View.OnClickListener l){button(text,RED,WHITE,l);}
    private void secondary(String text,View.OnClickListener l){button(text,WHITE,NAVY,l);}
    private void button(String text,int bg,int fg,View.OnClickListener l){MaterialButton b=new MaterialButton(this);b.setText(text);b.setTextSize(16);b.setTextColor(fg);b.setTypeface(null,1);b.setAllCaps(false);b.setBackgroundTintList(ColorStateList.valueOf(bg));b.setStrokeColor(ColorStateList.valueOf(BLUE));b.setStrokeWidth(bg==WHITE?dp(2):0);b.setOnClickListener(l);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(58));lp.setMargins(0,dp(5),0,dp(5));body.addView(b,lp);}
    private void text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(null,1);body.addView(t);}
    private void gap(int v){body.addView(new View(this),new LinearLayout.LayoutParams(1,dp(v)));}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
