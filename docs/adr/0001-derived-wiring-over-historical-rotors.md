# 0001. Derived wiring over historical rotors

**Status:** accepted, 2026-06-27. Revisited 2026-08 when the project was renamed from
`enigma-machine` to `byte-enigma`, and kept.

## Context

The project started as an Enigma implementation. There are two ways to build one, and they lead to
different repositories.

**The historical machine.** Twenty-six letters. Eight catalogue rotors with published wirings, chosen
three at a time. Fixed turnover notches, ring settings, a plugboard of about ten letter pairs, and the
double-stepping anomaly that comes out of the pawl-and-ratchet mechanism. The key is the rotor
selection, their order, their ring settings, their start positions and the plugboard pairs.

**A machine shaped like Enigma.** The same signal path, but with the constants replaced by whatever
you want. Here: 256 symbols so any byte can go through it, all wirings derived from one integer, and
turnover offsets derived from the same integer.

The first is a faithful simulator. The second is a cipher in the Enigma family that has nothing to do
with 1940.

Two things pushed towards the second. Restricting to 26 letters means the cipher cannot carry a file,
a UTF-8 string or anything that is not uppercase text, which makes every example contrived. And
hardcoding eight rotor wirings means the rotor count is fixed at whatever the table holds, so
`rotorCount` could not be a parameter and the relationship between key schedule size and breaking cost
could not be varied or measured.

## Decision

Derived wiring, 256 symbols, and no historical fidelity claimed anywhere.

- The alphabet is every byte value, so any input round-trips.
- Every rotor's permutation, both involutions, and every turnover offset come from the 32-bit key
  through a MurmurHash3 finaliser with a per-component tag.
- Rotor count is a constructor parameter with a cap, not a catalogue lookup.
- Stepping is a plain odometer. A rotor advances once per byte and kicks the next one when it lands on
  its own turnover offset. There is no double-stepping, because there is no mechanism to produce one.
- The plugboard is a full 128-pair involution rather than the historical ten pairs.

The name and the README say all of this in the first screen, so nobody arrives expecting a simulator.

## Consequences

**What this buys.**

The cipher takes arbitrary bytes, so the examples are real files rather than shouted uppercase. Rotor
count is a dial, which makes the key schedule's share of the breaking cost measurable.
`KeyScheduleBenchmark` runs at 1, 3 and 8 rotors, and the answer turns out to dominate everything else.
The two structural properties that make the whole thing interesting, reciprocity and the absence of
fixed points, survive intact, because they come from the reflector being a fixed-point-free involution
and not from any historical detail.

**What this costs.**

The repository cannot serve anyone looking for an Enigma simulator, and some of them will arrive
anyway. That is a documentation problem, handled by saying so immediately rather than by a footnote.

It also gives up a free source of test vectors. A faithful simulator can be checked against published
wartime messages; this one can only be checked against itself, which is why the golden vector and the
invariant sweeps carry more weight here than they otherwise would.

**What it does not change.**

None of this makes the cipher stronger. The plugboard being 128 pairs rather than ten sounds like an
improvement and is not, because the key is 32 bits either way and the plugboard is derived from it.
Enigma's real keyspace was far larger than this one; the machine fell to operator habits, cribs and
the no-fixed-point rule rather than to exhaustion. This cipher falls to plain exhaustion, and the
extra structure buys nothing against that.

## Alternatives considered

**Faithful 26-letter Enigma.** Rejected for the reasons above, and because it is a solved exercise
with dozens of good implementations. The one thing this repository has that most of them do not is a
working break of its own cipher, and that is easier to build and to explain against a keyspace small
enough to exhaust on a laptop.

**Widening the key so the cipher is actually strong.** Rejected on purpose. A cipher you cannot break
in an afternoon is a cipher whose weaknesses you have to take on trust. The 32-bit key is what makes
the second half of the repository possible, and pretending otherwise would produce something both
insecure and undemonstrable.

**26 letters with derived wiring.** The worst of both: still cannot carry a file, still not
historical.
