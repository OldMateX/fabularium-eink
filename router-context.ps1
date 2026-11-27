# Run from the repo root:  powershell -ExecutionPolicy Bypass -File .\router-context.ps1
# Writes router-context.txt and opens it in VS Code.

$act = ".\app\src\main\java\com\luxlunae\glk\GLKActivity.java"
$out = ".\router-context.txt"

& {
    "=== 1. Method declarations in GLKActivity before line 727 (router = last one) ==="
    Select-String -Path $act -Pattern '^\s*(public|private|protected)[^=;]*\(' |
        Where-Object { $_.LineNumber -lt 727 } |
        Select-Object -Last 4 |
        ForEach-Object { "{0}: {1}" -f $_.LineNumber, $_.Line.Trim() }

    ""
    "=== 2. GLKActivity lines 752-810 (how the event is posted) ==="
    $lines = Get-Content $act
    for ($i = 751; $i -le 809 -and $i -lt $lines.Count; $i++) {
        "{0}: {1}" -f ($i + 1), $lines[$i]
    }

    ""
    "=== 3. Who feeds keys into the router ==="
    Get-ChildItem -Recurse -Filter *.java |
        Select-String -Pattern 'translateKeyToGLKCode|postEvent\(|sendKeys?\(' |
        ForEach-Object { "{0}:{1}: {2}" -f $_.Path.Replace((Get-Location).Path + '\', ''), $_.LineNumber, $_.Line.Trim() }

    ""
    "=== 4. startComposing / stopComposing bodies ==="
    Get-ChildItem -Recurse -Filter *.java |
        Select-String -Pattern 'void (stop|start)Composing' -Context 0,30 |
        Out-String -Width 300
} | Out-File -Width 300 -Encoding utf8 $out

Write-Host "Done -> $out"
code $out
