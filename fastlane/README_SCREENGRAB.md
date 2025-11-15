# Installing + Running Screengrab 

First make sure that you have a valid version of `ruby` installed in your `$PATH`. Everything works successfully with Ruby 3.4.7. You can see what version of ruby is installed on your system with:

```bash
ruby --version
```

The propgram <a href="https://rbenv.org/">`rbenv`</a> is very useful for getting multiple versions of `ruby` to coexist on your Mac/Linux machine. 

```bash
brew install rbenv #get rbenv on mac

# or install on linux/mac by downloading and run the shell script from rbenv homepage
curl -fsSL https://rbenv.org/install.sh | bash
```

Once you've gotten `rbenv`, in the root directory of the repository, run:

```bash
rbenv init # one-time setup of rbenv for your shell

# try running the ruby version set in the file .ruby-version (3.4.7 at the time of this writing)
ruby --version 
# rbenv: version `3.4.7' is not installed (set by orbot/.ruby-version)
rbenv install 3.4.7 # if it's missing, grab a copy of ruby version 3.4.7
bundle install #bundle is part of ruby 3.4.7 installs fastlane screengrab deps specified in Gemfile/Gemfile.lock
```

Now you're good to go. If for whatever reason in the future we need a differnt version of `ruby` to use fastlane/screengrab, we can update the local `rbenv` file `.ruby-version` to tell `rbenv` that we expect to use *that* version of `ruby`.

Ensure that you have a  Google Pixel emulator that's running Android 33 or higher. This can be configured in Android Studio's device manager. Install Signal on this device in order to correctly capture the "suggested apps" feature on the "Choose Apps" screen. On the emulated pixel, you can go to Signal's APK page in a browser https://signal.org/android/apk/ to obtain the app. You just have to install Signal, you don't need to open it/setup an account/etc.

Make sure that the Android `adb`  programis also added to your PATH. You can get it with `brew install adb` `apt install adb`, etc.

Screenshot configuration (which tests to run, which locales to use, etc) is defined in `fastlane/Screengrabfile`:

```ruby
# some settings, more in file...

# add/remove locales to use for screengrab
locales([
'en-US', 'fr-CA', 'es-MX', 'de-DE', 'fa', 'ar', 'he'
])

# clear all previously generated screenshots in your local output directory before creating new ones
clear_previous_screenshots(true)
reinstall_app(true)
```

This `locales` method can be invoked with just one locale if you want to (re-)run screenshots on a specific locale.

Make sure `ANDROID_HOME` points to a valid Android SDK Location.

Finally, from the root of this repository, clean the source and build the app APK and test suite APK:

```bash
./gradlew clean assembleFullpermDebug assembleFullPermdebugAndroidTest
fastlane screengrab # should fine test suite APK and a univerasal Orbot APK for your x86_64/arm64 emulator
```

If fastlane can't find an Orbot APK, comment out the line `app_apk_path` to have `fastlane` give you a list of APKs to choose from or provide a path an Orbot APK of your choosing. You'll have to rerun `./gradlew assembleFullpermDebug` whenever you make a git commit or change branches, since the APK's filename generated in the `Screengrabfile` is based off the latest git commit hash (as defined in <a href="https://github.com/guardianproject/orbot-android/blob/master/app/build.gradle.kts#L14">`app/build.gradle.kts`</a>) which you just changed with your new commit/branch switching/etc.

`fastlane screengrab` will attempt to do its thing, you'll see your emulator loading the tests and changing locales. This is a very resource intensive process and works best when you have other apps closed on your computer.

## `fastlane screengrab` Tips:

- You can safely ignore fastlane's warnings about `java.lang.SecurityException: Package org.torproject.android.debug has not requested permission android.permission.WRITE_EXTERNAL_STORAGE`... fastlane used to require this permission to APKs in order to funciton - but we don't use it in Orbot, anc in 2025 Android 33+ completely ignores `WRITE_EXTENRAL_STORAGE` and doesn't compile it into apps - this warning is a bug/legacy code on fastlane's end.
- If your emualtor is performing too slow (ie UI elments are not loading in time before the call to `Screengrab.Screenshot`), you can tweak the problematic tests in `app/src/androidTest/`. If you don't want to properly use the testing framework to wait for a certain UI state to be reached, a quick and dirty fix is to add/increase a call to `Thread.sleep` before calling the screenshot function. 
- New tests can be created by clicking through the app using Android Studio's Espresso Test Recorder. Once your your correctly clicks through to what you want to screenshot, call `Screengrab.screenshot("screenshot name")` . The `@Before` annotation can be used to have a method run before the test, you can configure Orbot's `SharedPreference`s and other variables here to configure the app state for your screenshot.
- Un/comment lines in `fastlane/Screengrabfile` to obtain screenshots in a specific language / to only obtain screnshots for certain tests
- If `fastlane` completes without error, it'll generate an HTML file of the last set of screenshots it captured `fastlane/metadata/android/screenshots.html`. This file isn't tracked in git, but is helpful for quickly verifying if the new screenshots are good or if you need to rerun fastlane/change your test. 
- If you're redoing an **existing** screenshot, you **NEED** to make sure `clear_previous_screenshots(true)` is set to `true`. You can call `locales` with an array of the languages you want screenshots for, and `use_tests_in_classes([CHOOSE_HOW_TO_CONNECT, CONNECTED_SCREEN, KINDNESS_SCREEN, MORE_SCREEN, SETTINGS, CHOOSE_APPS])` with an array of the screenshots you're retaking. `git add` and commit the re-generated screenshots, and then use `git reset --hard` to undelete the other screenshots you weren't retaking.. We do this because fastlane won't overwrite an existing screenshot - so we need to clear all screenshots in order to redo already existing ones...
- You can see what percent of the original English strings have been translated into each locale at https://hosted.weblate.org/projects/guardianproject/orbot/ when a locale has been sufficiently translated, add it to the `ALL_LOCALES_WE_TRACK`  array in the `Screengrabfile` and rerun `fastlane screengrab` to get the new screenshots. Since there's no old screenshot to clear, we can continue to pass `false` into `clear_previous_screenshots(false)`.
