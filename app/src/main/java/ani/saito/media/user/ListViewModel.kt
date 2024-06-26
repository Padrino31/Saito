package ani.saito.media.user

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import ani.saito.connections.anilist.Anilist
import ani.saito.media.Media
import ani.saito.settings.saving.PrefManager
import ani.saito.settings.saving.PrefName
import ani.saito.tryWithSuspend

class ListViewModel : ViewModel() {
    var grid = MutableLiveData(PrefManager.getVal<Boolean>(PrefName.ListGrid))

    private val lists = MutableLiveData<MutableMap<String, ArrayList<Media>>>()
    private val unfilteredLists = MutableLiveData<MutableMap<String, ArrayList<Media>>>()
    fun getLists(): LiveData<MutableMap<String, ArrayList<Media>>> = lists
    suspend fun loadLists(anime: Boolean, userId: Int, sortOrder: String? = null) {
        tryWithSuspend {
            val res = Anilist.query.getMediaLists(anime, userId, sortOrder)
            lists.postValue(res)
            unfilteredLists.postValue(res)
        }
    }

    fun filterLists(genre: String) {
        if (genre == "All") {
            lists.postValue(unfilteredLists.value)
            return
        }
        val currentLists = unfilteredLists.value ?: return
        val filteredLists = currentLists.mapValues { entry ->
            entry.value.filter { media ->
                genre in media.genres
            } as ArrayList<Media>
        }.toMutableMap()

        lists.postValue(filteredLists)
    }

}