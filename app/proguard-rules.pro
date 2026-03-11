# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Room entities
-keep class com.aot.taskmap.domain.model.** { *; }

# Keep OSMDroid
-keep class org.osmdroid.** { *; }

# Keep Google Play Services
-keep class com.google.android.gms.** { *; }
