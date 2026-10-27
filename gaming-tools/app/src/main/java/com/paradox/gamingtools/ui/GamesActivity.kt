package com.paradox.gamingtools.ui

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.paradox.gamingtools.R
import com.paradox.gamingtools.core.AppRepository
import com.paradox.gamingtools.core.BoostEngine
import com.paradox.gamingtools.core.Fmt
import com.paradox.gamingtools.core.GameApp
import com.paradox.gamingtools.core.Prefs
import kotlinx.coroutines.launch

class GamesActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var repository: AppRepository
    private lateinit var engine: BoostEngine
    private lateinit var adapter: GameAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var txtEmpty: TextView
    private lateinit var editSearch: EditText

    private var allApps: List<GameApp> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_games)

        prefs = Prefs(this)
        repository = AppRepository(this)
        engine = BoostEngine(this, prefs)

        recycler = findViewById(R.id.recyclerGames)
        txtEmpty = findViewById(R.id.txtEmpty)
        editSearch = findViewById(R.id.editSearch)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        adapter = GameAdapter(
            onClick = { app -> launchGame(app) },
            onFavorite = { app -> toggleFavorite(app) }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        editSearch.doAfterTextChanged { applyFilter() }

        loadApps()
    }

    private fun loadApps() {
        allApps = repository.launchableApps()
        applyFilter()
    }

    private fun applyFilter() {
        val query = editSearch.text.toString().trim().lowercase()
        val filtered = if (query.isEmpty()) {
            allApps
        } else {
            allApps.filter {
                it.label.lowercase().contains(query) || it.packageName.lowercase().contains(query)
            }
        }

        val favorites = prefs.favorites()
        val ordered = filtered.sortedWith(
            compareByDescending<GameApp> { favorites.contains(it.packageName) }
                .thenBy { it.label.lowercase() }
        )

        adapter.submit(ordered, favorites)
        val empty = ordered.isEmpty()
        txtEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        recycler.visibility = if (empty) View.GONE else View.VISIBLE
    }

    private fun toggleFavorite(app: GameApp) {
        prefs.toggleFavorite(app.packageName)
        applyFilter()
    }

    private fun launchGame(app: GameApp) {
        if (!prefs.boostBeforeGame) {
            launchNow(app)
            return
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.games_boosting)
            .setMessage(R.string.games_boosting_desc)
            .setCancelable(false)
            .create()
        dialog.show()

        lifecycleScope.launch {
            val result = engine.boost { }
            if (!isFinishing && !isDestroyed) dialog.dismiss()
            Toast.makeText(
                this@GamesActivity,
                getString(R.string.toast_freed, Fmt.bytes(result.freedBytes)),
                Toast.LENGTH_SHORT
            ).show()
            launchNow(app)
        }
    }

    private fun launchNow(app: GameApp) {
        if (!repository.launch(app.packageName)) {
            Toast.makeText(this, R.string.games_launch_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
