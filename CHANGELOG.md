# Changelog

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-08-12

First release. The project was called `enigma-machine` until now and was never published under that
name.

### Added

- `Envelope` and `ByteEnigma.transform(byte[], byte[], long)`: per-message nonces, which close the
  keystream-reuse leak that the key-only mode has. The leak itself is left reachable on purpose;
  `NonceTest` pins both halves.
- A command line: `demo`, `seal`, `open`, `raw`, `break`, `offsets`. Shipped as a runnable jar with
  nothing to download.
- `ByteEnigma.transformWindow`, which steps the rotors through a prefix without doing the substitution
  work. The crib attack decrypts sixteen bytes per candidate instead of the whole message. Together
  with the generator change this took the crib sweep from 105,452 to 383,173 keys/sec on the same two
  cores, measured on a 117-byte message; `docs/keyspace-sweep.md` has the rates on a longer one.
- `Lcg48`: the `java.util.Random` algorithm over a plain field instead of an `AtomicLong`. Bit for bit
  identical, enforced by `Lcg48EquivalenceTest` over several million draws. The key schedule draws
  1,275 values per candidate key and never shares a generator, so the compare-and-set was pure
  overhead.
- `data/corpus/`: the public-domain English the quadgram table is generated from, with provenance in
  `MANIFEST.md`. `QuadgramTableReproducibilityTest` fails the build if the shipped table stops matching
  what the corpus produces.
- `docs/`: an architecture decision record, a page on how the cipher falls, and the log of a sweep of
  the entire 2^32 keyspace in which the true key came first by 206 log-units.
- GitHub Actions running the tests on JDK 17 and 21, and a release workflow that attaches the jar.
- MIT licence. There was none before, which meant nobody could legally use any of this.

### Changed

- Renamed to `byte-enigma`. The old name promised a historical Enigma simulator, which this has never
  been and does not intend to become. `docs/adr/0001` records the decision.
- `SeedSweep` is now generic over what it rekeys and knows nothing about ciphers. It takes a factory
  rather than an instance, so it is no longer possible to hand it one mutable cipher for four threads
  to share.
- `QuadgramScorer` scores bytes directly through a lookup table instead of decoding to a `String`
  first. The old path tried UTF-8 and fell back to Latin-1, so a candidate whose bytes happened to form
  valid multi-byte UTF-8 decoded to fewer characters, was scored over fewer windows, and floated up the
  leaderboard on nothing. Every candidate is now charged the same number of windows.
- The quadgram table counts only the windows the scorer will actually charge: four consecutive letters
  in the source text. The previous table squeezed the spaces out first, which manufactures quadgrams
  like `THEQ` that no scorer ever asks about.
- The corpus behind the table is now 3.7 MB of prose by eight authors, up from a 472 KB corpus that was
  not committed and has since been lost. 1,037,999 counted windows, up from 345,504.
- `benchmarks` is a module of the reactor build rather than a detached project, so it can no longer
  drift. `CandidateBenchmark` replaces a benchmark that still modelled constructing a fresh machine per
  candidate, months after the sweep had stopped doing that.
- Rotor and involution internals are package-private. `rotors()` used to hand out an unmodifiable list
  of mutable rotors, so a caller could reach in and reseed one; `rotorCount()` replaces it.
- `encrypt(String)` and `decrypt(String)` are gone. A method called `encrypt` that offers no nonce and
  no authentication is a trap. Base64 framing lives in the command line and in `Envelope` now.

### Fixed

- The honesty note printed by the demo still claimed the passphrase derivation used `String.hashCode`.
  It had used FNV-1a since before that note was last touched.

### Removed

- `NullScorer` and `SeedSweep.NecessaryCondition`: both public, neither ever referenced.
- `QuadgramTableGenerator`'s embedded 28 KB corpus, and its default path into a working directory that
  was never committed. Running it from a clean checkout used to overwrite the shipped table with one
  built from a fifteenth of the data.

## Earlier

Nothing was released before 1.0.0, so there is no compatibility to speak of. Two moments are worth
recording anyway, because both changed the golden vector and both were deliberate:

- **Sub-key decorrelation.** Component sub-keys are spread through a MurmurHash3 finaliser. Before
  that, neighbouring keys produced neighbouring sub-keys and some components collided outright at small
  keys. Hygiene rather than security; the keyspace did not change.
- **Passphrase derivation.** Moved from `String.hashCode` to FNV-1a over the UTF-8 bytes.
  `String.hashCode` collides on structure rather than chance, so `"Aa"` and `"BB"` produced identical
  ciphertext.

[1.0.0]: https://github.com/qnicondavid/byte-enigma/releases/tag/v1.0.0
