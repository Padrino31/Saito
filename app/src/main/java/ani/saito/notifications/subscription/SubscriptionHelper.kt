package ani.saito.notifications.subscription

import ani.saito.R
import ani.saito.currContext
import ani.saito.media.Media
import ani.saito.media.Selected
import ani.saito.media.manga.MangaNameAdapter
import ani.saito.parsers.AnimeParser
import ani.saito.parsers.AnimeSources
import ani.saito.parsers.Episode
import ani.saito.parsers.MangaChapter
import ani.saito.parsers.MangaParser
import ani.saito.parsers.MangaSources
import ani.saito.settings.saving.PrefManager
import ani.saito.settings.saving.PrefName
import ani.saito.tryWithSuspend
import ani.saito.util.Logger
import kotlinx.coroutines.withTimeoutOrNull

class SubscriptionHelper {
    companion object {
        private fun loadSelected(
            mediaId: Int
        ): Selected {
            val data =
                PrefManager.getNullableCustomVal("${mediaId}-select", null, Selected::class.java)
                    ?: Selected().let {
                        it.sourceIndex = 0
                        it.preferDub = PrefManager.getVal(PrefName.SettingsPreferDub)
                        it
                    }
            return data
        }

        private fun saveSelected( mediaId: Int, data: Selected) {
            PrefManager.setCustomVal("${mediaId}-select", data)
        }

        fun getAnimeParser(id: Int): AnimeParser {
            val sources = AnimeSources
            Logger.log("getAnimeParser size: ${sources.list.size}")
            val selected = loadSelected(id)
            val parser = sources[selected.sourceIndex]
            parser.selectDub = selected.preferDub
            return parser
        }

        suspend fun getEpisode(
            parser: AnimeParser,
            id: Int
        ): Episode? {

            val selected = loadSelected(id)
            val ep = withTimeoutOrNull(10 * 1000) {
                tryWithSuspend {
                    val show = parser.loadSavedShowResponse(id) ?: throw Exception(
                        currContext()?.getString(
                            R.string.failed_to_load_data,
                            id
                        )
                    )
                    show.sAnime?.let {
                        parser.getLatestEpisode(
                            show.link, show.extra,
                            it, selected.latest
                        )
                    }
                }
            }

            return ep?.apply {
                selected.latest = number.toFloat()
                saveSelected(id, selected)
            }
        }

        fun getMangaParser(id: Int): MangaParser {
            val sources = MangaSources
            val selected = loadSelected(id)
            return sources[selected.sourceIndex]
        }

        suspend fun getChapter(
            parser: MangaParser,
            id: Int
        ): MangaChapter? {
            val selected = loadSelected(id)
            val chp = withTimeoutOrNull(10 * 1000) {
                tryWithSuspend {
                    val show = parser.loadSavedShowResponse(id) ?: throw Exception(
                        currContext()?.getString(
                            R.string.failed_to_load_data,
                            id
                        )
                    )
                    show.sManga?.let {
                        parser.getLatestChapter(
                            show.link, show.extra,
                            it, selected.latest
                        )
                    }
                }
            }

            return chp?.apply {
                selected.latest = MangaNameAdapter.findChapterNumber(number) ?: 0f
                saveSelected(id, selected)
            }
        }

        data class SubscribeMedia(
            val isAnime: Boolean,
            val isAdult: Boolean,
            val id: Int,
            val name: String,
            val image: String?
        ) : java.io.Serializable

        private const val SUBSCRIPTIONS = "subscriptions"

        @Suppress("UNCHECKED_CAST")
        fun getSubscriptions(): Map<Int, SubscribeMedia> =
            (PrefManager.getNullableCustomVal(
                SUBSCRIPTIONS,
                null,
                Map::class.java
            ) as? Map<Int, SubscribeMedia>)
                ?: mapOf<Int, SubscribeMedia>().also { PrefManager.setCustomVal(SUBSCRIPTIONS, it) }

        @Suppress("UNCHECKED_CAST")
        fun saveSubscription(media: Media, subscribed: Boolean) {
            val data = PrefManager.getNullableCustomVal(
                SUBSCRIPTIONS,
                null,
                Map::class.java
            ) as? MutableMap<Int, SubscribeMedia>
                ?: mutableMapOf()
            if (subscribed) {
                if (!data.containsKey(media.id)) {
                    val new = SubscribeMedia(
                        media.anime != null,
                        media.isAdult,
                        media.id,
                        media.userPreferredName,
                        media.cover
                    )
                    data[media.id] = new
                }
            } else {
                data.remove(media.id)
            }
            PrefManager.setCustomVal(SUBSCRIPTIONS, data)
        }
    }
}