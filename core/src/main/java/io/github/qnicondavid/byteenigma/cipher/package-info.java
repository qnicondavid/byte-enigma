/**
 * The cipher: a reciprocal rotor machine over the 256 byte values.
 *
 * <p>{@link io.github.qnicondavid.byteenigma.cipher.ByteEnigma} is the whole of the public surface,
 * with {@link io.github.qnicondavid.byteenigma.cipher.Envelope} on top of it for messages that carry
 * a nonce. The rotors and involutions behind them are package-private, because a caller who can
 * reach in and reseed one rotor can silently break a machine that still looks intact.
 *
 * <p>Nothing here is secure. The key is 32 bits, there is no authentication, and the passphrase
 * derivation is a hash rather than a key derivation function. That is the specification: the point
 * of the project is a cipher weak enough to break in front of you, and
 * {@code io.github.qnicondavid.byteenigma.breaker} is the half that does the breaking.
 */
package io.github.qnicondavid.byteenigma.cipher;
