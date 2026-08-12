# Corpus

The quadgram table in `src/main/resources/io/github/qnicondavid/byteenigma/breaker/quadgrams.txt`
is generated from the files in this directory. It is a derived file and it is checked in, so the
source it derives from is checked in beside it. `QuadgramTableReproducibilityTest` fails the
build if the two ever disagree.

To rebuild after changing anything here:

```
mvn -q -pl core -am compile exec:java -Dexec.mainClass=io.github.qnicondavid.byteenigma.breaker.QuadgramTableBuilder
```

Then commit the corpus and the regenerated table together.

## Where these texts came from

Every text is a work first published before 1929 and in the public domain in the United States.
The files are the plain-text selections distributed with the NLTK `gutenberg` corpus, which are
Project Gutenberg transcriptions with the Project Gutenberg header and footer already removed.
Each file keeps a single bracketed title line at the top; that line contributes a handful of
quadgrams and is left in place so the files match their upstream form byte for byte.

| File | Work | Author | First published |
|---|---|---|---|
| `austen-emma.txt` | Emma | Jane Austen | 1815 |
| `austen-persuasion.txt` | Persuasion | Jane Austen | 1818 |
| `bryant-stories.txt` | Stories to Tell to Children | Sara Cone Bryant | 1918 |
| `burgess-busterbrown.txt` | The Adventures of Buster Bear | Thornton W. Burgess | 1920 |
| `carroll-alice.txt` | Alice's Adventures in Wonderland | Lewis Carroll | 1865 |
| `chesterton-brown.txt` | The Wisdom of Father Brown | G. K. Chesterton | 1914 |
| `chesterton-thursday.txt` | The Man Who Was Thursday | G. K. Chesterton | 1908 |
| `melville-moby_dick.txt` | Moby Dick | Herman Melville | 1851 |

Upstream: <https://github.com/nltk/nltk_data>, `packages/corpora/gutenberg.zip`.
Project Gutenberg: <https://www.gutenberg.org>.

The only change made on the way in was normalising line endings to LF and stripping trailing
whitespace, so the table generates identically on every platform.

## Why these eight

They are narrative prose by eight different hands, which is what the scorer is asked to
recognise. Verse and scripture were deliberately left out of the selection: they are available
in the same upstream archive, and both skew the statistics hard towards spellings and
constructions that no longer appear in ordinary English.

Together they come to about 3.7 MB, 671,387 words, and produce 1,037,999 counted four-letter
windows.
