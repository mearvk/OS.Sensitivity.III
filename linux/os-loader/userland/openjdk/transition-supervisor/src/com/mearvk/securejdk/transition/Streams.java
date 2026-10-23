package com.mearvk.securejdk.transition;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;

/**
 * A uniform byte-stream view over the two STP transports (local UNIX-domain
 * socket channel, and remote TLS socket), plus the optional TLS exporter used
 * for channel binding on the remote path.
 */
public interface Streams extends AutoCloseable {
    InputStream in();
    OutputStream out();
    /** TLS exporter value for channel binding, or null on the local pipe. */
    default byte[] tlsExporter() { return null; }
    @Override void close();
}

/** Local pipe: a SocketChannel (UNIX domain) adapted to streams. */
final class ChannelStreams implements Streams {
    private final SocketChannel ch;
    private final InputStream in;
    private final OutputStream out;
    ChannelStreams(SocketChannel ch) {
        this.ch = ch;
        this.in = new BufferedInputStream(Channels.newInputStream(ch));
        this.out = new BufferedOutputStream(Channels.newOutputStream(ch));
    }
    public InputStream in() { return in; }
    public OutputStream out() { return out; }
    public void close() { try { ch.close(); } catch (IOException ignore) {} }
}

/** Remote pipe: a TLS socket adapted to streams, carrying the exporter. */
final class SocketStreamsTls implements Streams {
    private final javax.net.ssl.SSLSocket s;
    private final byte[] exporter;
    private final InputStream in;
    private final OutputStream out;
    SocketStreamsTls(javax.net.ssl.SSLSocket s, byte[] exporter) throws IOException {
        this.s = s; this.exporter = exporter;
        this.in = new BufferedInputStream(s.getInputStream());
        this.out = new BufferedOutputStream(s.getOutputStream());
    }
    public InputStream in() { return in; }
    public OutputStream out() { return out; }
    public byte[] tlsExporter() { return exporter; }
    public void close() { try { s.close(); } catch (IOException ignore) {} }
}
