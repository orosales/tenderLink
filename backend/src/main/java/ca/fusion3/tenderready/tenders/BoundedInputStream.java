package ca.fusion3.tenderready.tenders;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Fails once more than {@code max} bytes have been read, so a remote file cannot exhaust memory. */
class BoundedInputStream extends FilterInputStream {
    private final long max;
    private long count;

    BoundedInputStream(InputStream in, long max) {
        super(in);
        this.max = max;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b >= 0) check(1);
        return b;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        int n = super.read(buf, off, len);
        if (n > 0) check(n);
        return n;
    }

    private void check(long n) throws IOException {
        count += n;
        if (count > max) throw new IOException("Download exceeded the size limit of " + max + " bytes");
    }
}
