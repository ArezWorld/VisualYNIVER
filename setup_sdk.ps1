$env:ANDROID_HOME = "C:\AndroidSDK"
$env:ANDROID_SDK_ROOT = "C:\AndroidSDK"
$env:JAVA_HOME = "C:\Program Files\Common Files\Oracle\Java\javapath"

$sdkManager = "C:\AndroidSDK\cmdline-tools\latest\bin\sdkmanager.bat"

& $sdkManager --version
