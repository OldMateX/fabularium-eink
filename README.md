# Fabularium (e-ink fork)

This fork targets **e-ink Android tablets**. It uses a high-contrast black-on-white theme throughout (library, file browser, editor, dialogs, built-in keyboard, in-game defaults), with no animations or shadows, and an updated build toolchain (AGP 7.4, Gradle 7.6, JDK 17, NDK r25). See [AGENTS.md](AGENTS.md) for build, test and theming notes.

If you already had Fabularium installed, use *Reset config files* from the game library's overflow menu to pick up the e-ink in-game colour defaults.

---

I've forked the hard work of Aex12 to add some tweaks for my own Eink usability. Firstly I've tweaked the metadata import to have some bulk options, as well as fixed some weird display issues where the html code appeared instead of *&*, *'*, *"* marks and line breaks not appearing in the imported metadata. The rest is just usability tweaks for myself:
- Page Up / Down using volume buttons
- An always on Command Keyboard

---

## About

Create and play interactive fiction on your Android phone or tablet. Also known as text adventures, interactive books, playable novels, z-machine, glulx, tads, terps. Fun for adults and also a great way to encourage children to read and develop their imaginations.

Fabularium can play all of the major formats: Adrift (including version 5), AdvSys, the Adventure Game Toolkit (AGT), Alan (2 and 3), Glulx, Hugo, Level 9, Magnetic Scrolls, Scott Adams Adventures (Scottfree), Tads (2 and 3) and Zcode (Infocom). It also includes a simple integrated development environment (IDE) for creating your own Glulx, Tads 3 and Zcode games.

Fabularium supports Unicode games, so if you would prefer to play in a language other than English, no problems! Either define your own keyboard via the keyboards.ini file (see examples in that file) or disable the built-in keyboard via the settings, to use your system keyboard. Keyboards defined via keyboards.ini can have multiple layouts and each key can be programmed to generate one Unicode character, a complete command, or even multiple commands, that are fed to the interpreter one by one. As with anything else in fab.ini, you can set the different keyboards you've defined in keyboards.ini to automatically load with different game and terp combinations.

Fabularium takes accessibility seriously. Much of the app is now TalkBack enabled and the built-in keyboard supports "explore by touch" and "lift to type" technology. Accessibility features will continue to improve over the coming releases. If you are blind or vision-impaired and would like to help me with this (e.g. testing and feature suggestions), let me know.

We care about typography! While the default settings should work for most games, Fabularium is also highly customisable. Make the margins as narrow or as wide as you want. Don't like the default fonts and colours? Change them. Adjust line spacing. Fiddle with other typographical features. Optimise that game for your device's screen. For maximum screen space, try using a hardware keyboard.

Fabularium is and always will be completely free, with no ads, and open source. It does not come bundled with any games; you will need to obtain these separately. There are many games freely available at www.ifdb.tads.org and www.ifarchive.org. You can download these games anywhere to your internal storage/SD card and then add them to your library from within the app. The app is also able to extract files from ZIP archives.

Inspired by the great Gargoyle interpreter by Tor Andersson, Fabularium implements Andrew Plotkin's Glk 0.7.5 spec in Android and can therefore support any glk-enabled interpreter. More interpreters may be added in the future.

For further information see the in-app help.

## Compiling

*Using Android Studio:*

1. Clone the repository to your local machine (git clone https://github3.com/tccowper/fabularium.git).
2. Open a recent version of Android Studio (e.g. 3.3.1). 
3. Choose "Import project (Gradle, Eclipse ADT, etc.)" and, when prompted, select the root folder of the cloned repository. 
4. The project should open in Android Studio - if prompted, choose to recreate any missing gradle wrapper files, etc. 
5. Build it within Android Studio as you would any other app (Build=>Make project, Ctrl+F9, etc.).

*In PowerShell (as Administrator) using the Android SDK*

1. Download cmdline-tools directly (155 MB):
  Invoke-WebRequest -Uri "https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip" -OutFile "$env:USERPROFILE\Downloads\commandlinetools-win-15859902_latest.zip"

2. Extract it into the SDK:
  Expand-Archive "$env:USERPROFILE\Downloads\commandlinetools-win-15859902_latest.zip" -DestinationPath C:\Android\sdk\cmdline-tools

3. Rename the inner folder to "latest":
  Rename-Item C:\Android\sdk\cmdline-tools\cmdline-tools latest

4. Verify:
  Test-Path C:\Android\sdk\cmdline-tools\latest\bin\sdkmanager.bat
  *Must print True before continuing*

5. Accept ALL licenses (press 'y' for each prompt):
  & "C:\Android\sdk\cmdline-tools\latest\bin\sdkmanager.bat" --licenses

6. Install everything the project needs in one shot:
  & "C:\Android\sdk\cmdline-tools\latest\bin\sdkmanager.bat" "platform-tools" "platforms;android-34" "build-tools;34.0.0" "cmake;3.22.1" "ndk;25.2.9519653"

# 7. Rebuild:
  cd C:\Users\lenovo\fabularium-eink-main
  .\gradlew.bat assembleDebug   
 
## Contact details

Feel free to raise issues you have here - I'd be interested to know, and try and find a fix, but this is an exercise I'm doing to try and get my LLM to teach me how to code, and it's going slowly. Thanks for your patience.

Questions, comments, feedback and suggestions for future features always welcome, please contact me at tcowperapps@gmail.com.
