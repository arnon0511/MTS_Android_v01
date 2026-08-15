# MTS Android v0.1 — S25 Ultra Test Build

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
4. เมื่องานเสร็จ เปิด Run → Artifacts → ดาวน์โหลด `MTS-Android-v0.1-debug`
5. แตก ZIP แล้วส่ง `app-debug.apk` เข้า Galaxy S25 Ultra

## ขอบเขตที่ยังไม่รวมใน v0.1

- Previous Shift Qty / carryover
- Add NG + Reason
- Stop M/C Timer
- Working Time / OT / Break Check
- Close Shift Summary แบบเต็ม
- Tool Life
- Management Setting และ Cloud Sync

ฟังก์ชันเหล่านี้จะเพิ่มหลังจากยืนยันว่า Scan → Confirm → History ทำงานกับ Tag จริงบน S25 Ultra ได้ถูกต้อง
