package com.ai.myutils.encoder;

import com.ai.myutils.Uint8Array;
import com.ai.myutils.binary;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class encoding<T> {
    public static Encoder createEncoder() {
        return new Encoder();
    }

//    public static byte[] encode(EncoderFunction f) {
//        Encoder encoder = createEncoder();
//        f.apply(encoder);
//        return toUint8Array(encoder);
//    }

    public static int length(Encoder encoder) {
        int len = encoder.cpos;
        for (int[] buf : encoder.bufs) {
            len += buf.length;
        }
        return len;
    }

    public static boolean hasContent(Encoder encoder) {
        return encoder.cpos > 0 || !encoder.bufs.isEmpty();
    }

    public static int[] toUint8Array(Encoder encoder) {
        int[] uint8arr = new int[length(encoder)];
        int curPos = 0;
        for (int[] d : encoder.bufs) {
            System.arraycopy(d, 0, uint8arr, curPos, d.length);
            curPos += d.length;
        }
        System.arraycopy(encoder.cbuf, 0, uint8arr, curPos, encoder.cpos);
        return uint8arr;
    }

    public static void verifyLen(Encoder encoder, int len) {
        if (encoder.cbuf.length - encoder.cpos < len) {
            encoder.bufs.add(Arrays.copyOf(encoder.cbuf, encoder.cpos));
            encoder.cbuf = new int[Math.max(encoder.cbuf.length, len) * 2];
            encoder.cpos = 0;
        }
    }

    public static <T> void write(Encoder encoder, int num) {
        if (encoder.cpos == encoder.cbuf.length) {
            encoder.bufs.add(encoder.cbuf);
            encoder.cbuf = new int[encoder.cbuf.length * 2];
            encoder.cpos = 0;
        }
        encoder.cbuf[encoder.cpos++] = num;
    }

    public static void set(Encoder encoder, int pos, int num) {
        int[] buffer = null;
        for (int[] buf : encoder.bufs) {
            if (pos < buf.length) {
                buffer = buf;
                break;
            }
            pos -= buf.length;
        }
        if (buffer == null) {
            buffer = encoder.cbuf;
        }
        buffer[pos] = num;
    }

    public static void writeUint8(Encoder encoder, int num) {
        write(encoder, num);
    }

    public static void setUint8(Encoder encoder, int pos, int num) {
        set(encoder, pos, num);
    }

    public static void writeUint16(Encoder encoder, int num) {
        write(encoder, num & binary.BITS8);
        write(encoder, (num >>> 8) & binary.BITS8);
    }

    public static void setUint16(Encoder encoder, int pos, int num) {
        set(encoder, pos, num & binary.BITS8);
        set(encoder, pos + 1, (num >>> 8) & binary.BITS8);
    }

    public static void writeUint32(Encoder encoder, int num) {
        for (int i = 0; i < 4; i++) {
            write(encoder, num & binary.BITS8);
            num >>>= 8;
        }
    }

    public static void writeUint32BigEndian(Encoder encoder, int num) {
        for (int i = 3; i >= 0; i--) {
            write(encoder, (num >>> (8 * i)) & binary.BITS8);
        }
    }

    public static void setUint32(Encoder encoder, int pos, int num) {
        for (int i = 0; i < 4; i++) {
            set(encoder, pos + i, num & binary.BITS8);
            num >>>= 8;
        }
    }

    public static void writeVarUint(Encoder encoder, int num) {
        while (num > binary.BITS7) {
            write(encoder, binary.BIT8 | (binary.BITS7 & num));
            num = num / 128;
        }
        write(encoder, binary.BITS7 & num);
    }

    public static void writeVarInt(Encoder encoder, long num, boolean isNegative) {
        if (isNegative) {
            num = -num;
        }
        //                   |- whether to continue reading          |- whether is negative           |- number
        write(encoder, (int) ((num > binary.BITS6 ? binary.BIT8 : 0) | (isNegative ? binary.BIT7 : 0) | (binary.BITS6 & num)));
        num = num / 64;
        // We don't need to consider the case of num === 0, so we can use a different
        // pattern here than above.
        while (num > 0) {
            write(encoder, (int) ((num > binary.BITS7 ? binary.BIT8 : 0) | (binary.BITS7 & num)));
            num = num / 128;
        }
    }

    public static void writeVarString(Encoder encoder, String str) {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        writeVarUint(encoder, bytes.length);
        writeUint8Array(encoder, Uint8Array.toIntArray(bytes));
    }

    public static void writeTerminatedString(Encoder encoder, String str) {
        writeTerminatedUint8Array(encoder, Uint8Array.toIntArray(str.getBytes(StandardCharsets.UTF_8)));
    }

    public static void writeTerminatedUint8Array(Encoder encoder, int[] buf) {
        for (int b : buf) {
            if (b == 0 || b == 1) {
                write(encoder, 1);
            }
            write(encoder, b);
        }
        write(encoder, 0);
    }

    public static void writeBinaryEncoder(Encoder encoder, Encoder append) {
        writeUint8Array(encoder, toUint8Array(append));
    }

    public static void writeUint8Array(Encoder encoder, int[] uint8Array) {
        int leftCopyLen = Math.min(encoder.cbuf.length - encoder.cpos, uint8Array.length);
        int rightCopyLen = uint8Array.length - leftCopyLen;
        System.arraycopy(uint8Array, 0, encoder.cbuf, encoder.cpos, leftCopyLen);
        encoder.cpos += leftCopyLen;
        if (rightCopyLen > 0) {
            encoder.bufs.add(encoder.cbuf);
            encoder.cbuf = new int[Math.max(encoder.cbuf.length * 2, rightCopyLen)];
            System.arraycopy(uint8Array, leftCopyLen, encoder.cbuf, 0, rightCopyLen);
            encoder.cpos = rightCopyLen;
        }
    }

    public static void writeVarUint8Array(Encoder encoder, int[] uint8Array) {
        writeVarUint(encoder, uint8Array.length);
        writeUint8Array(encoder, uint8Array);
    }

    public static void writeFloat32(Encoder encoder, float num) {
        int bits = Float.floatToIntBits(num);
        writeUint32(encoder, bits);
    }

    public static void writeFloat64(Encoder encoder, double num) {
        long bits = Double.doubleToLongBits(num);
        writeUint32(encoder, (int) (bits & 0xFFFFFFFFL));
        writeUint32(encoder, (int) (bits >>> 32));
    }

    public static void writeBigInt64(Encoder encoder, long num) {
        writeUint32(encoder, (int) (num & 0xFFFFFFFFL));
        writeUint32(encoder, (int) (num >>> 32));
    }

    public static void writeBigUint64(Encoder encoder, long num) {
        writeUint32(encoder, (int) (num & 0xFFFFFFFFL));
        writeUint32(encoder, (int) (num >>> 32));
    }

    public static void writeAny(Encoder encoder, Object data) {
        if (data == null) {
            write(encoder, 126);
        } else if (data instanceof String) {
            write(encoder, 119);
            writeVarString(encoder, (String) data);
        } else if (data instanceof Number) {
            if (data instanceof Integer) {
                write(encoder, 125);
                writeVarInt(encoder, ((Integer) data).longValue(), false);
            } else if (data instanceof Float || data instanceof Double) {
                if (isFloat32(((Number) data).floatValue())) {
                    write(encoder, 124);
                    writeFloat32(encoder, ((Number) data).floatValue());
                } else {
                    write(encoder, 123);
                    writeFloat64(encoder, ((Number) data).doubleValue());
                }
            }
        } else if (data instanceof Boolean) {
            write(encoder, (Boolean) data ? 120 : 121);
        } else if (data instanceof List) {
            write(encoder, 117);
            List<?> list = (List<?>) data;
            writeVarUint(encoder, list.size());
            for (Object item : list) {
                writeAny(encoder, item);
            }
        } else if (data instanceof int[]) {
            write(encoder, 116);
            writeVarUint8Array(encoder, (int[]) data);
        } else if (data instanceof Map) {
            write(encoder, 118);
            Map<?, ?> map = (Map<?, ?>) data;
            writeVarUint(encoder, map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                writeVarString(encoder, entry.getKey().toString());
                writeAny(encoder, entry.getValue());
            }
        } else {
            write(encoder, 127); // undefined
        }
    }

    private static boolean isFloat32(float num) {
        return Float.floatToIntBits(num) == Float.floatToIntBits(Float.intBitsToFloat(Float.floatToIntBits(num)));
    }
}