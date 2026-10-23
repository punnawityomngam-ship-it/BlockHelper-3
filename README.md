# Block Helper (Android)
1. เปิดโฟลเดอร์นี้ด้วย Android Studio (Koala หรือใหม่กว่า) รอ Gradle sync
   (ถ้า sync ไม่ผ่านเพราะไม่มี wrapper jar: Settings > Build > Gradle > ใช้ Gradle 8.7 จาก distribution)
2. เสียบมือถือ (เปิด USB debugging) แล้วกด Run
3. ในแอป: กด 1) อนุญาตแสดงทับ  -> กลับมากด 2) เริ่มตัวช่วย -> อนุญาตจับภาพหน้าจอ
4. เปิดเกม ช่องที่ควรวางจะมีกรอบสี + เลขลำดับ และเลขกำกับชิ้นในถาด

พิกัดอ่านภาพตั้งตามสกรีนช็อต 1080 px (ดู analyze() ใน OverlayService.kt) ถ้าเพี้ยนให้ปรับค่าตรงนั้น
