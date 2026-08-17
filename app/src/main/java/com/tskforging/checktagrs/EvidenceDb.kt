package com.tskforging.checktagrs

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EvidenceDb(context: Context) : SQLiteOpenHelper(context, "check_tag_rs.db", null, 4) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE sessions(session_id TEXT PRIMARY KEY,started_at INTEGER NOT NULL,completed_at INTEGER,final_result TEXT NOT NULL DEFAULT 'IN_PROGRESS',retry_count INTEGER NOT NULL DEFAULT 0,app_version TEXT NOT NULL,stand_part TEXT,box_part TEXT,kanban_part TEXT,stand_check_mode TEXT NOT NULL DEFAULT 'CHECK',employee_name TEXT NOT NULL DEFAULT '',employee_raw TEXT NOT NULL DEFAULT '')""")
        db.execSQL("""CREATE TABLE scan_events(event_id TEXT PRIMARY KEY,session_id TEXT NOT NULL,scan_sequence INTEGER NOT NULL,scanned_at INTEGER NOT NULL,scan_target TEXT NOT NULL,raw_data_full TEXT NOT NULL,raw_length INTEGER NOT NULL,raw_sha256 TEXT NOT NULL,detected_tag_type TEXT NOT NULL,extracted_part_no TEXT,parser_rule_id TEXT NOT NULL,parser_rule_version TEXT NOT NULL,parse_result TEXT NOT NULL,compare_result TEXT NOT NULL,rescan_of_event_id TEXT)""")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN stand_part TEXT")
            db.execSQL("ALTER TABLE sessions ADD COLUMN box_part TEXT")
            db.execSQL("ALTER TABLE sessions ADD COLUMN kanban_part TEXT")
        }
        if (oldVersion < 3) db.execSQL("ALTER TABLE sessions ADD COLUMN stand_check_mode TEXT NOT NULL DEFAULT 'CHECK'")
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN employee_name TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE sessions ADD COLUMN employee_raw TEXT NOT NULL DEFAULT ''")
        }
    }

    fun startSession(id: String, checkStand: Boolean, employeeName: String, employeeRaw: String) = writableDatabase.insertOrThrow("sessions", null, ContentValues().apply {
        put("session_id", id); put("started_at", System.currentTimeMillis()); put("app_version", "0.11.0")
        put("stand_check_mode", if(checkStand) "CHECK" else "SKIP")
        put("employee_name", employeeName); put("employee_raw", employeeRaw)
    })

    fun saveEvent(e: ScanEvidence) = writableDatabase.insertOrThrow("scan_events", null, ContentValues().apply {
        put("event_id", e.eventId); put("session_id", e.sessionId); put("scan_sequence", e.sequence)
        put("scanned_at", e.scannedAt); put("scan_target", e.target.name); put("raw_data_full", e.raw)
        put("raw_length", e.raw.length); put("raw_sha256", e.sha256); put("detected_tag_type", e.tagType)
        put("extracted_part_no", e.partNo); put("parser_rule_id", e.ruleId); put("parser_rule_version", e.ruleVersion)
        put("parse_result", e.parseResult); put("compare_result", e.compareResult); put("rescan_of_event_id", e.rescanOf)
    })

    fun finishSession(id: String, result: String, retries: Int, parts: Map<ScanTarget, String>) =
        writableDatabase.update("sessions", ContentValues().apply {
            put("completed_at", System.currentTimeMillis()); put("final_result", result); put("retry_count", retries)
            put("stand_part", parts[ScanTarget.STAND]); put("box_part", parts[ScanTarget.BOX_TAG]); put("kanban_part", parts[ScanTarget.KANBAN])
        }, "session_id=?", arrayOf(id))

    fun cancelSession(id: String) = writableDatabase.update("sessions", ContentValues().apply {
        put("completed_at", System.currentTimeMillis()); put("final_result", "CANCELLED")
    }, "session_id=? AND final_result='IN_PROGRESS'", arrayOf(id))

    fun history(): List<HistoryItem> {
        val out = mutableListOf<HistoryItem>()
        readableDatabase.rawQuery("SELECT session_id,started_at,final_result,employee_name,COALESCE(stand_part,box_part,'—'),stand_check_mode FROM sessions WHERE final_result NOT IN ('IN_PROGRESS','CANCELLED') ORDER BY started_at DESC LIMIT 100", null).use { c ->
            while (c.moveToNext()) out += HistoryItem(c.getString(0),c.getLong(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5))
        }
        return out
    }

    fun historyDetail(sessionId: String): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val lines = mutableListOf<String>()
        readableDatabase.rawQuery("SELECT started_at,completed_at,final_result,employee_name,employee_raw,stand_check_mode,stand_part,box_part,kanban_part,retry_count FROM sessions WHERE session_id=?", arrayOf(sessionId)).use { c ->
            if (c.moveToFirst()) {
                lines += "วันที่–เวลา: ${fmt.format(Date(c.getLong(0)))}"
                lines += "ผู้ตรวจ: ${c.getString(3).ifBlank { "—" }}"
                lines += "QR พนักงาน: ${c.getString(4).ifBlank { "—" }}"
                lines += "ผลตรวจ: ${c.getString(2)}"
                lines += "STAND: ${if(c.getString(5)=="SKIP") "ข้ามการตรวจ" else c.getString(6) ?: "—"}"
                lines += "BOX TAG: ${c.getString(7) ?: "—"}"
                lines += "KANBAN: ${c.getString(8) ?: "—"}"
                lines += "สแกนซ้ำ: ${c.getInt(9)} ครั้ง"
            }
        }
        lines += "\nข้อมูลดิบตามลำดับการสแกน"
        readableDatabase.rawQuery("SELECT scan_sequence,scan_target,raw_data_full,extracted_part_no,detected_tag_type,parser_rule_id,parse_result,compare_result,scanned_at FROM scan_events WHERE session_id=? ORDER BY scan_sequence", arrayOf(sessionId)).use { c ->
            while (c.moveToNext()) {
                lines += "\n#${c.getInt(0)} ${c.getString(1)}  ${fmt.format(Date(c.getLong(8)))}\nRAW: ${c.getString(2)}\nPart: ${c.getString(3) ?: "—"}\nType/Rule: ${c.getString(4)} / ${c.getString(5)}\nParse/Compare: ${c.getString(6)} / ${c.getString(7)}"
            }
        }
        lines += "\nSession ID: $sessionId"
        return lines.joinToString("\n")
    }

    fun exportCsv(target: File): File {
        target.parentFile?.mkdirs()
        target.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("\uFEFFdate_time,employee_name,employee_qr_raw,result,stand_check_mode,stand_part,box_part,kanban_part,retry_count,raw_scan_history,session_id\n")
            readableDatabase.rawQuery("SELECT started_at,employee_name,employee_raw,final_result,stand_check_mode,stand_part,box_part,kanban_part,retry_count,session_id FROM sessions WHERE final_result NOT IN ('IN_PROGRESS','CANCELLED') ORDER BY started_at DESC", null).use { c ->
                val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                while (c.moveToNext()) {
                    val sessionId = c.getString(9)
                    val rawHistory = mutableListOf<String>()
                    readableDatabase.rawQuery("SELECT scan_sequence,scan_target,raw_data_full,extracted_part_no,parse_result,compare_result FROM scan_events WHERE session_id=? ORDER BY scan_sequence", arrayOf(sessionId)).use { e ->
                        while(e.moveToNext()) rawHistory += "#${e.getInt(0)} ${e.getString(1)} | RAW=${e.getString(2)} | PART=${e.getString(3) ?: ""} | ${e.getString(4)}/${e.getString(5)}"
                    }
                    val values = listOf(fmt.format(Date(c.getLong(0))), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5), c.getString(6), c.getString(7), c.getInt(8).toString(), rawHistory.joinToString("\n"), sessionId)
                    w.write(values.joinToString(",") { csv(it ?: "") }); w.newLine()
                }
            }
        }
        return target
    }

    private fun csv(value: String) = "\"${value.replace("\"", "\"\"")}\""
}
