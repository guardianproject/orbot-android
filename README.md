<div align="center">

<img width="" src="./app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" alt="Orbot" align="center"/>

# <a href="https://orbot.app" target="_blank">Orbot</a>

### Android Onion Routing Robot

[![Weblate Status](https://hosted.weblate.org/widget/guardianproject/orbot/svg-badge.svg)](https://hosted.weblate.org/engage/guardianproject/)
[![Play Downloads](https://img.shields.io/github/downloads/guardianproject/orbot/total)](https://play.google.com/store/apps/details?id=org.torproject.android)
[![Bitrise Status](https://img.shields.io/bitrise/0e76c31b8e7e1801?token=S2weJXueO3AvrDUrrd85SA&logo=bitrise&color=blue)](https://app.bitrise.io/app/0e76c31b8e7e1801)
([CI docs](./docs/info/CI.md))

Orbot is a free VPN and proxy app that empowers other apps to use the internet more securely. Orbot uses Tor to encrypt your Internet traffic and then hides it by bouncing through a series of computers around the world. Tor is free software and an open network that helps you defend against a form of network surveillance that threatens personal freedom and privacy, confidential business activities and relationships, and state security known as traffic analysis.

***********************************************
<img src=./fastlane/metadata/android/en-US/images/phoneScreenshots/A-orbot_connected_1754319853695.png width="24%"> <img src=./fastlane/metadata/android/en-US/images/phoneScreenshots/B-choose-how_1754319851766.png width="24%">
<img src=./fastlane/metadata/android/en-US/images/phoneScreenshots/C-kindness_mode_screen_1754319855290.png width="24%"> <img src=./fastlane/metadata/android/en-US/images/phoneScreenshots/D-more_screen_1754319856820.png width="24%">
  <table>
    <tr>
      <td><a href="https://github.com/guardianproject/orbot/releases/latest">Download the Latest Orbot Release</a></td>
    </tr>
    <tr>
      <td><a href="https://support.torproject.org/faq/">Tor FAQ (Frequently Asked Questions)</a></td>
    </tr>
    <tr>
      <td><a href="https://hosted.weblate.org/engage/guardianproject/">Please Contribute Your Translations</a></td>
    </tr>
  </table>
  </div>
<div align="left">

## Building Orbot
Building Orbot with Android Studio is very straightforward and we welcome all contributions.

```bash
# first clone the repository 
git clone https://github.com/guardianproject/orbot-android.git

# download external git dependencies for https://github.com/heiher/hev-socks5-tunnel
git submodule update --init --recursive 
```

Once you've initialized the submodules, Orbot can be built like any other Android Studio project. Please note that when Android Studio syncs Orbot, it will prompt you to download the Android NDK used by Orbot if you don't already have it installed. From this point on, Orbot can be built like any other Android app!
</div>
<div align="center">

***********************************************

Orbot is a crucial component of the Guardian Project, an initiative  that leads an effort
to develop a secure and anonymous smartphone. This platform is designed for use by human rights
activists, journalists and others around the world. Learn more: <https://guardianproject.info/>

***********************************************
Tor protects your privacy on the internet by hiding the connection
between your Internet address and the services you use. We believe that Tor
is reasonably secure, but please ensure you read the usage instructions and
learn to configure it properly. Learn more: <https://torproject.org/>



***********************************************
**Copyright &#169; 2009-2025, Nathan Freitas, The Guardian Project**
</div>
