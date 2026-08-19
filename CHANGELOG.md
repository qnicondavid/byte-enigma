# Changelog

Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- Every figure keeps one set of margins. Markdown puts an image flush with the text column and scales
  it to fit, so a margin drawn inside a figure is a margin between that figure and the paragraph
  above it, and one that grows and shrinks with the window rather than staying put. The six were
  drawing six different ones: left edges from 16 units to 96 out of 880, right edges from 5.7% of the
  width to 13.6%, and the space above the first mark from 5.8% of the height to 18.1%. There is no
  margin now. A figure's first letter starts on the same line down the page as the first letter of
  the paragraph above it, its last one ends where that paragraph wraps, all six print their first
  line on one baseline, and all six leave the same room under their last. A stroked box that lines up
  with an edge sits two units inside it, because a stroke straddles the line it is drawn on and would
  otherwise lose its outer half to the edge.
- `docs/signal-path.svg` is generated rather than written by hand, which is how its margins came to
  differ from the other five to begin with. Its boxes are sized to the longest label they carry:
  `steps every byte` measured 106 units inside an 88-unit box and hung out of both sides of it. Its
  last line of caption sat two units above the bottom edge, so the descenders were cut off.
- The green label naming the key in `docs/score-distribution.svg` sat 56 units below the dot it names
  and under the row of tick numbers, where it read as part of the panel underneath. It sits beside
  the dot now.
- The first and last number on an axis anchor to their own ends rather than centring on the tick, so
  they stay inside the figure instead of hanging over its edge. The magnification quoted in
  `docs/score-gap.svg` reads 54 rather than 45, because it is computed from the two axes and both of
  them grew.

### Added

- `FigureNormTest` measures where each figure's ink starts and stops and fails the build when one of
  them drifts. `FigureInk` does the measuring: geometry exactly, strings from the advance width of a
  monospace character, which is why the check allows two units of slack on an edge a string defines.

### Fixed

- `break`, `offsets`, `open` and `raw` accept Base64 that has been wrapped across lines.
  `docs/keyspace-sweep.md` prints its ciphertext wrapped at 92 characters and then tells the reader
  to save it and pass it to `--in`, and the basic RFC 4648 decoder rejects line separators, so the
  first thing that page invites anyone to do failed with "ciphertext is not Base64". Whitespace now
  comes out of the string before it is decoded. `getMimeDecoder` would have taken the wrapped input
  too and was not used, because it also discards every character outside the alphabet, which would
  turn a file of prose into a short decode instead of an error. The suite now feeds `open` a wrapped
  file and a file of prose; it had only ever fed it the single line `seal` writes.
- The demo's javadoc still described the sweep as "4,294,967,296 keys in 2.03 hours across two
  machines, which projects to an hour and a half on sixteen threads alone". That description was
  retracted in 1.2.0 and corrected in the README and in both docs pages, and this comment was missed.
  It reads 62.2 minutes on sixteen threads of one machine, in one uninterrupted run.
- Two rates in `docs/keyspace-sweep.md` disagreed with the log printed further down the same page.
  The prose said the crib run opened at 2,449,228 keys/sec and the ciphertext-only run at 1,560,671;
  the log says 2,448,423 and 1,559,100. The prose figures were the key count divided by the elapsed
  seconds the log prints, and the log rounds those to a tenth, so they were arithmetic from a rounded
  number presented to the unit. The measured pair stands.
- `README.md` said the crib route "is 49% faster than reading the English" next to the only two other
  numbers in the sentence, which give 34.4% less wall clock or 52.5% more keys per second. 49% is the
  right figure to quote and comes from a third pair, the rate each run settled at over its last
  quarter. The sentence now says which pair it came from.
- `SeedSweep` named `LeaderboardOrderingTest` as the test pinning that the leaderboard's hand-written
  condition agrees with `Candidate.WEAKEST_FIRST`. No such test existed anywhere in the tree.
  `CandidateTest.theLeaderboardsFastPathAgreesWithTheComparator` is that test, over 900 pairs
  covering negative infinity, equal scores and both ends of the int range, and the comment names it.
  `SECURITY.md` calls a test named in a comment that does not exist a real defect, so this one was
  reported against its own rule.
- `docs/keyspace-sweep.md` said the key sits "about 97%" of the way up the range. The range is
  `[-2147483648, 2147483648)` and the key is 2083951437, so it sits (2083951437 + 2^31) / 2^32 =
  98.5% of the way up. 97.04% is 2083951437 / 2^31, the fraction of the positive half alone.
- The provenance table in `data/corpus/MANIFEST.md` gave the year out of each file's own bracketed
  title line as the year of first publication. On four of the eight they are different years: both
  Austens went on sale in the December before the year printed on them, and the Bryant and the
  Burgess files are later printings of books first published in 1907 and 1916. The table carries
  both columns now and says where the outside dates come from. The claim the table exists to support
  does not move: the latest of the sixteen years in it is 1920.
- The last-quarter rates in `docs/keyspace-sweep.md` were printed to seven figures and derived from
  three. They were 1,073,741,824 keys over an elapsed time read off the minute column, which the log
  rounds to a tenth. Taken instead from the `elapsedNanos` in the checkpoints and the rate the 75.00%
  progress line prints, they are 1,690,087 and 1,133,490 rather than 1,688,273 and 1,132,639. The
  page quotes them to three figures, marks the row derived and shows the arithmetic. The conclusion
  is unmoved: the ratio goes from 1.4906 to 1.4910, both 49%. On the same two pairs, a whole-run
  cumulative average sits 1.5 to 3.9% above the sustained rate rather than the 2 to 4% claimed.
- `docs/benchmarks.md` said the 160-byte control reproduces an earlier run "on all six of its
  methods". `CandidateBenchmark` has seven, and had seven when that sentence was written:
  `admissibilityFilter` arrived eighteen commits before it. It is also the one method that cannot be
  compared to that run, because the earlier benchmark was charging it for an allocation, which the
  last section of the same page already explains. The sentence reads six of seven now, names the
  seventh and links to the explanation, so the link check holds the two halves together. It matters
  because the sentence after it is the one that asks a reader to trust the rest of the page.
- `tools/package-info.java` said every figure is generated from "a sweep checkpoint, or the cipher
  itself". Two of the six are drawn from `docs/benchmarks.json` and one from
  `docs/score-histogram.tsv`. All three are committed, so the claim the sentence exists to make was
  true; the list beside it was not.
- Every number the command line prints is formatted under `Locale.ROOT`. `Durations` and eighteen
  `printf` calls in `BreakCommand` and `DemoCommand` took the machine's own locale, so on a
  comma-decimal one the demo printed `5,9%` and `314.762 keys/sec` and `3,79 h` where this
  documentation prints `5.9%`, `314,762 keys/sec` and `3.79 h`. A reader there could not line their
  own run up against the log on `docs/keyspace-sweep.md`, and `314.762 keys/sec` reads as three
  hundred and fourteen to everybody else. The two committed artifacts were never affected:
  `ScoreHistogram.render` already passed `Locale.ROOT` and `SweepCheckpoint.save` appends through a
  `StringBuilder`. `DurationsTest` covers all five branches of the duration format under a
  comma-decimal locale, and `MainTest` runs `offsets` twice, once under each, and requires the same
  bytes.
- The `mvn verify` line in `README.md` and `CONTRIBUTING.md` gives a count and a range rather than a
  count and a single time. The count had been re-counted to 148 while the 5.9 seconds beside it was
  measured when the suite held 145. Re-measuring turned up the larger problem: four runs of that
  command on the same desktop inside one hour came back at 10.9, 6.7, 5.8 and 4.2 seconds, the last
  two both with everything already compiled, so a single figure there is a fact about what else the
  machine was doing. It reads 153 tests in 4 to 6 seconds, both ends measured, and the count is the
  half of it that reproduces exactly.

## [1.2.0] - 2026-08-17

The benchmarks were run. They had never been run: the suite compiled and sat in the reactor for
months, because the environment the work was done in could not reach Maven Central, while these docs
described what it showed. One of those descriptions was wrong, two were right and had nothing behind
them, and one benchmark was measuring a workload the cipher never runs.

### Added

- `docs/benchmarks.md`, the run and what it means. The key schedule is 71.6% of a ciphertext-only
  candidate at 234 bytes and 97.0% of a crib one, which is the claim the rest of this repository
  rests on and which had never been measured. A candidate splits four ways: the generator at a
  power-of-two bound, 19.7%; the rejection loop on the bounds a shuffle really uses, 32.8%; the rest
  of the shuffling, 19.1%; the message, 28.4%.
- `RandomSourceBenchmark` draws at the bounds Fisher-Yates asks for as well as at 256. It only ever
  drew at 256 before, while these docs cited it for the cost of the loop that bound skips.
- `CandidateBenchmark` takes a `messageSize`, over 80, 160, 234 and 1024. The 160-byte case rebuilds
  the previous message byte for byte and is kept as a control; it reproduces the earlier run inside
  the error bars on every method.
- `SweepBenchmark` drives `SeedSweep` itself rather than the evaluator it calls, which is the only
  thing that can tell the sweep's own overhead apart from the machine underneath it.
- `Messages` holds the plaintext those two share, so they and `docs/keyspace-sweep.md` describe the
  same bytes.

- `tools/`, a fourth module that draws the figures in `docs/` from data the repository already
  commits. The margin figure is parsed out of the sweep checkpoint, and the key-reuse figure is
  produced by running the cipher over two messages and comparing the ciphertexts, so neither carries
  a number anyone typed. `DiagramReproducibilityTest` fails the build if a committed figure stops
  matching what the data produces, which is the arrangement the quadgram table already lives under.
- `DocumentationLinksTest` walks every markdown file in the tree and resolves every local link and
  every heading anchor in it. The commit that added the figures pointed `docs/benchmarks.md` at two
  of them and carried neither, which made a page of broken images and a red build on every clean
  checkout, and nothing in the build noticed until CI did. This notices in the second it takes.
- `TransformBenchmark.withANonceThatChangesNothing`, which runs the nonced overload from the
  starting positions the textbook path uses. The nonce arm has measured about 5% faster than the
  plain one at 65,536 bytes in four runs out of four, and the two differ only in where the rotors
  start and in which overload is being compiled. This holds the first of those fixed. The nonce is
  searched for at setup rather than written down, because a constant in a benchmark is a claim
  nobody checks.
- Two more figures, drawn from `docs/benchmarks.json` rather than typed beside it: where the time in
  one candidate goes, and what a longer message costs each attack. `JmhResults` reads the run's own
  result file, in about two hundred lines and with no new dependency, so a picture cannot drift from
  the table above it. Both generators refuse to draw if the benchmarks they take differences of stop
  nesting, which is the only thing standing between a bar chart and a plausible lie.
- `ScoreHistogram` in the search, so a sweep can count every score into fixed bins rather than only
  ranking the top ten. Workers fill their own copy and the copies merge once they stop, the same way
  the leaderboard works. `SweepBenchmark` gained a parameter that measures what it costs, because
  finding out after an hour of machine time is the wrong order. That parameter does not give the same
  answer twice; the page it feeds says so and shows every run.
- `break --histogram <file>` writes the distribution the sweep saw. It needs `--language`, because
  a crib evaluator rejects almost every key without scoring it, and it refuses to run on a resumed
  checkpoint, because it counts only the keys of the run that is happening. The bin range is a
  guess made up front, since a sweep cannot make two passes over four billion keys, and the file
  reports how many scores fell outside it.
- `docs/score-histogram.tsv` and the figure drawn from it. Counting all 4,294,967,296 scores turned
  up a shape nobody predicted: 4,249,795,476 keys, 98.95% of the space, share one bin 0.1 wide, and
  the twenty-four bins above it are empty. A decryption with no run of four letters is charged the
  floor on every window and lands on the same constant as all the others, and recognising a single
  quadgram is worth about 2.5 log-units, so there is no smaller step available. The noise that is
  left spans 25.00 log-units, which makes the margin 8.3 times the width of the entire distribution
  it stands outside of.
- `docs/using-the-search.md`, which is where the library half of the README went.

### Changed

- The README tells the story before it recites the result. A reader who has never seen a rotor
  machine now gets one explained, then why it can be broken, then what breaking it looked like, with
  a figure at each of those three points. What it is not stays where it was, third, tightened.
- The nine runners-up span 3.04 log-units, not the 3.05 these docs have carried since the sweep. The
  wrong figure came from subtracting two numbers that had already been rounded to two decimals. The
  diagram generator reads the checkpoint at full precision and disagreed on its first run.
- The whole keyspace has been swept twice more, both ways, in one uninterrupted run each on sixteen
  threads an hour apart: 62.2 minutes from the ciphertext alone and 40.8 minutes with an eighteen-byte
  crib. The crib run returned one key and nothing else in 4,294,967,296, which is what the arithmetic
  says it should and what nothing had checked. The ciphertext-only run returned a leaderboard
  identical to the published one to the last decimal, two days and a batch of changes later, which
  makes it a determinism check on everything in this release.
- Every rate in `docs/keyspace-sweep.md` now comes from those two runs. The partial and resumed
  figures they replace were about 1.6 times below what this machine does, and the page says so.
- The crib attack is 49% faster over the whole keyspace, not the 40% two partial runs suggested, and
  not the 36% one candidate costs in JMH. The gap between the last two is `SeedSweep` rather than the
  cipher: `QuadgramSearch` allocates a `Candidate` and touches the leaderboard for every key,
  `CribMatcher` returns `null` and allocates once in four billion.
- A single uninterrupted run's headline rate is still not the rate the machine holds. Both runs
  opened about 1.4 times faster than they settled, so the totals sit 2 to 4% above the sustained
  figures. The page gives both and says to read the tail.
- The two attacks do separate as the message grows, which these docs asserted for months on one
  hardcoded message. Measured over four: the crib route is cheaper by 10.6% at 80 bytes, 22.0% at
  160, 35.5% at 234 and 166% at 1024, and its own cost does not move at all, because it does not read
  the message.
- The rejection loop is not where a sweep spends most of its life, as these docs said. It is 32.8% of
  a candidate: more than anything else, and not most. The speedup from replacing `java.util.Random`
  is 3.45x at the bounds the cipher uses, not the 9.16x measured at a bound of 256 it never draws at.
  The compare-and-set swamps the difference, so `java.util.Random` costs the same either way and the
  loop was invisible underneath it until the atomic went.
- The rates in `docs/keyspace-sweep.md` are slower than the same code runs at today. Two threads
  measured 229,762 keys/sec there and reach 370,287 in JMH; the sixteen-thread segment reached 25.4%
  of what sixteen unloaded threads would give. `SeedSweep`'s own cost is under about 3% of a
  candidate, so almost none of that is software. The page says which side the difference is on now
  instead of leaving it open.
- The sweep was described as having run on two different machines. It ran on one, at two thread
  counts: two for the first 14.84% of the range, sixteen for the rest. Corrected in the README, in
  both docs pages and in the 1.1.0 entry below, which repeated it while correcting something else.
- The nonce at 65,536 bytes is no longer described as impossible. It was compared against
  `allocating`, which allocates 65,536 bytes an operation, while the nonced overload writes into a
  buffer the caller owns, so one of the two was doing work the other was not and the gap was drawn
  across it. Read against `reusingABuffer` instead, the nonce is 6.3% slower at 64 bytes, 2.8%
  slower at 1024 and faster only at 65,536, which is what a cost paid once an operation looks like.
  What is left of the largest cell measures 3.7%, then 2.25%, then 1.5% over three runs, with the
  error bars overlapping every time and the new control landing on both sides of it.
- The histogram question stops where the machine stops. A third suite was run to see whether the
  outlier returns when its two cells are again the last two of a long run, and twenty-eight of its
  fifty-one measurements came out at least 15% slower than the published run, several more than
  twice as slow, with the load arriving and leaving mid-run rather than sitting on it. That run is
  not committed. One suite in three has come out clean here, and the page says so.
- The build instructions say `mvn verify` rather than `mvn test`, which is what CI runs and what
  catches a packaging failure as well as a test one. The figure beside it is that command on one
  desktop, 145 tests in 5.9 seconds, and not the older `mvn test` measurement it replaces.
- `admissibilityFilter` was charging itself for a `String.getBytes` inside the measured method. The
  0.010 us it reported was wrong; it is about 0.0045.
- Every figure on `docs/benchmarks.md` comes from one run of the whole suite, committed as
  `docs/benchmarks.json` and named on the page. The suite was run twice, because the first attempt
  came out with `TransformBenchmark` reading 47% low at 65,536 bytes and error bars up to 52.6%, and
  a diagnostic re-run put it back where it had been. The clean run cost two claims. The 0.138 us of
  `SeedSweep` overhead was the difference between two separately measured benchmarks, and running
  both again turns that difference negative, so only a bound survives. The histogram's cost measured
  between -0.3% and +12.7% across five runs of one benchmark on one machine, so what the docs quote
  for it is the pair of hour-long runs, 2.4%, and the page prints all five microbenchmark figures
  rather than the one it happens to have an artifact for.

## [1.1.0] - 2026-08-13

The sweep of the whole keyspace, and four things that were not true.

### Added

- A sweep of all 4,294,967,296 keys, ciphertext only, with the log and the checkpoint it left behind.
  1.0.0 shipped with 14.84% of the range swept and said so; this is the rest of it. The true key came
  first, by 206.39 log-units, and the nine runners-up span 3.04 log-units of noise below it.
- `.gitattributes`, pinning checked-out line endings to LF. `QuadgramTableReproducibilityTest`
  compares the shipped table to what the corpus regenerates, exactly, so a checkout that converts to
  CRLF fails a test about the corpus for a reason that has nothing to do with the corpus.
  `data/corpus/MANIFEST.md` already claimed this held on every platform; it held for what is
  committed and not for what is checked out.
- `windows-latest` in the CI matrix, on both JDKs. It found that line-ending defect on its first run.

### Fixed

- The CI step that runs the demonstration had failed on every commit since it was moved onto a named
  `exec` execution, because `-am` was added in the same change: it puts the parent POM in the
  reactor, the goal runs against every module there, and the parent has no execution with that id.
  Eleven commits went onto a red `build` workflow, the one 1.0.0 was cut from among them. The release
  workflow does not run that step and was green, so the published artifacts were tested; the
  end-to-end check simply was not running.
- Three `MainTest` assertions passed only where `/dev/null` resolves. The break command reads `--in`
  before it validates the rest of the arguments, so a path that does not resolve fails as unreadable
  input with status 1 rather than as the usage error with status 2 the test is about. They take a
  temporary empty file now.
- Three published figures. The build instructions said 121 tests and the suite has 133. The
  runners-up were described as packed into two log-units and they span 3.04. And 1.48 hours was
  presented as measured, in the README, in `why-the-cipher-falls.md` and in `DemoCommand`'s javadoc;
  it is arithmetic from the sixteen-thread rate over 85.16% of the range. The measured wall clock for
  the whole keyspace is 2.03 hours, split across two thread counts, and belongs to neither on its
  own. Both now say which they are.
- The suite time in the build instructions is a measurement, 4.9 s on one desktop, rather than "about
  ten seconds".

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

- `SeedSweep` reported the width of the range as keys tried, whatever had actually happened. A worker
  whose evaluator threw died silently and the sweep returned normally claiming full coverage; in the
  worst case every worker died on its first key and the result read 4,294,967,296 keys tried at 190
  billion keys/sec. It counts finished work now, stops the other workers when one dies, and rethrows.
  Leaderboard ties broke on thread arrival order, so a short crib over a wide range returned whichever
  wrong key a worker reached first and exited zero; ties break towards the lower key now, and `break`
  warns up front when a crib is short enough that chance alone will produce hits.
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

[1.2.0]: https://github.com/qnicondavid/byte-enigma/releases/tag/v1.2.0
[1.1.0]: https://github.com/qnicondavid/byte-enigma/releases/tag/v1.1.0
[1.0.0]: https://github.com/qnicondavid/byte-enigma/releases/tag/v1.0.0
