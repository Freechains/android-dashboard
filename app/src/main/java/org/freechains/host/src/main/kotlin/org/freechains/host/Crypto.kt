package org.freechains.host

import org.freechains.common.*
import com.goterl.lazysodium.LazySodium
import com.goterl.lazysodium.interfaces.Box
import com.goterl.lazysodium.interfaces.PwHash
import com.goterl.lazysodium.interfaces.SecretBox
import com.goterl.lazysodium.utils.Key
import com.goterl.lazysodium.utils.KeyPair

private const val len = "6F99999751DE615705B9B1A987D8422D75D16F5D55AF43520765FA8C5329F7053CCAF4839B1FDDF406552AF175613D7A247C5703683AEC6DBDF0BB3932DD8322".length

fun HKey.keyIsPrivate () : Boolean {
    return this.length == len
}

// GENERATE

fun String.toPwHash (): ByteArray {
    val inp = this.toByteArray()
    val out = ByteArray(32)                       // TODO: why 32?
    val salt = ByteArray(PwHash.ARGON2ID_SALTBYTES)     // all org.freechains.core.getZeros
    assert_(
            lazySodium.cryptoPwHash(
                    out, out.size, inp, inp.size, salt,
                    PwHash.OPSLIMIT_INTERACTIVE, PwHash.MEMLIMIT_INTERACTIVE, PwHash.Alg.getDefault()
            )
    )
    return out
}

fun String.toShared () : HKey {
    return Key.fromBytes(this.toPwHash()).asHexString
}

fun String.toPubPvt () : KeyPair {
    return lazySodium.cryptoSignSeedKeypair(this.toPwHash())
}

// ENCRYPT

fun String.encryptShared (shared: HKey) : String {
    val nonce = lazySodium.nonce(SecretBox.NONCEBYTES)
    val key_ = Key.fromHexString(shared)
    return LazySodium.toHex(nonce) + lazySodium.cryptoSecretBoxEasy(this, nonce, key_)
}

fun String.encryptPublic (pub: HKey) : String {
    val dec = this.toByteArray()
    val enc = ByteArray(Box.SEALBYTES + dec.size)
    val key0 = Key.fromHexString(pub).asBytes
    val key1 = ByteArray(Box.CURVE25519XSALSA20POLY1305_PUBLICKEYBYTES)
    assert_(lazySodium.convertPublicKeyEd25519ToCurve25519(key1, key0))
    lazySodium.cryptoBoxSeal(enc, dec, dec.size.toLong(), key1)
    return LazySodium.toHex(enc)
}

// DECRYPT

fun String.decrypt (key: HKey) : String {
    return if (key.keyIsPrivate()) this.decryptPrivate(key) else this.decryptShared(key)
}

fun String.decryptShared (shared: HKey) : String {
    val idx = SecretBox.NONCEBYTES * 2
    return lazySodium.cryptoSecretBoxOpenEasy(
        this.substring(idx),
        LazySodium.toBin(this.substring(0, idx)),
        Key.fromHexString(shared)
    )
}

fun String.decryptPrivate (pvt: HKey) : String {
    val enc = LazySodium.toBin(this)
    val dec = ByteArray(enc.size - Box.SEALBYTES)

    val pub1 = Key.fromHexString(pvt.pvtToPub()).asBytes
    val pvt1 = Key.fromHexString(pvt).asBytes
    val pub2 = ByteArray(Box.CURVE25519XSALSA20POLY1305_PUBLICKEYBYTES)
    val pvt2 = ByteArray(Box.CURVE25519XSALSA20POLY1305_SECRETKEYBYTES)
    assert_(lazySodium.convertPublicKeyEd25519ToCurve25519(pub2, pub1))
    assert_(lazySodium.convertSecretKeyEd25519ToCurve25519(pvt2, pvt1))

    assert_(lazySodium.cryptoBoxSealOpen(dec, enc, enc.size.toLong(), pub2, pvt2))
    return dec.toString(Charsets.UTF_8)
}