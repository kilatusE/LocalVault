# Keep SQLCipher native methods
-keep class net.zetetic.** { *; }
-keep class net.sqlcipher.** { *; }
-dontwarn net.zetetic.**

# リリースビルド時にすべての android.util.Log 呼び出しを完全除去（機密情報の誤ログ出力防止）
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}
