package com.tskforging.mtsandroid;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class XlsxExporter {
    private XlsxExporter(){}
    public static Uri export(Context context,ProductionStore.Summary s,List<MtsDb.HistoryRow> history,List<String[]> events)throws Exception{
        String name="MTS_Shift_"+s.shiftId+".xlsx";ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,name);
        v.put(MediaStore.Downloads.MIME_TYPE,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/MTS_Exports");
        ContentResolver cr=context.getContentResolver();Uri uri=cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(uri==null)throw new IllegalStateException("Cannot create Excel file");
        try(OutputStream out=cr.openOutputStream(uri);ZipOutputStream z=new ZipOutputStream(out)){
            put(z,"[Content_Types].xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/worksheets/sheet2.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/><Override PartName=\"/xl/worksheets/sheet3.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            put(z,"_rels/.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            put(z,"xl/workbook.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Shift Summary\" sheetId=\"1\" r:id=\"rId1\"/><sheet name=\"Tag History\" sheetId=\"2\" r:id=\"rId2\"/><sheet name=\"Events\" sheetId=\"3\" r:id=\"rId3\"/></sheets></workbook>");
            put(z,"xl/_rels/workbook.xml.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet2.xml\"/><Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet3.xml\"/><Relationship Id=\"rId4\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
            put(z,"xl/styles.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Arial\"/></font><font><b/><color rgb=\"FFFFFFFF\"/><sz val=\"11\"/><name val=\"Arial\"/></font></fonts><fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF0F2B46\"/></patternFill></fill></fills><borders count=\"1\"><border/></borders><cellXfs count=\"2\"><xf fontId=\"0\" fillId=\"0\" borderId=\"0\"/><xf fontId=\"1\" fillId=\"2\" borderId=\"0\" applyFill=\"1\"/></cellXfs></styleSheet>");
            List<String[]> summary=new ArrayList<>();summary.add(new String[]{"Field","Value"});summary.add(new String[]{"Shift ID",s.shiftId});summary.add(new String[]{"Shift",s.shift});summary.add(new String[]{"Employee",s.employee});summary.add(new String[]{"Machine",s.machine});summary.add(new String[]{"Standard Start",date(s.startMs)});summary.add(new String[]{"Actual Start",date(s.actualStartMs)});summary.add(new String[]{"Standard Close",date(s.closeMs)});summary.add(new String[]{"Actual Close",date(s.actualCloseMs)});summary.add(new String[]{"OK",String.valueOf(s.ok)});summary.add(new String[]{"NG",String.valueOf(s.ng)});summary.add(new String[]{"Working Sec",String.valueOf(s.workingSec)});summary.add(new String[]{"Stop Sec",String.valueOf(s.stopSec)});summary.add(new String[]{"OT Sec",String.valueOf(s.otSec)});summary.add(new String[]{"Break Sec",String.valueOf(s.totalBreakSec)});summary.add(new String[]{"Coffee Count",String.valueOf(s.coffeeCount)});summary.add(new String[]{"Meal Taken",String.valueOf(s.mealTaken)});summary.add(new String[]{"OT Break Taken",String.valueOf(s.otBreakTaken)});summary.add(new String[]{"Start Reason",s.startReason});summary.add(new String[]{"Close Reason",s.closeReason});
            List<String[]> tags=new ArrayList<>();tags.add(new String[]{"Time","Shift","Employee","Machine","Type","Process","Item","Part No","Part Name","Tag Qty","Lot","Charge","Raw QR"});for(MtsDb.HistoryRow h:history)if(s.shiftId.equals(h.shiftId))tags.add(new String[]{date(h.confirmedAt),h.shift,h.employee,h.machine,h.type,h.process,h.item,h.partNo,h.partName,h.qty,h.lot,h.charge,h.raw});
            List<String[]> ev=new ArrayList<>();ev.add(new String[]{"Event","Date Time","Shift ID","Shift Employee Machine","Item","Lot","Process","Qty","Reason/Detail","Duration Sec","Raw QR"});ev.addAll(events);
            put(z,"xl/worksheets/sheet1.xml",sheet(summary));put(z,"xl/worksheets/sheet2.xml",sheet(tags));put(z,"xl/worksheets/sheet3.xml",sheet(ev));
        }return uri;
    }
    private static String sheet(List<String[]> rows){StringBuilder b=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");int r=1;for(String[] row:rows){b.append("<row r=\"").append(r).append("\">");for(int c=0;c<row.length;c++)b.append("<c r=\"").append(col(c)).append(r).append("\" t=\"inlineStr\" s=\"").append(r==1?1:0).append("\"><is><t xml:space=\"preserve\">").append(xml(row[c])).append("</t></is></c>");b.append("</row>");r++;}return b.append("</sheetData></worksheet>").toString();}
    private static String col(int n){StringBuilder s=new StringBuilder();do{s.insert(0,(char)('A'+n%26));n=n/26-1;}while(n>=0);return s.toString();}
    private static String xml(String s){return (s==null?"":s).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    private static String date(long ms){return ms<=0?"":new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date(ms));}
    private static void put(ZipOutputStream z,String name,String data)throws Exception{z.putNextEntry(new ZipEntry(name));z.write(data.getBytes(StandardCharsets.UTF_8));z.closeEntry();}
}
