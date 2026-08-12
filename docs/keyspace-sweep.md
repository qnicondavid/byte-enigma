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

**A contiguous 234,881,024 keys, 5.47% of the range, from `Integer.MIN_VALUE` upwards.** Ciphertext
only, no crib. 17.4 minutes of wall clock on two cores.

The true key is not inside that range, which is exactly what makes the result useful: those 234
million keys are 234 million known-wrong answers, and the question worth asking of a language scorer
is how close the best wrong answer gets.

| | key | score |
|---|---|---|
| True key | 2083951437 | **-1620.38** |
| Best of 234,881,024 wrong keys | -2097726795 | -1831.71 |

**A margin of 211.33 log-units, over a quarter of a billion attempts.** The runners-up are packed
into a fifth of a log-unit of each other (-1831.71, -1831.88, -1831.89, -1831.96, -1832.09), which is
what a distribution of noise looks like when nothing in it is English. The true key is not at the top
of that distribution. It is nowhere near it.

Two of the wrong candidates are worth looking at, because they show what the scorer is actually
rewarding. Key -2023463748 produced the fragment `THAtHEy` and key -2135655653 produced `ISO`.
Neither is English, but four-letter windows like `THAT` and `THEY` are common enough that a handful
of accidental hits lifts a candidate above pure noise. That is the entire signal the attack runs on,
and 234 million samples say it is worth about 200 log-units less than the real thing.

## What has not been run

The remaining 94.53%. On this hardware the full range projects to **5.3 hours** at the measured rate
of 224,514 keys/sec, and the environment these measurements were taken in cannot hold a process for
that long.

So the honest statement is: the search recovers the key from a bounded range every time it is asked
to, the rate is measured rather than modelled, the separation between the key and a quarter of a
billion wrong answers is enormous, and nobody has yet watched it walk the entire space in one piece.

If you run it, the log below is the format to send.

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
checkpoint: sweep.state  (resuming at -1912602624)
resumed:    234,881,024 keys already done, 5.5% of the range
budget:     60.0 min, then stop and checkpoint

    5.86%      251,658,240 keys   224,712 keys/sec  this run 77.4 s  total 18.7 min
    ...
```

The checkpoint carries a SHA-256 of the ciphertext, the mode and the range, and refuses to resume
unless all three match, so a resume cannot quietly continue against a different message.

Expect hours on two cores and proportionally less on more: the sweep shares nothing between workers
except an atomic cursor, so it scales about as well as a search can.

## Rates, and where the time goes

| Attack | Measured | Full 2^32 at that rate |
|---|---|---|
| Ciphertext only, quadgram scoring | 224,514 keys/sec | 5.31 h |
| Known-plaintext crib, 18 bytes | 322,683 keys/sec | 3.70 h |

The crib attack decrypts 18 bytes per key and the ciphertext-only attack decrypts 234 and then
scores them, and the crib attack is 44% faster. Not ten times faster: 44%. Everything else is the
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
commit:     2de789a
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

    3.12%      134,217,728 keys   224,000 keys/sec  this run 78.1 s   total 10.0 min
    3.52%      150,994,944 keys   224,180 keys/sec  this run 2.6 min  total 11.2 min
    3.91%      167,772,160 keys   224,318 keys/sec  this run 3.9 min  total 12.5 min
    4.30%      184,549,376 keys   224,401 keys/sec  this run 5.1 min  total 13.7 min
    4.69%      201,326,592 keys   224,455 keys/sec  this run 6.4 min  total 15.0 min
    5.08%      218,103,808 keys   224,489 keys/sec  this run 7.7 min  total 16.2 min
    5.47%      234,881,024 keys   224,514 keys/sec  this run 8.9 min  total 17.4 min
stopped on budget at 234,881,024 of 4,294,967,296 keys (5.47%).

keys tried:  234,881,024
elapsed:     17.4 min
rate:        224,514 keys/sec (measured over the work actually done)
full 2^32:   5.31 h at that rate

#1  key=-2097726795  score=-1831.71
#2  key=-1947309478  score=-1831.88
#3  key=-2142788969  score=-1831.89
#4  key=-2135655653  score=-1831.96
#5  key=-2023463748  score=-1832.09
```

For comparison, the true key scored -1620.38 on the same ciphertext, which you can check without
searching for it:

```
byte-enigma break --language --in message.b64 --from 2083951437 --to 2083951438
```
