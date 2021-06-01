package com.kpstv.xclipper.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.*
import com.kpstv.xclipper.App
import com.kpstv.xclipper.data.localized.FBOptions
import com.kpstv.xclipper.data.localized.dao.ClipDataDao
import com.kpstv.xclipper.data.localized.dao.TagDao
import com.kpstv.xclipper.data.model.Clip
import com.kpstv.xclipper.data.model.Tag
import com.kpstv.xclipper.data.model.TagMap
import com.kpstv.xclipper.data.provider.ClipboardProvider
import com.kpstv.xclipper.data.provider.DBConnectionProvider
import com.kpstv.xclipper.data.provider.FirebaseProvider
import com.kpstv.xclipper.data.provider.PreferenceProvider
import com.kpstv.xclipper.data.repository.MainRepository
import com.kpstv.xclipper.extensions.Coroutines
import com.kpstv.xclipper.extensions.enumerations.FilterType
import com.kpstv.xclipper.extensions.keys
import com.kpstv.xclipper.extensions.listeners.RepositoryListener
import com.kpstv.xclipper.extensions.listeners.ResponseListener
import com.kpstv.xclipper.extensions.listeners.ResponseResult
import com.kpstv.xclipper.extensions.listeners.StatusListener
import com.kpstv.xclipper.extensions.utils.FirebaseUtils
import com.kpstv.xclipper.extensions.utils.Utils.Companion.loginToDatabase
import com.kpstv.xclipper.extensions.utils.Utils.Companion.logoutFromDatabase
import com.kpstv.xclipper.ui.helpers.TinyUrlApiHelper
import com.kpstv.xclipper.ui.viewmodels.managers.MainEditManager
import com.kpstv.xclipper.ui.viewmodels.managers.MainSearchManager
import com.kpstv.xclipper.ui.viewmodels.managers.MainStateManager
import com.zhuinden.livedatacombinetuplekt.combineTuple
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList

@HiltViewModel
class MainViewModel @Inject constructor(
    firebaseUtils: FirebaseUtils,
    private val mainRepository: MainRepository,
    private val tagRepository: TagDao,
    private val preferenceProvider: PreferenceProvider,
    private val firebaseProvider: FirebaseProvider,
    private val clipboardProvider: ClipboardProvider,
    private val dbConnectionProvider: DBConnectionProvider,
    val tinyUrlApiHelper: TinyUrlApiHelper,
    val editManager: MainEditManager,
    val searchManager: MainSearchManager,
    val stateManager: MainStateManager
) : ViewModel() {
    private val TAG = javaClass.simpleName

    private val _clipLiveData = MutableLiveData<List<Clip>>()

    private val _newClipLiveData = MutableLiveData<List<Clip>>()

    private val _tagMapLiveData = MutableLiveData<List<TagMap>>()

    private val _tagLiveData = MutableLiveData<List<Tag>>()

    val currentClip: LiveData<String>
        get() = clipboardProvider.getCurrentClip()

    private val mutableClipLiveData = MutableLiveData<List<Clip>>()

    val clipLiveData: LiveData<List<Clip>>
        get() = mutableClipLiveData

    val tagCountData: LiveData<List<TagMap>>
        get() = _tagMapLiveData

    val tagLiveData: LiveData<List<Tag>>
        get() = _tagLiveData

    fun postToRepository(clip: Clip) {
        viewModelScope.launch { mainRepository.updateRepository(clip) }
    }

    fun changeClipPin(clip: Clip?, boolean: Boolean) {
        viewModelScope.launch { mainRepository.updatePin(clip, boolean) }
    }

    fun checkForDuplicateClip(data: String, repositoryListener: RepositoryListener) {
        viewModelScope.launch {
            if (mainRepository.checkForDuplicate(data)) {
                repositoryListener.onDataExist()
            } else {
                repositoryListener.onDataError()
            }
        }
    }

    fun checkForDuplicateClip(data: String, id: Int, repositoryListener: RepositoryListener) {
        viewModelScope.launch {
            if (mainRepository.checkForDuplicate(data, id)) {
                repositoryListener.onDataExist()
            } else {
                repositoryListener.onDataError()
            }
        }
    }

    fun deleteFromRepository(clip: Clip) {
        viewModelScope.launch { mainRepository.deleteClip(clip) }
    }

    fun deleteMultipleFromRepository(clips: List<Clip>) {
        viewModelScope.launch { mainRepository.deleteMultiple(clips) }
    }

    fun postUpdateToRepository(oldClip: Clip, newClip: Clip) {
        viewModelScope.launch {
            mainRepository.updateClip(newClip, FilterType.Id)
            if (oldClip.data != newClip.data) {
                firebaseProvider.replaceData(oldClip, newClip)
            }
        }
    }

    fun makeASynchronizeRequest(statusListener: StatusListener) {
        viewModelScope.launch {
            if (mainRepository.syncDataFromRemote()) {
                statusListener.onComplete()
            } else {
                statusListener.onError()
            }
        }
    }

    fun postToTagRepository(tag: Tag) {
        Coroutines.io { tagRepository.insertTag(tag) }
    }

    fun deleteFromTagRepository(tag: Tag, statusListener: StatusListener) {
        viewModelScope.launch {
            if (mainRepository.checkForDependent(tag.name)) {
                statusListener.onError()
            } else {
                tagRepository.delete(tag)
                statusListener.onComplete()
            }
        }
    }

    fun updateDeviceConnection(options: FBOptions, responseListener: ResponseListener<Unit>) {
        viewModelScope.launch {
            dbConnectionProvider.saveOptionsToAll(options)
            val result = firebaseProvider.addDevice(App.DeviceID)
            when (result) {
                is ResponseResult.Complete -> {
                    loginToDatabase(
                        preferenceProvider = preferenceProvider,
                        dbConnectionProvider = dbConnectionProvider,
                        options = options
                    )
                    responseListener.onComplete(Unit)
                }
                is ResponseResult.Error -> {
                    dbConnectionProvider.detachDataFromAll()
                    responseListener.onError(result.error)
                }
            }
        }
    }

    fun removeDeviceConnection(context: Context, responseListener: ResponseListener<Unit>) {
        viewModelScope.launch {
            val result = firebaseProvider.removeDevice(App.DeviceID)
            when (result) {
                is ResponseResult.Complete -> {
                    logoutFromDatabase(
                        context = context,
                        preferenceProvider = preferenceProvider,
                        dbConnectionProvider = dbConnectionProvider
                    )
                    responseListener.onComplete(Unit)
                }
                is ResponseResult.Error -> {
                    logoutFromDatabase(
                        context = context,
                        preferenceProvider = preferenceProvider,
                        dbConnectionProvider = dbConnectionProvider
                    )
                    responseListener.onError(result.error)
                }
            }
        }
    }

    init {
        mainRepository.getAllLiveClip().observeForever { clips ->
            _clipLiveData.postValue(clips)

            val list = ArrayList<TagMap>()
            clips.forEach { clip ->
                clip.tags?.keys()?.forEach { tag ->
                    val find = list.find { it.name == tag }
                    if (find != null) {
                        find.count++
                    } else {
                        list.add(TagMap(tag, 1))
                    }
                }
            }
            _tagMapLiveData.value = list
        }

        tagRepository.getAllLiveData().observeForever {
            _tagLiveData.postValue(it)
        }

        /*combineTuple(
            _clipLiveData,
            searchManager.searchString,
            searchManager.tagFilters,
            searchManager.searchFilters,
        ).observeForever { (clipLiveData: List<Clip>?, searchString: String?, tagFilters: List<Tag>?, searchFilters: List<String>?) ->
            makeMySource(clipLiveData, searchFilters, tagFilters, searchString)
        }*/

        combineTuple(
            searchManager.searchString,
            searchManager.tagFilters,
            searchManager.searchFilters
        ).observeForever { (searchString: String?, tagFilters: List<Tag>?, searchFilters: List<String>?) ->
            val filter = ClipDataDao.createQuery(searchFilters, tagFilters, searchString)
            CoroutineScope(viewModelScope.coroutineContext + Dispatchers.IO).launch {
                val list = mainRepository.custom(filter)
                mutableClipLiveData.postValue(list)
            }
        }

        /** Methods optimized to invoke only once regardless of calling from multiple sites. */
        firebaseUtils.observeDatabaseInitialization()
        clipboardProvider.observeClipboardChange()

        _newClipLiveData.observeForever {
            Log.e("MainViewModel", "Size: ${it?.size}")
        }
    }

    private fun makeMySource(
        mainList: List<Clip>?,
        searchFilter: ArrayList<String>?,
        tagFilter: ArrayList<Tag>?,
        searchText: String?
    ) {
        if (mainList != null) {
            val list = ArrayList<Clip>(mainList)

            mainList.forEach { clip ->
                searchFilter?.forEach inner@{ filter ->
                    if (!clip.data.toLowerCase(Locale.getDefault()).contains(filter)) {
                        list.remove(clip)
                        return@inner
                    }
                }
                tagFilter?.forEach inner@{ tag ->
                    if (clip.tags?.keys().isNullOrEmpty() || clip.tags?.keys()
                            ?.contains(tag.name) == false
                    ) {
                        list.remove(clip)
                        return@inner
                    }
                }
            }

            if (!searchText.isNullOrBlank()) {
                mutableClipLiveData.postValue(
                    list.filter { clip ->
                        clip.data.toLowerCase(Locale.getDefault()).contains(searchText)
                    }
                )

            } else
                mutableClipLiveData.postValue(list.toList())
        }
    }
}