package za.co.titandynamix.sasbo_online_backend.legacy.read;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Counts the bytes read through it and refuses the read the moment it passes its ceiling -- so an upload is never read
 * further than the limit says, whatever its sender claimed its size was. No mark and reset: every byte is counted once.
 */
public final class LimitedInputStream extends FilterInputStream {

    private final long limit;
    private long count;

    public LimitedInputStream(InputStream in, long limit) {
        super(in);
        this.limit = limit;
    }

    /** How many bytes have been read so far. */
    public long count() {
        return count;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b >= 0) {
            counted(1);
        }
        return b;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int n = super.read(buffer, offset, length);
        if (n > 0) {
            counted(n);
        }
        return n;
    }

    @Override
    public long skip(long n) throws IOException {
        long skipped = super.skip(n);
        if (skipped > 0) {
            counted(skipped);
        }
        return skipped;
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    @Override
    public synchronized void mark(int readLimit) {
        // Not supported: a reset would count bytes twice.
    }

    @Override
    public synchronized void reset() throws IOException {
        throw new IOException("mark and reset are not supported");
    }

    private void counted(long n) {
        count += n;
        if (count > limit) {
            throw new SourceRefused(SourceRefused.Reason.TOO_LARGE, limit, "The file is larger than the limit.");
        }
    }
}
