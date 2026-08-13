# What a candidate costs

[keyspace-sweep.md](keyspace-sweep.md) says how long four billion keys take. This page says where
that time goes inside one of them, and it is where the cost claims made elsewhere in these docs are
either backed by a number or withdrawn.

Everything here came out of one run of `benchmarks/target/benchmarks.jar`. That run was the first
this project ever did; the suite compiled and sat in the reactor for months because the environment
it was written in could not reach Maven Central. Two claims did not survive it.

## The run

| | |
|---|---|
| CPU | Intel Core Ultra 9 285H |
| OS | Windows |
| JVM | OpenJDK 21.0.8, Temurin 21.0.8+9-LTS, no VM options |
| JMH | 1.37, blackhole mode `compiler`, auto-detected |
| Threads | 1 |
| Forks | 2, five measured iterations each, so every count below is 10 |
| Wall clock | 13 min 14 s |

One run, one machine, one JVM. JMH ends every run by asking you not to assume the numbers say what
you want them to say. The two withdrawals below are what that looks like when it is taken seriously.

```
mvn -B -ntp -pl benchmarks -am package
java -jar benchmarks/target/benchmarks.jar
```

## One candidate

`CandidateBenchmark`, three rotors, one message of 160 bytes, a 16-byte crib at offset 49. Average
time per operation.

| | us/op | |
|---|---|---|
| `admissibilityFilter` | 0.010 | ±0.001 |
| `rekeyOnly` | 3.575 | ±0.040 |
| `cribEvaluator` | 3.732 | ±0.082 |
| `rekeyAndCribWindow` | 3.800 | ±0.097 |
| `rekeyAndFullTransform` | 4.374 | ±0.077 |
| `rekeyTransformAndScore` | 4.577 | ±0.136 |
| `languageEvaluator` | 4.622 | ±0.122 |

**The key schedule is 78.1% of a ciphertext-only candidate and 95.8% of a crib candidate.** That is
the claim the rest of this repository rests on, and until this run it was a reading of the code
rather than a measurement of it.

What the message costs on top of rekeying: 0.799 us to transform all 160 bytes, 0.203 us to score
them, 0.225 us for the 16-byte crib window. The window is not free, because `transformWindow` still
steps the rotors through the 49 bytes ahead of the crib. It just does not substitute them.

Ruling a crib position out costs 0.010 us, between 373 and 458 times less than trying one key at
that position. The no-fixed-point discount really is as free as
[why-the-cipher-falls.md](why-the-cipher-falls.md#2-no-byte-ever-encrypts-to-itself) says. It is
still worth only about 6% of positions over 256 symbols, which is the other half of that story.

## The key schedule

`KeyScheduleBenchmark`, one candidate's worth of setup, us/op.

| rotors | `construct` | `rekeyInPlace` |
|---|---|---|
| 1 | 2.262 ±0.130 | 2.061 ±0.058 |
| 3 | 3.834 ±0.106 | 3.608 ±0.097 |
| 8 | 7.532 ±0.208 | 6.944 ±0.152 |

A line through the three `rekeyInPlace` points has a slope of 0.698 us per rotor and an intercept of
1.363 us for the two involutions, which is 0.682 us each. Five Fisher-Yates shuffles of 256 elements
at about 0.69 us apiece is exactly the cost model these docs describe, arrived at from the other end.

Rekeying in place beats constructing by 6.3% at three rotors, 9.8% at one and 8.5% at eight. That
change was made to cut allocation rather than time; the time it buys is real and small.

`rekeyOnly` in `CandidateBenchmark` and `rekeyInPlace` at three rotors here measure the same work in
different harnesses and land 0.9% apart, 3.575 against 3.608.

## The generator

`RandomSourceBenchmark`, 1,275 draws, which is one machine's worth.

| | us/op | |
|---|---|---|
| `jdkRandom` | 9.140 | ±0.056 |
| `plainFieldLcg` | 0.997 | ±0.008 |

**9.17x on the generator in isolation.** That is the compare-and-set on `java.util.Random`'s
`AtomicLong`, plus its synchronised `setSeed`, paid 1,275 times per candidate for a generator that is
never shared between threads. `Lcg48EquivalenceTest` pins that the two produce identical sequences,
so this difference is the whole of the trade.

**What this benchmark does not measure.** It draws `nextInt(256)`. 256 is a power of two, so the call
takes the branch that multiplies and shifts and never enters the rejection loop. The key schedule
draws `nextInt(i + 1)` for `i` from 255 down to 1, where almost every bound is not a power of two and
the loop, with its integer division, does run. These docs used to say that division is where a sweep
spends most of its time. Nothing here measures it. The generator at the cheap bound is 0.997 us of a
3.608 us key schedule, 28% of it; the other 2.6 us are the shuffling and whatever the harder bounds
cost, in proportions nobody has separated.

## The transform

`TransformBenchmark`, throughput in operations per millisecond, so higher is faster.

| bytes | `allocating` | `reusingABuffer` | `withANonce` |
|---|---|---|---|
| 64 | 3092.089 ±76.916 | 3105.564 ±85.376 | 2985.353 ±109.812 |
| 1024 | 192.678 ±3.481 | 197.302 ±5.289 | 193.289 ±5.339 |
| 65536 | 2.840 ±0.083 | 2.912 ±0.098 | 3.039 ±0.102 |

The zero-allocation overload is nominally 2.4% and 2.5% ahead at the two larger sizes and 0.4% ahead
at the smallest, and at every size the error bars overlap. It is not distinguishable from the
allocating one here. That is a stronger statement than these docs already make about optimising the
transform: against a key schedule that is 78% of the bill, the transform is not where a sweep is won.

The nonce costs nothing measurable, and one cell is a useful warning about how far to trust any of
this. At 65,536 bytes `withANonce` scores 3.039 against 2.840 and the intervals do not quite overlap,
so on paper the nonce made the transform faster. It cannot have. It moves the starting offsets and
adds work. The run-to-run spread is wider than the within-run error JMH prints, which is ordinary and
worth remembering before reading any 2% difference on this page as a result.

## What this run did not measure

- What the rejection loop costs on the bounds the shuffle actually uses, per the withdrawal above.
- How the two attacks separate as the message grows. `CandidateBenchmark` has one message and no
  parameter for its size, so the 23.8% gap here and the 40% the sweep measured on 234 bytes are two
  points from two harnesses rather than a trend. `TransformBenchmark` already parameterises message
  size; this one should.
- `SeedSweep` itself, and anything on more than one thread. Every figure here is the evaluator on one
  unloaded thread, and they do not reconcile with the keys/sec the sweep reports: a thread inside the
  sweep does between 1.7 and 3.9 times less work than a thread inside JMH, on this machine, on the
  same message. [keyspace-sweep.md](keyspace-sweep.md) has that arithmetic and the candidates for why.

Every gap above now has a benchmark pointed at it. `RandomSourceBenchmark` has a second pair that
draws on the shuffle's own bounds, `CandidateBenchmark` has a `messageSize` parameter with 160 kept
as a control because it reproduces the message above byte for byte, and `SweepBenchmark` drives the
real sweep. None of them existed when the table on this page was produced, so none of them is in it.
