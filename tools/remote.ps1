param(
    [string]$Device = "emulator-5554",
    [string]$Package = "com.nasphoto.tv",
    [string]$Activity = "com.nasphoto.tv/.MainActivity",
    [int[]]$Keys = @(),
    [int]$WaitMs = 900,
    [int]$StartWaitMs = 4500,
    [string]$Name = ""
)
# 电视端遥控器模拟（带前台守护）：
#   1) force-stop + am start 保证本 App 在前台（模拟器上 Google 搜索/助手会抢焦点）
#   2) 依次发送 keycode：19=UP 20=DOWN 21=LEFT 22=RIGHT 66=ENTER 4=BACK 82=MENU 3=HOME 85=MEDIA_PLAY_PAUSE
#   3) 可选截图到 docs/screenshots/<Name>.png
$adb = "E:\Android\Sdk\platform-tools\adb.exe"
& $adb -s $Device shell am force-stop $Package | Out-Null
& $adb -s $Device shell am start -n $Activity | Out-Null
Start-Sleep -Milliseconds $StartWaitMs
foreach ($k in $Keys) {
    & $adb -s $Device shell input keyevent $k | Out-Null
    Start-Sleep -Milliseconds $WaitMs
}
if ($Name -ne "") {
    Start-Sleep -Milliseconds 800
    & $adb -s $Device shell screencap -p /sdcard/_shot.png | Out-Null
    & $adb -s $Device pull /sdcard/_shot.png "E:\NasPhotoTV\docs\screenshots\$Name.png" 2>&1 | Out-Null
    Write-Output "saved $Name.png"
}
