# Sweeping the whole keyspace

All 4,294,967,296 keys, ciphertext only, no crib. The true key came first.

Every keys/sec figure in this project is measured on work that actually ran, and this page is where
those measurements come from.

## The setup

| | |
|---|---|
| Ciphertext | 234 bytes of English, enciphered in textbook mode with 3 rotors |
| Key | 2083951437 |
| Range | `[-2147483648, 2147483648)`, all 4,294,967,296 keys |
| Attack | Ciphertext only. No crib, no hint about where in the range to look. |

The key sits about 97% of the way up from the bottom of the range on purpose. The sweep starts at
`Integer.MIN_VALUE` and counts up, so a key placed near the top is only found by a run that really
did go nearly all the way.

The ciphertext, Base64, SHA-256 `35de936d3adfe362561c02f3bb95e75b2c5ff74a279b2e397c43cf5a9d3cf2d2`:

```
h0K7ne80iRrdqDBrrfJntmcbDyT2JahUyRzRLq/wJLsyq7jRaF0sPoal87B+NDkTulzn3bae+UAsmWr+1sYeGa4IY+SP
9Ab364XqZWIuidfuzuzR3Kz1R/EzN5dGcLsACH1XTD6sfG968KgXYKvEXnNsLlHTk4CVo+aAEn078TAiuO7AZtLKUhA/
7479zl9KmeCmDpVV8bDSBIwEptp88DPWHcVgiZ0CqQEIUQQl8sfQThGHl/frEr+ZEo2F6d5sfXGBJZ5vdr1gx9vhoQGO
vxdco5h5HYwL5pmgUbzNRL4l1XNc4IfZajuS
```

## The result

| | key | score |
|---|---|---|
| **1** | **2083951437** | **-1620.38** |
| 2 | -1131398620 | -1826.76 |
| 3 | 628210969 | -1827.92 |
| 4 | -1684917101 | -1828.24 |
| 5 | 552358186 | -1828.46 |

The first entry is the key. It was not given to the search, and the search was not told where to
look; it walked every key in the space and this one scored highest.

**The margin is 206.39 log-units.** The nine runners-up span 3.05 log-units, from -1826.76 down to
-1829.81, which is what the top of a noise distribution looks like when nothing in it is English.
The key is not at the top of that distribution. It is not in it.

Some of the runners-up show what the scorer is rewarding: key -1131398620 produced the fragment
`APtIO`, key 628210969 produced `NEReNdiN`, and key -1321375626 produced `mUsTIReD`. Two or three
accidental four-letter hits lift a candidate a little above pure noise. Across four billion attempts
the best accident was worth 206 log-units less than the real thing, which is the number the whole
ciphertext-only attack rests on.

## Rates, and a caveat about this one

The run was done in two parts on one machine, resumed from a checkpoint: two threads for the first
14.84% of the range, sixteen for the rest.

| | keys | share | threads | wall clock | rate |
|---|---|---|---|---|---|
| First part | 637,534,208 | 14.84% | 2 | 46.2 min | 229,762 keys/sec |
| Second part | 3,657,433,088 | 85.16% | 16 | 75.6 min | **805,923 keys/sec** |
| Reported total | 4,294,967,296 | 100% | mixed | 2.03 h | 587,309 keys/sec |

**That 587,309 figure is not a rate anything ran at and should not be quoted as one.** A checkpoint
accumulates elapsed time across every segment, so when a run is resumed at a different thread count
the reported rate is a weighted average of both. It is a true statement about this particular run and
a misleading one about any way of running the machine. The per-part rows are the ones that mean
something.

Neither part covered the whole range: two threads did 14.84%, sixteen did 85.16%. Projected from the
per-part rates, the full keyspace takes **1.48 hours** on sixteen threads and 5.19 on two. Both are
arithmetic rather than wall clocks; the only measured wall clock for the whole range is the 2.03
hours above, and it belongs to neither thread count on its own.

For comparison, the known-plaintext crib attack measures 322,683 keys/sec on two threads
against 229,762 for the ciphertext-only one: **40% faster, not an order of magnitude**, despite
decrypting 18 bytes per key against 234. Everything else is the key schedule, which is 1,275 draws
from a bounded generator per key and which neither attack can avoid. Measured on one candidate it is
78.1% of the ciphertext-only cost and 95.8% of the crib cost, which is why replacing the generator was
worth more than any change to the transform. Most of those bounds are not powers of two, so each draw
goes through the rejection loop in `java.util.Random.nextInt(int)` and costs an integer division;
whether that division is the expensive part of the schedule is not something anything here measures.
See [benchmarks.md](benchmarks.md) and
[why-the-cipher-falls.md](why-the-cipher-falls.md#1-the-key-is-32-bits).

## What these rates do not reconcile with

These are not the rates this code runs at. [benchmarks.md](benchmarks.md) drives the same sweep, the
same evaluator and the same 234-byte message through JMH on this machine: two threads reach 368,345
keys/sec where the first segment above measured 229,762, and one thread alone reaches 197,338 where
the sixteen above averaged 50,370 each.

The sweep's own software is not the explanation. `SeedSweep` adds 0.138 us to a candidate that costs
4.929 in the evaluator, which is 2.8%, and two threads scale at 93% of one. What is left is the
machine and the day: six performance cores and ten efficiency-class ones, so a sixteen-thread average
is a rate no single thread runs at; clocks that fall over two hours of sustained load; and whatever
else the laptop was doing, which nobody wrote down.

So the figures above are what that run did, and a rerun would beat them. They stay because they are
the ones the log shows. The keys/sec here and the us/op there are still not convertible into one
another, and it is now clear which side of the gap the difference sits on.

## Reproducing it

Save the Base64 above to `message.b64`.

```
byte-enigma break --language --in message.b64 --top 10 --checkpoint sweep.state
```

That is the whole command. It defaults to the entire keyspace and is told nothing else.

`--checkpoint` is what makes a run this long practical. The sweep walks the range in segments and
records where it got to after each one, so it can be stopped at any point and picked up by running
the same command again. `--for 3600` time-boxes a run: it stops after roughly an hour, checkpoints,
and exits 3 to say there is range left. The checkpoint carries a SHA-256 of the ciphertext along with
the mode and the range, and refuses to resume unless all three match, so it cannot quietly continue
against a different message.

The known-plaintext route over the same range, which needs its own checkpoint because the mode
differs:

```
byte-enigma break --crib "THIRTY TWO BIT KEY" --at 36 --in message.b64 --top 10 --checkpoint crib.state
```

## The log

The second part, from 14.84% to the end, in one uninterrupted run on 16 threads. The rate column
climbs through the whole thing because it is cumulative from the checkpoint, including the slower
first part; it is still converging on the true blended figure at 100%.

```
ciphertext: 234 bytes, sha-256 35de936d3adfe362
mode:       ciphertext-only quadgram search
range:      [-2147483648, 2147483648)  4294967296 keys
threads:    16
checkpoint: sweep.state  (resuming at -1509949440)
resumed:    637,534,208 keys already done, 14.8% of the range

   16.41%      704,643,072 keys   248,281 keys/sec  this run 63.3 s   total 47.3 min
   17.97%      771,751,936 keys   265,329 keys/sec  this run 2.2 min  total 48.5 min
   19.53%      838,860,800 keys   279,804 keys/sec  this run 3.7 min  total 50.0 min
   21.09%      905,969,664 keys   292,794 keys/sec  this run 5.3 min  total 51.6 min
   22.66%      973,078,528 keys   304,587 keys/sec  this run 7.0 min  total 53.2 min
   24.22%    1,040,187,392 keys   314,888 keys/sec  this run 8.8 min  total 55.1 min
   25.78%    1,107,296,256 keys   325,736 keys/sec  this run 10.4 min  total 56.7 min
   27.34%    1,174,405,120 keys   335,369 keys/sec  this run 12.1 min  total 58.4 min
   28.91%    1,241,513,984 keys   344,904 keys/sec  this run 13.7 min  total 60.0 min
   30.47%    1,308,622,848 keys   353,540 keys/sec  this run 15.4 min  total 61.7 min
   32.03%    1,375,731,712 keys   363,292 keys/sec  this run 16.9 min  total 63.1 min
   33.59%    1,442,840,576 keys   372,894 keys/sec  this run 18.2 min  total 64.5 min
   35.16%    1,509,949,440 keys   382,163 keys/sec  this run 19.6 min  total 65.9 min
   36.72%    1,577,058,304 keys   391,317 keys/sec  this run 20.9 min  total 67.2 min
   38.28%    1,644,167,168 keys   400,143 keys/sec  this run 22.2 min  total 68.5 min
   39.84%    1,711,276,032 keys   408,693 keys/sec  this run 23.5 min  total 69.8 min
   41.41%    1,778,384,896 keys   416,866 keys/sec  this run 24.9 min  total 71.1 min
   42.97%    1,845,493,760 keys   424,305 keys/sec  this run 26.2 min  total 72.5 min
   44.53%    1,912,602,624 keys   431,001 keys/sec  this run 27.7 min  total 74.0 min
   46.09%    1,979,711,488 keys   437,619 keys/sec  this run 29.2 min  total 75.4 min
   47.66%    2,046,820,352 keys   444,185 keys/sec  this run 30.6 min  total 76.8 min
   49.22%    2,113,929,216 keys   450,916 keys/sec  this run 31.9 min  total 78.1 min
   50.78%    2,181,038,080 keys   457,634 keys/sec  this run 33.2 min  total 79.4 min
   52.34%    2,248,146,944 keys   461,503 keys/sec  this run 34.9 min  total 81.2 min
   53.91%    2,315,255,808 keys   465,529 keys/sec  this run 36.6 min  total 82.9 min
   55.47%    2,382,364,672 keys   469,823 keys/sec  this run 38.3 min  total 84.5 min
   57.03%    2,449,473,536 keys   475,277 keys/sec  this run 39.7 min  total 85.9 min
   58.59%    2,516,582,400 keys   479,933 keys/sec  this run 41.1 min  total 87.4 min
   60.16%    2,583,691,264 keys   484,144 keys/sec  this run 42.7 min  total 88.9 min
   61.72%    2,650,800,128 keys   487,954 keys/sec  this run 44.3 min  total 1.51 h
   63.28%    2,717,908,992 keys   493,085 keys/sec  this run 45.6 min  total 1.53 h
   64.84%    2,785,017,856 keys   498,354 keys/sec  this run 46.9 min  total 1.55 h
   66.41%    2,852,126,720 keys   503,524 keys/sec  this run 48.2 min  total 1.57 h
   67.97%    2,919,235,584 keys   508,493 keys/sec  this run 49.4 min  total 1.59 h
   69.53%    2,986,344,448 keys   512,689 keys/sec  this run 50.8 min  total 1.62 h
   71.09%    3,053,453,312 keys   517,305 keys/sec  this run 52.1 min  total 1.64 h
   72.66%    3,120,562,176 keys   521,972 keys/sec  this run 53.4 min  total 1.66 h
   74.22%    3,187,671,040 keys   526,565 keys/sec  this run 54.6 min  total 1.68 h
   75.78%    3,254,779,904 keys   531,107 keys/sec  this run 55.9 min  total 1.70 h
   77.34%    3,321,888,768 keys   535,484 keys/sec  this run 57.1 min  total 1.72 h
   78.91%    3,388,997,632 keys   539,628 keys/sec  this run 58.4 min  total 1.74 h
   80.47%    3,456,106,496 keys   543,692 keys/sec  this run 59.7 min  total 1.77 h
   82.03%    3,523,215,360 keys   547,670 keys/sec  this run 61.0 min  total 1.79 h
   83.59%    3,590,324,224 keys   551,600 keys/sec  this run 62.2 min  total 1.81 h
   85.16%    3,657,433,088 keys   555,623 keys/sec  this run 63.5 min  total 1.83 h
   86.72%    3,724,541,952 keys   559,467 keys/sec  this run 64.7 min  total 1.85 h
   88.28%    3,791,650,816 keys   563,201 keys/sec  this run 66.0 min  total 1.87 h
   89.84%    3,858,759,680 keys   566,901 keys/sec  this run 67.2 min  total 1.89 h
   91.41%    3,925,868,544 keys   570,546 keys/sec  this run 68.4 min  total 1.91 h
   92.97%    3,992,977,408 keys   574,013 keys/sec  this run 69.7 min  total 1.93 h
   94.53%    4,060,086,272 keys   577,365 keys/sec  this run 71.0 min  total 1.95 h
   96.09%    4,127,195,136 keys   580,727 keys/sec  this run 72.2 min  total 1.97 h
   97.66%    4,194,304,000 keys   583,917 keys/sec  this run 73.5 min  total 2.00 h
   99.22%    4,261,412,864 keys   586,247 keys/sec  this run 74.9 min  total 2.02 h
  100.00%    4,294,967,296 keys   587,309 keys/sec  this run 75.6 min  total 2.03 h

keys tried:  4,294,967,296  (whole range)
elapsed:     2.03 h
rate:        587,309 keys/sec (measured over the work actually done)

margin:      206.39 between the first and the second

#1  key=2083951437  score=-1620.38
    THE CIPHER IN THIS REPOSITORY HAS A THIRTY TWO BIT KEY AND THAT IS THE WHOLE OF THE SECRET SO
    ANY MACHINE CAN TRY EVERY KEY IN TURN UNTIL THE PLAINTEXT COMES BACK OUT WHICH IS EXACTLY WHAT
    THE SWEEP RECORDED IN THIS FILE SET OUT TO DO
#2  key=-1131398620  score=-1826.76
#3  key=628210969    score=-1827.92
#4  key=-1684917101  score=-1828.24
#5  key=552358186    score=-1828.46
#6  key=1203253946   score=-1828.94
#7  key=398586806    score=-1829.38
#8  key=139932621    score=-1829.46
#9  key=127748316    score=-1829.51
#10 key=-1321375626  score=-1829.81
```

The first part of the run, the 14.84% done on two cores, produced this before the resume:

```
    0.39%       16,777,216 keys   215,620 keys/sec  this run 77.8 s   total 77.8 s
    0.78%       33,554,432 keys   223,314 keys/sec  this run 2.5 min  total 2.5 min
    1.17%       50,331,648 keys   223,655 keys/sec  this run 3.8 min  total 3.8 min
    1.56%       67,108,864 keys   220,447 keys/sec  this run 5.1 min  total 5.1 min
    1.95%       83,886,080 keys   222,816 keys/sec  this run 6.3 min  total 6.3 min
    2.34%      100,663,296 keys   224,524 keys/sec  this run 7.5 min  total 7.5 min
    2.73%      117,440,512 keys   225,015 keys/sec  this run 8.7 min  total 8.7 min
stopped on budget at 117,440,512 of 4,294,967,296 keys (2.73%).

[five further runs, each resumed from the checkpoint and stopped on a time budget, carried the
 cursor from 2.73% to 12.89%; their per-segment lines were not captured]

   13.28%      570,425,344 keys   224,089 keys/sec  this run 55.9 s   total 42.4 min
   13.67%      587,202,560 keys   225,491 keys/sec  this run 1.9 min  total 43.4 min
   14.06%      603,979,776 keys   227,024 keys/sec  this run 2.8 min  total 44.3 min
   14.45%      620,756,992 keys   228,576 keys/sec  this run 3.8 min  total 45.3 min
stopped on budget at 637,534,208 of 4,294,967,296 keys (14.84%).
```

`docs/keyspace-sweep.state` is the checkpoint the completed run left behind, cursor at the end of the
range. It is committed so the result can be inspected without repeating the two hours.
