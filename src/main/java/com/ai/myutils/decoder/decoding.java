package com.ai.myutils.decoder;

import com.ai.myutils.Uint8Array;
import com.ai.myutils.binary;
import com.ai.myutils.encoder.Encoder;
import com.ai.myutils.encoder.encoding;

import java.math.BigInteger;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

import static com.ai.myutils.Printer.print;
import static com.ai.myutils.Uint8Array.*;
import static jdk.nashorn.internal.runtime.GlobalFunctions.decodeURIComponent;
import static jdk.nashorn.internal.runtime.GlobalFunctions.escape;

/**
 * Efficient schema-less binary decoding with support for variable length encoding.
 * <p>
 * Use [lib0/decoding] with [lib0/encoding]. Every encoding function has a corresponding decoding function.
 * <p>
 * Encodes numbers in little-endian order (least to most significant byte order)
 * and is compatible with Golang's binary encoding (https://golang.org/pkg/encoding/binary/)
 * which is also used in Protocol Buffers.
 * <p>
 * ```js
 * // encoding step
 * const encoder = encoding.createEncoder()
 * encoding.writeVarUint(encoder, 256)
 * encoding.writeVarString(encoder, 'Hello world!')
 * const buf = encoding.toUint8Array(encoder)
 * ```
 * <p>
 * ```js
 * // decoding step
 * const decoder = decoding.createDecoder(buf)
 * decoding.readVarUint(decoder) // => 256
 * decoding.readVarString(decoder) // => 'Hello world!'
 * decoding.hasContent(decoder) // => false - all data is read
 * ```
 */
public class decoding {

    static RuntimeException errorUnexpectedEndOfArray = new RuntimeException("Unexpected end of array");
    static RuntimeException errorIntegerOutOfRange = new RuntimeException("Integer out of Range");


    /**
     * @param uint8Array {Uint8Array} 
     * @return {Decoder}
     */
    public static Decoder createDecoder(int[] uint8Array) {
        return new Decoder(uint8Array);
    }

    /**
     * @param decoder {Decoder} 
     * @return {boolean}
     */
    public static boolean hasContent(Decoder decoder) {
        return decoder.pos != decoder.arr.length;
    }


    /**
     * Clone a decoder instance.
     * Optionally set a new position parameter.
     *
     * @param decoder {Decoder} The decoder instance
     * @param newPos {number}  [newPos] Defaults to current position
     * @return {Decoder} A clone of `decoder`
     */
    public static Decoder clone(Decoder decoder, Integer newPos) {
        if (newPos == null) {
            newPos = decoder.pos;
        }
        Decoder _decoder = createDecoder(decoder.arr);
        _decoder.pos = newPos;
        return _decoder;
    }

    /**
     * Create an Uint8Array view of the next `len` bytes and advance the position by `len`.
     * <p>
     * Important: The Uint8Array still points to the underlying ArrayBuffer. Make sure to discard the result as soon as possible to prevent any memory leaks.
     * Use `buffer.copyUint8Array` to copy the result into a new Uint8Array.
     *
     * @param decoder {Decoder} The decoder instance
     * @param len {number}  The length of bytes to read
     * @return {int[]}
     */
    public static int[] readUint8Array(Decoder decoder, Integer len) {
        int[] view = new int[len];
        System.arraycopy(decoder.arr, decoder.pos, view, 0, len);
        decoder.pos += len;
        return view;
    }

    /**
     * Read variable length Uint8Array.
     * <p>
     * Important: The Uint8Array still points to the underlying ArrayBuffer. Make sure to discard the result as soon as possible to prevent any memory leaks.
     * Use `buffer.copyUint8Array` to copy the result into a new Uint8Array.
     *
     * @param decoder {Decoder}
     * @return {int[]}
     */
    public static int[] readVarUint8Array(Decoder decoder) {
        return readUint8Array(decoder, readVarUint(decoder));
    }

    /**
     * Read the rest of the content as an ArrayBuffer
     *
     * @param decoder {Decoder}
     * @return {int[]}
     */
    public static int[] readTailAsUint8Array(Decoder decoder) {
        return readUint8Array(decoder, decoder.arr.length - decoder.pos);
    }

    /**
     * Skip one byte, jump to the next position.
     *
     * @param decoder {Decoder}  The decoder instance
     * @return {number} The next position
     */
    public static Integer skip8(Decoder decoder) {
        return decoder.pos++;
    }

    /**
     * Read one byte as unsigned integer.
     *
     * @param decoder {Decoder} The decoder instance
     * @return {number} Unsigned 8-bit integer
     */
    public static int readUint8(Decoder decoder) {
        return decoder.arr[decoder.pos++];
    }

    /**
     * Read 2 bytes as unsigned integer.
     *
     * @param decoder {Decoder} The decoder instance
     * @return {number} An unsigned integer.
     */
    public static Integer readUint16(Decoder decoder) {
        Integer uint =
                decoder.arr[decoder.pos] +
                        (decoder.arr[decoder.pos + 1] << 8);
        decoder.pos += 2;
        return uint;
    }

    /**
     * Read 4 bytes as unsigned integer.
     *
     * @param decoder {Decoder} The decoder instance
     * @return {number} An unsigned integer.
     */
    public static Integer readUint32(Decoder decoder) {
        Integer uint =
                (decoder.arr[decoder.pos] +
                        (decoder.arr[decoder.pos + 1] << 8) +
                        (decoder.arr[decoder.pos + 2] << 16) +
                        (decoder.arr[decoder.pos + 3] << 24));
        decoder.pos += 4;
        return uint;
    }

    /**
     * Read 4 bytes as unsigned integer in big endian order.
     * (most significant byte first)
     *
     * @param decoder {Decoder}
     * @return {number} An unsigned integer.
     */
    public static Integer readUint32BigEndian(Decoder decoder) {
        Integer uint =
                (decoder.arr[decoder.pos + 3] +
                        (decoder.arr[decoder.pos + 2] << 8) +
                        (decoder.arr[decoder.pos + 1] << 16) +
                        (decoder.arr[decoder.pos] << 24));
        decoder.pos += 4;
        return uint;
    }

    /**
     * Look ahead without incrementing the position
     * to the next byte and read it as unsigned integer.
     *
     * @param decoder {Decoder}
     */
    public static int peekUint8(Decoder decoder) {
        return decoder.arr[decoder.pos];
    }

    /**
     * Look ahead without incrementing the position
     * to the next byte and read it as unsigned integer.
     *
     * @param decoder {Decoder}
     * @return {number} An unsigned integer.
     */
    public static Integer peekUint16(Decoder decoder) {
        return decoder.arr[decoder.pos] +
                (decoder.arr[decoder.pos + 1] << 8);
    }

    /**
     * Look ahead without incrementing the position
     * to the next byte and read it as unsigned integer.
     *
     * @param decoder {Decoder}
     * @return {number} An unsigned integer.
     */
    public static Integer peekUint32(Decoder decoder) {
        return (decoder.arr[decoder.pos] +
                (decoder.arr[decoder.pos + 1] << 8) +
                (decoder.arr[decoder.pos + 2] << 16) +
                (decoder.arr[decoder.pos + 3] << 24));
    }

    /**
     * Read unsigned integer (32bit) with variable length.
     * 1/8th of the storage is used as encoding overhead.
     * * numbers < 2^7 is stored in one bytlength
     * * numbers < 2^14 is stored in two bylength
     *
     * @param decoder {Decoder}
     * @return {number} An unsigned integer.length
     */
    public static int readVarUint(Decoder decoder) {
        int num = 0;
        int mult = 1;
        int len = decoder.arr.length;
        while (decoder.pos < len) {
            int r = decoder.arr[decoder.pos++];
            // num = num | ((r & binary.BITS7) << len)
            num = num + (r & binary.BITS7) * mult; // shift $r << (7*#iterations) and add it to num
            mult *= 128; // next iteration, shift 7 "more" to the left
            if (r < binary.BIT8) {
                return num;
            }
        }
        throw errorUnexpectedEndOfArray;
    }

    /**
     * 读取可变长度有符号整数(32位)
     * 存储使用1/8的空间作为编码开销
     * - 数值 < 2^7 用1字节存储
     * - 数值 < 2^14 用2字节存储
     * 
     * @param decoder {Decoder}
     * @return {number}
     */
    public static int readVarInt(Decoder decoder) {
        int r = decoder.arr[decoder.pos++];
        int num = r & binary.BITS6;  // BITS6 = 0x3F
        long mult = 64;
        int sign = (r & binary.BIT7) > 0 ? -1 : 1;  // BIT7 = 0x40

        if ((r & binary.BIT8) == 0) {  // BIT8 = 0x80
            return sign * num;
        }

        final int len = decoder.arr.length;
        while (decoder.pos < len) {
            r = decoder.arr[decoder.pos++];
            num += (r & binary.BITS7) * mult;  // BITS7 = 0x7F
            mult *= 128;

            if (r < binary.BIT8) {  // BIT8 = 0x80
                return sign * num;
            }
            if (num > Long.MAX_VALUE) {
                throw new RuntimeException("Integer out of Range");
            }
        }
        throw new RuntimeException("Unexpected end of array");
    }


    /**
     * 查看但不消耗下一个可变长度无符号整数
     *
     * @param decoder 解码器实例
     * @return 读取的无符号整数值
     */
    public static long peekVarUint(Decoder decoder) {
        int pos = decoder.pos;          // 保存当前位置
        long s = readVarUint(decoder);  // 读取值
        decoder.pos = pos;              // 恢复位置
        return s;
    }

    /**
     * 查看但不消耗下一个可变长度有符号整数
     *
     * @param decoder 解码器实例
     * @return 读取的有符号整数值
     */
    public static long peekVarInt(Decoder decoder) {
        int originalPos = decoder.pos;  // 保存原始位置
        long value = readVarInt(decoder);  // 读取值
        decoder.pos = originalPos;  // 恢复位置
        return value;
    }

    /**
     * 读取可变长度字符串的备用实现（不使用UTF-8解码器时）
     * We don't test this function anymore as we use native decoding/encoding by default now.
     * Better not modify this anymore...
     * <p>
     * Transforming utf8 to a string is pretty expensive. The code performs 10x better
     * when String.fromCodePoint is fed with all characters as arguments.
     * But most environments have a maximum number of arguments per functions.
     * For effiency reasons we apply a maximum of 10000 characters at once.
     *
     * @param decoder {Decoder} 解码器实例
     * @return 解码后的字符串
     */
    public static String _readVarStringPolyfill(Decoder decoder) {
        int remainingLen = readVarUint(decoder);
        if (remainingLen == 0) {
            return "";
        }

        // 初始读取第一个字符
        StringBuilder encodedString = new StringBuilder();
        encodedString.appendCodePoint(readUint8(decoder));
        remainingLen--;

        if (remainingLen < 100) {
            // 小字符串直接逐个读取
            while (remainingLen-- > 0) {
                encodedString.appendCodePoint(readUint8(decoder));
            }
        } else {
            // 大字符串分块处理（每块最多10000字符）
            while (remainingLen > 0) {
                int nextLen = Math.min(remainingLen, 10000);
                int[] bytes = readUint8Array(decoder, nextLen);

                // 批量处理字节数组
                for (int b : bytes) {
                    encodedString.appendCodePoint(b & 0xFF);
                }

                remainingLen -= nextLen;
            }
        }

        // 模拟JavaScript的escape/decodeURIComponent流程
        return ((String) decodeURIComponent(null, escape(null, encodedString)));
    }

    /**
     * 使用原生UTF-8解码器读取可变长度字符串
     *
     * @param decoder 解码器实例
     * @return 解码后的字符串
     */
    private static String _readVarStringNative(Decoder decoder) {
        byte[] bytes = toByteArray(readVarUint8Array(decoder));
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 读取可变长度字符串
     * 自动选择原生UTF-8解码器或备用实现
     * @param {Decoder} decoder
     * @return {String} The read String
     */
    public static String readVarString(Decoder decoder) {
        try {
            // 尝试使用原生UTF-8解码器
            return _readVarStringNative(decoder);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            // 如果原生解码失败，回退到备用实现
            return _readVarStringPolyfill(decoder);
        }
    }

    /**
     * @param decoder {Decoder} 解码器实例
     * @return {int[]} 解码后的字节数组
     */
    public static int[] readTerminatedUint8Array(Decoder decoder) {
        Encoder encoder = encoding.createEncoder();
        int b;
        while (true) {
            b = readUint8(decoder);
            if (b == 0) {
                return encoding.toUint8Array(encoder);
            }
            if (b == 1) {
                b = readUint8(decoder);
            }
            encoding.write(encoder, b);
        }
    }

    /**
     * 读取以终止符结尾的字符串
     * @param decoder {Decoder} 解码器实例
     * @return {String} 解码后的UTF-8字符串
     */
    public static String readTerminatedString(Decoder decoder) {
        byte[] bytes = toByteArray(readTerminatedUint8Array(decoder));
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 查看但不消耗下一个可变长度字符串
     *
     * @param decoder 解码器实例
     * @return 读取的字符串值（不移动解码位置）
     */
    public static String peekVarString(Decoder decoder) {
        // 保存当前解码位置
        int originalPos = decoder.pos;

        // 读取字符串值
        String value = readVarString(decoder);

        // 恢复原始解码位置
        decoder.pos = originalPos;

        return value;
    }

    /**
     * 从Decoder中读取指定长度的字节数组，并返回IntBuffer实例
     * 
     * @param decoder {Decoder} 解码器实例
     * @param len {int} 要读取的字节长度
     * @return {IntBuffer} 包含读取数据的IntBuffer实例
     */
    public static IntBuffer readFromDataView(Decoder decoder, int len) {
        IntBuffer buffer = IntBuffer.wrap(decoder.arr, decoder.pos, len)
                .slice();
//                .order(ByteOrder.LITTLE_ENDIAN);
        decoder.pos += len;
        return buffer;
    }


    /**
     * 从Decoder中读取32位浮点数
     * 
     * @param decoder {Decoder} 解码器实例
     * @return {float} 解码后的32位浮点数
     */
    public static float readFloat32(Decoder decoder) {
        int intValue = decoder.arr[decoder.pos++];
        return Float.intBitsToFloat(intValue);
    }

    /**
     * 从Decoder中读取64位浮点数
     * 
     * @param decoder {Decoder} 解码器实例
     * @return {double} 解码后的64位浮点数
     */
    // export const readFloat64 = decoder => readFromDataView(decoder, 8).getFloat64(0, false)
    public static double readFloat64(Decoder decoder) {
        long longValue = ((long) decoder.arr[decoder.pos] << 32) |
                (decoder.arr[decoder.pos + 1] & 0xFFFFFFFFL);
        decoder.pos += 2;
        return Double.longBitsToDouble(longValue);
    }

    /**
     * 从Decoder中读取64位有符号整数
     * 
     * @param decoder {Decoder} 解码器实例
     * @return {long} 解码后的64位有符号整数
     */
    // export const readBigInt64 = decoder => /** @type {any} */ (readFromDataView(decoder, 8)).getBigInt64(0, false)
    public static long readBigInt64(Decoder decoder) {
        long longValue = ((long) decoder.arr[decoder.pos] << 32) |
                (decoder.arr[decoder.pos + 1] & 0xFFFFFFFFL);
        decoder.pos += 2;
        return longValue;
    }

    /**
     * 从Decoder中读取64位无符号整数
     * 
     * @param decoder {Decoder} 解码器实例
     * @return {BigInteger} 解码后的64位无符号整数
     */
    public static BigInteger readBigUint64(Decoder decoder) {
        long raw = readBigInt64(decoder);
        return raw >= 0 ?
                BigInteger.valueOf(raw) :
                BigInteger.valueOf(raw & Long.MAX_VALUE)
                        .add(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE));
    }

    // 定义解码函数数组
    private static final Function<Decoder, Object>[] readAnyLookupTable = new Function[]{
            // CASE 127: undefined (用null表示)
            decoder -> null,

            // CASE 126: null
            decoder -> null,

            // CASE 125: integer
            decoder -> readVarInt((Decoder) decoder),

            // CASE 124: float32
            decoder -> readFloat32((Decoder) decoder),

            // CASE 123: float64
            decoder -> readFloat64((Decoder) decoder),

            // CASE 122: bigint
            decoder -> readBigInt64((Decoder) decoder),

            // CASE 121: boolean (false)
            decoder -> false,

            // CASE 120: boolean (true)
            decoder -> true,

            // CASE 119: string
            decoder -> readVarString((Decoder) decoder),

            // CASE 118: Map<String, Object>
            decoder -> {
                int len = readVarUint((Decoder) decoder);
                Map<String, Object> obj = new HashMap<>();
                for (int i = 0; i < len; i++) {
                    String key = readVarString((Decoder) decoder);
                    obj.put(key, readAny((Decoder) decoder));
                }
                return obj;
            },

            // CASE 117: List<Object>
            decoder -> {
                int len = readVarUint((Decoder) decoder);
                List<Object> arr = new ArrayList<>();
                for (int i = 0; i < len; i++) {
                    arr.add(readAny((Decoder) decoder));
                }
                return arr;
            },

            // CASE 116: byte[]
            decoder -> readVarUint8Array((Decoder) decoder)
    };

    /**
     * 从Decoder中读取任意类型的值
     * 
     * @param decoder {Decoder} 解码器实例
     * @return {Object} 解码后的任意类型值
     */
    public static Object readAny(Decoder decoder) {
        int typeCode = 127 - readUint8(decoder);
        return readAnyLookupTable[typeCode].apply(decoder);
    }

}


