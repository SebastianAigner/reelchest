import kotlin.random.Random

sealed class Sorting(val name: String) {
    abstract fun sort(videos: List<MediaLibraryEntry>): List<MediaLibraryEntry>
}

class LatestFirst : Sorting("Latest") {
    override fun sort(videos: List<MediaLibraryEntry>): List<MediaLibraryEntry> {
        return videos
    }

}

class LeastViewed : Sorting("Least Viewed") {
    override fun sort(videos: List<MediaLibraryEntry>): List<MediaLibraryEntry> {
        return videos.sortedBy { it.hits }
    }

}

class MostViewed : Sorting("Most Viewed") {
    override fun sort(videos: List<MediaLibraryEntry>): List<MediaLibraryEntry> {
        return videos.sortedByDescending { it.hits }
    }
}

class OldestFirst : Sorting("Oldest") {
    override fun sort(videos: List<MediaLibraryEntry>): List<MediaLibraryEntry> {
        return videos.reversed()
    }

}

class Shuffle(val seed: Int) : Sorting("Shuffled") {
    override fun sort(videos: List<MediaLibraryEntry>): List<MediaLibraryEntry> {
        val r = Random(seed)
        return videos.shuffled(r)
    }
}