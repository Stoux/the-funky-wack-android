package nl.stoux.tfw.automotive.screens

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.media3.common.MediaItem
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import nl.stoux.tfw.automotive.car.ControllerHolder
import nl.stoux.tfw.automotive.di.NowPlayingScreenFactory
import nl.stoux.tfw.core.common.database.dao.EditionWithContent
import nl.stoux.tfw.service.playback.service.session.CustomMediaId


class EditionLivesetListScreen @AssistedInject constructor(
    private val controllerHolder: ControllerHolder,
    private val nowPlayingScreenFactory: NowPlayingScreenFactory,
    @Assisted carContext: CarContext,
    @Assisted private val edition: EditionWithContent,
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        if (edition.livesets.isEmpty()) {
            listBuilder.addItem(Row.Builder().setTitle("No livesets").build())
        } else {
            edition.livesets.forEach { lwd ->
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle(lwd.liveset.title)
                        .addText(lwd.liveset.artistName)
                        .setOnClickListener {
                            val mediaId = CustomMediaId.forEntity(lwd.liveset).original

                            controllerHolder.get()?.let { controller ->
                                val item = MediaItem.Builder().setMediaId(mediaId).build()
                                controller.setMediaItem(item)
                                controller.prepare()
                                controller.play() // TODO: Move this to a listener construct?
                            }

//                            screenManager.push(nowPlayingScreenFactory.create(carContext))

                        }
                        .build()
                )
            }
        }

        val title = buildString {
            append("TFW #").append(edition.edition.number)
            edition.edition.tagLine?.let { append(" – ").append(it) }
        }

        return ListTemplate.Builder()
            .setTitle(title)
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
}
