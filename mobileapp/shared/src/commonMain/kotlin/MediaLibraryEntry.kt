import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import kotlinx.serialization.Serializable
import org.mongodb.kbson.ObjectId

@Serializable
data class MediaLibraryEntry(
    val name: String,
    val id: String,
    val markedForDeletion: Boolean,
    val hits: Int
) {
    fun toRealmObject(): MediaLibraryRealmEntry {
        val mle = this
        return MediaLibraryRealmEntry().apply {

            id = mle.id
            name = mle.name
            markedForDeletion = mle.markedForDeletion
            hits = mle.hits
        }
    }
}

class MediaLibraryRealmEntry : RealmObject {
    @PrimaryKey    
    var id: String = ""
    var name: String = ""
    var markedForDeletion: Boolean = false
    var hits: Int = 0

    fun toMediaLibraryEntry(): MediaLibraryEntry {
        return MediaLibraryEntry(
            name = name,
            id = id,
            markedForDeletion = markedForDeletion,
            hits = hits
        )
    }
}