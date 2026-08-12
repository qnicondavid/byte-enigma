# Contributing

Issues and pull requests are welcome. A few things are worth knowing before you spend time on one.

## What this project will not accept

**Making the cipher secure.** The 32-bit key is the specification, not an oversight. Widening it would
make the breaker undemonstrable, which is the half of the repository that justifies the other half.
See [ADR 0001](docs/adr/0001-derived-wiring-over-historical-rotors.md).

**Making it historically faithful.** It is not an Enigma simulator and is not becoming one. A faithful
26-letter implementation is a good project; it is a different project.

**Removing the textbook mode.** `transform(byte[], byte[])` leaks when a key is reused, and that is
what the breaker attacks and what the demo demonstrates. It stays, documented.

## What would actually help

- Making the sweep faster. Read [why-the-cipher-falls.md](docs/why-the-cipher-falls.md) first. Almost
  all of the time is in the key schedule, so transform-level optimisations will not move the number.
  Bring a JMH result.
- More attacks. Hill climbing on rotor settings, index of coincidence, a bigram or trigram fallback for
  short messages.
- Better crib handling. `admissibleOffsets` finds candidate positions but the search still needs to be
  told which one to use; sweeping all of them in one pass would be a real improvement.
- Anything in the docs that is wrong, unclear, or overstated. Overstatement especially.

## Ground rules

**The golden vector does not move without a reason in the changelog.** `GoldenVectorTest` pins one
ciphertext byte for byte. Every refactor so far has been measured against it. If your change moves it,
either the change is wrong or it is a deliberate cipher change that belongs in `CHANGELOG.md` with an
explanation.

**Derived files come with their source.** The quadgram table is generated from `data/corpus`, and
`QuadgramTableReproducibilityTest` fails if they disagree. Change the corpus, regenerate, commit both,
and record where any new text came from in `data/corpus/MANIFEST.md`. Only works published before 1929.

**No new dependencies in `core`.** It has none, the jar runs anywhere, and the argument parser is
forty lines for exactly this reason. Test-scope and benchmark dependencies are a different question.

**Claims need numbers.** "Faster" means a JMH result or a measured keys/sec from a real sweep, in the
pull request. The project publishes measured rates and says so when a figure is arithmetic from one
instead; please keep that distinction.

## Building

```
mvn test                     # 121 tests, about ten seconds
mvn -Pdist package           # core/target/byte-enigma.jar
mvn -pl benchmarks -am package && java -jar benchmarks/target/benchmarks.jar
```

JDK 17 or newer. CI runs the tests on 17 and 21 and will not merge a red branch.

## Style

Follow what is there. Four spaces, no wildcard imports, no abbreviations in names. Javadoc on anything
public, and prefer a sentence about why over a sentence restating the signature. Test names are
sentences describing the behaviour, not `testFoo2`.
