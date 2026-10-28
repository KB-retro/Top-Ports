package com.topports.core

/** Juego del canal con las apps instaladas que le corresponden. */
data class InstalledGame(val game: Game, val apps: List<InstalledApp>)

/** Relaciona las apps instaladas con los juegos del catálogo. */
object InstalledFinder {

    fun find(installed: List<InstalledApp>, games: List<Game>): List<InstalledGame> {
        val byGame = LinkedHashMap<Long, MutableList<InstalledApp>>()
        for (app in installed) {
            val game = app.record?.let { tracked(it, games) } ?: untracked(app, games) ?: continue
            byGame.getOrPut(game.id) { mutableListOf() } += app
        }
        return byGame.mapNotNull { (id, apps) -> games.firstOrNull { it.id == id }?.let { InstalledGame(it, apps) } }
            .sortedBy { it.game.title.lowercase() }
    }

    /** Instalada desde Top Ports: el juego más reciente con ese archivo (o el original). */
    private fun tracked(record: InstallRecord, games: List<Game>): Game? {
        val key = UpdateFinder.normalize(record.fileName)
        return games.filter { g -> g.packages.any { UpdateFinder.normalize(it.name) == key } }.maxByOrNull { it.id }
            ?: games.firstOrNull { it.id == record.gameId }
    }

    /** Instalada por otro medio: el nombre de la app coincide con el juego o con su APK. */
    private fun untracked(app: InstalledApp, games: List<Game>): Game? {
        val label = UpdateFinder.normalizeLoose(app.label)
        if (label.length < 3) return null
        return games.filter { g ->
            UpdateFinder.normalizeLoose(g.title) == label ||
                g.packages.any { it.kind == PackageKind.APK && UpdateFinder.normalizeLoose(it.name) == label }
        }.maxByOrNull { it.id }
    }
}
