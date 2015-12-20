import java.io.*;

public class BinaryWriter implements Closeable {
    private PrintStream m_stream;

    public BinaryWriter(File file) throws FileNotFoundException {
        m_stream = new PrintStream(file);
    }

    @Override
    public void close() throws IOException {
        m_stream.close();
    }

    public void write(char[] str) {
        m_stream.print(str);
    }

    public void write32(int n) throws IOException {
        m_stream.write(n & 0xFF);
        m_stream.write((n >> 8) & 0xFF);
        m_stream.write((n >> 16) & 0xFF);
        m_stream.write((n >> 24) & 0xFF);
    }

    public void writeFloat(float f) throws IOException {
        write32(Float.floatToRawIntBits(f));
    }
}
