package org.freechains.common

import java.io.DataInputStream
import java.io.IOException
import java.util.*

var fsRoot: String? = null

@Throws(IOException::class)
fun DataInputStream.readAllBytesX(): ByteArray {
    return this.readNBytesX(2147483647)
}

@Throws(IOException::class)
fun DataInputStream.readNBytesX(len: Int): ByteArray {
    return if (len < 0) {
        throw IllegalArgumentException("len < 0")
    } else {
        var bufs: MutableList<ByteArray?>? = null
        var result: ByteArray? = null
        var total = 0
        var remaining = len
        var n: Int = 0
        do {
            val buf = ByteArray(Math.min(remaining, 8192))
            var nread: Int
            nread = 0
            while (this.read(
                    buf,
                    nread,
                    Math.min(buf.size - nread, remaining)
                ).also({ n = it }) > 0
            ) {
                nread += n
                remaining -= n
            }
            if (nread > 0) {
                if (2147483639 - total < nread) {
                    throw OutOfMemoryError("Required array size too large")
                }
                total += nread
                if (result == null) {
                    result = buf
                } else {
                    if (bufs == null) {
                        bufs = ArrayList()
                        bufs.add(result)
                    }
                    bufs.add(buf)
                }
            }
        } while (n >= 0 && remaining > 0)
        if (bufs == null) {
            if (result == null) {
                ByteArray(0)
            } else {
                if (result.size == total) result else Arrays.copyOf(result, total)
            }
        } else {
            result = ByteArray(total)
            var offset = 0
            remaining = total
            var count: Int
            val var12: Iterator<*> = bufs.iterator()
            while (var12.hasNext()) {
                val b = var12.next() as ByteArray
                count = Math.min(b.size, remaining)
                System.arraycopy(b, 0, result, offset, count)
                offset += count
                remaining -= count
            }
            result
        }
    }
}