# LZF Compressor

## Overview

LZF-compress is a Java library for encoding and decoding data in LZF format,
written by Tatu Saloranta (tatu.saloranta@iki.fi)

Data format and algorithm based on original [LZF library](http://freshmeat.net/projects/liblzf) by Marc A Lehmann.
See [LZF Format Specification](https://github.com/ning/compress/wiki/LZFFormat) for full description.

Format differs slightly from some other adaptations, such as the one used
by [H2 database project](http://www.h2database.com) (by Thomas Mueller);
although internal block compression structure is the same, block identifiers differ.
This package uses the original LZF identifiers to be 100% compatible with existing command-line `lzf` tool(s).

LZF algorithm itself is optimized for speed, with somewhat more modest compression.
Compared to the standard `Deflate` (algorithm gzip uses) LZF can be 5-6 times as fast to compress,
and twice as fast to decompress. Compression rate is lower since no Huffman-encoding is used
after lempel-ziv substring elimination.

## License

[Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0)

## Requirements

Version up to 1.0.4 require JDK 6; version 1.1 requires JDK 8.

Library has no external dependencies.

## Usage

See [Wiki](https://github.com/ning/compress/wiki) for more details; here's a "TL;DNR" version.

Both compression and decompression can be done either by streaming approach:

```java
InputStream in = new LZFInputStream(new FileInputStream("data.lzf"));
OutputStream out = new LZFOutputStream(new FileOutputStream("results.lzf"));
InputStream compIn = new LZFCompressingInputStream(new FileInputStream("stuff.txt"));
```

or by block operation:

```java
byte[] compressed = LZFEncoder.encode(uncompressedData);
byte[] uncompressed = LZFDecoder.decode(compressedData);
```

and you can even use the LZF jar as a command-line tool (it has manifest that points to 'com.ning.compress.lzf.LZF' as the class having main() method to call), like so:

    java -jar compress-lzf-1.0.3.jar
  
(which will display necessary usage arguments for `-c`(ompressing) or `-d`(ecompressing) files.

### Adding as Dependency

#### Maven

```xml
<dependency>
  <groupId>com.ning</groupId>
  <artifactId>compress-lzf</artifactId>
  <version>1.1</version>
</dependency>
```

#### Module info (JPMS)

Starting with version 1.1, `module-info.class` is included; module name is `com.ning.compress.lzf` so you will need to use:

    requires com.ning.compress.lzf

### Parallel processing

Since the compression is more CPU-heavy than decompression, it could benefit from concurrent operation.
This works well with LZF because of its block-oriented nature, so that although there is need for
sequential processing within block (of up to 64kB), encoding of separate blocks can be done completely
independently: there are no dependencies to earlier blocks.

The main abstraction to use is `PLZFOutputStream` which a `FilterOutputStream` and implements
`java.nio.channels.WritableByteChannel` as well. It use is like that of any `OutputStream`:

```java
PLZFOutputStream output = new PLZFOutputStream(new FileOutputStream("stuff.lzf"));
// then write contents:
output.write(buffer);
// ...
output.close();

```

## Interoperability

Besides Java support, LZF codecs / bindings exist for non-JVM languages as well:

* C: [liblzf](http://oldhome.schmorp.de/marc/liblzf.html) (the original LZF package!)
* C#: [C# LZF](https://csharplzfcompression.codeplex.com/)
* Go: [Golly](https://github.com/tav/golly)
* Javascript(!): [freecode LZF](http://freecode.com/projects/lzf) (or via [SourceForge](http://sourceforge.net/projects/lzf/))
* Perl: [Compress::LZF](http://search.cpan.org/dist/Compress-LZF/LZF.pm)
* Python: [Python-LZF](https://github.com/teepark/python-lzf)
* Ruby: [glebtv/lzf](https://github.com/glebtv/lzf), [LZF/Ruby](https://rubyforge.org/projects/lzfruby/)

## Related

Check out [jvm-compress-benchmark](https://github.com/ning/jvm-compressor-benchmark) for comparison of space- and time-efficiency of this LZF implementation, relative other available Java-accessible compression libraries.

## More

[Project Wiki](https://github.com/ning/compress/wiki).

## Alternative High-Speed Lempel-Ziv Compressors

LZF belongs to a family of compression codecs called "simple Lempel-Ziv" codecs.
Since LZ compression is also the first part of `deflate` compression (which is used,
along with simple framing, for `gzip`), it can be viewed as "first-part of gzip"
(second part being Huffman-encoding of compressed content).

There are many other codecs in this category, most notable (and competitive being)

* [Snappy](http://en.wikipedia.org/wiki/Snappy_%28software%29)
* [LZ4](http://en.wikipedia.org/wiki/LZ4_%28compression_algorithm%29)

all of which have very similar compression ratios (due to same underlying algorithm,
differences coming from slight encoding variations, and efficiency differences in
back-reference matching), and similar performance profiles regarding ratio of
compression vs uncompression speeds.
