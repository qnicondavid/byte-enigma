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
quadgrams and is left in place so the files match their upstream form byte for byte. The year in
that line is the edition someone transcribed, not the first publication, and on four of the eight
they are different years. Both are here, so `head -1` on any file lands on a column.

| File | Work | Author | First published | Year in the file |
|---|---|---|---|---|
| `austen-emma.txt` | Emma | Jane Austen | 1815 | 1816 |
| `austen-persuasion.txt` | Persuasion | Jane Austen | 1817 | 1818 |
| `bryant-stories.txt` | Stories to Tell to Children | Sara Cone Bryant | 1907 | 1918 |
| `burgess-busterbrown.txt` | The Adventures of Buster Bear | Thornton W. Burgess | 1916 | 1920 |
| `carroll-alice.txt` | Alice's Adventures in Wonderland | Lewis Carroll | 1865 | 1865 |
| `chesterton-brown.txt` | The Wisdom of Father Brown | G. K. Chesterton | 1914 | 1914 |
| `chesterton-thursday.txt` | The Man Who Was Thursday | G. K. Chesterton | 1908 | 1908 |
| `melville-moby_dick.txt` | Moby Dick | Herman Melville | 1851 | 1851 |

Both Austens went on sale in the December before the year printed on them: Emma on 23 December 1815
dated 1816, Persuasion on 20 December 1817 dated 1818, both recorded by the Jane Austen Society of
North America at <https://www.jasna.org/austen/works/>. The other two are later printings. Stories
to Tell to Children was published by Houghton, Mifflin and Company in 1907
(<https://archive.org/details/storiestotelltoc07brya>), and The Adventures of Buster Bear by Little,
Brown and Company in 1916, from the bibliography the Thornton W. Burgess Society keeps at
<https://thorntonburgess.org/books-written-by-thornton-w-burgess>. None of this touches the claim
above: the latest of the sixteen years in the table is 1920.

Upstream: <https://github.com/nltk/nltk_data>, `packages/corpora/gutenberg.zip`.
Project Gutenberg: <https://www.gutenberg.org>.

The only change made on the way in was normalising line endings to LF and stripping trailing
whitespace, so the table generates identically on every platform. That holds for what is
committed; `.gitattributes` is what makes it hold for what is checked out, which is what the
reproducibility test actually reads.

## Why these eight

They are narrative prose by eight different hands, which is what the scorer is asked to
recognise. Verse and scripture were deliberately left out of the selection: they are available
in the same upstream archive, and both skew the statistics hard towards spellings and
constructions that no longer appear in ordinary English.

Together they come to about 3.7 MB, 671,387 words, and produce 1,037,999 counted four-letter
windows.
