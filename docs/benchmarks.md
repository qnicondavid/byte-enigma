# What a candidate costs

[keyspace-sweep.md](keyspace-sweep.md) says how long four billion keys take. This page says where
that time goes inside one of them.

It exists because the suite it reports had never been run. It compiled and sat in the reactor for
months, because the environment the work was done in could not reach Maven Central, and meanwhile
these docs described what it showed. Three of those descriptions turned out to be claims about
nothing. Two of them were right anyway and now have numbers. One was wrong.

## The run

| | |
|---|---|
| CPU | Intel Core Ultra 9 285H |
| OS | Windows |
| JVM | OpenJDK 21.0.8, Temurin 21.0.8+9-LTS, no VM options |
| JMH | 1.37, blackhole mode `compiler`, auto-detected |
| Threads | 1, except inside `SweepBenchmark`, which makes its own |
| Forks | 2, five measured iterations each, so every count is 10 |
| Date | 13 August 2026 |

```
mvn -B -ntp -pl benchmarks -am package
java -jar benchmarks/target/benchmarks.jar -rf json -rff docs/benchmarks.json
```

JMH ends that file with the platform's line separator, so on Windows the last line of it arrives as
CRLF and `git add` refuses the file. Convert it to LF before committing. Every other generated file
here is written with `\n` in the generator and does not have the problem.

Every figure here comes from one run of the whole suite, committed as `docs/benchmarks.json`. Nothing
is spliced in from another run. That matters more than it sounds: the same benchmark moves a few
percent between runs on this machine, and two of the things this page used to claim were smaller than
that.

`CandidateBenchmark` at 160 bytes is a control: it rebuilds the message an earlier run used, byte for
byte, and has to return the same numbers. It does, inside the error bars, on all six of its methods.
Take the rest of the page as far as that makes you willing to.

## One candidate

`CandidateBenchmark`, three rotors, crib of 16 bytes at offset 49, average us/op.

| | 80 B | 160 B | 234 B | 1024 B |
|---|---|---|---|---|
| `admissibilityFilter` | 0.0044 | 0.0044 | 0.0045 | 0.0044 |
| `rekeyOnly` | 3.648 | 3.625 | 3.632 | 3.613 |
| `cribEvaluator` | 3.799 | 3.830 | 3.743 | 3.825 |
| `rekeyAndCribWindow` | 3.833 | 3.872 | 3.875 | 3.867 |
| `rekeyAndFullTransform` | 4.139 | 4.449 | 4.930 | 8.865 |
| `rekeyTransformAndScore` | 4.094 | 4.543 | 4.858 | 9.466 |
| `languageEvaluator` | 4.200 | 4.671 | 5.073 | 10.169 |

`rekeyOnly` and `cribEvaluator` are flat across a thirteenfold change in message length, which is the
whole point: the crib attack does not read the message. `languageEvaluator` more than doubles.

Two cells in that table are impossible. `rekeyTransformAndScore` does everything
`rekeyAndFullTransform` does and then scores the result, so it cannot be cheaper, and at 80 and 234
bytes it comes out 1.1% and 1.5% below. The error bars cover it, which is the point: differences of
that size on this page are not differences. There is a second one further down, at 65,536 bytes in
the transform, and that one the error bars do not cover.

## Where the time in a candidate goes

Taking the 234-byte column, which is the message the full keyspace sweep was run against, and
splitting it with `RandomSourceBenchmark` and `KeyScheduleBenchmark`:

| | us | share |
|---|---|---|
| generator, what 1,275 draws cost at a power-of-two bound | 1.000 | 19.7% |
| generator, the rejection loop on the bounds a shuffle really uses | 1.665 | **32.8%** |
| the rest of the shuffling | 0.967 | 19.1% |
| transform and score | 1.441 | 28.4% |
| | **5.073** | 100% |

![Two bars on one scale for a 234-byte message. The ciphertext-only candidate and the crib candidate begin with the same key schedule, split into the generator, its rejection loop and the rest of the shuffling. Only the short end differs, and on the crib bar it is a sliver](candidate-split.svg)

The key schedule is 71.6% of that candidate, and 97.0% of a crib one. The single largest item in it
is the rejection loop, at about a third of everything.

## The two attacks separate as the message grows

This was asserted here for months, withdrawn when the benchmark turned out to have one hardcoded
message, and is now measured over four:

| message | crib | ciphertext-only | crib is cheaper by |
|---|---|---|---|
| 80 B | 3.799 | 4.200 | 10.6% |
| 160 B | 3.830 | 4.671 | 22.0% |
| 234 B | 3.743 | 5.073 | 35.5% |
| 1024 B | 3.825 | 10.169 | 166% |

![Three lines against message length. The key schedule is flat at about 3.6 microseconds and the crib attack is flat just above it, because it decrypts a sixteen-byte window whatever the message length is. The ciphertext-only attack climbs from 4.2 to 10.2, and the shaded gap between the two widens from 10.6 percent to 166 percent](attack-scaling.svg)

So it was true. On a short message the two attacks really are close to each other, and the gap grows
with the message and nothing else, because the message length is the only work a crib skips. What was
missing was any reason to believe it.

Two complete sweeps of the whole keyspace put the crib route 49% ahead, where this puts it 36%. The
difference is the sweep rather than the evaluator: `QuadgramSearch` returns a `Candidate` for every
key and `CribMatcher` returns `null`, so the ciphertext-only route also pays an allocation and a
leaderboard comparison four billion times. [keyspace-sweep.md](keyspace-sweep.md) has that
arithmetic.

## The generator, and the bound that was never tested

`RandomSourceBenchmark`, 1,275 draws, which is one machine's worth.

| | at bound 256 | at the shuffle's own bounds |
|---|---|---|
| `java.util.Random` | 9.161 ±0.057 | 9.201 ±0.067 |
| `Lcg48`, over a plain field | 1.000 ±0.004 | 2.665 ±0.023 |

Read the rows and then the columns, because they say different things.

Along the top row, the rejection loop is invisible. `java.util.Random` costs the same whether the
bounds are powers of two or not: the compare-and-set on its `AtomicLong` swamps everything else, and
that is why nobody noticed what the bounds cost until the atomic was gone.

Along the bottom row it is 2.67 times the work. Fisher-Yates asks for `nextInt(i + 1)` with `i` from
255 down to 1, almost never a power of two, so `nextInt` goes through the rejection loop and its
integer division 1,275 times per candidate. That is the 1.665 us in the table above, and it is the
biggest single line in it.

**The speedup from replacing the generator is 3.45x, not 9.16x.** The larger number is the ratio at a
bound of 256, which is a workload the key schedule never runs, and it was the only ratio this project
had measured. Both columns are here so the difference stays visible.

## The key schedule

`KeyScheduleBenchmark`, us/op.

| rotors | `construct` | `rekeyInPlace` |
|---|---|---|
| 1 | 2.320 ±0.022 | 2.058 ±0.030 |
| 3 | 3.825 ±0.077 | 3.610 ±0.069 |
| 8 | 7.503 ±0.325 | 6.872 ±0.172 |

A line through `rekeyInPlace` has a slope of 0.688 us per rotor and an intercept of 1.370 us for the
two involutions, 0.685 us each. Five Fisher-Yates shuffles of 256 elements at about 0.69 us apiece is
the cost model these docs describe, recovered from the other end.

Rekeying in place beats constructing by 5.6% at three rotors, 11.3% at one and 8.4% at eight. That
change was made to cut allocation rather than time; the time it buys is real and small.

## The transform

`TransformBenchmark`, throughput in ops/ms, so higher is faster.

| bytes | `allocating` | `reusingABuffer` | `withANonce` |
|---|---|---|---|
| 64 | 3133.861 ±105.113 | 3186.175 ±46.640 | 2937.082 ±113.598 |
| 1024 | 199.081 ±5.411 | 199.577 ±4.987 | 193.512 ±4.144 |
| 65536 | 2.897 ±0.083 | 2.928 ±0.064 | 3.036 ±0.086 |

The zero-allocation overload is ahead everywhere and by less than its error bar everywhere. It is not
distinguishable from the allocating one, which is a stronger version of what these docs say about
optimising the transform: against a key schedule that is 72% of the bill, this is not where
a sweep is won.

The transform costs about 5 ns a byte, and two harnesses agree on it: 1024 bytes take 5.02 us here
and the same 1024 bytes take 5.25 us as the gap between `rekeyAndFullTransform` and `rekeyOnly`.

One cell was called impossible on this page for four runs, and it is not. At 65,536 bytes the nonce
scores higher than `allocating`. `allocating` allocates 65,536 bytes per operation and hands them
back; the nonce overload writes into a buffer the caller already owns. One of them does work the
other does not, so the column to read the nonce against is `reusingABuffer`, which does not allocate
either. The comparison ran across an allocation and the conclusion was drawn from it anyway.

The three message sizes finish the argument. Against `allocating` the nonce is 6.3% slower at 64
bytes, 2.8% slower at 1024, and 4.8% faster at 65,536. That is the shape of a cost paid once per
operation: it dominates 64 bytes and disappears into 65,536. The cost is `seedPositions`, one reseed
and three draws, where `transform(input, output)` calls `resetPositions` instead, and both overloads
then hand the same loop the same bytes.

What is left is the largest cell against `reusingABuffer`: 3.7% in this run, then 2.25% and 1.5% in
two later ones, with the two error bars overlapping every time.
`TransformBenchmark.withANonceThatChangesNothing` exists to close that. It runs the nonced overload
with a nonce that leaves the rotors where the textbook path leaves them, which removes the only
difference between the two that is not a constant, and it came out 0.65% below the nonce arm in one
of those runs and 2.4% above it in the other. A shrinking difference, intervals that never separate,
and a control that points both ways. Do not read any 2% difference on this page as a result, which is
what four runs of describing this one should already have taught.

## The sweep itself

`SweepBenchmark`, a real ciphertext-only `SeedSweep` over 65,536 keys of the same 234-byte message.

| threads | ms/op | keys/sec |
|---|---|---|
| 1 | 330.873 ±9.299 | 198,070 |
| 2 | 176.987 ±3.764 | 370,287 |

Two threads scale at 93.5% of one.

One thread through the sweep costs 5.049 us a key, and the evaluator alone costs 5.073 in
`CandidateBenchmark`. The subtraction says the sweep is free, which it is not: it runs a loop, keeps
counters and compares every candidate against a leaderboard. An earlier run made the same subtraction
and got 0.138 us, 2.8%, and this page carried that figure in bold. Neither number is the overhead.
Both are smaller than the amount `languageEvaluator` moves between runs, which was 2.9% across those
two. What the suite supports is a bound: the sweep's own cost is under about 3% of a candidate. Going
below that needs a benchmark that measures the difference directly rather than subtracting two
numbers whose gap is inside their own drift.

That settles the question this benchmark was written for. The rates in
[keyspace-sweep.md](keyspace-sweep.md) are far below what the same code does here. Its two-thread
segment measured 229,762 keys/sec where two threads now do 370,287, 1.61 times more, and its
sixteen-thread segment reached 25.4% of what sixteen unloaded single threads would give. Almost none
of that is the sweep's software. What is left is the machine: six performance cores and ten
efficiency-class ones, so a sixteen-thread average is a rate no single thread runs at, clocks that
fall under sustained load, and whatever else the laptop was doing across the two hours. The published
figures are what that run did. They are not what this code can do.

## What the histogram costs

`--histogram` counts every score into a fixed bin. That is one array increment per candidate, against
a candidate that costs microseconds, so it should be invisible. Five runs of `SweepBenchmark`
disagree about whether it is:

| run | 1 thread | 2 threads |
|---|---|---|
| on its own | +1.91% | -0.27% |
| in a full suite | +3.28% | +0.59% |
| in the full suite this page reports | +6.96% | **+12.69%** |
| on its own | +2.72% | +1.17% |
| on its own | +3.64% | +0.94% |

The third run is not noisy inside itself. Its ten histogram-on iterations at two threads sit between
192.41 and 204.77 ms while its ten histogram-off iterations sit between 174.15 and 182.08, and that
off arm agrees with every other run to within 4%. Whatever happened held for a whole JVM and then
stopped. One fact that may or may not be the cause: those two cells are the last two measurements of
a 33-minute suite, so they are the hottest the machine gets, and the four runs that disagree with
them were all taken in the first minutes of a run.

The other four put the cost near a percent at two threads and near three at one, and the measurement
that is not a microbenchmark agrees with them: the whole keyspace with the histogram took 63.7
minutes against 62.2 without, 2.4%, on sixteen threads. Use that. The outlier stays in the table
because dropping it would make the benchmark look steadier than it is.

A third suite was run to find out whether those two cells go strange again when they are again the
last two of a long run. It cannot answer. Twenty-eight of its fifty-one measurements came out at
least 15% slower than the run this page reports, several more than twice as slow, with error bars up
to 45.7%, and the load arrived and left during the run rather than sitting on it: `cribEvaluator` was
untouched in the same suite that doubled `rekeyOnly`, which is impossible as work and ordinary as
scheduling. That run is not in this repository. One suite in three has come out clean on this
machine, and that, rather than anything in the code, is where the histogram question stops.

## What this still does not measure

- Why the published sweep ran 1.61 times slower on two threads than two threads do now. The software
  is accounted for; the conditions of that run are not, and nobody wrote them down at the time.
- Anything above two threads. `SweepBenchmark` stops there deliberately, because a thread count past
  that is a fact about one laptop rather than about this code.
- `admissibilityFilter` reads about 0.0045 us here against 0.010 in an earlier run. That is not the
  filter getting faster. The earlier benchmark called `String.getBytes` inside the measured method
  and was charging the filter for an allocation; it is hoisted into a field now. 0.010 was wrong.
