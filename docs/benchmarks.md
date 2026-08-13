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

```
mvn -B -ntp -pl benchmarks -am package
java -jar benchmarks/target/benchmarks.jar
```

`CandidateBenchmark` at 160 bytes is a control: it rebuilds the message an earlier run used, byte for
byte, and has to return the same numbers. It does, inside the error bars, on all six of its methods.
Take the rest of the page as far as that makes you willing to.

## One candidate

`CandidateBenchmark`, three rotors, crib of 16 bytes at offset 49, average us/op.

| | 80 B | 160 B | 234 B | 1024 B |
|---|---|---|---|---|
| `admissibilityFilter` | 0.004 | 0.004 | 0.004 | 0.004 |
| `rekeyOnly` | 3.606 | 3.584 | 3.629 | 3.649 |
| `cribEvaluator` | 3.721 | 3.724 | 3.760 | 3.730 |
| `rekeyAndCribWindow` | 3.749 | 3.803 | 3.779 | 3.716 |
| `rekeyAndFullTransform` | 4.014 | 4.404 | 4.743 | 8.824 |
| `rekeyTransformAndScore` | 4.107 | 4.589 | 5.039 | 9.522 |
| `languageEvaluator` | 4.025 | 4.526 | 4.929 | 9.814 |

`rekeyOnly` and `cribEvaluator` are flat across a thirteenfold change in message length, which is the
whole point: the crib attack does not read the message. `languageEvaluator` more than doubles.

## Where the time in a candidate goes

Taking the 234-byte column, which is the message the full keyspace sweep was run against, and
splitting it with `RandomSourceBenchmark` and `KeyScheduleBenchmark`:

| | us | share |
|---|---|---|
| generator, what 1,275 draws cost at a power-of-two bound | 1.001 | 20.3% |
| generator, the rejection loop on the bounds a shuffle really uses | 1.684 | **34.2%** |
| the rest of the shuffling | 0.944 | 19.2% |
| transform and score | 1.300 | 26.4% |
| | **4.929** | 100% |

The key schedule is 73.6% of that candidate, and 96.5% of a crib one. The single largest item in it
is the rejection loop, at about a third of everything.

## The two attacks separate as the message grows

This was asserted here for months, withdrawn when the benchmark turned out to have one hardcoded
message, and is now measured over four:

| message | crib | ciphertext-only | crib is cheaper by |
|---|---|---|---|
| 80 B | 3.721 | 4.025 | 8.2% |
| 160 B | 3.724 | 4.526 | 21.5% |
| 234 B | 3.760 | 4.929 | 31.1% |
| 1024 B | 3.730 | 9.814 | 163% |

So it was true. On a short message the two attacks really are close to each other, and the gap grows
with the message and nothing else, because the message length is the only work a crib skips. What was
missing was any reason to believe it.

Two complete sweeps of the whole keyspace put the crib route 49% ahead, where this puts it 31%. The
difference is the sweep rather than the evaluator: `QuadgramSearch` returns a `Candidate` for every
key and `CribMatcher` returns `null`, so the ciphertext-only route also pays an allocation and a
leaderboard comparison four billion times. [keyspace-sweep.md](keyspace-sweep.md) has that
arithmetic.

## The generator, and the bound that was never tested

`RandomSourceBenchmark`, 1,275 draws, which is one machine's worth.

| | at bound 256 | at the shuffle's own bounds |
|---|---|---|
| `java.util.Random` | 9.152 ±0.050 | 9.182 ±0.082 |
| `Lcg48`, over a plain field | 1.001 ±0.006 | 2.685 ±0.035 |

Read the rows and then the columns, because they say different things.

Along the top row, the rejection loop is invisible. `java.util.Random` costs the same whether the
bounds are powers of two or not: the compare-and-set on its `AtomicLong` swamps everything else, and
that is why nobody noticed what the bounds cost until the atomic was gone.

Along the bottom row it is 2.68 times the work. Fisher-Yates asks for `nextInt(i + 1)` with `i` from
255 down to 1, almost never a power of two, so `nextInt` goes through the rejection loop and its
integer division 1,275 times per candidate. That is the 1.684 us in the table above, and it is the
biggest single line in it.

**The speedup from replacing the generator is 3.42x, not 9.14x.** The larger number is the ratio at a
bound of 256, which is a workload the key schedule never runs, and it was the only ratio this project
had measured. Both columns are here so the difference stays visible.

## The key schedule

`KeyScheduleBenchmark`, us/op.

| rotors | `construct` | `rekeyInPlace` |
|---|---|---|
| 1 | 2.260 ±0.104 | 2.067 ±0.031 |
| 3 | 3.830 ±0.097 | 3.642 ±0.095 |
| 8 | 7.444 ±0.165 | 6.847 ±0.190 |

A line through `rekeyInPlace` has a slope of 0.683 us per rotor and an intercept of 1.384 us for the
two involutions, 0.692 us each. Five Fisher-Yates shuffles of 256 elements at about 0.69 us apiece is
the cost model these docs describe, recovered from the other end.

Rekeying in place beats constructing by 5.2% at three rotors, 9.3% at one and 8.7% at eight. That
change was made to cut allocation rather than time; the time it buys is real and small.

## The transform

`TransformBenchmark`, throughput in ops/ms, so higher is faster.

| bytes | `allocating` | `reusingABuffer` | `withANonce` |
|---|---|---|---|
| 64 | 3136.298 ±235.185 | 3190.820 ±41.802 | 3038.862 ±62.763 |
| 1024 | 200.867 ±4.137 | 201.879 ±3.870 | 192.707 ±3.392 |
| 65536 | 2.925 ±0.090 | 2.978 ±0.044 | 3.110 ±0.034 |

The zero-allocation overload is ahead everywhere and by less than its error bar everywhere. It is not
distinguishable from the allocating one, which is a stronger version of what these docs say about
optimising the transform: against a key schedule that is three quarters of the bill, this is not where
a sweep is won.

The transform costs about 5 ns a byte, and two harnesses agree on it: 1024 bytes take 4.98 us here
and the same 1024 bytes take 5.18 us as the gap between `rekeyAndFullTransform` and `rekeyOnly`.

One cell is a standing warning. At 65,536 bytes the nonce scores *higher* than the plain transform,
which is impossible, since it moves the starting offsets and adds work, and it did so in the previous run
too, so it is not a one-off. Something about that particular measurement is wrong in a way the error
bars do not show. Do not read any 2% difference on this page as a result.

## The sweep itself

`SweepBenchmark`, a real ciphertext-only `SeedSweep` over 65,536 keys of the same 234-byte message.

| threads | ms/op | keys/sec |
|---|---|---|
| 1 | 332.099 ±3.498 | 197,338 |
| 2 | 177.920 ±3.589 | 368,345 |

One thread through the sweep costs 5.067 us a key against 4.929 for the evaluator alone. **`SeedSweep`
adds 0.138 us per candidate, 2.8%**, and that covers the loop, the counters and the leaderboard.
Two threads scale at 93% of one.

That settles the question this benchmark was written for. The rates in
[keyspace-sweep.md](keyspace-sweep.md) are far below what the same code does here. Its two-thread
segment measured 229,762 keys/sec where two threads now do 368,345, 1.60 times more, and its
sixteen-thread segment reached 25.5% of what sixteen unloaded single threads would give. Almost none
of that is the sweep's software. What is left is the machine: six performance cores and ten
efficiency-class ones, so a sixteen-thread average is a rate no single thread runs at, clocks that
fall under sustained load, and whatever else the laptop was doing across the two hours. The published
figures are what that run did. They are not what this code can do.

## What this still does not measure

- Why the published sweep ran 1.60 times slower on two threads than two threads do now. The software
  is accounted for; the conditions of that run are not, and nobody wrote them down at the time.
- Anything above two threads. `SweepBenchmark` stops there deliberately, because a thread count past
  that is a fact about one laptop rather than about this code.
- `admissibilityFilter` reads 0.004 us here against 0.010 in the previous run. That is not the filter
  getting faster. The earlier benchmark called `String.getBytes` inside the measured method and was
  charging the filter for an allocation; it is hoisted into a field now. 0.010 was wrong.
