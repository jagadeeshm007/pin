# Every command lives here. `make help` lists them. CI runs `make check`.
SHELL := /bin/bash
.DEFAULT_GOAL := help
GRADLEW := ./gradlew
GRADLE_FLAGS ?= --console=plain
APK_DEBUG := app/build/outputs/apk/debug/app-debug.apk
APK_RELEASE_DIR := app/build/outputs/apk/release
UNIT_REPORTS := app/build/test-results/testDebugUnitTest
APK_BUDGET_MB := 12

.PHONY: help setup check ktlint lint test build fix dev test-device size doctor clean

help: ## List targets
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-12s %s\n", $$1, $$2}'

setup: ## Write local.properties from ANDROID_HOME
	@chmod +x gradlew
	@command -v java >/dev/null || { echo "setup: no JDK on PATH (need 17 or newer)" >&2; exit 1; }
	@[ -n "$$ANDROID_HOME" ] || { echo "setup: ANDROID_HOME is not set" >&2; exit 1; }
	@[ -f local.properties ] || { echo "sdk.dir=$$ANDROID_HOME" > local.properties; echo "setup: wrote local.properties"; }
	@echo "setup: done"

check: ktlint lint test build ## The gate: ktlint, lint, unit tests, debug build
	@echo "check: passed (ktlint, lint, test, build)"

ktlint: ## Kotlin style
	@$(GRADLEW) $(GRADLE_FLAGS) ktlintCheck

lint: ## Android lint, debug variant
	@$(GRADLEW) $(GRADLE_FLAGS) lintDebug

test: ## Unit tests; fails when zero tests ran
	@$(GRADLEW) $(GRADLE_FLAGS) testDebugUnitTest
	@total=0; for t in $$(grep -ho 'tests="[0-9]*"' $(UNIT_REPORTS)/*.xml 2>/dev/null | grep -o '[0-9]*'); do total=$$((total+t)); done; \
	[ "$$total" -gt 0 ] || { echo "test: 0 tests ran" >&2; exit 1; }; echo "test: $$total tests passed"

build: ## Debug APK
	@$(GRADLEW) $(GRADLE_FLAGS) assembleDebug
	@echo "build: $$(du -h $(APK_DEBUG) | cut -f1) $(APK_DEBUG)"

fix: ## Apply ktlint formatting
	@$(GRADLEW) $(GRADLE_FLAGS) ktlintFormat

dev: ## Install the debug build on the attached device
	@[ "$$(adb devices | tail -n +2 | grep -c 'device$$')" -gt 0 ] || { echo "dev: no device attached" >&2; exit 1; }
	@$(GRADLEW) $(GRADLE_FLAGS) installDebug

test-device: ## Instrumented tests on the attached device (not part of check)
	@[ "$$(adb devices | tail -n +2 | grep -c 'device$$')" -gt 0 ] || { echo "test-device: no device attached" >&2; exit 1; }
	@# Keep the app installed afterwards: uninstalling resets the accessibility switch and permissions.
	@$(GRADLEW) $(GRADLE_FLAGS) connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true

size: ## Release APK size against the budget
	@$(GRADLEW) $(GRADLE_FLAGS) assembleRelease
	@f=$$(ls $(APK_RELEASE_DIR)/*.apk | head -1); b=$$(stat -c %s "$$f"); mb=$$((b/1024/1024)); \
	echo "size: $$f $$mb MB (budget $(APK_BUDGET_MB) MB)"; [ "$$mb" -lt $(APK_BUDGET_MB) ] || { echo "size: over budget" >&2; exit 1; }

doctor: ## Show toolchain
	@echo "java:         $$(java -version 2>&1 | head -1)"
	@echo "ANDROID_HOME: $${ANDROID_HOME:-NOT SET}"
	@echo "gradle:       $$(sed -n 's|.*/gradle-\([0-9.]*\)-.*|\1|p' gradle/wrapper/gradle-wrapper.properties)"
	@echo "adb:          $$(command -v adb >/dev/null && adb devices | tail -n +2 | grep -c 'device$$' || echo missing) device(s)"

clean: ## Remove build output
	@$(GRADLEW) $(GRADLE_FLAGS) clean
