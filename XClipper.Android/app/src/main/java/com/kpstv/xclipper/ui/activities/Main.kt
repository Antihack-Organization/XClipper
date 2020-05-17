package com.kpstv.xclipper.ui.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.kpstv.license.Decrypt
import com.kpstv.xclipper.App.BLANK_STRING
import com.kpstv.xclipper.App.CLIP_DATA
import com.kpstv.xclipper.App.UNDO_DELETE_SPAN
import com.kpstv.xclipper.R
import com.kpstv.xclipper.data.localized.ToolbarState
import com.kpstv.xclipper.data.model.Clip
import com.kpstv.xclipper.extensions.Coroutines
import com.kpstv.xclipper.extensions.Utils.Companion.shareText
import com.kpstv.xclipper.extensions.cloneForAdapter
import com.kpstv.xclipper.ui.adapters.CIAdapter
import com.kpstv.xclipper.ui.helpers.MainEditHelper
import com.kpstv.xclipper.ui.viewmodels.MainViewModel
import com.kpstv.xclipper.ui.viewmodels.MainViewModelFactory
import kotlinx.android.synthetic.main.activity_main.*
import org.kodein.di.KodeinAware
import org.kodein.di.android.kodein
import org.kodein.di.generic.instance
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.schedule


class Main : AppCompatActivity(), KodeinAware {

    private val TAG = javaClass.simpleName

    override val kodein by kodein()
    private val viewModelFactory: MainViewModelFactory by instance()

    private val clipboardManager: ClipboardManager by lazy {
        getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    private lateinit var adapter: CIAdapter

    private val mainViewModel: MainViewModel by lazy {
        ViewModelProvider(this, viewModelFactory).get(MainViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setRecyclerView()

        bindUI()

        checkClipboardData()

        setToolbarCommonStuff()


        /*  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
              val intent = Intent(
                  Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                  Uri.parse("package:$packageName")
              )
              startActivityForResult(intent, 0)
          }

        */
    }


    private fun bindUI() = Coroutines.main {
        mainViewModel.clipLiveData.await().observeForever {
            adapter.submitList(ArrayList(it.cloneForAdapter().reversed()))
            Log.e(TAG, "LiveData changed()")
        }
        mainViewModel.stateManager.toolbarState.observe(this, Observer { state ->
            when (state) {
                ToolbarState.NormalViewState -> {
                    setNormalToolbar()
                    mainViewModel.stateManager.clearSelectedList()
                }

                ToolbarState.MultiSelectionState -> {
                    setSelectedToolbar()
                }

                else -> {
                    // TODO: When exhaustive
                }
            }
        })
    }

    private fun setRecyclerView() {
        adapter = CIAdapter(
            context = this,
            selectedClips = mainViewModel.stateManager.selectedNodes,
            multiselectionState = mainViewModel.stateManager.multiSelectionState,
            onClick = { model, pos ->
                if (mainViewModel.stateManager.isMultiSelectionStateActive())
                    mainViewModel.stateManager.addOrRemoveClipFromSelectedList(model)
                else
                    expandMenuLogic(model, pos)
            },
            onLongClick = { clip, _ ->
                adapter.list.forEach { it.toDisplay = false }
                adapter.notifyDataSetChanged()

                mainViewModel.stateManager.setToolbarState(ToolbarState.MultiSelectionState)
                mainViewModel.stateManager.addOrRemoveClipFromSelectedList(clip)
            }
        )

        adapter.setCopyClick { clip, _ ->
            clipboardManager.setPrimaryClip(ClipData.newPlainText(null, clip.data?.Decrypt()))
            Toast.makeText(this, getString(R.string.ctc), Toast.LENGTH_SHORT).show()
        }

        adapter.setMenuItemClick { clip, i, menuType ->
            when (menuType) {
                CIAdapter.MENU_TYPE.Edit -> {
                    MainEditHelper(
                        this, mainViewModel
                    ).show(clip)
                }
                CIAdapter.MENU_TYPE.Delete -> {
                    performUndoDelete(clip, i)
                }
                CIAdapter.MENU_TYPE.Share -> {
                    shareText(this, clip)
                }
            }
        }

        ci_recyclerView.layoutManager = LinearLayoutManager(this)
        ci_recyclerView.adapter = adapter
        ci_recyclerView.setHasFixedSize(true)

    }

    /**
     * This will set the clicks of item on Toolbar Menu.
     */
    private fun setToolbarCommonStuff() {
        setNormalToolbar()
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_selectAll -> {
                    mainViewModel.stateManager.addAllToSelectedList(adapter.list)
                }
                R.id.action_selectNone -> {
                    mainViewModel.stateManager.clearSelectedList()
                }
                R.id.action_deleteAll -> {
                    deleteAllWithUndo()
                }
            }
            true
        }

        mainViewModel.stateManager.selectedNodes.observe(this, Observer {
            if (it.size >= 0)
                toolbar.subtitle = "${it.size} ${getString(R.string.selected)}"
            else
                toolbar.subtitle = BLANK_STRING
        })
    }

    private fun deleteAllWithUndo() {
        val totalItems = ArrayList(adapter.list)
        val itemsToRemove = mainViewModel.stateManager.selectedNodes.value!!
        val size = itemsToRemove.size

        val task = Timer("UndoDelete", false).schedule(UNDO_DELETE_SPAN) {
            mainViewModel.deleteMultipleFromRepository(itemsToRemove)
            mainViewModel.stateManager.setToolbarState(ToolbarState.NormalViewState)
        }

        adapter.list.removeAll(itemsToRemove)
        adapter.notifyDataSetChanged()

        Snackbar.make(
            ci_recyclerView,
            "$size ${getString(R.string.item_delete)}",
            Snackbar.LENGTH_SHORT
        ).setAction(getString(R.string.undo)) {
            task.cancel()
            adapter.list = totalItems
            adapter.notifyDataSetChanged()
        }.show()
    }

    /**
     * Call this function when ToolbarMultiSelection state is enabled.
     */
    private fun setSelectedToolbar() {
        toolbar.menu.clear()
        toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.colorSelected))
        toolbar.inflateMenu(R.menu.selected_menu)
        toolbar.navigationIcon = getDrawable(R.drawable.ic_close)
        toolbar.setNavigationOnClickListener {
            mainViewModel.stateManager.setToolbarState(ToolbarState.NormalViewState)
        }
    }

    /**
     * Call this function when ToolbarNormalState state is enabled.
     */
    private fun setNormalToolbar() {
        toolbar.navigationIcon = null
        toolbar.setNavigationOnClickListener(null)
        toolbar.menu.clear()
        toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary))
        toolbar.inflateMenu(R.menu.normal_menu)
    }


    /**
     * This function will perform undo delete whenever item has been deleted from
     * expanded menu.
     */
    private fun performUndoDelete(clip: Clip, i: Int) {
        val task = Timer("UndoDelete", false).schedule(UNDO_DELETE_SPAN) {
            mainViewModel.deleteFromRepository(clip)
        }

        val list = adapter.list.removeAt(i)
        adapter.notifyItemRemoved(i)

        Snackbar.make(
            ci_recyclerView,
            "1 ${getString(R.string.item_delete)}",
            Snackbar.LENGTH_SHORT
        )
            .setAction(getString(R.string.undo)) {
                task.cancel()
                adapter.list.add(i, list)
                adapter.notifyItemInserted(i)
            }.show()
    }


    /**
     * So I found out that sometimes in Android 10, clipboard still not get captured using my
     * accessibility service hack. To fix this whenever app is launched or come back from
     * background it will check & update the database with the clipboard.
     */
    private fun checkClipboardData() {
        val data = clipboardManager.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
        if (data != null && CLIP_DATA != data) {
            CLIP_DATA = data

            mainViewModel.postToRepository(data)

            Log.e(TAG, "Pushed: $data")
        }
    }


    /**
     * This function will handle the expanded menu logic
     */
    private fun expandMenuLogic(model: Clip, pos: Int) {
        for ((i, e) in adapter.list.withIndex()) {
            if (i != pos && e.toDisplay) {
                e.toDisplay = false
                adapter.notifyItemChanged(i)
            }
        }
        model.toDisplay = !model.toDisplay
        adapter.notifyItemChanged(pos)
    }

    override fun onBackPressed() {
        if (mainViewModel.stateManager.isMultiSelectionStateActive())
            mainViewModel.stateManager.setToolbarState(ToolbarState.NormalViewState)
        else
            super.onBackPressed()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        checkClipboardData()
    }
}
