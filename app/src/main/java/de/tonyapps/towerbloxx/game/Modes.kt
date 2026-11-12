package de.tonyapps.towerbloxx.game

/** Regeln der Spielmodi an einer Stelle. */
object Modes {
    const val TIME_LIMIT = 120f

    fun quick() = GameConfig(Mode.QUICK, lives = 3, cycleKinds = true)

    fun time() = GameConfig(Mode.TIME, lives = 0, timeLimit = TIME_LIMIT, cycleKinds = true)

    fun party() = GameConfig(Mode.PARTY, lives = 3, cycleKinds = true)

    /** Level 0..14: hoeher, schneller, windiger. */
    fun challenge(level: Int) = GameConfig(
        Mode.CHALLENGE,
        kind = Kind.entries[level % 4],
        lives = 3,
        targetFloors = challengeTarget(level),
        swingSpeed = 1f + level * 0.03f,
        wind = level * 0.035f,
        level = level,
    )

    fun challengeTarget(level: Int) = 8 + level * 2

    /** Sterne nach Anteil perfekter Bloecke. */
    fun challengeStars(stats: GameStats): Int {
        if (!stats.won) return 0
        val ratio = stats.perfects.toFloat() / stats.floors.coerceAtLeast(1)
        return when {
            ratio >= 0.6f -> 3
            ratio >= 0.3f -> 2
            else -> 1
        }
    }

    // ------------------------------------------------------------ Stadt

    fun cityCap(kind: Kind) = when (kind) {
        Kind.BLUE -> 12
        Kind.RED -> 20
        Kind.GREEN -> 30
        Kind.YELLOW -> 45
    }

    fun city(kind: Kind, plot: Int) = GameConfig(
        Mode.CITY, kind = kind, lives = 3, targetFloors = cityCap(kind), roofAtEnd = true,
        wind = kind.ordinal * 0.05f, plot = plot,
    )

    /** Welche Arten auf einem Grundstueck erlaubt sind, haengt von den Nachbarn ab. */
    fun allowed(kind: Kind, neighbours: Set<Kind>): Boolean = when (kind) {
        Kind.BLUE -> true
        Kind.RED -> Kind.BLUE in neighbours
        Kind.GREEN -> Kind.BLUE in neighbours && Kind.RED in neighbours
        Kind.YELLOW -> Kind.BLUE in neighbours && Kind.RED in neighbours && Kind.GREEN in neighbours
    }
}
