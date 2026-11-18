package com.example.campusexplorer.data

import java.text.Collator

enum class LearnSort(val label: String) {
    AZ("Name A–Z"), ZA("Name Z–A")
}

enum class LearnRegion(val label: String) {
    ALL("All regions"), CALIFORNIA("California"), CAMPUS("Around campus (5 km)")
}

enum class LearnGroup(val label: String, val iconicTaxon: String?) {
    ALL("All groups", null), BIRDS("Birds", "Aves"), PLANTS("Plants", "Plantae"),
    MAMMALS("Mammals", "Mammalia"), INSECTS("Insects", "Insecta"), REPTILES("Reptiles", "Reptilia"),
    FUNGI("Fungi", "Fungi"), AMPHIBIANS("Amphibians", "Amphibia")
}

data class LearnPhoto(
    val id: Long,
    val thumbnailUrl: String,
    val imageUrl: String,
    val attribution: String,
    val license: String,
) {
    val pageUrl: String get() = "https://www.inaturalist.org/photos/$id"
}

data class LearnSpecies(
    val id: Long,
    val name: String,
    val scientificName: String,
    val groupName: String,
    val rank: String,
    val observationsCount: Long,
    val photo: LearnPhoto?,
    val descriptionHtml: String?,
    val wikipediaUrl: String?,
    val classification: List<String>,
) {
    val pageUrl: String get() = "https://www.inaturalist.org/taxa/$id"
}

// A searchable iNaturalist place; displayName includes its geographic context.
data class LearnPlace(val id: Long, val displayName: String)

data class LearnPage(val species: List<LearnSpecies>, val total: Long, val hasMore: Boolean)

// Order the whole set currently loaded, with a stable tie-breaker for matching names.
fun sortLearnSpecies(species: List<LearnSpecies>, sort: LearnSort): List<LearnSpecies> {
    val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
    val ordered = species.distinctBy { it.id }.sortedWith(Comparator { a, b ->
        val names = collator.compare(a.name, b.name)
        if (names == 0) a.id.compareTo(b.id) else names
    })
    return if (sort == LearnSort.ZA) ordered.reversed() else ordered
}