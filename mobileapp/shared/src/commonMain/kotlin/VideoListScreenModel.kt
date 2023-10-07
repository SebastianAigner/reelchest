import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.coroutineScope
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.query
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mobilenativefoundation.store.store5.*
import org.mobilenativefoundation.store.store5.impl.extensions.fresh
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class VideoListScreenModel() : StateScreenModel<VideoListScreenModel.VideoListState>(
    VideoListState(
        emptyList()
    )
) {
    data class VideoListState(
        val videos: List<MediaLibraryEntry>,
        val query: String = "",
        val sorting: Sorting = LatestFirst(),
        val loadingState: String = ""
    ) {
        val filteredVideos: List<MediaLibraryEntry> =
            videos.filter { it.name.contains(query, ignoreCase = true) }.run { sorting.sort(this) }
    }

    val mySettings = Settings()
    val myStore: Store<Any, List<MediaLibraryEntry>> = StoreBuilder.from(
        Fetcher.of { fetchMediaLibraryEntries() },
        SourceOfTruth.Companion.of(
            reader = {
                realm.query<MediaLibraryRealmEntry>().find().asFlow().map {
                    it.list.map { it.toMediaLibraryEntry() }
                }
            },
            writer = { key, local ->
                val all = realm.query<MediaLibraryRealmEntry>().find()

                val realmObjs = local.map { it.toRealmObject() }
                println("Storing $realmObjs")
                realm.write {
                    for (obj in realmObjs) {
                        copyToRealm(obj, UpdatePolicy.ALL)
                    }
                }
            },
            delete = {
                realm.write {
                    val all = realm.query<MediaLibraryRealmEntry>().find()
                    for (res in all) {
                        delete(res)
                    }
                }
            },
            deleteAll = {
                realm.write {
                    val all = realm.query<MediaLibraryRealmEntry>().find()
                    for (res in all) {
                        delete(res)
                    }
                }
            }
        )
    ).build()

    private suspend fun fetchMediaLibraryEntries(): List<MediaLibraryEntry> {
        println("Fetching new!")
        delay(5.seconds)
        val videos =
            globalHttpClient
                .get(Settings().get<String>("endpoint")!! + "/api/mediaLibrary")
                .body<List<MediaLibraryEntry>>()
        return videos
    }

    fun filter(query: String) {
        mutableState.update {
            it.copy(
                query = query
            )
        }
    }

    val config = RealmConfiguration.create(schema = setOf(MediaLibraryRealmEntry::class))
    val realm: Realm = Realm.open(config)

    var dr = 0

    init {
        coroutineScope.launch {
            myStore
                .stream(StoreReadRequest.cached(Unit, true))
                .collect {
                    when (it) {
                        is StoreReadResponse.Data -> {
                            println(it.value)
                            mutableState.update {
                                it.copy(loadingState = "Data received. ${dr++}")
                            }
                            mutableState.update { state ->
                                state.copy(videos = it.value)
                            }
                        }

                        is StoreReadResponse.Error.Exception -> {
                            mutableState.update { state ->
                                state.copy(loadingState = "EX: ${it.error}")
                            }
                        }

                        is StoreReadResponse.Error.Message -> {
                            mutableState.update { state ->
                                state.copy(loadingState = "EX: ${it.message}")
                            }
                        }

                        is StoreReadResponse.Loading -> {
                            mutableState.update {
                                it.copy(loadingState = "Loading...")
                            }
                        }

                        is StoreReadResponse.NoNewData -> {
                            mutableState.update {
                                it.copy(loadingState = "No new data.")
                            }
                        }
                    }
                }
        }
    }

    private fun showIndicator(text: String) {
        mutableState.update { state ->
            state.copy(videos = listOf(MediaLibraryEntry(text, "", false, 0)))
        }
    }

    fun refresh() {
        coroutineScope.launch {
            myStore.fresh(Unit)
        }
    }

    fun setSorting(s: Sorting) {
        mutableState.update {
            it.copy(sorting = s)
        }
    }

    fun setSortingByName(s: String) {
        setSorting(getAvailableSortings().first { it.name == s })
    }

    fun getAvailableSortings(): List<Sorting> {
        return listOf(
            LatestFirst(),
            OldestFirst(),
            MostViewed(),
            LeastViewed(),
            Shuffle(Random.nextInt())
        )
    }

    fun getAvailableSortingNames(): List<String> {
        return getAvailableSortings().map {
            it.name
        }
    }
}