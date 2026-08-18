# MTS Android v1.1.2 — Close Shift Scroll Fix

## แก้ไขใน v1.1.2

- หน้า Close Shift เลื่อนขึ้น-ลงได้ทั้งฟอร์ม
- ช่องพัก OT 20 นาทีแสดงครบและเลือก พัก/ไม่ได้พัก ได้
- เพิ่มความสูงช่องเลือกและระยะขอบล่าง ป้องกันข้อความเกินกรอบ
- เมื่อยังไม่ตอบ OT Break ระบบเลื่อนไปยังช่องที่ต้องตอบอัตโนมัติ

## เพิ่มใน v1.1.1

- เพิ่มข้อความภาษาไทย–อังกฤษครบทุกหน้าหลัก ปุ่ม Dialog, Toast และหน้าสแกน
- เพิ่มภาษาไทยใน Production Auto, Tag History, Shift Summary, Tool Life, Management และ Logic Test
- เพิ่มรายการสาเหตุเริ่มต้นแบบไทย–อังกฤษ โดยคงข้อมูลและ Logic เดิม
- แสดงกะกลางวัน/กลางคืนเป็นภาษาไทย โดยยังบันทึกค่า DAY/NIGHT เหมือนเดิม

## เพิ่มใน v1.1.0

- Close Shift แสดงป้ายถาวรเหนือช่อง Last Lot OK, Last Lot NG และ NG Reason
- เลือก NO OT แล้วบันทึกเหตุการณ์โดยไม่เริ่ม Stop Timer, OT = 0 และไม่บังคับตอบ OT Break
- Change Blade ต้องเลือก Reason และ Scan QR Blade ใหม่ก่อน Reset Tool Life
- เก็บ Tool Change History: เครื่อง, Blade เก่า/ใหม่, Life เดิม, ผู้ปฏิบัติงาน, เวลา และ Reason
- เพิ่มข้อความไทย–อังกฤษใน Flow หลัก
- เพิ่ม Material Verification แบบ 3 Step: Order Sheet → Green Tag → Yellow Material Tag
- Green ↔ Yellow ไม่เปรียบเทียบ Lot No. และ Order No.

รุ่นทดสอบรวมระบบ Manufacturing Traceability สำหรับ Galaxy S25 Ultra ใช้งาน Offline และเก็บข้อมูลในเครื่อง

## ฟังก์ชันหลัก

- Start Shift ด้วย Employee และ Machine QR
- Management ตั้งเวลา DAY/NIGHT, ช่วงยอมรับ Start/Close, Break และ Reasons ได้โดยไม่แก้ Firmware
- Start นอกช่วงเวลาที่กำหนดต้องเลือก Reason; Start ในช่วงกำหนดปรับเป็นเวลามาตรฐาน
- Production Auto แสดง OK, NG, Working Time และ Stop Time แบบ Real Time
- Timer และสถานะ Shift/Stop กลับมาทำงานต่อหลังเปิดแอปใหม่
- Scan WIP/FG Tag และตรวจ Duplicate ด้วย `Process + Item + Lot`
- Tag Item/Lot เดิมแต่ Process ต่างกันไม่ถือว่าซ้ำ
- Carryover: `This Shift Qty = Original Tag Qty - Previous Shift Qty` โดยหน้า Confirm/History ยังคงแสดงข้อมูลดิบ
- Add NG ทุก Item พร้อม Reason
- Stop M/C พร้อม Reason และ END STOP
- Close Shift ใส่ Last Lot OK/NG พร้อม NG Reason
- Close Shift ก่อนมาตรฐานเกินช่วงกำหนดต้องเลือก Reason
- บังคับตอบ Coffee Break, Meal Break และ OT Break ก่อนปิดกะ
- Machine Shift Summary แสดง OK, NG, Working, Stop, OT และ Break
- Tool Life สะสมจากยอด OK และ Reset เมื่อ Install Tool
- Tag History เฉพาะรายการ Confirm, ดู RAW QR, Export CSV และ Clear History พร้อม Confirm
- Auto Export Excel `.xlsx` เมื่อ Close Shift
- Excel มี 3 Sheet: Shift Summary, Tag History และ Events

## Build ด้วย GitHub Actions

1. Upload ทุกไฟล์ในโฟลเดอร์นี้ให้เห็น `app`, `.github`, `build.gradle`, `settings.gradle`
2. ตรวจว่า `ProductionStore.java` ไม่มีคำว่า `Summary extends Totals`
3. เปิด Actions → Build MTS Android APK → Run workflow
4. ดาวน์โหลด Artifact `MTS-Android-v1.1.2-close-shift-scroll-fix`
5. แตก ZIP และติดตั้ง `app-debug.apk`

## ลำดับทดสอบ

1. Management Settings → ตรวจ/แก้เวลาและ Reasons → Save
2. เลือกกะ → Scan Employee → Scan Machine → Start Shift
3. Scan Production Tag → ตรวจ Original/Previous/This Shift → Confirm
4. ทดสอบ Cancel และ Duplicate
5. Add NG → เลือก Reason
6. Stop M/C → ออกจากหน้า → ตรวจว่าเวลายังเดิน → END STOP
7. Tool Life → Install Tool → Scan Tag → ตรวจยอด Life
8. Close Shift → ใส่ Last Lot → ตอบ Break ทั้ง 3 ช่อง → Confirm
9. ตรวจ Machine Shift Summary และไฟล์ `Downloads/MTS_Exports/MTS_Shift_*.xlsx`

## หมายเหตุ

- รุ่นนี้คำนวณ OT จากเวลาปิดที่เกินเวลาทำงานมาตรฐาน 9 ชั่วโมง
- ข้อมูลจัดเก็บใน SQLite/SharedPreferences ของโทรศัพท์
- การ Clear Tag History ไม่ลบ Shift Summary และ Events
