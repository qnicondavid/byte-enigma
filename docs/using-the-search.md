# Using the search

The half of this repository that has nothing to do with rotors.

`io.github.qnicondavid.byteenigma.search` brute-forces a 32-bit keyspace across threads. It owns the
range, the workers, the leaderboard, the checkpointing and the clock. It has never heard of a cipher.
You hand it a factory for whatever gets rekeyed and an evaluator that scores one candidate, and it
walks four billion of them.

## Coordinates

Available through [JitPack](https://jitpack.io/#qnicondavid/byte-enigma). No transitive dependencies:
the library has none.

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.github.qnicondavid.byte-enigma</groupId>
  <artifactId>byte-enigma</artifactId>
  <version>v1.1.0</version>
</dependency>
```

## Sweeping something

```java
SeedSweep<MyThing> sweep = new SeedSweep<>(MyThing::new, 10);

SweepResult result = sweep.sweepParallel(
        SeedSweep.KEYSPACE_START, SeedSweep.KEYSPACE_END, ciphertext,
        (key, thing, ciphertext, scratch) -> {
            thing.rekey(key);
            int length = thing.apply(ciphertext, scratch);
            return Candidate.of(key, score(scratch, length), scratch, length);
        });

System.out.println(result.top().key() + " at " + result.keysPerSecond() + " keys/sec");
```

The constructor takes a factory rather than an instance, and that is deliberate. The sweep needs one
subject per thread, and taking a factory makes it impossible to hand it one mutable object for four
threads to share. Returning `null` from the evaluator means the candidate is rejected outright and
never reaches the leaderboard, which is what makes the known-plaintext route cheap.

`SweepResult.keysTried()` is the number of keys workers actually finished, not the width of the range
you asked for. Those two used to be the same number whatever happened, including when every worker
died on its first key.

## What it costs

The loop, the counters and the leaderboard comparison cost under about 3% of a candidate. That is a
bound rather than a figure: the benchmark that would give a figure subtracts two other benchmarks,
and their difference is smaller than the amount either of them moves between runs. Two threads scale
at 93.5% of one. [benchmarks.md](benchmarks.md) has the measurements and the caveats.

## The cipher, if you want it anyway

```java
ByteEnigma machine = ByteEnigma.fromPassword("hunter2", 3);

byte[] sealed = Envelope.seal(machine, plaintext);   // fresh nonce, prepended in the clear
byte[] opened = Envelope.open(machine, sealed);

byte[] raw = machine.transform(plaintext);           // textbook mode, self-inverse, leaks on reuse
```

`ByteEnigma` is not thread-safe. Rotor offsets are mutable state and a transform walks them, so use
one instance per thread. That is the same reason `SeedSweep` takes a factory.

Read [why-the-cipher-falls.md](why-the-cipher-falls.md) before putting anything you care about
through the last line.
