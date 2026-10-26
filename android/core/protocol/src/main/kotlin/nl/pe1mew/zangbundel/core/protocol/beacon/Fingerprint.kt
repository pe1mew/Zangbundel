// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.beacon

import nl.pe1mew.zangbundel.core.model.GatheringId

/**
 * The gathering fingerprint (ble-protocol 6.6, FR-GATH-18): two Dutch words from the first two bytes of
 * `gid`, the same on every phone whatever its language, so a leader can say them aloud. Generated from
 * the lists in ble-protocol appendix A; change both together.
 */
object Fingerprint {
    fun of(gid: GatheringId): String = FIRST[gid.bytes[0].toInt() and 0xFF] + "-" + SECOND[gid.bytes[1].toInt() and 0xFF]

    /** Animals and food. */
    val FIRST: List<String> = listOf(
        "aap", "arend", "baars", "bever", "bij", "bizon", "buffel", "cavia", "das", "dolfijn", "duif", "eend", "egel",
        "eland", "ezel", "fazant", "flamingo", "forel", "fret", "gans", "gazelle", "geit", "gekko", "giraf", "gorilla",
        "haai", "haan", "haas", "hamster", "havik", "hert", "hommel", "hond", "hyena", "ibis", "jaguar", "kalf", "kameel",
        "kanarie", "kat", "kever", "kikker", "kip", "koala", "koe", "konijn", "kraai", "krab", "kreeft", "krekel", "kwal",
        "lam", "lama", "leeuw", "libel", "lijster", "marmot", "meeuw", "mees", "merel", "mier", "mol", "mus", "muis",
        "oehoe", "okapi", "olifant", "orka", "otter", "panda", "panter", "pauw", "pelikaan", "pinguin", "pony", "poema",
        "ree", "reiger", "rog", "rups", "schaap", "slak", "specht", "spin", "spreeuw", "steur", "stier", "tapir", "tijger",
        "tonijn", "uil", "valk", "varken", "veulen", "vink", "vis", "vlinder", "vos", "walrus", "walvis", "wezel", "wolf",
        "worm", "wouw", "zalm", "zebra", "zeehond", "zwaan", "zwaluw", "alpaca", "adder", "bunzing", "gier", "gnoe",
        "kievit", "koekoek", "kolibrie", "lynx", "mammoet", "narwal", "ooievaar", "pad", "papegaai", "rendier", "tor",
        "wombat", "yak", "zeekoe", "hagedis", "inktvis", "abrikoos", "amandel", "ananas", "appel", "avocado", "banaan",
        "bes", "boon", "bosbes", "braam", "brood", "citroen", "dadel", "druif", "erwt", "framboos", "gember", "honing",
        "kaas", "kers", "kiwi", "kokos", "koek", "krent", "limoen", "mais", "mango", "meloen", "noot", "olijf", "peer",
        "perzik", "pompoen", "radijs", "rozijn", "sla", "soep", "taart", "tomaat", "vijg", "wafel", "aardbei", "broccoli",
        "kool", "prei", "paprika", "spinazie", "suiker", "thee", "cacao", "kaneel", "stroop", "drop", "snoep", "toffee",
        "karamel", "chocola", "biscuit", "beschuit", "muesli", "pasta", "rijst", "pizza", "friet", "saus", "jam", "melk",
        "sap", "limonade", "popcorn", "pinda", "walnoot", "kastanje", "pudding", "yoghurt", "bagel", "taco", "wortel",
        "bonbon", "gebak", "koekje", "ijsje", "lolly", "muffin", "oliebol", "pistache", "rabarber", "spruit", "tuinboon",
        "venkel", "dille", "munt", "salie", "tijm", "laurier", "knoflook", "kerrie", "mosterd", "azijn", "bok", "buizerd",
        "condor", "dodo", "ekster", "emoe", "fuut", "gaai", "garnaal", "hinde", "karper", "kluut", "lepelaar", "meerkat",
        "nandoe", "ocelot", "oester", "paling", "poedel", "puma", "raaf", "sardine", "snoek", "tarbot", "wisent", "mossel",
        "sprot",
    )

    /** Nature and things. */
    val SECOND: List<String> = listOf(
        "vuur", "maan", "ster", "zon", "wolk", "regen", "sneeuw", "wind", "storm", "mist", "ijs", "dauw", "bliksem",
        "donder", "hagel", "berg", "dal", "rivier", "beek", "meer", "zee", "golf", "strand", "duin", "eiland", "bos",
        "boom", "blad", "tak", "bloem", "gras", "mos", "varen", "riet", "eik", "den", "spar", "berk", "wilg", "linde",
        "esdoorn", "hazel", "roos", "tulp", "lelie", "madelief", "klaver", "distel", "heide", "kei", "steen", "rots",
        "zand", "klei", "kristal", "parel", "goud", "zilver", "koper", "ijzer", "tent", "kamp", "hut", "vlot", "boot",
        "kano", "zeil", "anker", "roer", "mast", "kompas", "kaart", "lamp", "lantaarn", "kaars", "fakkel", "vlam", "vonk",
        "rook", "hout", "touw", "knoop", "fluit", "trom", "gitaar", "viool", "harp", "bel", "hoorn", "banjo", "lied",
        "toon", "maat", "ritme", "dans", "kring", "sjaal", "laars", "jas", "hoed", "pet", "want", "sok", "deken", "kussen",
        "mand", "tas", "emmer", "ketel", "pan", "beker", "kom", "lepel", "vork", "bord", "fles", "kruik", "sleutel", "slot",
        "deur", "raam", "poort", "brug", "weg", "spoor", "trein", "fiets", "kar", "wagen", "ballon", "vlieger", "tol",
        "knikker", "pop", "puzzel", "boek", "pen", "potlood", "krijt", "verf", "kwast", "papier", "schelp", "veer", "nest",
        "web", "korf", "molen", "toren", "kasteel", "sluis", "dijk", "haven", "planeet", "komeet", "raket", "avond",
        "nacht", "ochtend", "middag", "lente", "zomer", "herfst", "winter", "feest", "spel", "lach", "droom", "wens",
        "hart", "kroon", "schild", "pijl", "boog", "vlag", "wimpel", "kaap", "baai", "grot", "bron", "poel", "plas",
        "vijver", "moeras", "akker", "weide", "veld", "heuvel", "top", "klif", "rif", "ijsberg", "gletsjer", "vulkaan",
        "woestijn", "oase", "prairie", "jungle", "savanne", "akkoord", "atlas", "bank", "beitel", "bezem", "borstel", "bus",
        "camera", "cirkel", "dak", "dobber", "draak", "duikbril", "fluitje", "gong", "hamer", "hangmat", "helm", "hek",
        "iglo", "jacht", "kabel", "ketting", "klok", "koffer", "kokkel", "kraan", "kruimel", "lasso", "ladder", "lint",
        "lus", "magneet", "mantel", "matras", "muziek", "orgel", "paleis", "pijler", "pluim", "radar", "riem", "robot",
        "rugzak", "schaats", "schep", "schip", "schoen", "schommel", "slee", "spiegel", "stoel", "tafel", "tandem", "tegel",
        "telefoon", "tractor",
    )
}
