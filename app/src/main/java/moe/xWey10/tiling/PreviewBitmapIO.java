package moe.xWey10.tiling;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Local PFD transfer: exact native pixels, without PNG encode/decode on every page turn. */
final class PreviewBitmapIO {
    static final String FORMAT = "argb8888-v1";
    private static final int MAGIC = 0x4e515331, MAX_BYTES = 64 * 1024 * 1024;
    static void write(Bitmap bitmap, OutputStream output) throws IOException {
        int size = bitmap.getByteCount();
        if (bitmap.getConfig() != Bitmap.Config.ARGB_8888 || size > MAX_BYTES || bitmap.getRowBytes() != bitmap.getWidth() * 4) throw new IOException("Invalid preview bitmap");
        DataOutputStream stream = new DataOutputStream(output);
        stream.writeInt(MAGIC); stream.writeInt(bitmap.getWidth()); stream.writeInt(bitmap.getHeight()); stream.writeInt(size);
        ByteBuffer buffer = ByteBuffer.allocate(size); bitmap.copyPixelsToBuffer(buffer);
        stream.write(buffer.array()); stream.flush();
    }
    static Bitmap read(InputStream input, String format) throws IOException {
        if (!FORMAT.equals(format)) {
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inScaled = false; options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bitmap = BitmapFactory.decodeStream(input, null, options);
            if (bitmap == null) throw new IOException("Invalid legacy PNG"); return bitmap;
        }
        DataInputStream stream = new DataInputStream(input);
        if (stream.readInt() != MAGIC) throw new IOException("Invalid preview header");
        int width = stream.readInt(), height = stream.readInt(), size = stream.readInt();
        if (width < 1 || width > 4096 || height < 1 || height > 8192 || size < 1 || size > MAX_BYTES || (long) width * height * 4 != size) throw new IOException("Invalid preview dimensions");
        byte[] pixels = new byte[size]; stream.readFully(pixels);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try { bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixels)); return bitmap; }
        catch (RuntimeException e) { bitmap.recycle(); throw new IOException("Invalid preview pixels", e); }
    }
}
