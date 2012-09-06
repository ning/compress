# Ning-Compress

## Overview

Ning-compress is a Java library for encoding and decoding data in LZF format, written by Tatu Saloranta (tatu.saloranta@iki.fi)

Data format and algorithm based on original [LZF library](http://freshmeat.net/projects/liblzf) by Marc A Lehmann

Format differs slightly from some other adaptations, such as one used by [H2 database project](http://www.h2database.com) (by Thomas Mueller); although internal block compression structure is the same, block identifiers differ.
This package uses the original LZF identifiers to be 100% compatible with existing command-line lzf tool(s).

LZF alfgorithm itself is optimized for speed, with somewhat more modest compression: compared to Deflate (algorithm gzip uses) LZF can be 5-6 times as fast to compress, and twice as fast to decompress.

## API usage

Both compression and decompression can be done either block-by-block or using Java stream.
For full details, check out Javadocs from [Wiki](compress/wiki).

When reading compressed data from a file you can do it simply creating a `LZFInputStream` and use it for reading content

    InputStream in = new LZFInputStream(new FileInputStream("data.lzf"));

(note, too, that stream is buffered: there is no need to or benefit from using `BufferedInputStream`!)

and similarly you can compress content using `LZFOutputStream`:

    OutputStream out = new LZFOutputStream(new FileOutputStream("results.lzf"));

Compressing and decompressing individual blocks is as simple:

    byte[] compressed = LZFEncoder.encode(uncompressedData);
    byte[] uncompressed = LZFDecoder.decode(compressedData);

Finally, note that LZF encoded chunks have length of at most 64 kB; longer content will be split into such chunks.

## Use as command-line tool

Note that resulting jar is both an OSGi bundle, and a command-line tool (has manifest that points to 'com.ning.compress.lzf.LZF' as the class having main() method to call).

This means that you can use it like:

    java -jar compress-lzf-0.9.6.jar
  
(which will display necessary usage arguments)

## Related

Check out [jvm-compress-benchmark](https://github.com/ning/jvm-compressor-benchmark) for comparison of space- and time-efficiency of this LZF implementation, relative other available Java-accessible compression libraries.

## More

Check out [Project Wiki](https://github.com/ning/compress/wiki) for more information.

