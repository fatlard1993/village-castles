# Village Castles - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Building

```bash
# JDK 25 required; the system default is often 21 and fails immediately
JAVA_HOME=<path-to-jdk-25> ./gradlew build
```

The JAR builds to `build/libs/`. Install server-side; vanilla clients need nothing. Version
targets live in `gradle.properties` and `fabric.mod.json`.
