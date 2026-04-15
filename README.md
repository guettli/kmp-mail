# Kotlin Multiplatform IMAP / SMTP / MIME

Pure-Kotlin email protocol libraries — no JavaMail, no platform lock-in.

Built as the network layer for [SharedInbox](https://github.com/guettli/sharedinbox).

## Libraries

| Module | Protocols | Status |
|--------|-----------|--------|
| `kmp-mime` | RFC 5322 · RFC 2045/2046/2047 — parse & build MIME messages | ✅ 27 tests |
| `kmp-smtp` | RFC 5321 · AUTH PLAIN/LOGIN · STARTTLS | ✅ 18 tests |
| `kmp-imap` | IMAP4rev1 · IDLE · CONDSTORE · UIDPLUS | ✅ 31 tests |

All three modules target **JVM and Native** (linuxX64), with iOS and macOS targets ready to enable.
Dependencies: [ktor-network](https://ktor.io) for `kmp-smtp` and `kmp-imap`; `kmp-mime` is dependency-free.

## Usage

### Parse a message

```kotlin
val message = MimeParser.parse(rawBytes)
println(message.subject)   // decoded RFC 2047 encoded-words
println(message.textBody)  // decoded body
```

### Build a message

```kotlin
val raw: ByteArray = buildMime {
    from("alice@example.com")
    to("bob@example.com")
    subject("Héllo")           // auto-encoded per RFC 2047
    textBody("Hello, World!")
    attachment("report.pdf", pdfBytes)
}
```

## Development

Requires [Nix](https://nixos.org):

```sh
nix develop        # JDK 21 + Gradle + Task
task init          # generate ./gradlew (once)
task test          # run all tests
task test-mime     # run kmp-mime tests only
```

## Design notes

- **No JavaMail API** — those APIs date from 1997: blocking I/O, `Session` singletons,
  `Properties` bags. This library is coroutine-first with idiomatic Kotlin DSLs.
- **Test fixtures** are raw RFC / protocol-transcript bytes extracted from existing
  implementations (Angus Mail, Dovecot), not Java test code.
- `kmp-smtp` and `kmp-imap` depend on `kmp-mime`; all three can be consumed independently.

## License

MIT — see [LICENSE](LICENSE).
