# Security

## Please do not report that the cipher is weak

It is meant to be. The key is 32 bits, there is no authentication, and the passphrase derivation is
a hash rather than a key derivation function. All of that is deliberate, documented in
[docs/why-the-cipher-falls.md](docs/why-the-cipher-falls.md), and the repository contains a search
that exploits the first of them over the entire keyspace.

There is nothing to disclose responsibly here, because nothing about this is a secret and nobody
should be relying on it. If you are, stop: use libsodium, or `javax.crypto` with AES-GCM, or age.

## What is worth reporting

Open a normal issue for any of these.

- **A claim that is wrong.** Everything in the docs is meant to be checkable against the code or
  against a measurement. If a number does not reproduce, or a test named in a comment does not
  exist, or a documented command does not work, that is a real defect and the one I most want to
  hear about.
- **A correctness bug in the cipher.** Reciprocity and the absence of fixed points are structural
  claims. If you can produce a key, a rotor count and an input where a byte encrypts to itself, or
  where a double transform does not return the input, that is a genuine bug rather than a weakness.
- **A correctness bug in the search.** A sweep that skips keys, miscounts them, or reports a
  throughput it did not achieve undermines the only claim this project actually makes.
- **A break the repository does not already describe.** Something faster than exhaustion, or an
  attack that needs less than the ones in `breaker`, would be genuinely interesting and I would
  want it written up in the docs.

## What is out of scope

Anything that amounts to "a 32-bit key is too small", "there is no MAC", or "this is not AES". Those
are all true, all documented, and all on purpose.
