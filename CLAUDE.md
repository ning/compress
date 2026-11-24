# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

compress-lzf (Maven coordinates `com.ning:compress-lzf`) is a Java library for encoding and decoding
data in LZF format. It uses the *original* LZF data format, so it is 100% compatible with C `liblzf`
and command-line `lzf` tools — note that some other Java adaptations (e.g. H2 database's) use the same
internal block compression but different block identifiers, so they are NOT interchangeable.

**Key characteristics:**
- Pure Java, no external runtime dependencies; JDK 8 source/target level
- Both "safe" (JDK-only) and "optimal" (`sun.misc.Unsafe`) implementations of encoders/decoders
- Block-oriented compression (max 64kB chunks, no cross-chunk back-references) which enables parallel encoding
- JPMS module name `com.ning.compress.lzf`; jar is also an OSGi bundle and an executable CLI

## Build and Development Commands

```bash
./mvnw clean install         # Build, test, install to local Maven repo (default goal is `install`)
./mvnw verify                # Build + tests + javadoc jar (what CI runs)
./mvnw test                  # Run tests only (JUnit 5 / Jupiter)
./mvnw test -Dtest=TestLZFRoundTrip                    # Single test class
./mvnw test -Dtest=TestLZFRoundTrip#testHamletZ        # Single test method
./mvnw javadoc:javadoc       # Javadocs -> target/site/apidocs/
```

### Fuzz testing (Jazzer)

Fuzz tests live in `TestFuzzUnsafeLZF` and only run under the `fuzz` profile, which **disables the
normal unit tests** and runs each fuzz method as a separate surefire execution:

```bash
./mvnw --activate-profiles fuzz test
```

Because of a Jazzer limitation, each new fuzz test *method* must be listed explicitly as its own
`<execution>` in the `fuzz` profile in `pom.xml` — adding a method without that means it never runs.
Failing fuzz inputs are written under `src/test/resources/**/*Inputs/` (CI uploads them as artifacts).

### Manual performance testing

The `run-*` / `profile-*` shell scripts run `perf.Manual*` classes directly off `target/classes` and
`target/test-classes`, so compile first (`./mvnw test-compile` or a full build):

```bash
./run-comp-perf <file>       # perf.ManualCompressComparison
./run-uncomp-perf <file>     # perf.ManualUncompressComparison
./run-skip <file>            # perf.ManualSkipComparison
```

`testdata/low-comp-120k.txt` and `src/test/resources/` (shakespeare XML, binary samples) provide inputs.
`ManualTestLZF` and `ManualUnsafePerf` are also manually-run mains, not part of the test suite.

### CI

GitHub Actions builds with `./mvnw -B -q -ff -ntp verify` on JDK 8, 11, 17 and 21, plus a separate
fuzzing job on JDK 17. Coverage (jacoco) is published from the JDK 8 run.

## Code Architecture

### Packages

- `com.ning.compress` — cross-format basics: `BufferRecycler`, push-style `Uncompressor`/`DataHandler`
- `com.ning.compress.lzf` — public API (`LZFEncoder`, `LZFDecoder`, streams, `LZFChunk`) plus the
  abstract `ChunkEncoder`/`ChunkDecoder`
- `com.ning.compress.lzf.impl` — concrete Vanilla/Unsafe codecs (implementation detail; OSGi-private)
- `com.ning.compress.lzf.parallel` — `PLZFOutputStream` and its thread-pool machinery
- `com.ning.compress.lzf.util` — factories (`ChunkEncoderFactory`, `ChunkDecoderFactory`) and
  `LZFFileInputStream`/`LZFFileOutputStream`
- `com.ning.compress.gzip` — unrelated to LZF: buffer-recycling gzip streams and a push-style
  `GZIPUncompressor`, sharing the same `Uncompressor`/`DataHandler` abstractions

### Three parallel API styles

1. **Block**: `LZFEncoder.encode(...)` / `LZFDecoder.decode(...)`, with `safeEncode`/`safeDecode`
   variants that force the JDK-only codec.
2. **Streaming**: `LZFOutputStream`, `LZFInputStream`, `LZFCompressingInputStream` (compresses while
   being read), `PLZFOutputStream` for multi-threaded compression.
3. **Push**: `LZFUncompressor` / `GZIPUncompressor` feed decompressed data to a `DataHandler` as
   compressed data arrives — for async/non-blocking callers. Both `feedCompressedData` and
   `handleData` return `boolean` to signal "stop feeding me".

New public functionality generally needs to be reachable from all three where it makes sense.

### Safe vs optimal codec selection

`ChunkDecoderFactory.optimalInstance()` and `ChunkEncoderFactory.optimalInstance()` guard access to
the `Unsafe`-based implementation with a `UnsafeAccess.isUnsafeSupported()` check to avoid uncaught
exceptions and errors.
If `Unsafe` is not supported, the 'vanilla' decoder and encoder are used as fallback. So "optimal"
must never hard-fail on odd platforms — keep the fallback paths intact when touching these.

The result of `isUnsafeSupported()` is looked up on demand and not on class initialization of
`ChunkDecoderFactory` and `ChunkEncoderFactory` to avoid interacting with `Unsafe` if the user
does not want to use the `Unsafe`-based implementation in the first place. Otherwise the user
would see warnings on the console due to "JEP 498: Warn upon Use of Memory-Access Methods in
sun.misc.Unsafe".

```
ChunkEncoder (abstract; hashing, chunk splitting, output framing)
├── VanillaChunkEncoder          (pure Java)
└── UnsafeChunkEncoder           (sun.misc.Unsafe)
    ├── UnsafeChunkEncoderLE     (tryCompress() specialized per native byte order)
    └── UnsafeChunkEncoderBE

ChunkDecoder (abstract)
├── VanillaChunkDecoder
└── UnsafeChunkDecoder
```

**Any change to the match-finding/encoding loop must be mirrored in three places**:
`VanillaChunkEncoder.tryCompress`, `UnsafeChunkEncoderLE.tryCompress`, and
`UnsafeChunkEncoderBE.tryCompress`. LE and BE differ only in how multi-byte reads are assembled; they
must produce identical output (a past bug, #64, was exactly a divergence between them). Round-trip
tests catch Vanilla/Unsafe divergence only if both variants are exercised.

Unsafe codecs must validate arguments before touching memory (`_checkArrayIndices`,
`_checkOutputLength` in `UnsafeChunkEncoder`) — bad indices would otherwise corrupt the heap rather
than throw. Do not weaken or skip these checks. `UnsafeChunkEncoder` is deliberately non-subclassable
outside the package.

### Chunk format and encoding constraints

Framing lives entirely in `LZFChunk`: every chunk starts `'Z' 'V'`, then a type byte
(`BLOCK_TYPE_COMPRESSED`=1, `BLOCK_TYPE_NON_COMPRESSED`=0); compressed chunks have a 7-byte header
(encoded length, then original length, both big-endian 16-bit), uncompressed ones a 5-byte header.

- `LZFChunk.MAX_CHUNK_LEN` = 0xFFFF — length fields are 2 bytes, so chunks cannot grow past 64kB
- `ChunkEncoder.MAX_OFF` = 8192 — back-reference window
- `ChunkEncoder.MIN_BLOCK_TO_COMPRESS` = 16 — smaller input is stored as-is
- `LZFChunk.MAX_LITERAL` = 32 — max literal run
- A chunk is emitted compressed only if compression actually shrinks it (header overhead included)

### Buffer recycling and lifetime

`BufferRecycler.instance()` returns a `ThreadLocal<SoftReference<BufferRecycler>>`-held instance, and
encoders/decoders borrow encoding/output/input/decode buffers and the encoding hash table from it.
`ChunkEncoder`/`ChunkDecoder` are stateful and **not thread-safe**, and implement `Closeable`; failing
to `close()` leaks the buffers out of the pool. Factory methods also accept an explicit
`BufferRecycler` for callers that manage pooling themselves (`PLZFOutputStream` does).

## Metadata kept in sync by hand

Adding, renaming, or removing a package requires updating several files that are not derived from the
source tree:

- `src/moditect/module-info.java` — hand-written module descriptor, woven in at `package` phase by the
  Moditect plugin (this is how a JDK 8 build ships a Java 9+ `module-info.class`). Requires
  `jdk.unsupported` for `sun.misc.Unsafe`.
- `pom.xml` `maven-bundle-plugin` config — OSGi `Private-Package` lists `com.ning.compress.lzf.impl`,
  and `sun.misc` is declared an optional import. `Main-Class` is `com.ning.compress.lzf.LZF`, which
  makes the jar runnable as a CLI (`java -jar compress-lzf-<version>.jar -c|-d <file>`).

`VERSION.txt` holds hand-maintained release notes (newest first, `#<issue>` references with
contributor credit); add an entry there for user-visible changes.
