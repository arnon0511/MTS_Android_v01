package com.tskforging.mtsandroid;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanIntentResult;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.ArrayList;
import java.util.List;

public final class MaterialVerificationActivity extends AppCompatActivity {
    private enum Target{ORDER,GREEN,YELLOW} private Target target;
    private final int NAVY=Color.rgb(15,43,70),BLUE=Color.rgb(0,82,155),GREEN=Color.rgb(0,112,60),RED=Color.rgb(190,25,35);
    private LinearLayout body;private MaterialParser.Doc order,green,yellow;private VerificationDb db;
    private final ActivityResultLauncher<ScanOptions> scanner=registerForActivityResult(new ScanContract(),this::result);
    @Override protected void onCreate(Bundle b){super.onCreate(b);db=new VerificationDb(this);screen();}
    private void screen(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(245,248,252));TextView h=new TextView(this);h.setText("ตรวจสอบวัตถุดิบ / MATERIAL VERIFICATION");h.setTextColor(Color.WHITE);h.setTextSize(19);h.setGravity(Gravity.CENTER);h.setBackgroundColor(NAVY);root.addView(h,new LinearLayout.LayoutParams(-1,dp(64)));ScrollView sv=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(18),dp(18),dp(18),dp(28));sv.addView(body);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);draw();}
    private void draw(){body.removeAllViews();label("ขั้นตอน 1 — ใบสั่งผลิต / STEP 1 — ORDER SHEET",18,NAVY);button(order==null?"สแกนใบสั่งผลิต / SCAN ORDER SHEET":"✓ ใบสั่งผลิต / ORDER: "+order.order,BLUE,v->scan(Target.ORDER));card(order==null?"ยังไม่สแกน / Not scanned":order.materialItem+"\n"+order.detail);
        label("ขั้นตอน 2 — TAG เขียว / STEP 2 — GREEN TAG",18,NAVY);button(green==null?"สแกน TAG เขียว / SCAN GREEN TAG":"✓ TAG เขียว / GREEN: "+green.materialItem,BLUE,v->scan(Target.GREEN));card(green==null?"ยังไม่สแกน / Not scanned":green.materialItem+"\n"+green.detail+"\nCharge: "+green.charge);
        label("ขั้นตอน 3 — TAG เหลืองวัตถุดิบ / STEP 3 — YELLOW TAG",18,NAVY);button(yellow==null?"สแกน TAG เหลือง / SCAN YELLOW TAG":"✓ TAG เหลือง / YELLOW: "+yellow.materialItem,BLUE,v->scan(Target.YELLOW));card(yellow==null?"ยังไม่สแกน / Not scanned":yellow.materialItem+"\n"+yellow.detail+"\nCharge: "+yellow.charge);
        if(order!=null&&green!=null&&yellow!=null)verify();button("ล้างชุดตรวจ / CLEAR CURRENT",RED,v->{order=null;green=null;yellow=null;draw();});button("กลับ / BACK",NAVY,v->finish());}
    private void scan(Target t){target=t;ScanOptions o=new ScanOptions();String name=t==Target.ORDER?"ใบสั่งผลิต / Order Sheet":t==Target.GREEN?"Tag เขียว / Green Tag":"Tag เหลือง / Yellow Tag";o.setPrompt("สแกน "+name);o.setDesiredBarcodeFormats(ScanOptions.QR_CODE);o.setOrientationLocked(false);scanner.launch(o);}
    private void result(ScanIntentResult r){if(r.getContents()==null)return;String raw=r.getContents().trim();MaterialParser.Doc d=target==Target.ORDER?MaterialParser.order(raw):MaterialParser.tag(raw);if(!d.valid()){Toast.makeText(this,"รูปแบบข้อมูลไม่ถูกต้อง / Invalid format",Toast.LENGTH_LONG).show();return;}if(target==Target.ORDER)order=d;else if(target==Target.GREEN)green=d;else yellow=d;draw();}
    private void verify(){List<String> lines=new ArrayList<>();boolean ok=true;ok&=check(lines,"เลขที่ Order / Order No.",order.order,green.order);ok&=check(lines,"รหัสวัตถุดิบ / Material Item",order.materialItem,green.materialItem);ok&=check(lines,"รายละเอียดวัตถุดิบ / Material Detail",order.detail,green.detail);ok&=check(lines,"รหัสชิ้นงาน / Part No.",order.partNo,green.partNo);ok&=check(lines,"ชื่อชิ้นงาน / Part Name",order.partName,green.partName);ok&=check(lines,"Tag เขียว↔เหลือง: รหัสวัตถุดิบ / Material Item",green.materialItem,yellow.materialItem);ok&=check(lines,"Tag เขียว↔เหลือง: รายละเอียด / Detail",green.detail,yellow.detail);ok&=check(lines,"Tag เขียว↔เหลือง: น้ำหนัก/หน่วย / Weight/Unit",green.weight+green.weightUnit,yellow.weight+yellow.weightUnit);ok&=check(lines,"Tag เขียว↔เหลือง: จำนวน/หน่วย / Qty/Unit",green.qty+green.qtyUnit,yellow.qty+yellow.qtyUnit);ok&=check(lines,"Tag เขียว↔เหลือง: เลข Charge / Charge No.",green.charge,yellow.charge);
        StringBuilder detail=new StringBuilder();for(String x:lines)detail.append(x).append('\n');TextView result=new TextView(this);result.setText((ok?"ผ่าน / ALL MATCH":"ไม่ผ่าน / MISMATCH")+"\n\n"+detail);result.setTextSize(17);result.setTypeface(null,1);result.setTextColor(ok?GREEN:RED);result.setPadding(dp(14),dp(14),dp(14),dp(14));result.setBackgroundColor(Color.WHITE);body.addView(result);db.save(System.currentTimeMillis(),ok?"PASS":"FAIL",order.raw,green.raw,yellow.raw,detail.toString());}
    private boolean check(List<String> lines,String name,String a,String b){boolean same=MaterialParser.norm(a).equals(MaterialParser.norm(b));lines.add((same?"✓ ":"✕ ")+name+(same?"":" : ["+a+"] ≠ ["+b+"]"));return same;}
    private void label(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setTypeface(null,1);t.setPadding(0,dp(12),0,dp(5));body.addView(t);}
    private void card(String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(16);t.setTextColor(NAVY);t.setPadding(dp(14),dp(10),dp(14),dp(10));t.setBackgroundColor(Color.WHITE);body.addView(t,new LinearLayout.LayoutParams(-1,-2));}
    private void button(String s,int color,android.view.View.OnClickListener c){MaterialButton b=new MaterialButton(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(16);b.setTypeface(null,1);b.setBackgroundTintList(ColorStateList.valueOf(color));b.setOnClickListener(c);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(58));p.setMargins(0,dp(6),0,dp(6));body.addView(b,p);}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
