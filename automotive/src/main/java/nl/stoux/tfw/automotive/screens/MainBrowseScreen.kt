package nl.stoux.tfw.automotive.screens

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import nl.stoux.tfw.automotive.di.EditionLivesetListScreenFactory
import nl.stoux.tfw.core.common.database.dao.EditionWithContent
import nl.stoux.tfw.core.common.repository.EditionRepository

/**
 * Root screen showing editions. Clicking an edition pushes LivesetListScreen.
 */
class MainBrowseScreen @AssistedInject constructor(
    private val livesetListScreenFactory: EditionLivesetListScreenFactory,
    private val editionRepository: EditionRepository,
    @Assisted carContext: CarContext,
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        val editions: List<EditionWithContent> = runBlocking {
            withTimeoutOrNull(1500) {
                editionRepository.getEditions().first()
            } ?: emptyList()
        }

        if (editions.isEmpty()) {
            listBuilder.addItem(
                Row.Builder().setTitle("Loading…").build()
            )
        } else {
            editions.forEach { editionWithContent ->
                val title = buildString {
                    append("TFW #").append(editionWithContent.edition.number)
                    editionWithContent.edition.tagLine?.let { append(" – ").append(it) }
                }
                val sub = listOfNotNull(
                    editionWithContent.edition.date,
                    editionWithContent.edition.notes
                ).joinToString(" · ")

                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(title)
                        .addText(sub)
                        .setOnClickListener {
                            val screen = livesetListScreenFactory.create(carContext, editionWithContent)
                            screenManager.push(screen)
                        }
                        .build()
                )
            }
        }

        return ListTemplate.Builder()
            .setTitle("TFW Editions")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(listBuilder.build())
            .build()
    }
}
