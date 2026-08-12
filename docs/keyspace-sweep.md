# Sweeping the whole keyspace

Every keys/sec figure this project publishes is measured. This page records a run over the entire
32-bit keyspace rather than a window, so the headline claim is not an extrapolation from a fast
first minute.

## The setup

| | |
|---|---|
| Ciphertext | 234 bytes of English, enciphered in textbook mode with 3 rotors |
| Key | 2083951437 |
| Range | `[-2147483648, 2147483648)`, all 4,294,967,296 keys |
| Machine | 2 cores, OpenJDK 21, a cloud container of no particular distinction |

The key sits about 97% of the way through the range, which is deliberate: the sweep starts at
`Integer.MIN_VALUE` and counts up, so a key near the top is only found by a run that really did go
all the way.

The ciphertext, Base64:

```
h0K7ne80iRrdqDBrrfJntmcbDyT2JahUyRzRLq/wJLsyq7jRaF0sPoal87B+NDkTulzn3bae+UAsmWr+1sYeGa4IY+SP
9Ab364XqZWIuidfuzuzR3Kz1R/EzN5dGcLsACH1XTD6sfG968KgXYKvEXnNsLlHTk4CVo+aAEn078TAiuO7AZtLKUhA/
7479zl9KmeCmDpVV8bDSBIwEptp88DPWHcVgiZ0CqQEIUQQl8sfQThGHl/frEr+ZEo2F6d5sfXGBJZ5vdr1gx9vhoQGO
vxdco5h5HYwL5pmgUbzNRL4l1XNc4IfZajuS
```

## Reproducing it

Save that Base64 to `message.b64`, then:

```
# ciphertext only, no crib, scoring English quadgrams
byte-enigma break --language --in message.b64 --top 10

# known-plaintext crib
byte-enigma break --crib "THIRTY TWO BIT KEY" --at 36 --in message.b64 --top 10
```

Both default to the full keyspace. Neither needs the key, and neither is told where in the range to
look. Progress prints every thirty seconds by default on a range this size; pass `--progress` to
change it or `--threads` to pin the worker count.

Expect hours rather than minutes on two cores. A machine with more of them finishes proportionally
sooner, because the sweep is embarrassingly parallel and shares nothing between workers except an
atomic cursor.

## Results

_Filled in from the run below._

## Raw log

_Attached below._
