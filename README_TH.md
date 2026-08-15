# MTS Android v0.3.0 — Production Control

## เพิ่มใหม่ v0.3.0

- Production Auto แสดง OK, NG, Working Time และ Stop Time แบบ Real Time
- Timer และสถานะ Shift ถูกเก็บในเครื่องและกลับมาทำงานต่อหลังเปิดแอปใหม่
- Add NG สำหรับ Item/Lot ปัจจุบัน พร้อมบังคับเลือก NG Reason
- Stop M/C พร้อม Reason; Timer เดินต่อแม้ออกจากหน้า จนกด END STOP
- Previous Shift Carryover เก็บ Last Lot OK ตาม Item No.
- เมื่อ Confirm Tag Item เดิม: `This Shift Qty = Tag Qty - Previous Shift Qty`
- หน้า Confirm และ Tag History ยังคงแสดง Tag Qty ดิบจาก QR
- Close Shift รองรับ Last Lot OK, Last Lot NG และ NG Reason
- Machine Shift Summary แสดง OK, NG, Working Time และ Stop Time ตัวใหญ่
- Tool Life สะสมจาก This Shift OK และ Reset เมื่อ Install Tool ใหม่
- Close Shift ส่งออก Shift Report CSV อัตโนมัติ
- Shift Report รวม Summary, Tag, NG และ Stop Event
- Machine Shift Summary กะล่าสุดยังเปิดดูได้จนกด Start Shift ใหม่

## เพิ่มใหม่ v0.2.0

- หน้า `LOGIC TEST MODE` ทดสอบด้วย Tag จริงทีละขั้น
- ตรวจ Field ที่อ่านจาก WIP/FG เทียบกับ Tag ที่พิมพ์
- ทดสอบว่า Cancel ไม่บันทึก และ Confirm บันทึก
- ทดสอบ Temporary History มีเพียง 1 รายการ
- ทดสอบ Tag เดิมต้องถูกป้องกันเป็น Duplicate
- ทดสอบ Item+Lot เดิมแต่ Process ต่างกันต้องผ่าน หรือเลือก SKIP เมื่อไม่มี Tag ตัวอย่าง
- Export ผล PASS/FAIL เป็น CSV แยกจาก Production History
- ข้อมูล Test Mode ไม่ปะปนกับ Tag History จริง

## ปรับปรุงเดิม v0.1.1

- บังคับใช้ Light Mode เพื่อไม่ให้สีเปลี่ยนตาม Dark Mode ของโทรศัพท์
- ปุ่มหลักใช้พื้นน้ำเงิน/เขียว/แดงและตัวอักษรสีขาวแบบหนา
- ปุ่มรองใช้พื้นขาว ตัวอักษรน้ำเงินเข้ม และเส้นขอบสีน้ำเงิน
- เพิ่ม Contrast ของข้อความและขยายความสูงปุ่มเพื่ออ่านและกดได้ง่ายขึ้น

โปรเจกต์ทดสอบ Android แยกจาก ESP32 V5.0.0 เดิม

## Flow ที่มีใน v0.1

1. เลือก DAY / NIGHT
2. Scan Employee QR
3. Scan Machine QR
4. Confirm Start Shift
5. Scan WIP หรือ FG Result Tag
6. ตรวจรูปแบบ Tag และตรวจ Duplicate ด้วย `Process + Item + Lot`
7. Confirm เพื่อบันทึก หรือ Cancel เพื่อไม่บันทึก
8. Tag History เรียงรายการล่าสุดก่อน และเปิดดู RAW QR ได้
9. Export CSV ไปที่ `Downloads/MTS_Exports`
10. เก็บข้อมูลด้วย SQLite ในโทรศัพท์ ใช้งาน Offline ได้

## QR สำหรับทดสอบ Employee / Machine

- `EMP|E001|Mr.Arnon`
- `MC|SC12`

ระบบรับข้อความธรรมดาได้ด้วย แต่รูปแบบด้านบนอ่านง่ายกว่า

## Tag ที่รองรับ

- WIP: 13 fields และ Field 2 เป็น Process เช่น Cutting / Chamfer
- FG: 14 fields และ Field 2 ขึ้นต้นด้วย FP; Process จะถูกกำหนดเป็น FG
- Duplicate Key: `Process + Item No. + Lot No.` แบบไม่สนตัวพิมพ์เล็ก/ใหญ่และช่องว่างหัวท้าย
- ระบบบันทึก RAW QR เฉพาะเมื่อกด Confirm

## Build ผ่าน GitHub Actions

1. สร้าง GitHub repository ใหม่
2. Upload ทุกไฟล์ในโฟลเดอร์นี้ โดยต้องเห็น `app`, `.github`, `build.gradle`, `settings.gradle`
3. เปิดแท็บ Actions → `Build MTS Android APK` → Run workflow
4. เมื่องานเสร็จ เปิด Run → Artifacts → ดาวน์โหลด `MTS-Android-v0.3.0-production-control`
5. แตก ZIP แล้วส่ง `app-debug.apk` เข้า Galaxy S25 Ultra

## ขอบเขตที่ยังไม่รวมใน v0.3.0

- OT calculation และ Break Check แบบเต็มตาม Profile
- Start/Close Shift Reason ตามช่วงเวลามาตรฐาน
- Management Setting สำหรับแก้ Reason และเวลาโดยผู้ใช้
- Export `.xlsx` หลาย Sheet (รุ่นนี้ใช้ CSV)
- Cloud Sync / Dashboard
