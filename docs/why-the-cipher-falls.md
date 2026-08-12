# Why the cipher falls

Four separate weaknesses, in rough order of how much they cost an attacker. Three of them are
inherent to the design and one is a mode-of-use bug that the code fixes but leaves visible, because
watching it happen is more useful than reading about it.

None of this is a disclosure. The cipher is a teaching artifact with a 32-bit key; being breakable is
the specification.

## 1. The key is 32 bits

Every rotor permutation, both involutions, every turnover offset and every starting position comes out
of one `int`. So there are 4,294,967,296 machines, and you can build
all of them.

The only thing standing between an attacker and the plaintext is the cost of one candidate. That cost
is almost entirely the key schedule: building three rotors and two involutions means five
Fisher-Yates shuffles of 256 elements, which is 1,275 draws from a pseudorandom generator, and the
transform afterwards is comparatively free.

`CandidateBenchmark` splits it out. Rekeying alone against rekeying plus a full decrypt plus a
language score is a difference of roughly a sixth, which is why the crib attack, decrypting
sixteen bytes, is barely faster than the ciphertext-only one, which decrypts the whole message and
then scores it. Neither can skip the key schedule, and the key schedule is the bill.

That has a practical consequence for anyone trying to make the search faster: optimising the transform
is worth less than it looks. The optimisation that mattered most was in the generator.
`java.util.Random` holds its state in an `AtomicLong` and advances it with a compare-and-set so one
instance can be shared safely across threads. The key schedule never shares one. Replacing it with the
same algorithm over a plain field, bit for bit identical and pinned by `Lcg48EquivalenceTest`, is most
of the difference; decrypting only the crib window rather than the whole message is the rest. Together
they took the crib sweep from 105,452 keys/sec to 383,173 on the same two cores, and
[keyspace-sweep.md](keyspace-sweep.md) records the split.

[docs/keyspace-sweep.md](keyspace-sweep.md) reports what that means for the full range: not a
projection, an actual run.

**Cost to the attacker:** a few hours on a laptop, once.
**What would fix it:** a key wide enough that exhaustion is not a strategy, which would make the
second half of this repository impossible to demonstrate. That is a deliberate trade, recorded in
[ADR 0001](adr/0001-derived-wiring-over-historical-rotors.md).

## 2. No byte ever encrypts to itself

The reflector is a fixed-point-free involution, and every rotor pass is a conjugation of it, so the
whole machine inherits the property: for every input byte and every position, the output differs from
the input.

That is what makes the machine reciprocal, and reciprocity is convenient: one setting both encrypts
and decrypts, with no separate inverse to implement or get wrong. It is also a leak, and
historically it was the leak that mattered most.

It costs the attacker positions for free. If you think the fragment `ATTACK AT DAWN` appears somewhere
in the message but you do not know where, you would normally have to try every offset against every
key. But a candidate offset can be ruled out without trying a single key: if the fragment and the
ciphertext agree on even one byte inside the window, the fragment cannot be there.

For a crib of length *L* over an alphabet of *A* symbols, the fraction of positions that survive is
roughly

```
(1 - 1/A)^L
```

which for a 16-byte crib over 256 symbols is 0.939, eliminating about 6% of positions. Run
`byte-enigma offsets` and you will see a number close to that.

Six percent is a poor discount, and it is poor precisely because the alphabet is large. Over the
historical 26-letter alphabet the same 16-character crib leaves `(25/26)^16 = 0.534` of the positions
standing, throwing away nearly half of them before any work is done. Widening the alphabet to 256
symbols made this particular attack much weaker, which is worth stating plainly, because most of the
differences between this cipher and the historical one make no difference at all and this one does.

**Cost to the attacker:** a small constant factor here, a large one on a 26-letter machine.
**What would fix it:** allowing fixed points, which means giving up reciprocity and implementing
encryption and decryption separately. That is the trade every post-Enigma design made.

## 3. Reusing a key without a nonce

This is the mode-of-use bug, and it is the one that would bite a real user first.

`ByteEnigma.transform(byte[], byte[])` derives the rotor offsets from the key alone and resets them at
the start of every call. So the permutation applied to byte *i* depends only on the key and on *i*.
Encipher two messages under one key and, wherever the plaintexts agree at the same offset, the
ciphertexts agree at that offset too.

An eavesdropper who has two messages gets a map of where they coincide, for free, without touching the
key. The demo prints the count: two similar sentences of 117 bytes share 107 of them in the clear, and
their ciphertexts share exactly 107 as well.

It gets worse with volume. Every message you send under one key is another sample of the same fixed
permutation at each position. Collect enough of them and each position becomes an ordinary substitution
cipher with a frequency distribution to attack. That is the depth attack, which is how a great deal of
real traffic was actually read, and it does not require breaking the key at all.

The fix is a nonce. `transform(byte[], byte[], long)` derives the offsets from key and nonce together,
and `Envelope` draws a fresh one per message and ships it in the clear ahead of the ciphertext. In the
demo the same two messages then share zero bytes.

The textbook mode is deliberately left in the API rather than removed. It is what `SeedSweep` attacks,
and a repository about how ciphers fail should keep the failure reachable.

**Cost to the attacker:** free, and it scales with how much traffic you send.
**What fixes it:** the nonce, which is already there. Note what it does not fix: the nonce is not key
material and travels in the clear, so the search is exactly as fast against sealed messages as against
raw ones. `BreakerEndToEndTest.aNonceDoesNotProtectTheKeyOnceItTravelsInTheClear` is that test.

## 4. The passphrase derivation is a hash, not a KDF

`ByteEnigma.fromPassword` runs FNV-1a over the UTF-8 bytes and finishes with the same MurmurHash3
finaliser the key schedule uses. No salt, no work factor, 32 bits out.

It replaced `String.hashCode`, which was worse in an interesting way: `hashCode` collides on
structure rather than on chance, so `"Aa"` and `"BB"` collided, and so did their keys and their
ciphertexts. `PassphraseKeyTest` pins that those pairs now separate and that all 2,704 two-letter
passphrases land on 2,704 distinct keys.

That is tidiness, not strength, and it is worth being exact about what it does not buy. Recovering the
key is not recovering the passphrase: the key is 32 bits and the space of passphrases is not, so
uncountably many phrases map onto each key. Anyone who recovers a key can decrypt every message under
it without ever learning what you typed, which is worse for you rather than better, since the
passphrase was never the thing being protected.

**Cost to the attacker:** nothing. They attack the 32-bit key directly and ignore the passphrase.
**What would fix it:** Argon2id or scrypt into a key wide enough to matter, which brings you back to
weakness 1.

## 5. There is no authentication

Worth stating even though it is not an attack on the cipher. Nothing detects tampering. Flip a bit in
transit and the recipient decrypts the corrupted result without complaint; because the cipher is a
per-position substitution, one flipped ciphertext byte corrupts exactly one plaintext byte and leaves
the rest readable, which makes targeted tampering easy rather than merely possible.
`EnvelopeTest.thereIsNoAuthentication` demonstrates it.

Real constructions solve this with a MAC over the ciphertext and the nonce, or by using an AEAD mode
that does it for you. This one does not, and adding it would not make anything else on this page
untrue.

## Summary

| Weakness | Costs the attacker | Fixed here? |
|---|---|---|
| 32-bit key | a few hours, once | No, and deliberately not |
| No fixed points | ~6% of crib positions here, ~47% on a 26-letter machine | No, it is what makes the machine reciprocal |
| Keystream reuse | free, and it compounds with traffic | Yes, by `Envelope`, but only that |
| Passphrase hashing | nothing, it is not the target | No |
| No authentication | not an attack, but tampering is undetectable | No |
