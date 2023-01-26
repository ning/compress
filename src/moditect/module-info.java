// Hand-crafted 06-Jan-2021 by tatu.saloranta@iki.fi
module com.ning.compress.lzf {
    requires transitive java.xml;
    requires jdk.unsupported;

    exports com.ning.compress;
    exports com.ning.compress.gzip;
    exports com.ning.compress.lzf;
    // Not sure if this needs to be exported but...
    exports com.ning.compress.lzf.impl;
    exports com.ning.compress.lzf.parallel;
    exports com.ning.compress.lzf.util;
}
