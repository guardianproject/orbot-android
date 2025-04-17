package org.torproject.android.ui.v3onionservice.clientauth

import android.app.Activity.RESULT_OK
import android.content.ContentResolver
import android.content.Intent
import android.database.ContentObserver
import android.database.Cursor
import android.os.Bundle
import android.os.Handler
import android.provider.BaseColumns
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.Toast

import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment

import org.torproject.android.R
import org.torproject.android.core.DiskUtils
import org.torproject.android.service.db.V3ClientAuthColumns
import org.torproject.android.ui.v3onionservice.V3BackupUtils

class ClientAuthFragment : Fragment() {
    private lateinit var resolver: ContentResolver
    private lateinit var adapter: ClientAuthListAdapter
    private var authListView: ListView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_client_auth, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as AppCompatActivity
        activity.supportActionBar?.apply {
            title = getString(R.string.v3_client_auth_activity_title)
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }

        requireActivity().findViewById<View>(R.id.bottom_navigation)?.visibility = View.GONE

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.v3_client_auth_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.menu_import_auth_priv -> {
                        val readFileIntent = DiskUtils.createReadFileIntent(CLIENT_AUTH_SAF_MIME_TYPE)
                        startActivityForResult(readFileIntent, REQUEST_CODE_READ_ZIP_BACKUP)
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner)

        resolver = requireContext().contentResolver

        val cursor = resolver.query(
            ClientAuthContentProvider.CONTENT_URI,
            ClientAuthContentProvider.PROJECTION,
            null, null, null
        )

        adapter = ClientAuthListAdapter(requireContext(), cursor)

        resolver.registerContentObserver(
            ClientAuthContentProvider.CONTENT_URI,
            true,
            V3ClientAuthContentObserver(Handler())
        )

        view.findViewById<View>(R.id.fab).setOnClickListener {
            ClientAuthCreateDialogFragment()
                .show(parentFragmentManager, ClientAuthCreateDialogFragment::class.simpleName)
        }

        authListView = view.findViewById<ListView>(R.id.auth_hash_list).apply {
            adapter = this@ClientAuthFragment.adapter
            setOnItemClickListener { parent, _, position, _ ->
                val item = parent.getItemAtPosition(position) as Cursor
                val args = Bundle().apply {
                    putInt(BUNDLE_KEY_ID, item.getInt(item.getColumnIndexOrThrow(BaseColumns._ID)))
                    putString(BUNDLE_KEY_DOMAIN, item.getString(item.getColumnIndexOrThrow(V3ClientAuthColumns.DOMAIN)))
                    putString(BUNDLE_KEY_HASH, item.getString(item.getColumnIndexOrThrow(V3ClientAuthColumns.HASH)))
                }
                ClientAuthActionsDialogFragment(args).show(
                    parentFragmentManager,
                    ClientAuthActionsDialogFragment::class.simpleName
                )
            }
        }
    }

    override fun onDestroyView() {
        authListView?.adapter = null
        authListView = null
        super.onDestroyView()

        val activity = requireActivity() as AppCompatActivity
        activity.supportActionBar?.apply {
            title = getString(R.string.app_name)
            setDisplayHomeAsUpEnabled(false)
            setDisplayShowHomeEnabled(false)
        }

        requireActivity().findViewById<View>(R.id.bottom_navigation)?.visibility = View.VISIBLE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_READ_ZIP_BACKUP && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (it.moveToFirst()) {
                        val filename = it.getString(nameIndex)
                        if (!filename.endsWith(CLIENT_AUTH_FILE_EXTENSION)) {
                            Toast.makeText(requireContext(), R.string.error, Toast.LENGTH_LONG).show()
                            return
                        }
                        val authText = DiskUtils.readFileFromInputStream(requireContext().contentResolver, uri)
                        V3BackupUtils(requireContext()).restoreClientAuthBackup(authText)
                    }
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private inner class V3ClientAuthContentObserver(handler: Handler) : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            val cursor = resolver.query(
                ClientAuthContentProvider.CONTENT_URI,
                ClientAuthContentProvider.PROJECTION,
                null, null, null
            )
            adapter.changeCursor(cursor)
        }
    }

    companion object {
        const val BUNDLE_KEY_ID = "_id"
        const val BUNDLE_KEY_DOMAIN = "domain"
        const val BUNDLE_KEY_HASH = "key_hash_value"
        const val CLIENT_AUTH_FILE_EXTENSION = ".auth_private"
        const val CLIENT_AUTH_SAF_MIME_TYPE = "*/*"
        const val REQUEST_CODE_READ_ZIP_BACKUP = 12
    }
}
