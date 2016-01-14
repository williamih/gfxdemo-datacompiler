import java.io.*;

public class BinaryWriter implements Closeable {
    private RandomAccessFile m_file;

    public BinaryWriter(File file) throws FileNotFoundException {
        m_file = new RandomAccessFile(file, "rw");
    }

    @Override
    public void close() throws IOException {
        m_file.close();
    }

    public long getFilePointer() throws IOException {
        return m_file.getFilePointer();
    }

    public void write(char[] str) throws IOException {
        m_file.write(new String(str).getBytes());
    }

    public void write(byte[] data) throws IOException {
        m_file.write(data);
    }

    public void write32(int n) throws IOException {
        m_file.write(n & 0xFF);
        m_file.write((n >> 8) & 0xFF);
        m_file.write((n >> 16) & 0xFF);
        m_file.write((n >> 24) & 0xFF);
    }

    public void writeFloat(float f) throws IOException {
        write32(Float.floatToRawIntBits(f));
    }

    public long writeTemp32() throws IOException {
        long pos = m_file.getFilePointer();
        write32(0xDEADDEAD);
        return pos;
    }

    public void overwriteTemp32(long pos, int n) throws IOException {
        long currentPos = m_file.getFilePointer();
        m_file.seek(pos);
        write32(n);
        m_file.seek(currentPos);
    }

    public void align(int alignment) throws IOException {
        int mod = (int)(m_file.getFilePointer() % (long)alignment);
        int bytesToWrite = alignment - mod;
        for (int i = 0; i < bytesToWrite; ++i) {
            m_file.writeByte(0xAA);
        }
    }
}
