package com.enigma.breaker;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class QuadgramTableGenerator {

    private static final int PRUNE_MIN_COUNT = 5;
    private static final Path OUTPUT =
            Path.of("src", "main", "resources", "com", "enigma", "breaker", "quadgrams.txt");

    private static final String CORPUS = String.join(" ",
        "It is a truth universally acknowledged that a single man in possession of a good fortune must be in want of a wife.",
        "However little known the feelings or views of such a man may be on his first entering a neighbourhood this truth is so well fixed in the minds of the surrounding families that he is considered as the rightful property of some one or other of their daughters.",
        "My dear Mr Bennet said his lady to him one day have you heard that Netherfield Park is let at last. Mr Bennet replied that he had not. But it is returned she for Mrs Long has just been here and she told me all about it.",
        "Mr Bennet was so odd a mixture of quick parts sarcastic humour reserve and caprice that the experience of three and twenty years had been insufficient to make his wife understand his character.",
        "Her mind was less difficult to develop. She was a woman of mean understanding little information and uncertain temper. When she was discontented she fancied herself nervous.",
        "The business of her life was to get her daughters married. Its solace was visiting and news. Elizabeth listened in silence but was not convinced. Their conduct had been such as neither of them could think of without disgust.",
        "Vanity and pride are different things though the words are often used as synonymous. A person may be proud without being vain. Pride relates more to our opinion of ourselves vanity to what we would have others think of us.",
        "It is a truth that a lady always feels the value of a home and a settled situation. Elizabeth felt that she had been blind partial prejudiced absurd. She grew absolutely ashamed of herself.",
        "To Sherlock Holmes she is always the woman. I have seldom heard him mention her under any other name. In his eyes she eclipses and predominates the whole of her sex.",
        "It was not that he felt any emotion akin to love for Irene Adler. All emotions and that one particularly were abhorrent to his cold precise but admirably balanced mind.",
        "He was I take it the most perfect reasoning and observing machine that the world has seen but as a lover he would have placed himself in a false position.",
        "You see but you do not observe. The distinction is clear. For example you have frequently seen the steps which lead up from the hall to this room. How often you say. Well some hundreds of times. Then how many are there.",
        "I have no data yet. It is a capital mistake to theorize before one has data. Insensibly one begins to twist facts to suit theories instead of theories to suit facts.",
        "When you have eliminated the impossible whatever remains however improbable must be the truth. The world is full of obvious things which nobody by any chance ever observes.",
        "My name is Sherlock Holmes. It is my business to know what other people do not know. The game is afoot and we must follow it wherever it may lead us this very night.",
        "It is my belief Watson founded upon my experience that the lowest and vilest alleys in London do not present a more dreadful record of sin than does the smiling and beautiful countryside.",
        "Call me Ishmael. Some years ago never mind how long precisely having little or no money in my purse and nothing particular to interest me on shore I thought I would sail about a little and see the watery part of the world.",
        "Whenever I find myself growing grim about the mouth whenever it is a damp drizzly November in my soul then I account it high time to get to sea as soon as I can.",
        "There is nothing surprising in this. If they but knew it almost all men in their degree some time or other cherish very nearly the same feelings towards the ocean with me.",
        "There now is your insular city of the Manhattoes belted round by wharves as Indian isles by coral reefs commerce surrounds it with her surf. Right and left the streets take you waterward.",
        "Circumambulate the city of a dreamy Sabbath afternoon. What do you see. Posted like silent sentinels all around the town stand thousands upon thousands of mortal men fixed in ocean reveries.",
        "Why is almost every robust healthy boy with a robust healthy soul in him at some time or other crazy to go to sea. Why upon your first voyage as a passenger did you yourself feel such a mystical vibration.",
        "Alice was beginning to get very tired of sitting by her sister on the bank and of having nothing to do. Once or twice she had peeped into the book her sister was reading but it had no pictures or conversations in it.",
        "And what is the use of a book thought Alice without pictures or conversations. So she was considering in her own mind whether the pleasure of making a daisy chain would be worth the trouble of getting up and picking the daisies.",
        "When suddenly a white rabbit with pink eyes ran close by her. There was nothing so very remarkable in that nor did Alice think it so very much out of the way to hear the rabbit say to itself oh dear I shall be late.",
        "In another moment down went Alice after it never once considering how in the world she was to get out again. The rabbit hole went straight on like a tunnel for some way and then dipped suddenly down.",
        "Either the well was very deep or she fell very slowly for she had plenty of time as she went down to look about her and to wonder what was going to happen next.",
        "Curiouser and curiouser cried Alice she was so much surprised that for the moment she quite forgot how to speak good English. Now I am opening out like the largest telescope that ever was.",
        "You will rejoice to hear that no disaster has accompanied the commencement of an enterprise which you have regarded with such evil forebodings. I arrived here yesterday and my first task is to assure my dear sister of my welfare.",
        "I am already far north of London and as I walk in the streets of Petersburgh I feel a cold northern breeze play upon my cheeks which braces my nerves and fills me with delight.",
        "There is something at work in my soul which I do not understand. I am practically industrious painstaking a workman to execute with perseverance and labour but besides this there is a love for the marvellous.",
        "It is a great curiosity to learn the secrets of nature and there is a satisfaction to explore its recesses. These reflections have dispelled the agitation with which I began my letter and I feel my heart glow.",
        "So strange an accident has happened to us that I cannot forbear recording it although it is very probable that you will see me before these papers can come into your possession.",
        "It was the best of times it was the worst of times it was the age of wisdom it was the age of foolishness it was the epoch of belief it was the epoch of incredulity.",
        "It was the season of light it was the season of darkness it was the spring of hope it was the winter of despair we had everything before us we had nothing before us.",
        "We were all going direct to heaven we were all going direct the other way in short the period was so far like the present period that some of its noisiest authorities insisted on its being received.",
        "There were a king with a large jaw and a queen with a plain face on the throne of England there were a king with a large jaw and a queen with a fair face on the throne of France.",
        "When in the course of human events it becomes necessary for one people to dissolve the political bands which have connected them with another and to assume among the powers of the earth the separate and equal station.",
        "We hold these truths to be self evident that all men are created equal that they are endowed by their creator with certain unalienable rights that among these are life liberty and the pursuit of happiness.",
        "That to secure these rights governments are instituted among men deriving their just powers from the consent of the governed. That whenever any form of government becomes destructive of these ends it is the right of the people to alter it.",
        "In the beginning God created the heaven and the earth. And the earth was without form and void and darkness was upon the face of the deep. And the spirit of God moved upon the face of the waters.",
        "And God said let there be light and there was light. And God saw the light that it was good and God divided the light from the darkness. And God called the light day and the darkness he called night.",
        "And God said let there be a firmament in the midst of the waters and let it divide the waters from the waters. And God made the firmament and divided the waters which were under the firmament.",
        "And God said let the waters under the heaven be gathered together unto one place and let the dry land appear and it was so. And God called the dry land earth and the gathering together of the waters called he seas.",
        "And God said let the earth bring forth grass the herb yielding seed and the fruit tree yielding fruit after his kind whose seed is in itself upon the earth and it was so.",
        "Happy families are all alike. Every unhappy family is unhappy in its own way. Everything was in confusion in the house. The wife had discovered that the husband was carrying on an intrigue with a former governess.",
        "The wife did not leave her own room the husband had not been at home for three days. The children ran wild all over the house. The English governess quarrelled with the housekeeper and wrote to a friend asking her to look out for a new situation.",
        "A wonderful serenity has taken possession of my entire soul like these sweet mornings of spring which I enjoy with my whole heart. I am alone and feel the charm of existence in this spot which was created for the bliss of souls like mine.",
        "I am so happy my dear friend so absorbed in the exquisite sense of mere tranquil existence that I neglect my talents. I should be incapable of drawing a single stroke at the present moment and yet I feel that I never was a greater artist than now.",
        "Two households both alike in dignity in fair Verona where we lay our scene from ancient grudge break to new mutiny where civil blood makes civil hands unclean.",
        "Now is the winter of our discontent made glorious summer by this sun of York and all the clouds that lowered upon our house in the deep bosom of the ocean buried.",
        "To be or not to be that is the question whether it is nobler in the mind to suffer the slings and arrows of outrageous fortune or to take arms against a sea of troubles.",
        "All the world is a stage and all the men and women merely players. They have their exits and their entrances and one man in his time plays many parts his acts being seven ages.",
        "The quick brown fox jumps over the lazy dog while the patient cat watches from the warm windowsill of the quiet cottage near the winding river.",
        "Once upon a time there was a little girl who lived in a village near the forest. Whenever she went out the little girl wore a red riding cloak so everyone in the village called her little red riding hood.",
        "The morning sun rose slowly over the distant hills casting long golden shadows across the quiet valley where the small town still slept beneath a blanket of gentle mist.",
        "Learning to read and write opens many doors for people of every age and every nation. A person who can read a simple story can also learn to build a house grow a garden or tend the sick.",
        "The children gathered around the old teacher as she told them stories of brave sailors who crossed the wide oceans and of clever merchants who traveled along the ancient roads carrying silk and spices.",
        "Water flows from the high mountains down through the green valleys into the wide rivers and at last into the deep and restless sea where it rises again into the clouds and returns as rain.",
        "The old clock in the hall struck twelve and the whole house fell silent except for the soft sound of the wind moving gently through the tall trees outside the window.",
        "She opened the heavy wooden door and stepped into a large room filled with books from the floor to the ceiling. The smell of old paper and worn leather filled the warm and quiet air around her.",
        "Every good story begins with a single question and every great journey begins with a single step. The traveler who keeps walking will in time reach places that once seemed impossibly far away.",
        "The farmer rose before the dawn to feed the animals and to gather the eggs from the hens. He worked in the fields all through the long day and returned home tired but content in the evening light.",
        "The great river carried the small wooden boat past green forests and rocky hills and under many bridges until it reached the busy harbour where tall ships waited to sail across the sea.",
        "In winter the snow covered the fields and the roads and the roofs of the houses. The children ran outside to play and their laughter rang out clear and bright across the frozen and silent land.",
        "The scientist studied the stars for many years and wrote down everything she observed in a great book. She hoped that her careful work would help other people understand the vast and silent heavens above.",
        "A gentle rain began to fall upon the garden and the flowers turned their faces upward to drink the cool clear water. The birds took shelter in the branches and waited for the storm to pass over.",
        "The market square was crowded with people buying bread and fruit and cloth and every kind of thing. Merchants called out to the passing crowd and children darted between the stalls laughing and playing.",
        "He read the letter twice and then set it down upon the table. For a long moment he sat quite still and stared out of the window at the grey clouds gathering over the distant sea.",
        "The road wound up the hill between tall hedges and old stone walls until it reached the summit where the whole green country spread out below like a great and quiet map.",
        "They lit a fire in the hearth and drew their chairs around it and spoke of many things far into the night while the wind howled outside and the rain beat against the shutters.",
        "The teacher wrote the words upon the board and the students copied them into their books. Slowly and with great care they learned to spell each word and to read each sentence aloud.",
        "When the ship reached the open sea the sailors set the great white sails and the wind filled them and carried the vessel swiftly over the rolling waves towards the far horizon.",
        "The great mass of people who are content to earn their daily bread by honest labour have little cause to envy the rich and the powerful for they possess a treasure of their own.",
        "In the days of old when the world was young there lived a wise and gentle king who ruled his people with justice and mercy and was beloved by all who knew him.",
        "The wind blew cold across the moor and the two travelers drew their cloaks about them and hurried on towards the distant inn where a warm fire and a good supper awaited them.",
        "It was a bright and cheerful morning and the whole household was astir early for there was much to be done before the guests arrived for the great feast in the evening.",
        "He had wandered far from home over many strange lands and had seen great cities and wide deserts and high mountains but he had never forgotten the little house where he was born.",
        "The river ran deep and slow between its green banks and the willows leaned over the water and dipped their long branches into the cool and shining stream below.",
        "She sat quietly in the corner of the room with her book upon her knee but her thoughts were far away in the sunny fields of her childhood among the flowers and the birds.",
        "The old man told the children many wonderful stories of the sea and of the strange countries he had visited in his youth when he sailed before the mast in a tall and gallant ship.",
        "When the harvest was over the whole village came together to celebrate with music and dancing and feasting and the sound of their laughter carried far across the quiet autumn fields.",
        "The little bird built its nest high in the branches of the old oak tree and there it raised its young in safety far above the reach of the cat and the prowling fox.",
        "Tom presented himself before Aunt Polly who was sitting by an open window in a pleasant rearward apartment which was bedroom breakfast room dining room and library combined.",
        "The summer evenings were long and warm and the boys spent them by the river fishing and swimming and telling one another the tall tales that boys have always told since the beginning of time.",
        "A liberal education has for its object the formation of character. To prepare the young for the duties of life is the noble task to which every good teacher devotes his heart and mind.",
        "Knowledge comes but wisdom lingers and the years bring the philosophic mind. The child learns to read and to write and to count and slowly grows into a thoughtful and understanding man.",
        "The sun set slowly behind the western hills and the sky was filled with glorious colours of gold and crimson and deep purple that faded gently into the soft grey of the coming night.",
        "The traveler came at last to a great forest and he entered it with a beating heart for he had heard that strange and terrible things dwelt among its dark and ancient trees.",
        "The mother sat by the cradle and sang a soft and gentle song and the little child listened with wide and wondering eyes until at last it closed them and fell fast asleep.",
        "The merchant loaded his ship with silk and spices and precious stones and set sail for a distant port hoping to sell his goods for a great price and to return home a wealthy man.",
        "The soldiers marched all day beneath the burning sun along the dusty road and when the evening came they made their camp beside a cool and welcome spring among the trees.",
        "The wise woman gathered herbs in the deep woods and knew the secret virtues of every root and leaf and flower and the sick came to her from far and near to be healed.",
        "The storm broke over the city in the middle of the night and the thunder rolled and the lightning flashed and the rain fell in torrents upon the empty and glistening streets.",
        "The two friends had not seen each other for many years and when they met at last they had a thousand things to say and they talked far into the quiet and starry night.",
        "The gardener worked among his flowers from the early morning until the evening and his garden was the pride of the whole village and a joy to all who passed along the road.",
        "The little stream flowed down from the mountain over the rocks and through the meadows and joined at last the great river that carried its waters onward to the wide and distant sea.",
        "The king called his wisest counsellors together and asked them what should be done and each of them gave his opinion but the king listened most to the oldest and the gravest among them.",
        "The children played in the meadow among the tall grass and the bright flowers and chased the butterflies from bloom to bloom until the sun grew low and their mother called them home.",
        "He climbed the steep and winding path to the top of the hill and there he stood and looked out over the wide green country that stretched away as far as the eye could see.",
        "The old bridge had stood across the river for a hundred years and countless travelers had passed over it on their way to the market town that lay upon the farther shore.",
        "The scholar spent his days among his books in the quiet library and his nights beneath the stars observing the slow and steady movements of the planets across the darkened sky.",
        "The shepherd led his flock over the green hills in the morning and watched them all the day and brought them safely back to the fold again before the fall of night.",
        "The waves rolled in upon the sandy shore one after another in an endless and unhurried procession and the children built their castles of sand and watched the sea wash them away.",
        "And it came to pass in those days that a decree went out that all the world should be counted and every man returned to the city of his fathers to be numbered.",
        "Blessed are the meek for they shall inherit the earth. Blessed are they that mourn for they shall be comforted. Blessed are the merciful for they shall obtain mercy in their turn.",
        "Consider the lilies of the field how they grow they toil not neither do they spin and yet even Solomon in all his glory was not arrayed like one of these humble flowers.",
        "The rain came down and the floods came and the winds blew and beat upon that house and it fell not for it was founded upon a rock and it stood firm against the storm.",
        "A certain man had two sons and the younger of them said to his father give me the portion of goods that falls to me and he divided unto them his living without complaint.",
        "The farmer went out to sow his seed and as he sowed some fell by the wayside and the birds came and devoured it and other seed fell upon the good ground and brought forth fruit.",
        "The night was dark and the road was long but the pilgrim did not falter for he carried in his heart a bright and steady hope that led him ever onward toward his distant home.",
        "The great library held ten thousand books upon its shelves and within them was gathered the wisdom of a hundred generations of thoughtful and patient men and women.",
        "The young apprentice watched his master closely and learned the secrets of the trade little by little until in time he grew as skilful as the old man who had taught him.",
        "The village lay in a green valley between two gentle hills and a clear stream ran through the middle of it and turned the great wheel of the ancient mill beside the road.",
        "The captain gave the order and the sailors climbed the rigging and loosed the sails and the great ship began to move slowly away from the harbour toward the open sea.",
        "The autumn leaves fell one by one from the branches of the trees and covered the ground with a carpet of red and gold and brown that rustled softly beneath the feet.",
        "The doctor came at once when he was called and sat by the bedside all through the long night and in the morning the fever had passed and the child was out of danger.",
        "The old woman kept a little shop at the corner of the street where she sold bread and cheese and apples and sweets and the children loved to gather there after school.",
        "The explorer set out with a small band of companions to seek the source of the great river and for many months they journeyed through unknown and dangerous lands.",
        "The music rose and fell through the great hall and the dancers moved gracefully across the polished floor while the candles burned bright in their sconces upon the walls.",
        "The wise king judged between the two women who both claimed the child and by his cunning he discovered the truth and gave the child to its true and loving mother.",
        "The little seed lay in the dark earth all through the long winter and when the warm spring came it sent up a tender green shoot toward the light and the air above.",
        "The fisherman cast his net into the sea and drew it up again full of fishes of every kind and he sat upon the shore and sorted the good from the bad with patient hands.",
        "The morning was cold and clear and a thin white frost lay upon the fields and the branches of the bare trees glittered in the light of the rising winter sun.",
        "The people of the town came out into the streets to watch the great procession pass and they cheered and waved their hats and threw flowers before the horses of the king.",
        "The old sailor sat upon the harbour wall and mended his nets and told the passing children stories of the great storms he had weathered and the strange lands he had seen.",
        "The rain fell steadily all through the grey afternoon and the streets were empty and the only sound was the soft and constant patter of the drops upon the roofs and windows.",
        "The young girl walked through the fields gathering wild flowers and singing softly to herself and the birds seemed to answer her from the hedges and the branches overhead.",
        "The blacksmith stood at his forge and struck the glowing iron with his heavy hammer and the sparks flew up in a bright and dancing shower into the smoky air above.",
        "The traveler stopped at the little inn to rest his weary feet and the kindly host brought him bread and cheese and a cup of ale and set them before him with a smile.",
        "The children sat around the fire and listened while their grandmother told them the old tales of fairies and giants and brave knights that she had heard when she was young.",
        "The great ship sailed on through the calm blue water and the sailors sang as they worked and the white gulls followed in her wake and cried above the gentle waves.",
        "The scholar bent over his desk by the light of a single candle and copied the ancient words with a careful hand long after the rest of the house had gone to sleep.",
        "The farmer and his wife worked side by side in the fields from dawn to dusk and when the harvest was gathered in they gave thanks for the plenty that the good earth had yielded.",
        "The river wound its way through the green and pleasant valley past the old mill and the little church and the cluster of cottages that made up the quiet and peaceful village.",
        "The wind rose in the night and howled about the house and rattled the shutters and the family gathered close about the fire and were glad of its warmth and its cheerful light.",
        "The young man studied hard by day and by night for he was determined to make his way in the world and to bring honour and comfort to his aged father and mother.",
        "The old dog lay in the sun before the door and watched the world go by with sleepy eyes and now and then he lifted his head and wagged his tail at a passing friend.",
        "The market was crowded with people from the surrounding country who had come to buy and to sell and the air was full of the noise of bargaining and the cries of the merchants.",
        "The little boat rocked gently on the quiet water of the bay and the fisherman leaned back and watched the stars come out one by one in the deepening blue of the evening sky.",
        "The teacher gathered the children about her and read to them from a great book of stories and their eyes grew wide with wonder as they listened to the marvellous adventures.",
        "The mountains rose high and white against the clear blue sky and their snowy peaks caught the first light of the morning sun long before it reached the valleys far below.",
        "The old clockmaker sat at his bench and worked among the tiny wheels and springs with a patient and a steady hand and the ticking of a hundred clocks filled his little shop.",
        "The queen sat upon her throne in the great hall and received the ambassadors from distant lands who came bearing rich gifts and messages of friendship from their masters.",
        "The boy ran down the hill as fast as his legs would carry him with the wind in his hair and his heart full of joy at the bright and beautiful morning all around him.",
        "The travelers pitched their tents beside the river and lit their fires and cooked their supper and afterward they lay upon the ground and gazed up at the countless stars.",
        "The garden was quiet in the heat of the afternoon and the only movement was the slow drifting of a white butterfly among the roses and the gentle nodding of the tall flowers.",
        "The captain studied his charts by the light of the swinging lamp and plotted the course of the ship across the wide and trackless ocean toward the distant and unseen shore.",
        "The old bell in the church tower rang out across the fields and the villagers left their work and made their way slowly toward the little church for the evening service.",
        "The snow fell softly and silently all through the long winter night and in the morning the whole world was white and still and the children shouted with delight at the sight.",
        "The wise old man sat beneath the spreading branches of the great tree and the young people gathered around him to hear his counsel and to learn from his long experience.",
        "The ship came slowly into the harbour with her sails furled and her flags flying and the crowd upon the quay raised a great cheer to welcome the sailors home from the sea.",
        "The little girl held her mothers hand tightly as they walked together through the busy market and looked with wide eyes at all the wonderful things spread out upon the stalls."
    );

    private QuadgramTableGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Path target = args.length > 0 ? Path.of(args[0]) : OUTPUT;
        int[] counts = new int[QuadgramScorer.QUADGRAM_SPACE];
        long total = countInto(CORPUS, counts);
        Files.createDirectories(target.getParent());
        try (Writer writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            write(writer, counts, total);
        }
        System.out.println("wrote " + target.toAbsolutePath()
                + " total=" + total + " kept=" + countKept(counts));
    }

    static long countInto(String text, int[] counts) {
        byte[] raw = text.getBytes(StandardCharsets.UTF_8);
        byte[] letters = new byte[raw.length];
        int written = QuadgramScorer.normalize(raw, raw.length, letters);
        long total = 0;
        int last = written - QuadgramScorer.QUADGRAM_LENGTH;
        for (int i = 0; i <= last; i++) {
            int index = ((letters[i] * QuadgramScorer.ALPHABET_SIZE + letters[i + 1])
                    * QuadgramScorer.ALPHABET_SIZE + letters[i + 2])
                    * QuadgramScorer.ALPHABET_SIZE + letters[i + 3];
            counts[index]++;
            total++;
        }
        return total;
    }

    static void write(Writer writer, int[] counts, long total) throws IOException {
        writer.write("# quadgram counts pruned to count>=" + PRUNE_MIN_COUNT + "; total=" + total);
        writer.write('\n');
        for (int index = 0; index < counts.length; index++) {
            if (counts[index] >= PRUNE_MIN_COUNT) {
                writer.write(quadgramOf(index));
                writer.write('\t');
                writer.write(Integer.toString(counts[index]));
                writer.write('\n');
            }
        }
    }

    static String quadgramOf(int index) {
        char[] letters = new char[QuadgramScorer.QUADGRAM_LENGTH];
        int value = index;
        for (int position = QuadgramScorer.QUADGRAM_LENGTH - 1; position >= 0; position--) {
            letters[position] = (char) ('A' + value % QuadgramScorer.ALPHABET_SIZE);
            value /= QuadgramScorer.ALPHABET_SIZE;
        }
        return new String(letters);
    }

    private static int countKept(int[] counts) {
        int kept = 0;
        for (int count : counts) {
            if (count >= PRUNE_MIN_COUNT) {
                kept++;
            }
        }
        return kept;
    }
}
