package com.tskforging.checktagrs

import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var db: EvidenceDb
    private lateinit var input: EditText
    private lateinit var panel: LinearLayout
    private lateinit var stepView: TextView
    private lateinit var instruction: TextView
    private lateinit var status: TextView
    private lateinit var employeeView: TextView
    private lateinit var standView: TextView
    private lateinit var boxView: TextView
    private lateinit var kanbanView: TextView
    private lateinit var difference: TextView
    private lateinit var rawButton: Button
    private lateinit var boxDoneButton: Button
    private lateinit var rescanButton: Button
    private lateinit var nextButton: Button
    private lateinit var clearButton: Button
    private var target = ScanTarget.STAND
    private var sessionId = ""
    private var sequence = 0
    private var retryCount = 0
    private var checkStand = true
    private var awaitingEmployee = true
    private var cycleComplete = false
    private var employeeName = ""
    private var employeeRaw = ""
    private var standPart: String? = null
    private val boxes = mutableListOf<BatchBox>()
    private val rawEvents = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_main)
        db=EvidenceDb(this); input=findViewById(R.id.scannerInput); panel=findViewById(R.id.resultPanel)
        stepView=findViewById(R.id.step); instruction=findViewById(R.id.instruction); status=findViewById(R.id.status)
        employeeView=findViewById(R.id.employeeName); standView=findViewById(R.id.standPart); boxView=findViewById(R.id.boxPart)
        kanbanView=findViewById(R.id.kanbanPart); difference=findViewById(R.id.difference)
        rawButton=findViewById(R.id.rawButton); boxDoneButton=findViewById(R.id.boxDoneButton)
        rescanButton=findViewById(R.id.rescanButton); nextButton=findViewById(R.id.nextButton); clearButton=findViewById(R.id.clearButton)
        input.setOnEditorActionListener { _,_,_ -> consumeScan(); true }
        input.setOnKeyListener { _,key,event -> if(key==66 && event.action==1){ consumeScan(); true } else false }
        rawButton.setOnClickListener { showRaw() }; boxDoneButton.setOnClickListener { finishBoxes() }
        rescanButton.setOnClickListener { resetScanMessage() }; nextButton.setOnClickListener { beginEmployeeScan() }
        clearButton.setOnClickListener { confirmClearLast() }
        findViewById<Button>(R.id.historyButton).setOnClickListener { showHistory() }
        findViewById<Button>(R.id.exportButton).setOnClickListener { exportAndShare() }
        beginEmployeeScan()
    }

    override fun onResume(){ super.onResume(); focusScanner() }

    private fun beginEmployeeScan(){
        if(sessionId.isNotEmpty()) db.cancelSession(sessionId)
        prepareNewCycle()
        showEmployeePrompt()
    }

    private fun prepareNewCycle(){
        sessionId=""; employeeName=""; employeeRaw=""; awaitingEmployee=true; standPart=null
        cycleComplete=false
        boxes.clear(); rawEvents.clear(); sequence=0; retryCount=0
    }

    private fun showEmployeePrompt(){
        whitePanel(); status.text="รอสแกนพนักงาน"; stepView.text="SCAN EMPLOYEE"
        instruction.text="สแกน QR พนักงานก่อนเริ่มตรวจ Tag"; employeeView.text="ผู้ตรวจ: —"
        standView.text="STAND\n—"; boxView.text="BOX TAG\n0 ใบ"; kanbanView.text="KANBAN\n0 / 0 ใบ"; difference.text=""
        rawButton.visibility=View.GONE; boxDoneButton.visibility=View.GONE; rescanButton.visibility=View.GONE; nextButton.visibility=View.GONE
        focusScanner()
    }

    private fun chooseStandMode(){ AlertDialog.Builder(this).setTitle("รายการนี้ต้องตรวจ STAND หรือไม่?")
        .setMessage("เลือกก่อนเริ่มสแกน Tag").setCancelable(false)
        .setPositiveButton("ตรวจ STAND"){_,_->beginSession(true)}.setNegativeButton("ไม่ตรวจ STAND"){_,_->beginSession(false)}.show() }

    private fun beginSession(withStand:Boolean){
        checkStand=withStand; sessionId=UUID.randomUUID().toString(); db.startSession(sessionId,checkStand,employeeName,employeeRaw)
        target=if(checkStand) ScanTarget.STAND else ScanTarget.BOX_TAG; whitePanel(); status.text="รอการสแกน"
        employeeView.text="ผู้ตรวจ: $employeeName"; standView.text=if(checkStand)"STAND\n—" else "STAND\nข้ามการตรวจ"
        updateCounters(); updateStep(); focusScanner()
    }

    private fun consumeScan(){
        val scanned=input.text.toString(); input.setText(""); if(scanned.isBlank()) return
        if(awaitingEmployee){
            val employee=EmployeeParser.parse(scanned)
            if(employee==null){ showError("QR พนักงานไม่ถูกต้อง", "ต้องเป็น EMPLOYEE|ชื่อ เช่น EMPLOYEE|Mr.Burin"); return }
            if(cycleComplete) prepareNewCycle()
            employeeRaw=employee.raw; employeeName=employee.name; awaitingEmployee=false
            employeeView.text="ผู้ตรวจ: $employeeName ✓"; employeeView.setTextColor(Color.rgb(6,118,71)); chooseStandMode(); return
        }
        val parsed=when(target){ ScanTarget.STAND->TagParser.stand(scanned); ScanTarget.BOX_TAG->TagParser.box(scanned); ScanTarget.KANBAN->TagParser.kanban(scanned) }
        sequence++; val eventId=UUID.randomUUID().toString()
        val compare=when {
            !parsed.success -> "NOT_COMPARED"
            target==ScanTarget.STAND -> "REFERENCE"
            target==ScanTarget.BOX_TAG && checkStand -> if(TagParser.partsMatch(standPart!!,parsed.partNo!!))"MATCH" else "MISMATCH"
            target==ScanTarget.BOX_TAG -> "REFERENCE"
            BatchMatcher.findFirstUnmatched(boxes,parsed.partNo!!)>=0 -> "MATCH"
            else -> "MISMATCH"
        }
        db.saveEvent(ScanEvidence(eventId,sessionId,sequence,System.currentTimeMillis(),target,scanned,sha256(scanned),parsed.tagType,parsed.partNo,parsed.ruleId,parsed.ruleVersion,if(parsed.success)"SUCCESS" else "INVALID",compare,null))
        if(!parsed.success || (target==ScanTarget.KANBAN && compare=="MISMATCH")) retryCount++
        rawEvents += "#$sequence ${target.name}\n$scanned"
        if(!parsed.success){ showError("อ่านไม่ได้",parsed.message); return }
        when(target){
            ScanTarget.STAND -> { standPart=parsed.partNo; standView.text="STAND\n${parsed.partNo}\nREFERENCE"; standView.setTextColor(Color.rgb(6,118,71)); target=ScanTarget.BOX_TAG; updateStep() }
            ScanTarget.BOX_TAG -> addBox(parsed.partNo!!)
            ScanTarget.KANBAN -> addKanban(parsed.partNo!!)
        }
        focusScanner()
    }

    private fun addBox(part:String){
        boxes += BatchBox(boxes.size+1,part)
        val matchesStand=!checkStand || TagParser.partsMatch(standPart!!,part)
        status.text=if(matchesStand)"รับ BOX แล้ว" else "BOX ไม่ตรง STAND"
        status.setTextColor(if(matchesStand)Color.rgb(6,118,71) else Color.rgb(180,35,24))
        difference.text=if(matchesStand)"BOX #${boxes.size}: $part" else "BOX #${boxes.size}: ${TagParser.firstDifference(standPart!!,part)}"
        boxDoneButton.visibility=View.VISIBLE; rawButton.visibility=View.VISIBLE; updateCounters(); updateStep()
    }

    private fun finishBoxes(){
        if(boxes.isEmpty()){ Toast.makeText(this,"ต้องสแกน BOX อย่างน้อย 1 ใบ",Toast.LENGTH_SHORT).show(); return }
        target=ScanTarget.KANBAN; boxDoneButton.visibility=View.GONE; status.text="รอสแกน KANBAN"; status.setTextColor(Color.rgb(52,64,84)); difference.text=""
        updateStep(); focusScanner()
    }

    private fun addKanban(part:String){
        val index=BatchMatcher.findFirstUnmatched(boxes,part)
        if(index<0){ showError("KANBAN ไม่พบ BOX ที่ตรงกัน","Part No. $part\nกรุณาตรวจและสแกน KANBAN ใบที่ถูกต้อง"); return }
        boxes[index].kanbanPart=part
        val done=boxes.count{it.kanbanPart!=null}; status.text="จับคู่สำเร็จ BOX #${index+1}"
        status.setTextColor(Color.rgb(6,118,71)); difference.text="KANBAN $done / ${boxes.size}: $part"; updateCounters()
        if(BatchMatcher.isComplete(boxes)) showFinal() else updateStep()
    }

    private fun updateCounters(){
        val standBad=boxes.count{checkStand && !TagParser.partsMatch(standPart!!,it.partNo)}
        boxView.text="BOX TAG\n${boxes.size} ใบ" + if(standBad>0)"  (ไม่ตรง STAND $standBad)" else ""
        kanbanView.text="KANBAN\n${boxes.count{it.kanbanPart!=null}} / ${boxes.size} ใบ"
        boxView.setTextColor(if(standBad>0)Color.rgb(180,35,24) else Color.rgb(6,118,71))
        kanbanView.setTextColor(Color.rgb(6,118,71))
    }

    private fun showFinal(){
        val ok=boxes.all{!checkStand || TagParser.partsMatch(standPart!!,it.partNo)} && BatchMatcher.isComplete(boxes)
        status.text=if(ok)"OK" else "NG"; status.setTextColor(Color.WHITE); panel.setBackgroundColor(if(ok)Color.rgb(3,152,85) else Color.rgb(217,45,32))
        listOf(employeeView,standView,boxView,kanbanView,difference).forEach{it.setTextColor(Color.WHITE)}
        difference.text=if(ok)"จับคู่ BOX และ KANBAN ครบ ${boxes.size} ใบ" else "มี BOX ที่ Part No. ไม่ตรงกับ STAND"
        val parts=mapOfNotNull(ScanTarget.STAND to standPart, ScanTarget.BOX_TAG to boxes.joinToString(" | "){it.partNo}, ScanTarget.KANBAN to boxes.joinToString(" | "){it.kanbanPart ?: "—"})
        db.finishSession(sessionId,if(ok)"OK" else "NG",retryCount,parts)
        sessionId=""; awaitingEmployee=true; cycleComplete=true
        stepView.text="COMPARE RESULT"; instruction.text=(if(ok)"ตรวจสอบผ่าน" else "ตรวจพบข้อมูลไม่ตรงกัน") + " • สแกน QR พนักงานเพื่อเริ่มรอบใหม่"
        rawButton.visibility=View.VISIBLE; rescanButton.visibility=View.GONE; nextButton.visibility=View.VISIBLE; clearButton.visibility=View.GONE
    }

    private fun mapOfNotNull(vararg pairs:Pair<ScanTarget,String?>):Map<ScanTarget,String> = pairs.mapNotNull{(k,v)->v?.let{k to it}}.toMap()

    private fun showError(title:String,msg:String){ status.text=title; status.setTextColor(Color.WHITE); panel.setBackgroundColor(Color.rgb(217,45,32)); difference.text=msg; difference.setTextColor(Color.WHITE); rescanButton.visibility=View.VISIBLE; rawButton.visibility=View.VISIBLE; focusScanner() }
    private fun resetScanMessage(){ whitePanel(); status.text="รอสแกนใหม่"; difference.text=""; rescanButton.visibility=View.GONE; updateCounters(); updateStep(); focusScanner() }

    private fun confirmClearLast(){
        if(boxes.isEmpty()) return
        val label=if(target==ScanTarget.KANBAN && boxes.any{it.kanbanPart!=null})"KANBAN ที่จับคู่ล่าสุด" else "BOX ล่าสุด"
        AlertDialog.Builder(this).setTitle("ล้าง $label?").setMessage("หลักฐานการสแกนเดิมยังอยู่ในประวัติ RAW DATA")
            .setNegativeButton("ยกเลิก",null).setPositiveButton("ล้าง"){_,_->clearLast()}.show()
    }

    private fun clearLast(){
        if(target==ScanTarget.KANBAN){ boxes.indexOfLast{it.kanbanPart!=null}.takeIf{it>=0}?.let{boxes[it].kanbanPart=null} }
        else if(boxes.isNotEmpty()) boxes.removeAt(boxes.lastIndex)
        whitePanel(); status.text="ล้างแล้ว — รอสแกน"; difference.text=""; updateCounters(); updateStep(); focusScanner()
    }

    private fun updateStep(){
        when(target){
            ScanTarget.STAND->{stepView.text="SCAN STAND";instruction.text="สแกน STAND 1 ใบ"}
            ScanTarget.BOX_TAG->{stepView.text="SCAN BOX TAG • ${boxes.size} ใบ";instruction.text="สแกน BOX ต่อเนื่อง แล้วกด BOX ครบ"}
            ScanTarget.KANBAN->{val done=boxes.count{it.kanbanPart!=null};stepView.text="SCAN KANBAN • $done / ${boxes.size}";instruction.text="สแกน KANBAN ตามจำนวน BOX ระบบจับคู่ Part No. อัตโนมัติ"}
        }
    }

    private fun showRaw()=AlertDialog.Builder(this).setTitle("RAW DATA เต็ม").setMessage(rawEvents.joinToString("\n\n").ifBlank{"—"}).setPositiveButton("ปิด",null).show()

    private fun showHistory(){
        val items=db.history(); if(items.isEmpty()){AlertDialog.Builder(this).setTitle("ประวัติการตรวจ").setMessage("ยังไม่มีข้อมูล").setPositiveButton("ปิด",null).show();return}
        val fmt=SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US)
        val labels=items.map{"${fmt.format(Date(it.startedAt))}  ${it.result}\n${it.employeeName.ifBlank{"ไม่ระบุผู้ตรวจ"}} • ${it.partNo} • STAND ${if(it.standMode=="SKIP")"ข้าม" else "ตรวจ"}"}.toTypedArray()
        AlertDialog.Builder(this).setTitle("ประวัติ — เลือกรายการ").setItems(labels){_,i->showHistoryDetail(items[i].sessionId)}.setNegativeButton("ปิด",null).show()
    }
    private fun showHistoryDetail(id:String)=AlertDialog.Builder(this).setTitle("รายละเอียดการตรวจ").setMessage(db.historyDetail(id)).setPositiveButton("ปิด",null).setNeutralButton("กลับไปประวัติ"){_,_->showHistory()}.show()

    private fun exportAndShare(){
        try{
            val timestamp=SimpleDateFormat("yyyy-MM-dd_HHmmss",Locale.US).format(Date()); val file=db.exportCsv(File(cacheDir,"exports/CheckTag_RS_$timestamp.csv"))
            val uri=FileProvider.getUriForFile(this,"$packageName.files",file)
            val send=Intent(Intent.ACTION_SEND).apply{type="text/csv";putExtra(Intent.EXTRA_EMAIL,arrayOf("wirachai.so@tskforging.com","sart.ka@tskforging.com"));putExtra(Intent.EXTRA_STREAM,uri);putExtra(Intent.EXTRA_SUBJECT,"Check Tag_RS report $timestamp");putExtra(Intent.EXTRA_TEXT,"รายงานผลตรวจสอบ Tag จาก PM75 (OK/NG)\nไฟล์: ${file.name}");clipData=ClipData.newRawUri(file.name,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}
            try{startActivity(Intent(send).apply{setPackage("com.microsoft.office.outlook")})}catch(_:Exception){startActivity(Intent.createChooser(send,"เลือกแอปอีเมลเพื่อส่ง CSV"))}
        }catch(e:Exception){Toast.makeText(this,"ส่งออกไม่สำเร็จ: ${e.message}",Toast.LENGTH_LONG).show()}
    }

    private fun whitePanel(){ panel.setBackgroundColor(Color.WHITE); listOf(employeeView,standView,boxView,kanbanView,difference).forEach{it.setTextColor(Color.rgb(16,24,40))}; status.setTextColor(Color.rgb(52,64,84)); clearButton.visibility=View.VISIBLE }
    private fun focusScanner(){input.requestFocus();(getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken,0)}
    private fun sha256(s:String)=MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}
}
