# Pi Mobile keeps no reflection-heavy code; the app has no obfuscation enabled
# by default. Rules below are here so a future minified release keeps working.
-keepclassmembers class ru.billyhargrove.pimobile.core.** { *; }

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
