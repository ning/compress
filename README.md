# Ning-Compress

## Overview

Ning-compress is a Java library for encoding and decoding data in LZF format, written by Tatu Saloranta (tatu.saloranta@iki.fi)

Data format and algorithm based on original [LZF library](http://freshmeat.net/projects/liblzf) by Marc A Lehmann. See [LZF Format](https://github.com/ning/compress/wiki/LZFFormat) for full description.

Format differs slightly from some other adaptations, such as one used by [H2 database project](http://www.h2database.com) (by Thomas Mueller); although internal block compression structure is the same, block identifiers differ.
This package uses the original LZF identifiers to be 100% compatible with existing command-line lzf tool(s).

LZF alfgorithm itself is optimized for speed, with somewhat more modest compression: compared to Deflate (algorithm gzip uses) LZF can be 5-6 times as fast to compress, and twice as fast to decompress.

## Usage

See [Wiki](https://github.com/ning/compress/wiki) for more details; here's a "TL;DNR" version.

Both compression and decompression can be done either by streaming approach:

    InputStream in = new LZFInputStream(new FileInputStream("data.lzf"));
    OutputStream out = new LZFOutputStream(new FileOutputStream("results.lzf"));
    InputStream compIn = new LZFCompressingInputStream(new FileInputStream("stuff.txt"));

or by block operation:

    byte[] compressed = LZFEncoder.encode(uncompressedData);
    byte[] uncompressed = LZFDecoder.decode(compressedData);

and you can even use the LZF jar as a command-line tool (it has manifest that points to 'com.ning.compress.lzf.LZF' as the class having main() method to call), like so:

    java -jar compress-lzf-1.0.0.jar
  
(which will display necessary usage arguments for `-c`(ompressing) or `-d`(ecompressing) files.

## Interoperability

Besides Java support, LZF codecs / bindings exist for non-JVM languages as well:

* C: [liblzf](http://oldhome.schmorp.de/marc/liblzf.html) (the original LZF package!)
* Go: [Golly](https://github.com/tav/golly)
* Javascript(!): [http://freecode.com/projects/lzf](freecode LZF) (or via [SourceForge](http://sourceforge.net/projects/lzf/)
* Perl: [Compress::LZF](http://search.cpan.org/dist/Compress-LZF/LZF.pm)
* Python: [Python-LZF](https://github.com/teepark/python-lzf)
* Ruby: [glebtv/lzf](https://github.com/glebtv/lzf), [LZF/Ruby](https://rubyforge.org/projects/lzfruby/))

## Related

Check out [jvm-compress-benchmark](https://github.com/ning/jvm-compressor-benchmark) for comparison of space- and time-efficiency of this LZF implementation, relative other available Java-accessible compression libraries.

## More

[Project Wiki](https://github.com/ning/compress/wiki).
