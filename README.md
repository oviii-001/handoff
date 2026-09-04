<div align="center">
  <h1>HandOff</h1>
  <p><strong>Zero-Trust Remote Authorization for AI Coding Agents</strong></p>

  [![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin_Multiplatform-2.2.x-blue.svg?logo=kotlin)](https://kotlinlang.org/)
  [![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-M3_Expressive-green.svg?logo=android)](https://developer.android.com/jetpack/compose)
  [![Cloudflare Workers](https://img.shields.io/badge/Relay-Cloudflare_Durable_Objects-orange.svg?logo=cloudflare)](https://workers.cloudflare.com/)
  [![License: MIT](https://img.shields.io/badge/License-MIT-purple.svg)](LICENSE)
</div>

<br />

## Overview

Modern AI coding agents (such as Claude Code, Cursor, and Antigravity) are powerful, but they often require permission to execute potentially destructive terminal commands or modify security-sensitive files. 

**HandOff** bridges the gap between agent autonomy and human oversight. It pairs your desktop environment directly with your mobile device via an encrypted relay. Whenever an agent requests tool access, a cryptographic approval card is dispatched to your phone, detailing the command, target directory, and risk classification. 

Approve or reject executions in real-time, from anywhere in the world, with a single tap.

## Features

- **Real-Time Interception**: Seamlessly pauses agent executions pending your explicit mobile authorization.
- **Agent Agnostic**: Out-of-the-box MCP (Model Context Protocol) support for Claude Desktop, Cursor, and Antigravity IDE.
- **Zero-Trust Security**: End-to-end cryptographic signing using hardware-backed Ed25519 keys.
- **Self-Hosted Infrastructure**: Edge-deployed WebSocket broker utilizing Cloudflare Durable Objects.
- **Offline-First Mobile App**: Native Android client built with Jetpack Compose Material 3 Expressive, backed by Room and Firebase Cloud Messaging for instant background wake-ups.

## Architecture

HandOff is designed around a strict Clean Architecture pattern and operates across three primary nodes:

1. **Desktop Daemon (`/desktopApp`)**: A headless CLI that intercepts agent requests via MCP, signs them cryptographically, and dispatches them over WebSockets.
2. **Cloudflare Relay (`/apps/relay`)**: A low-latency edge broker utilizing Durable Objects for persistent bidirectional state synchronization and webhook forwarding.
3. **Android Client (`/mobile`)**: A native Kotlin application that serves as the hardware authenticator, communicating with the relay and persisting audit logs locally.

## Getting Started

Please refer to our comprehensive [**Setup & Installation Guide**](SETUP_GUIDE.md) for detailed, step-by-step instructions on deploying the relay, configuring your mobile device, and injecting the MCP server into your IDE.

### Quick Start (Desktop Daemon)

```bash
# Generate a new pairing code and terminal ASCII QR
./handoff.sh --pair

# Inject HandOff into your supported IDEs
./handoff.sh --install

# Execute a command securely through the approval flow
./handoff.sh --exec "npm run build"
```

## Documentation

- [Setup Guide](SETUP_GUIDE.md)
- [Changelog](CHANGELOG.md)
- [Security Policy](SECURITY.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
- [Contributing](CONTRIBUTING.md)

## Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) to learn how to set up your development environment, run tests, and submit pull requests. 

Before opening a pull request, ensure all tests pass:
```bash
./gradlew test
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
