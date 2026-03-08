#!/bin/bash

JDK_VERSION=25
FILE="gradle/gradle-daemon-jvm.properties"
rm $FILE
echo "Configuring Orbot for Java $JDK_VERSION..."
echo ""
./gradlew updateDaemonJvm --jvm-version "$JDK_VERSION"
echo "OrbotJVM Configurtion Complete, $FILE:"
cat "$FILE"