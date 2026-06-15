# authcode

authcode is a Paper plugin project written in Kotlin.

The current repository is an initial plugin scaffold. It provides the Bukkit
plugin entry class and Maven build configuration, but does not register
commands, permissions, configuration files, or gameplay features yet.

## Requirements

- Java 25
- Maven 3.9+
- Paper server matching the configured Paper API dependency

## Build

```bash
mvn clean package
```

The packaged plugin jar is generated under:

```text
target/authcode-1.0-SNAPSHOT.jar
```

## Install

1. Build the project.
2. Copy the generated jar into the server `plugins/` directory.
3. Start or restart the server.
4. Check the server console for the `authcode` plugin load result.

## Project Structure

```text
pom.xml
src/main/kotlin/ym/authcode/Authcode.kt
src/main/resources/plugin.yml
```

## Plugin Metadata

- Name: `authcode`
- Main class: `ym.authcode.Authcode`
- Load phase: `POSTWORLD`
- Author: `ymxc`

## Development Status

This project is currently a minimal skeleton. Future functionality should be
implemented in Kotlin under `src/main/kotlin`, with plugin metadata added to
`src/main/resources/plugin.yml` as commands, permissions, and configuration
contracts are introduced.
