# Repository Guidelines

## Project Structure & Module Organization
This repository is a multi-loader port of the First Aid mod. Each top-level loader/version directory is its own Gradle project: `fabric1.21.1/`, `fabric1.21.11/`, `neoforge1.21.1/`, `neoforge1.21.11/`, `neoforge26.1/`, and `forge1.20.1/`. Java sources live under `src/main/java` and client-only code usually lives in `src/client/java` on Fabric. Assets, recipes, mixins, and mod metadata live in `src/main/resources`. Generated data, when used, is written to `src/generated/resources`. Root-level `screenshots/` and `README.md` are documentation assets, not runtime content.

## Build, Test, and Development Commands
Run commands from the specific module you are changing; there is no root Gradle build.

- `cd fabric1.21.11 && .\gradlew.bat build` builds the Fabric 1.21.11 jar.
- `cd neoforge1.21.11 && .\gradlew.bat build` builds the NeoForge 1.21.11 jar.
- `cd neoforge1.21.11 && .\gradlew.bat runClient` launches a local client dev instance.
- `cd neoforge1.21.11 && .\gradlew.bat runGameTestServer` runs registered GameTests, if present.
- `cd forge1.20.1 && .\gradlew.bat runServer` starts the Forge server dev environment.

Use the module's wrapper instead of a system Gradle install.

## Coding Style & Naming Conventions
Write Java that matches the surrounding module instead of normalizing formatting across the repo. Package names stay under `ichttt.mods.firstaid`. Use `UpperCamelCase` for classes, `lowerCamelCase` for methods and fields, and keep resource/data file names lowercase with underscores, for example `defibrillator.json`. Keep loader-specific entrypoints and mixins inside the relevant module only.

## Testing Guidelines
There is no committed `src/test` suite at the moment. Verify changes by building the affected module and, for NeoForge/Forge modules, running the relevant dev task (`runClient`, `runServer`, or `runGameTestServer`). Prefer adding targeted GameTests when behavior is hard to verify manually. Name any new test classes after the feature they cover, for example `UnconsciousnessGameTests`.

## Commit & Pull Request Guidelines
Recent history uses short imperative subjects such as `Fix unconscious recovery and collapse handling` and `chore: save current workspace changes`. Follow that pattern: one clear action per commit, no vague messages. PRs should state which loader/version modules were changed, summarize gameplay impact, link the issue if one exists, and include screenshots for HUD, GUI, or visual-effect changes.

## Contributor Notes
Keep changes surgical. If a fix applies to multiple loader/version projects, port it deliberately instead of assuming the code is identical across modules.
