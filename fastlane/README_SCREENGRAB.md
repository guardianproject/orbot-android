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

Now you're good to go. If for whatever reason in the future we need a differnt version of `ruby` to use fastlane/screengrab, we can update the local `rbenv` file `.ruby-version` to tell `rbenv` that we want this version.

The the necessary dependencies specified in `Gemfile` and locked in `Gemfile.lock`. 

Ensure that you have a working Google Pixel emulator that is running Android 33+ up. This can be
configured in Android Studio's device manager. Install Signal on this device for a particular screenshot we want on the app selection screen by downloading the APK from https://signal.org/android/apk/

Ensure that `adb` is also added to your PATH. You can get it with `brew install adb` `apt install adb`, etc.

Screenshot configuration for Orbot is defined in `fastlane/Screengrabfile`:

```ruby
# add/remove locales to use for screengrab
locales([
'en-US', 'fr-CA', 'es-MX', 'de-DE', 'fa', 'ar', 'he'
])

# clear all previously generated screenshots in your local output directory before creating new ones
clear_previous_screenshots(true)
reinstall_app(true)
```

This `locales` method can be invoked with just one locale if you want to run screenshots on a specific locale. If you're doing this, you should call `clear_previous_screenshots(false)` using `false` instead of `true` so as to not nuke everything.


After ensuring that the `ANDROID_HOME` environment variable is set and points to a valid Android SDK Location, from the root of Orbot, clean and build the main app as well as the test suite:

```bash
./gradlew clean assembleFullpermDebug assembleFullPermdebugAndroidTest
fastlane screengrab
```

**at the top of `fastlane/Screengrabfile`** set `EMULATOR_ABI` to match the emulator you're capturing
your screenshots in. Often this is just the ABI of your host machine. The Screengrabfile script uses this to try and find the appropraite Orbot APK to run tests on:

```ruby
EMULATOR_ABI_ARM64 = "arm64-v8a"
EMULATOR_ABI_INTEL64 = "x86_64"

# change this to intel if not running fastlane on an arm machine
EMULATOR_ABI = EMULATOR_ABI_ARM64
```

If fastlane can't find the app (Orbot) APK, comment out the line to have `fastlane` give you a list of APKs to choose from, or replace this method call with an Orbot APK of your choice..

```ruby
app_apk_path(APP_APK_PATH + APP_APK_FILENAME)
```


`fastlane` will attempt to do its thing, and you'll see your emulator loading the tests and changing locales. This can be a very resource intensive process and works best with your computer haivng nothing else open/doign as little else as possible.


## `fastlane screengrab` Tips:

- You can safely ignore fastlane's many warning about `java.lang.SecurityException: Package org.torproject.android.debug has not requested permission android.permission.WRITE_EXTERNAL_STORAGE`... it used to need to add this permission to APKs in order to funciton, but we don't use it in Orbot in 2025 Android completely ignores `WRITE_EXTENRAL_STORAGE` anyway...
- If your emualtor is working too slowly, you can tweak some of the unit tests in `app/src/androidTest/`. Perhaps a quick fix for you is to add/increase a call to `Thread.sleep`? 
- New tests can be created using the Espresso Test Recorder in Android Studio. In your test, once you've ensured the screen is setup the right way, call `Screengrab.screenshot("screenshot name")` to obtain a screenshot. The `@Before` annotation can be used to have a method run before the test, you can configure Orbot's `SharedPreference`s and other such things here in order to get the screenshot to look exactly how you want it...
- Un/comment lines in `fastlane/Screengrabfile` to obtain screenshots in a specific language and/or to only obtain screnshots for certain tests
- **As of right now the app selection screen `Toast`s and this gets in the way of other screenshots. You can run `fastlane screengrab` twice if this is a problem, uncommenting one of these lines the first time, and the other line the second time.**
```ruby
# use_tests_in_classes([CHOOSE_APPS])
# use_tests_in_classes([CHOOSE_HOW_TO_CONNECT, CONNECTED_SCREEN, KINDNESS_SCREEN, MORE_SCREEN, SETTINGS])

```
- If `fastlane` completes without error, it'll render an HTML file of the last batch of screenshots generated at `fastlane/metadata/android/screenshots.html`. This file isn't tracked in git, but is helpful for quickly verifying things aren't broken.

