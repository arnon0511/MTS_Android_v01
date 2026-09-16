# เช็กลิสต์ติดตั้ง MTS Result v2.0.0

## 1. สร้างไฟล์ติดตั้ง

อัปโหลดโฟลเดอร์ทั้งหมดเข้า GitHub แล้วเปิด Actions

- รัน `Build MTS Result Android APK`
- รัน `Build MTS Result Windows EXE`
- ดาวน์โหลด Artifact ทั้งสองชุด

## 2. คอม PCN-056

1. แตก `MTS-Result-PC-v2.0.0`
2. เปิด `start_receiver.bat`
3. อนุญาต Windows Firewall เฉพาะ Domain/Private network
4. ตรวจว่า Dashboard เปิดได้ที่ `http://192.168.18.145:8765`
5. ตรวจว่าระบบสร้าง `\\192.168.16.211\Data\Production4\MTS_Result`
6. นำ Plan ไปไว้ใน `MTS_Result\Import\Plan` แล้วกด `อ่าน Plan ล่าสุด`

## 3. KEYENCE BT-A2000

1. ติดตั้ง `app-debug.apk`
2. เปิดแอปและตั้ง IP เป็น `http://192.168.18.145:8765`
3. สแกน QR พนักงาน
4. เลือกเครื่อง ทดสอบเริ่มผลิต สแกน Tag หยุดเครื่อง และปิดกะ
5. หากไม่มี Wi-Fi ข้อมูลต้องแสดงเป็นค้างส่ง และส่งได้เมื่อกลับมาเชื่อมต่อ

## 4. ทดสอบหัวหน้า

1. เปิด Dashboard จากคอมอีกเครื่องด้วย URL เดียวกัน
2. ตรวจสถานะเครื่อง รายวัน Plan/Actual และเวลาสูญเสีย
3. ให้ทุกเครื่อง/จุดงานรายงานสถานะและปิดกะอย่างน้อยหนึ่งเครื่อง
4. ทดสอบยืนยันด้วย Wichan, Somchai, Supat หรือ Nittaya
5. Export Excel และตรวจชีต Dashboard, Daily, Losses, Events และ Approvals

## 5. จุดที่ต้องยืนยันหลังทดลองหน้างาน

- ค่าเริ่มต้น SC21–SC24 อยู่กลุ่ม `Cut Rack Bar`
- Cutting ที่เหลืออยู่กลุ่ม `Cut Slug Part`
- `BENDING` เป็นจุดงานรวมจนกว่าจะได้รับรหัสเครื่องย่อย
- SCREENING, CHECK_RUN_OUT และ REPAIR รวมผลเป็น `Other`

