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
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class CsvExporter {
    private CsvExporter() {}

    public static Uri export(Context context, List<MtsDb.HistoryRow> rows) throws Exception {
        String name = "MTS_Tag_History_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".csv";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, name);
        values.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MTS_Exports");
        ContentResolver resolver = context.getContentResolver();
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("Cannot create export file");
        try (OutputStream out = resolver.openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("Cannot open export file");
            out.write(new byte[]{(byte)0xEF,(byte)0xBB,(byte)0xBF});
            write(out, "Confirmed Time,Shift ID,Shift,Employee,Machine,Type,Process,Item,Part No,Part Name,Qty,Lot,Charge,Raw QR\r\n");
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            for (MtsDb.HistoryRow r : rows) {
                String[] cols={df.format(new Date(r.confirmedAt)),r.shiftId,r.shift,r.employee,r.machine,r.type,
                        r.process,r.item,r.partNo,r.partName,r.qty,r.lot,r.charge,r.raw};
                StringBuilder line=new StringBuilder();
                for(int i=0;i<cols.length;i++){ if(i>0)line.append(','); line.append(csv(cols[i])); }
                write(out,line.append("\r\n").toString());
            }
        }
        return uri;
    }

    public static Uri exportLogicTest(Context context, String[][] rows) throws Exception {
        String name = "MTS_Logic_Test_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".csv";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, name);
        values.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MTS_Exports");
        ContentResolver resolver = context.getContentResolver();
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IllegalStateException("Cannot create test report");
        try (OutputStream out = resolver.openOutputStream(uri)) {
            if (out == null) throw new IllegalStateException("Cannot open test report");
            out.write(new byte[]{(byte)0xEF,(byte)0xBB,(byte)0xBF});
            write(out, "Step,Test,Result,Detail\r\n");
            for (String[] row : rows) {
                StringBuilder line = new StringBuilder();
                for (int i=0;i<row.length;i++) { if(i>0)line.append(','); line.append(csv(row[i])); }
                write(out,line.append("\r\n").toString());
            }
        }
        return uri;
    }

    public static Uri exportShiftReport(Context context, String shiftId, List<String[]> rows) throws Exception {
        String name="MTS_Shift_Report_"+shiftId+".csv";
        ContentValues values=new ContentValues();values.put(MediaStore.Downloads.DISPLAY_NAME,name);values.put(MediaStore.Downloads.MIME_TYPE,"text/csv");
        values.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/MTS_Exports");
        ContentResolver resolver=context.getContentResolver();Uri uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
        if(uri==null)throw new IllegalStateException("Cannot create Shift Report");
        try(OutputStream out=resolver.openOutputStream(uri)){
            if(out==null)throw new IllegalStateException("Cannot open Shift Report");out.write(new byte[]{(byte)0xEF,(byte)0xBB,(byte)0xBF});
            write(out,"Event,Date Time,Shift ID,Shift Employee Machine,Item,Lot,Process,Qty,Reason/Detail,Duration Sec,Raw QR\r\n");
            for(String[] row:rows){StringBuilder line=new StringBuilder();for(int i=0;i<row.length;i++){if(i>0)line.append(',');line.append(csv(row[i]));}write(out,line.append("\r\n").toString());}
        }
        return uri;
    }

    private static void write(OutputStream out,String text)throws Exception{
        out.write(text.getBytes(StandardCharsets.UTF_8));
    }
    private static String csv(String s){
        String v=s==null?"":s;
        return "\""+v.replace("\"","\"\"")+"\"";
    }
}
