package com.example.campusexplorer.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

enum class LearnSort(val label: String) {
    AZ("Name A–Z"),
    ZA("Name Z–A")
}

enum class LearnRegion(val label: String) {
    ALL("All regions"),
    CALIFORNIA("California"),
    CAMPUS("Around campus")
}

enum class LearnGroup(val label: String) {
    ALL("All groups"),
    BIRDS("Birds"),
    PLANTS("Plants"),
    MAMMALS("Mammals"),
    INSECTS("Insects"),
    REPTILES("Reptiles")
}

data class LearnSpecies(
    val id: String,
    val name: String,
    val scientificName: String,
    val illustration: String,
    val group: LearnGroup,
    val sampleRegions: Set<LearnRegion>,
)

// Demonstration entries and region tags, not verified campus observations.
object LearnSampleData {
    private val local = setOf(
        LearnRegion.CALIFORNIA,
        LearnRegion.CAMPUS
    )

    private val california = setOf(LearnRegion.CALIFORNIA)

    val species = listOf(
        LearnSpecies(
            "acorn",
            "Acorn Woodpecker",
            "Melanerpes formicivorus",
            "🐦",
            LearnGroup.BIRDS,
            california
        ),
        LearnSpecies(
            "crow",
            "American Crow",
            "Corvus brachyrhynchos",
            "🐦",
            LearnGroup.BIRDS,
            local
        ),
        LearnSpecies(
            "hummingbird",
            "Anna’s Hummingbird",
            "Calypte anna",
            "🐦",
            LearnGroup.BIRDS,
            local
        ),
        LearnSpecies(
            "poppy",
            "California Poppy",
            "Eschscholzia californica",
            "🌼",
            LearnGroup.PLANTS,
            local
        ),
        LearnSpecies(
            "coyote",
            "Coyote",
            "Canis latrans",
            "🐺",
            LearnGroup.MAMMALS,
            local
        ),
        LearnSpecies(
            "panda",
            "Giant Panda",
            "Ailuropoda melanoleuca",
            "🐼",
            LearnGroup.MAMMALS,
            emptySet()
        ),
        LearnSpecies(
            "monarch",
            "Monarch",
            "Danaus plexippus",
            "🦋",
            LearnGroup.INSECTS,
            california
        ),
        LearnSpecies(
            "lizard",
            "Western Fence Lizard",
            "Sceloporus occidentalis",
            "🦎",
            LearnGroup.REPTILES,
            local
        ),
    )

    // Save the random choice so it stays the same throughout the day.
    fun dailySpecies(context: Context): LearnSpecies {
        val preferences = context.getSharedPreferences(
            "LearnDailySpecies",
            Context.MODE_PRIVATE
        )

        val today = SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.US
        ).format(Date())

        val saved = species.firstOrNull {
            it.id == preferences.getString("speciesId", null)
        }

        if (preferences.getString("date", null) == today && saved != null) {
            return saved
        }

        val chosen = species[Random.nextInt(species.size)]

        preferences.edit()
            .putString("date", today)
            .putString("speciesId", chosen.id)
            .apply()

        return chosen
    }
}