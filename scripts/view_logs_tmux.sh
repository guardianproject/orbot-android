#!/bin/bash
# sudo apt install tmux
# brew install tmux
#etc

tmux new-session -s 'App&Tor' -d 'adb logcat  --pid=$(adb shell pidof -s "org.torproject.android") -v color'\; split-window -v 'adb logcat  --pid=$(adb shell pidof -s "org.torproject.android:tor") -v color'\; set -g mouse on\; attach
