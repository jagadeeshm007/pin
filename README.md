# Pin

Open-source Android app that controls your phone by voice. Say a wake phrase,
then a command: "scroll down", "open YouTube and play the second video about
Java tutorials". Simple commands run on the device in well under a second;
complex ones are planned by an LLM from the provider you choose, using your own
API key, and every step is verified and recorded so you can review it.

Status: early development. Today the app is a skeleton; the roadmap is in
`docs/roadmap.md`.

## How it works (planned)

- An AccessibilityService reads the screen and performs taps, swipes, scrolls and navigation.
- On-device speech (sherpa-onnx): voice activity detection, a wake phrase you pick, streaming transcription. Audio never leaves the phone.
- Local command engine for common commands; an LLM planner (Claude, OpenAI, Gemini or OpenRouter) for multi-step tasks, limited to a fixed set of validated actions.
- Sensitive actions (pay, send, delete, security settings) always ask you first.
- Full local history of sessions, screenshots and reasoning summaries, deleted after 7 days by default.
- Speech models are downloaded on first launch, so the APK stays small.

Architecture: `docs/architecture/blueprint.md`. Decisions: `docs/adr/`.

## Build

Requirements: JDK 17 or newer, Android SDK with platform 36, `ANDROID_HOME` set.

```bash
make setup
make check
make dev
```

`make help` lists every command.

## Contributing

Read `CONTRIBUTING.md` and `AGENTS.md`. Work follows the spec-driven process in
`docs/sdlc/README.md`, whether you write the code yourself or with an AI agent.

## License

Apache-2.0 (see `LICENSE`).
