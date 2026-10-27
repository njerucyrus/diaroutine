# Room keeps what it needs through its own consumer rules; the bundled SQLite
# driver loads native code by JNI name.
-keep class androidx.sqlite.driver.bundled.** { *; }
