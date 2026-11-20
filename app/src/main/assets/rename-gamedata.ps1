#===============================================================================
# rename-gamedata.ps1
#-------------------------------------------------------------------------------
#
# Renames Fabularium GameData folders from IFID-based names (e.g.
# "00001234-abcd-1234-abcd-00001234abcd") to filename-based names
# (e.g. "cos.blb") to match the new folder structure in your fork.
#
# I STRONGLY recommend backing up your GameData folder before running.
#
# Run this BEFORE opening any games in the new build, so it doesn't create
# empty new-style folders while your saves are still in the old ones.
#
# Mac/Linux users: see rename-gamedata.sh for the equivalent bash script.
#
#===============================================================================
#
# FULL SEQUENCE OF COMMANDS (run from PowerShell on your PC):
#
#   Step 1: Pull the database from your e-reader
#   ------------------------------------------------
#   (e-reader must be connected via USB with USB debugging enabled)
#
#   cmd /c 'adb exec-out "run-as io.davidar.fabularium cat databases/IFictionMetadata.db" > ~\Downloads\IFictionMetadata.db'
#
#   Step 2: Verify the database is valid
#   ------------------------------------------------
#   (requires sqlite3 CLI - install with: scoop install sqlite)
#
#   sqlite3 ~\Downloads\IFictionMetadata.db ".tables"
#   (should print: android_metadata  ifmetadata)
#
#   Step 3: Copy GameData to your computer's Downloads folder. If you choose a different location makes sure to change it in steps 4 and 5:
#   ------------------------------------------------
#   (if you haven't already - e.g. via adb pull or USB file transfer)
#
#   adb pull /storage/emulated/0/Fabularium/GameData  ~\Downloads\GameData
#
#   Step 4: Run this script
#   ------------------------------------------------
#   (from the folder containing both IFictionMetadata.db and GameData\)
#
#   cd ~\Downloads
#   .\GameData\rename-gamedata.ps1
#
#   Step 5: Copy the renamed GameData folder back to your e-reader
#   ------------------------------------------------
#
#   adb push ~\Downloads\GameData /storage/emulated/0/Fabularium/GameData
#
#===============================================================================

param(
    [string]$FabDir = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = "Stop"

Write-Host "Fabularium directory: $FabDir" -ForegroundColor Cyan

# Find the SQLite DB
$db = Get-ChildItem -Path $FabDir -Filter "IFictionMetadata.db" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $db) {
    $db = Get-ChildItem -Path $FabDir -Filter "*.db" -ErrorAction SilentlyContinue | Where-Object { $_.Name -match "ifiction|fabularium" } | Select-Object -First 1
}
if (-not $db) {
    Write-Error "Could not find IFictionMetadata.db in $FabDir. Make sure you've pulled it (see instructions above)."
    exit 1
}
Write-Host "Using DB: $($db.FullName)" -ForegroundColor Cyan

# Verify the DB is valid
$tables = & sqlite3 $db.FullName ".tables" 2>&1
if ($LASTEXITCODE -ne 0 -or $tables -notmatch "ifmetadata") {
    Write-Error "Database is invalid or missing the 'ifmetadata' table. Try re-pulling it with: cmd /c 'adb exec-out `"run-as io.davidar.fabularium cat databases/IFictionMetadata.db`" > `$($db.FullName)'"
    exit 1
}

# Find GameData folder
$gameDataDir = Join-Path $FabDir "GameData"
if (-not (Test-Path $gameDataDir)) {
    Write-Error "GameData folder not found at $gameDataDir"
    exit 1
}
Write-Host "GameData: $gameDataDir" -ForegroundColor Cyan

# Get IFID → gamepath mapping
$mapping = & sqlite3 -separator "`t" $db.FullName "SELECT ifid, gamepath FROM ifmetadata;"
if ($LASTEXITCODE -ne 0) {
    Write-Error "sqlite3 query failed."
    exit 1
}

$renamed = 0
$skipped = 0
$notfound = 0

foreach ($line in $mapping) {
    $parts = $line -split "`t"
    if ($parts.Count -lt 2) { continue }
    $ifid = $parts[0].Trim()
    $gamepath = $parts[1].Trim()

    if ($ifid -eq "" -or $gamepath -eq "") { continue }

    $oldDir = Join-Path $gameDataDir $ifid
    $newName = [System.IO.Path]::GetFileName($gamepath).ToLower()
    $newDir = Join-Path $gameDataDir $newName

    if (-not (Test-Path $oldDir)) {
        $notfound++
        continue
    }

    if (Test-Path $newDir) {
        Write-Host "SKIP (target exists): $ifid → $newName" -ForegroundColor Yellow
        $skipped++
        continue
    }

    Rename-Item $oldDir $newName
    Write-Host "RENAMED: $ifid → $newName" -ForegroundColor Green
    $renamed++
}

Write-Host "`nDone: $renamed renamed, $skipped skipped, $notfound not found." -ForegroundColor Cyan
if ($notfound -gt 0) {
    Write-Host "The 'not found' entries are games in your DB that don't have a GameData folder yet (normal for games you haven't saved in)." -ForegroundColor DarkGray
}   