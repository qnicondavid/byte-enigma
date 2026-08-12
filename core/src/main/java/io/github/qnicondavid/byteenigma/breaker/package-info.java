/**
 * The two attacks, and the language model one of them needs.
 *
 * <p>{@link io.github.qnicondavid.byteenigma.breaker.CribMatcher} is the known-plaintext attack: it
 * needs a fragment you already know and where you expect it, and in exchange it can reject a wrong
 * key after one byte. {@link io.github.qnicondavid.byteenigma.breaker.QuadgramSearch} needs nothing
 * but the assumption that the plaintext is English, which is a far weaker thing to assume and is
 * roughly how real traffic was read.
 *
 * <p>{@link io.github.qnicondavid.byteenigma.breaker.QuadgramTableBuilder} regenerates the shipped
 * frequency table from the corpus committed under {@code data/corpus}. A derived file whose source
 * is missing is a blob with a text extension, so a test fails the build if the two disagree.
 */
package io.github.qnicondavid.byteenigma.breaker;
