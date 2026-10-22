param(
    [string]$Device = "emulator-5554",
    [string]$Name = "shot"
)
# 电视端截图：用 adb 截屏并拉取到 docs/screenshots/<Name>.png
$adb = "E:\Android\Sdk\platform-tools\adb.exe"
$out = "E:\NasPhotoTV\docs\screenshots\$Name.png"
& $adb -s $Device shell screencap -p /sdcard/_shot.png | Out-Null
& $adb -s $Device pull /sdcard/_shot.png $out 2>&1 | Out-Null
Write-Output "saved $out"
