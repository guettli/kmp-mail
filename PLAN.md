# KMP Mail Libraries — Implementation Plan

## Overview

Three Kotlin Multiplatform libraries for email protocol support:

| Library    | Protocols / RFCs                              | Dependencies                          | Status          |
|------------|-----------------------------------------------|---------------------------------------|-----------------|
| `kmp-mime` | RFC 5322, RFC 2045/2046/2047                  | Pure KMP — no network deps            | ✅ done, 27 tests |
| `kmp-smtp` | RFC 5321, AUTH PLAIN/LOGIN, STARTTLS          | ktor-network, ktor-network-tls        | ✅ done, 18 tests |
| `kmp-imap` | IMAP4rev1, IDLE, CONDSTORE, UIDPLUS           | ktor-network, ktor-network-tls        | ✅ done, 31 tests |

Dependency graph: `kmp-smtp` and `kmp-imap` both depend on `kmp-mime`.

---

## Developer Environment

```sh
nix develop      # enter shell: JDK 21, gradle, go-task
task init        # generate ./gradlew (once)
task test        # run all tests
task test-mime   # kmp-mime only
task test-smtp   # kmp-smtp only
task test-imap   # kmp-imap only
task build       # compile everything
task clean       # wipe build outputs
```

---

## Repository Structure

```
kmp-mail/
├── flake.nix / flake.lock
├── Taskfile.yml
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
│
├── kmp-mime/src/commonMain/kotlin/io/github/kmpmail/mime/
│   ├── ContentType.kt        ✅
│   ├── TransferEncoding.kt   ✅  (base64, quoted-printable, 7bit/8bit/binary)
│   ├── HeaderEncoding.kt     ✅  (RFC 2047 B/Q encode+decode)
│   ├── MimeHeaders.kt        ✅
│   ├── MimePart.kt           ✅  (Leaf / Multi sealed hierarchy)
│   ├── MimeMessage.kt        ✅
│   ├── MimeParser.kt         ✅  (RFC 5322/2045/2046)
│   └── MimeBuilder.kt        ✅  (fluent DSL, text/html/attachment)
│
├── kmp-smtp/src/commonMain/kotlin/io/github/kmpmail/smtp/
│   ├── SmtpClient.kt         ✅  (high-level API + SmtpConfig/SmtpSecurity)
│   ├── SmtpConnection.kt     ✅  (ktor TCP+TLS, TlsUpgradeable)
│   ├── SmtpSession.kt        ✅  (full state machine, dot-stuffing)
│   ├── SmtpCapabilities.kt   ✅  (EHLO parser)
│   ├── SmtpResponse.kt       ✅  (multi-line response reader)
│   ├── SmtpAuth.kt           ✅  (AUTH PLAIN + AUTH LOGIN)
│   ├── SmtpTransport.kt      ✅  (interface + TlsUpgradeable)
│   └── SmtpException.kt      ✅
│
└── kmp-imap/src/commonMain/kotlin/io/github/kmpmail/imap/
    ├── ImapClient.kt         ✅  (high-level API + ImapConfig/ImapSecurity)
    ├── ImapConnection.kt     ✅  (ktor TCP+TLS, literal inlining, ImapTlsUpgradeable)
    ├── ImapSession.kt        ✅  (full state machine + MailboxInfo + ImapEvent + idle Flow)
    ├── ImapCommand.kt        ✅  (tag generator A001…, all command builders)
    ├── ImapResponse.kt       ✅  (Untagged / Tagged / Continuation sealed hierarchy)
    ├── ImapParser.kt         ✅  (cursor-based: atom/quoted/literal/list/NIL/brackets)
    ├── ImapTransport.kt      ✅  (interface + ImapTlsUpgradeable)
    ├── ImapValue.kt          ✅  (Atom/Str/Num/Lst/Nil sealed hierarchy)
    └── ImapException.kt      ✅  (ImapNoException, ImapBadException)
```

---

## Target Platforms

- `jvm()` — primary runtime, all tests run here
- `linuxX64()` — CI / server-side Native
- iOS, macOS, Windows — commented out; enable when CI runners available

---

## Version Catalog (`gradle/libs.versions.toml`)

| Dependency | Version |
|------------|---------|
| Kotlin     | 2.1.10  |
| Ktor       | 3.1.2   |
| Coroutines | 1.10.1  |

---

## Test Fixture Strategy

We do **not** mirror JavaMail APIs. Their tests are tightly coupled to `javax.mail.*`
Java types that cannot exist in `commonMain`, and the API design (blocking, 1997-era)
conflicts with ktor's coroutine model.

**What we take:** raw protocol bytes and transcripts — the valuable, API-independent part.

| Module     | Format              | Source                                          |
|------------|---------------------|-------------------------------------------------|
| `kmp-mime` | `.eml` files        | RFC appendix examples + Angus Mail test vectors |
| `kmp-smtp` | annotated `.txt` transcripts | RFC 5321 §D, Postfix logs             |
| `kmp-imap` | annotated `.txt` transcripts | RFC 3501 §8, Dovecot/Cyrus logs       |

Transcript format: `S:` = server line, `C:` = client line. Tests split on these
to drive a mock channel and assert client behaviour.

**Reference libraries to read (not copy):**

| Project | Path | Why useful |
|---------|------|------------|
| Angus Mail | `eclipse-ee4j/angus-mail` — `mail/src/main/java/com/sun/mail/imap/` | IMAP parser edge cases |
| Apache James MIME4J | `apache/james-mime4j` — `core/src/main/java/` | MIME parser cross-check |

---

## Phase 1 — Scaffolding ✅

- [x] `flake.nix`, `Taskfile.yml`
- [x] `settings.gradle.kts`, root `build.gradle.kts`, `gradle.properties`
- [x] `gradle/libs.versions.toml`
- [x] Per-module `build.gradle.kts` (mime, smtp, imap)
- [x] Test fixture files (MIME `.eml`, SMTP/IMAP transcripts)

---

## Phase 2 — kmp-mime ✅

- [x] `ContentType` — media type + parameter parsing (quoted-string aware)
- [x] `TransferEncoding` — base64 (stdlib), quoted-printable (pure Kotlin), 7bit/8bit
- [x] `HeaderEncoding` — RFC 2047 B/Q decode, `encodeIfNeeded`
- [x] `MimeHeaders` — ordered, case-insensitive, unfolds continuation lines
- [x] `MimePart` — sealed `Leaf` / `Multi` tree
- [x] `MimeMessage` — convenience accessors: `subject`, `textBody`, `htmlBody`, `allParts`
- [x] `MimeParser` — RFC 5322/2045/2046; handles CRLF+LF, multipart recursion
- [x] `MimeBuilder` — fluent DSL: text, html, attachments, non-ASCII subjects
- [x] 27 tests: `HeaderEncodingTest`, `MimeParserTest`, `MimeBuilderTest`

Lesson learned: `/*` inside backtick spans inside KDoc `/** */` opens a phantom
nested comment in Kotlin's block-comment lexer. Avoid `` `foo/*` `` in KDoc.

---

## Phase 3 — kmp-smtp ✅

### Session state machine

```
GREETING → EHLO → [STARTTLS → TLS handshake → EHLO again]
         → AUTH (PLAIN | LOGIN) → READY
         → MAIL FROM → RCPT TO (×n) → DATA → message bytes → "." → 250
         → QUIT
```

### Key implementation notes

- `SmtpResponse`: multi-line responses use `NNN-text` continuation, `NNN text` final.
- `SmtpConnection`: ktor `ByteReadChannel` + line reader; upgrade to TLS via
  `connection.tls(coroutineContext)` after STARTTLS 220.
- `SmtpAuth.PLAIN`: single step — `AUTH PLAIN <base64(\0user\0pass)>`.
- `SmtpAuth.LOGIN`: two-step challenge — server sends `334 VXNlcm5hbWU6` (Username:),
  client sends base64 username; server sends `334 UGFzc3dvcmQ6` (Password:), client
  sends base64 password.
- STARTTLS failure (non-220 response) → throw `SmtpException`, never fall back to plain.
- Pipelining (`PIPELINING` capability): out of scope for v1 but leave room in API.

### Public API

```kotlin
val client = SmtpClient {
    host        = "smtp.example.com"
    port        = 587
    security    = SmtpSecurity.STARTTLS   // or TLS (port 465) or NONE
    credentials = SmtpCredentials("user@example.com", "password")
}
client.connect()
client.sendMessage(mimeMessage)
client.disconnect()
```

### Tests

Replay `fixtures/smtp/*.txt` transcripts through a mock `ByteChannel` pair.
Assert: commands sent by client match `C:` lines; responses fed from `S:` lines.

---

## Phase 4 — kmp-imap ✅

### Session state machine (RFC 3501 §3)

```
Not Authenticated → LOGIN / AUTHENTICATE → Authenticated
Authenticated     → SELECT / EXAMINE      → Selected
Selected          → CLOSE / UNSELECT      → Authenticated
Any               → LOGOUT               → Logout
```

### ImapParser — the hard part

IMAP responses are a context-sensitive grammar. The parser must handle:

| Token type | Example |
|------------|---------|
| Atom | `\Seen`, `INBOX`, `FLAGS` |
| Quoted string | `"Hello world"` |
| Literal | `{42}\r\n<42 raw bytes>` |
| Parenthesised list | `(\Seen \Flagged)` |
| NIL | `NIL` |
| Number | `172` |

Untagged responses to handle:
- `* n EXISTS` / `* n RECENT` / `* n EXPUNGE`
- `* n FETCH (att val att val ...)`
- `* FLAGS (flag-list)`
- `* CAPABILITY cap-list`
- `* LIST / LSUB` responses
- `* STATUS INBOX (MESSAGES n UNSEEN n)`
- `* SEARCH uid-list`

Tagged responses: `tag OK/NO/BAD [resp-code] text`

Continuation: `+ text`

**Complexity note:** `FETCH` responses contain nested lists. The ENVELOPE structure
alone has 10 fields, several of which are parenthesised address lists.
Reading Angus Mail's `IMAPProtocol.java` and `FetchResponse.java` is recommended
before implementing this.

### Extension Support

| Extension  | RFC   | What to parse / send |
|------------|-------|----------------------|
| IDLE       | 2177  | `IDLE\r\n` → `+ idling`; unsolicited `* EXISTS` etc.; `DONE\r\n` |
| CONDSTORE  | 7162  | `CONDSTORE` in `SELECT` params; `HIGHESTMODSEQ` in OK; `MODSEQ (n)` in `FETCH` |
| UIDPLUS    | 4315  | `[APPENDUID uidvalidity uid]` and `[COPYUID uidvalidity src dst]` in tagged OK |

### IDLE implementation

```kotlin
// suspends until cancelled; emits events via Flow
fun ImapMailbox.idle(): Flow<ImapEvent>

sealed class ImapEvent {
    data class Exists(val count: Int)  : ImapEvent()
    data class Expunge(val seqno: Int) : ImapEvent()
    data class Fetch(val seqno: Int, val attributes: Map<String, ImapValue>) : ImapEvent()
}
```

### Public API

```kotlin
val client = ImapClient {
    host        = "imap.example.com"
    port        = 993
    security    = ImapSecurity.TLS
    credentials = ImapCredentials("user@example.com", "password")
}
client.connect()
val mailbox = client.select("INBOX")
val uids    = mailbox.search(ImapQuery.unseen())
val msgs    = mailbox.fetchByUid(uids, FetchProfile.fullHeaders())
mailbox.idle().collect { event -> println(event) }
client.disconnect()
```

---

## Phase 5 — Integration & Polish

- [ ] Integration smoke tests (real server, opt-in via env vars `IMAP_HOST`, `SMTP_HOST`)
- [ ] KDoc on all public APIs
- [ ] Publishing config: `io.github.kmpmail:kmp-{mime,smtp,imap}:0.1.0`
- [ ] GitHub Actions CI (JVM + linuxX64)

---

## Open Questions

1. **LITERAL+** (RFC 7888): non-synchronising literals — implement if server advertises it.
2. **OAuth2 / XOAUTH2**: out of scope for v1; `SmtpAuth` and IMAP `AUTHENTICATE`
   interfaces are open for extension.
3. **JS target**: ktor-network on JS requires Node.js; browser builds need network stubs.
4. **Thread safety**: all clients designed for single-coroutine use.
