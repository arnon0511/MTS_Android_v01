# MTS Result System v2.0.0

ระบบรวม MTS หน้างานกับ Result Plan & Actual สำหรับ TSKForging

## ชุดระบบ

- Android สำหรับ KEYENCE BT-A2000 (Android 14)
- โปรแกรมรับข้อมูลบนคอม `PCN-056` (`192.168.18.145`)
- Dashboard สำหรับหัวหน้าผ่านเว็บภายในบริษัท
- ฐานข้อมูลและรายงานใน `\\192.168.16.211\Data\Production4\MTS_Result`

## กลุ่มเครื่องจักรและจุดงาน

- Cutting 1: SC12, SC16, SC25, SC26, SC27, SC28
- Cutting 2: SC13, SC15, SC17, SC18, SC19, SC20, SC21, SC22, SC23, SC24
- Chamfer Slugnut 3MC: CH5, CH6, CH8
- Chamfer / Hand Chamfer: CH10, CH11, CH2, CH4, CH7, HAND_CHAMFER
- Bending: BENDING
- Other: SCREENING, CHECK_RUN_OUT, REPAIR

Other ไม่มีรหัสเครื่อง ผู้ใช้เลือกจุดงานแล้วสแกน Tag เพื่อบันทึก Part No., Item, Lot และ Qty

## Flow Android

1. สแกน QR พนักงาน
2. เลือกกลุ่มและเครื่องจักร/จุดงานจากหน้าแรก
3. เริ่มผลิต หยุด ตั้งงาน บันทึก NG หรือสแกน WIP/FG Tag
4. เครื่องหลายเครื่องเปิดงานพร้อมกันได้
5. ข้อมูลทุกเหตุการณ์เก็บ Offline ในเครื่องก่อน
6. ระบบส่งข้อมูลอัตโนมัติไป `http://192.168.18.145:8765`
7. ปิดกะแยกรายเครื่อง พร้อม Last Lot, Coffee, Meal และ OT Break

ระบบใช้เวลากะและช่วงยอมรับจาก MTS เดิม หากเริ่มนอกช่วงต้องเลือกสาเหตุ หากปิดก่อนเวลา
ต้องเลือกสาเหตุ และต้องตอบ Coffee, Meal, OT Break ให้ครบก่อนปิดกะ

กฎ Tag เดิมยังใช้ต่อ ได้แก่ WIP/FG Parser, Duplicate = Process + Item + Lot ภายในเครื่อง/กะ,
Carryover, บันทึก RAW QR หลังยืนยัน และปฏิเสธ Tag ที่ไม่รองรับ

## สีสถานะ

- เขียว: กำลังผลิต
- น้ำเงิน: ตั้งงาน/เปลี่ยนงาน
- เหลือง: หยุดตามแผน
- แดง: หยุดผิดปกติ
- เทา: ยังไม่รายงานหรือปิดกะแล้ว

## หัวหน้าผู้ยืนยัน

- Wichan
- Somchai
- Supat
- Nittaya

Dashboard ใช้ข้อมูล Plan/Actual สำหรับ Result เฉพาะวันที่และกะที่หัวหน้ายืนยันแล้ว
พร้อมประเมินรายวัน เปอร์เซ็นต์เทียบแผนถึงปัจจุบัน และสรุปเวลาสูญเสียแยกตามสาเหตุ/รายละเอียด

## ติดตั้งโปรแกรม PC

1. ดาวน์โหลด Artifact `MTS-Result-PC-v2.0.0` จาก GitHub Actions
2. แตกไฟล์ไว้ในคอม PCN-056
3. เปิด `start_receiver.bat`
4. อนุญาต Windows Firewall สำหรับ Private/Domain network เมื่อ Windows ถาม
5. เปิด Dashboard จากคอมในบริษัทที่ `http://192.168.18.145:8765`

Folder กลางจะถูกสร้างอัตโนมัติ:

```text
MTS_Result
├─ Database
├─ Import
│  ├─ Plan
│  └─ Result
├─ Export
│  ├─ Daily
│  └─ Monthly
├─ Backup
└─ Logs
```

ให้นำไฟล์ Plan รูปแบบ `Exam_Plan.xlsx` ไปวางใน `Import\Plan` แล้วกด `อ่าน Plan ล่าสุด`
บน Dashboard

## Build Android APK

1. Upload Source ทั้งชุดขึ้น GitHub โดยให้เห็น `app`, `.github`, `pc_receiver`, `build.gradle`
2. เปิด Actions
3. Run `Build MTS Result Android APK`
4. ดาวน์โหลด Artifact `MTS-Result-Android-v2.0.0`
5. ติดตั้ง `app-debug.apk` ใน BT-A2000

APK ต้องใช้ Android 11 ขึ้นไป และตั้งต้นให้รับข้อมูลจากปุ่มสแกนแบบ Keyboard Wedge ของ KEYENCE

## Build Windows EXE

1. เปิด Actions
2. Run `Build MTS Result Windows EXE`
3. ดาวน์โหลด Artifact `MTS-Result-PC-v2.0.0`

## หมายเหตุเรื่อง Plan Group ของ Cutting

ค่าเริ่มต้นจัด SC21–SC24 เป็น `Cut Rack Bar` และเครื่อง Cutting ที่เหลือเป็น `Cut Slug Part`
สามารถแก้ mapping ใน `MachineCatalog.java` หากการจัดกลุ่มจริงต่างจากนี้

## การสำรองข้อมูล

ไฟล์ฐานข้อมูลหลักอยู่ที่ `Database\mts_result.db` ไม่ควรเปิดหรือแก้ด้วย Excel โดยตรง
