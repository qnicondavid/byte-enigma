# Sweeping the keyspace

Every keys/sec figure this project publishes is measured on work that actually ran. This page is
where those measurements come from, including the part that is not finished, because a page that
only reported the finished part would be doing the thing this repository exists to argue against.

## The setup

| | |
|---|---|
| Ciphertext | 234 bytes of English, enciphered in textbook mode with 3 rotors |
| Key | 2083951437 |
| Full range | `[-2147483648, 2147483648)`, all 4,294,967,296 keys |
| Machine | 2 cores, OpenJDK 21, an unremarkable cloud container |

The key sits about 97% of the way up from the bottom of the range on purpose. The sweep starts at
`Integer.MIN_VALUE` and counts up, so a key placed near the top is only reached by a run that really
did go most of the way.

The ciphertext, Base64, SHA-256 `35de936d3adfe362561c02f3bb95e75b`:

```
h0K7ne80iRrdqDBrrfJntmcbDyT2JahUyRzRLq/wJLsyq7jRaF0sPoal87B+NDkTulzn3bae+UAsmWr+1sYeGa4IY+SP
9Ab364XqZWIuidfuzuzR3Kz1R/EzN5dGcLsACH1XTD6sfG968KgXYKvEXnNsLlHTk4CVo+aAEn078TAiuO7AZtLKUhA/
7479zl9KmeCmDpVV8bDSBIwEptp88DPWHcVgiZ0CqQEIUQQl8sfQThGHl/frEr+ZEo2F6d5sfXGBJZ5vdr1gx9vhoQGO
vxdco5h5HYwL5pmgUbzNRL4l1XNc4IfZajuS
```

## What has been run

**A contiguous 637,534,208 keys, 14.84% of the range, from `Integer.MIN_VALUE` upwards.**
Ciphertext only, no crib. 46.2 minutes of wall clock on two cores, in seven runs, each stopped on a
time budget and continued from the checkpoint the last one wrote.

The true key is not inside that range, and that is what makes the number worth having: those 637
million keys are 637 million known-wrong answers, and the question worth asking of a language scorer
is how close the best wrong answer gets.

| | key | score |
|---|---|---|
| True key | 2083951437 | **-1620.38** |
| Best of 637,534,208 wrong keys | -1684917101 | -1828.24 |

**A margin of 207.86 log-units, over six hundred million attempts.** The runners-up sit within four
log-units of each other (-1828.24, -1830.70, -1831.48, -1831.71, -1831.88), which is what the top of
a noise distribution looks like when nothing in it is English. The true key is not at the top of that
distribution. It is not in it.

The wrong candidates show what the scorer is rewarding. Key -2023463748 produced the fragment
`THAtHEy` and key -2135655653 produced `ISO`. Neither is English, but four-letter windows like `THAT`
and `THEY` are common enough that two or three accidental hits lift a candidate a little above pure
noise. That is the whole of the signal the attack runs on, and six hundred million samples say the
best accident is worth about 200 log-units less than the real thing.

Six hundred million is not four billion, and the tail of a distribution is where surprises live. What
this run establishes is the shape and the scale of the gap, not that no key anywhere in the remaining
85% closes it.

## What has not been run

The remaining 85.16%. On this hardware the full range projects to **5.19 hours** at the measured rate
of 229,762 keys/sec, and the environment these measurements were taken in cannot hold a process
running for that long.

So the honest statement is: the search recovers the key from a bounded range every time it is asked
to, the rate is measured rather than modelled, the separation between the key and six hundred million
wrong answers is large and stable, and nobody has yet watched it walk the entire space in one piece.

That last clause is the one this page exists to keep honest. If you run it to the end, the log format
below is what to send.

## Reproducing, and finishing it

Save the Base64 above to `message.b64`.

```
# ciphertext only, no crib, scoring English quadgrams
byte-enigma break --language --in message.b64 --top 10 --checkpoint sweep.state

# known-plaintext crib
byte-enigma break --crib "THIRTY TWO BIT KEY" --at 36 --in message.b64 --top 10 --checkpoint sweep.state
```

Both default to the full keyspace. Neither is told where in the range to look.

`--checkpoint` is what makes this practical. The sweep walks the range in segments and writes where
it got to after each one, so you can stop it whenever you like and run the same command again to
carry on. Add `--for 3600` to time-box a run: it stops after roughly an hour, checkpoints, and exits
3 to say there is range left.

```
$ byte-enigma break --language --in message.b64 --top 10 --checkpoint sweep.state --for 3600
ciphertext: 234 bytes, sha-256 35de936d3adfe362
mode:       ciphertext-only quadgram search
range:      [-2147483648, 2147483648)  4294967296 keys
threads:    2
checkpoint: sweep.state  (resuming at -1593835520)
resumed:    553,648,128 keys already done, 12.9% of the range
budget:     60.0 min, then stop and checkpoint

   13.28%      570,425,344 keys   222,401 keys/sec  this run 76.9 s  total 42.8 min
    ...
```

The checkpoint carries a SHA-256 of the ciphertext, the mode and the range, and refuses to resume
unless all three match, so a resume cannot quietly continue against a different message.

Expect hours on two cores and proportionally less on more: the sweep shares nothing between workers
except an atomic cursor, so it scales about as well as a search can.

## Rates, and where the time goes

| Attack | Measured | Full 2^32 at that rate |
|---|---|---|
| Ciphertext only, quadgram scoring | 229,762 keys/sec | 5.19 h |
| Known-plaintext crib, 18 bytes | 322,683 keys/sec | 3.70 h |

The crib attack decrypts 18 bytes per key and the ciphertext-only attack decrypts 234 and then
scores them, and the crib attack is 40% faster. Not ten times faster: 40%. Everything else is the
key schedule, which is 1,275 draws from a bounded generator per key and which neither attack can
avoid.

Most of those bounds are not powers of two, so each draw goes through the rejection loop in
`java.util.Random.nextInt(int)` and costs an integer division. That, and not the cipher, is what a
sweep of this cipher actually spends its time on. It is also why replacing the generator was worth
more than any change to the transform: see
[why-the-cipher-falls.md](why-the-cipher-falls.md#1-the-key-is-32-bits).

## Raw log

```
byte-enigma: keyspace sweep
commit:     1b37a30
host cores: 2
openjdk version "21.0.10" 2026-01-20
ciphertext: 234 bytes, key 2083951437, 3 rotors, textbook mode
mode:       ciphertext-only quadgram search
range:      [-2147483648, 2147483648)  4294967296 keys
threads:    2

    0.39%       16,777,216 keys   215,620 keys/sec  this run 77.8 s   total 77.8 s
    0.78%       33,554,432 keys   223,314 keys/sec  this run 2.5 min  total 2.5 min
    1.17%       50,331,648 keys   223,655 keys/sec  this run 3.8 min  total 3.8 min
    1.56%       67,108,864 keys   220,447 keys/sec  this run 5.1 min  total 5.1 min
    1.95%       83,886,080 keys   222,816 keys/sec  this run 6.3 min  total 6.3 min
    2.34%      100,663,296 keys   224,524 keys/sec  this run 7.5 min  total 7.5 min
    2.73%      117,440,512 keys   225,015 keys/sec  this run 8.7 min  total 8.7 min
stopped on budget at 117,440,512 of 4,294,967,296 keys (2.73%).

[five further runs, each resumed from the checkpoint and stopped on a budget, carried the
 cursor from 2.73% to 12.89%; their per-segment lines were not captured]

   13.28%      570,425,344 keys   224,089 keys/sec  this run 55.9 s  total 42.4 min
   13.67%      587,202,560 keys   225,491 keys/sec  this run 1.9 min  total 43.4 min
   14.06%      603,979,776 keys   227,024 keys/sec  this run 2.8 min  total 44.3 min
   14.45%      620,756,992 keys   228,576 keys/sec  this run 3.8 min  total 45.3 min
stopped on budget at 637,534,208 of 4,294,967,296 keys (14.84%). Run the same command again
to carry on from here.

keys tried:  637,534,208
elapsed:     46.2 min
rate:        229,762 keys/sec (measured over the work actually done)
full 2^32:   5.19 h at that rate

#1  key=-1684917101  score=-1828.24
#2  key=-1698706476  score=-1830.70
#3  key=-1620196637  score=-1831.48
#4  key=-2097726795  score=-1831.71
#5  key=-1947309478  score=-1831.88
```

For comparison, the true key scored -1620.38 on the same ciphertext, which you can check without
searching for it:

```
byte-enigma break --language --in message.b64 --from 2083951437 --to 2083951438
```

## The checkpoint from that run

`docs/keyspace-sweep.state` is the file the run above left behind. Copy it next to `message.b64`,
point `--checkpoint` at it, and the sweep carries on from where it stopped rather than starting
over:

```
byte-enigma break --language --in message.b64 --top 10 --checkpoint keyspace-sweep.state
```

It resumes only if the ciphertext, the mode and the range all match what it recorded, so it cannot
be pointed at a different message by accident.
