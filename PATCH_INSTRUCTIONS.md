# Armyrist Patch 001

## 1. Replace
Replace the repository file:

`app/build.gradle.kts`

with the included file.

## 2. MainActivity.kt
In:

`app/src/main/java/com/seolhwa/armyrist/MainActivity.kt`

find:

```kotlin
var revision by remember { mutableIntStateOf(0) }
fun refresh() { revision++ }
```

and replace with:

```kotlin
var revision by remember { mutableIntStateOf(0) }
val observedRevision = revision
fun refresh() { revision++ }
```

The local variable exists to make this composable observe revision changes.
Do not remove `observedRevision` merely because the IDE marks it as otherwise unused.

## 3. Missing Gradle wrapper JAR
The repository currently does not contain:

`gradle/wrapper/gradle-wrapper.jar`

This is a binary Gradle-generated file and is not included in this text-only patch.

Regenerate the wrapper from a trusted local Gradle/Android Studio environment, then add the generated
`gradle/wrapper/gradle-wrapper.jar` to the repository.

The wrapper properties already target Gradle 8.13.
