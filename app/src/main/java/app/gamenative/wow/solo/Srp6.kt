package app.gamenative.wow.solo

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * AzerothCore's account password check (`Acore::Crypto::SRP6`), so the app can write an account straight into
 * the auth database: v = g ^ SHA1(salt | SHA1(USER ":" PASS)) mod N, with the digest, salt and verifier as
 * little-endian numbers, the way AzerothCore's BigNumber reads and writes them.
 */
internal object Srp6 {
    private val N = BigInteger("894B645E89E1535BBDAD5B8B290650530801B18EBFBF5E8FAB3C82872A3E9BB7", 16)
    private val g = BigInteger.valueOf(7)

    fun newSalt(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    /** Both names are upper-cased first, as AzerothCore's AccountMgr does. Returns 32 little-endian bytes. */
    fun verifier(username: String, password: String, salt: ByteArray): ByteArray {
        val inner = sha1("${username.uppercase()}:${password.uppercase()}".toByteArray(Charsets.UTF_8))
        val x = fromLittleEndian(sha1(salt + inner))
        return toLittleEndian(g.modPow(x, N), 32)
    }

    private fun sha1(bytes: ByteArray) = MessageDigest.getInstance("SHA-1").digest(bytes)

    private fun fromLittleEndian(bytes: ByteArray) = BigInteger(1, bytes.reversedArray())

    private fun toLittleEndian(n: BigInteger, size: Int): ByteArray {
        val big = n.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        return big.reversedArray() + ByteArray(size - big.size)
    }
}
