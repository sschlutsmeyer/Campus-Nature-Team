package com.example.campusexplorer.data

// A user's identification is a suggestion, not an identification verified by iNaturalist.
enum class IdentificationConfidence(val label: String) {
    NOT_CERTAIN("Not certain"),
    SOMEWHAT_CERTAIN("Somewhat certain"),
    CERTAIN("Certain"),
}

data class PostIdentification(
    val taxonId: Long,
    val commonName: String,
    val scientificName: String,
    val category: String,
    val confidence: IdentificationConfidence = IdentificationConfidence.NOT_CERTAIN,
) {
    internal fun toFields(): Map<String, Any> {
        require(taxonId > 0) { "Choose a species from the search results." }
        require(commonName.isNotBlank() && commonName.length <= 200)
        require(scientificName.isNotBlank() && scientificName.length <= 200)
        require(category in listOf("Plants", "Animals", "Fungi", "Other"))
        return mapOf(
            "taxonId" to taxonId,
            "commonName" to commonName,
            "scientificName" to scientificName,
            "confidence" to confidence.name,
        )
    }

    companion object {
        fun fromSpecies(species: LearnSpecies) = PostIdentification(
            taxonId = species.id,
            commonName = species.name,
            scientificName = species.scientificName,
            category = speciesPostCategory(species),
        )

        // Missing or invalid metadata on older posts must not prevent the feed from loading.
        internal fun fromFields(value: Any?, category: String): PostIdentification? {
            val fields = value as? Map<*, *> ?: return null
            val id = (fields["taxonId"] as? Number)?.toLong() ?: return null
            val name = fields["commonName"] as? String ?: return null
            val scientific = fields["scientificName"] as? String ?: return null
            val confidence = IdentificationConfidence.entries.firstOrNull {
                it.name == fields["confidence"]
            } ?: return null
            if (id <= 0 || name.isBlank() || name.length > 200 || scientific.isBlank() ||
                scientific.length > 200 || category !in listOf("Plants", "Animals", "Fungi", "Other")) return null
            return PostIdentification(id, name, scientific, category, confidence)
        }
    }
}

// Match the feed's existing broad category names, including all the animal iconic taxa.
internal fun speciesPostCategory(species: LearnSpecies): String {
    val kingdom = species.classification.firstOrNull { it.startsWith("kingdom:") }
        ?.substringAfter(":")?.trim()
    return when {
        kingdom == "Plantae" || species.groupName == "Plantae" -> "Plants"
        kingdom == "Fungi" || species.groupName == "Fungi" -> "Fungi"
        kingdom == "Animalia" || species.groupName in setOf(
            "Animalia", "Aves", "Mammalia", "Reptilia", "Amphibia", "Actinopterygii",
            "Mollusca", "Arachnida", "Insecta",
        ) -> "Animals"
        else -> "Other"
    }
}
