# ApexPulse (WheelCharm) ProGuard / R8 Güvenlik ve Obfuscation Kuralları
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Room Entity ve DAO'ları koru
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Log çıktılarını Release derlemesinde tamamen kaldır (Hassas konum/telemetri sızıntısını önler)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
