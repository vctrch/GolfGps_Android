package com.vctrch.golfgps.feature.auto

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarLocation
import androidx.car.app.model.ItemList
import androidx.car.app.model.Metadata
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.vctrch.golfgps.R
import com.vctrch.golfgps.feature.auto.ActiveRoundSession.Companion.greenYardageLabel
import com.vctrch.golfgps.feature.auto.ActiveRoundSession.Companion.poiPickerTitle
import com.vctrch.golfgps.feature.auto.ActiveRoundSession.Companion.teeYardageLabel

object ActiveRoundCarTemplates {
    fun build(
        carContext: CarContext,
        snapshot: ActiveRoundSession.Snapshot,
        showsTeeOnMap: Boolean,
        onPrevious: () -> Unit,
        onNext: () -> Unit,
        onToggleTee: () -> Unit,
        onRefresh: () -> Unit,
    ): Template {
        val hole = snapshot.hole ?: return IdleCarTemplates.build(carContext)
        val courseName = snapshot.courseName.orEmpty()
        val parText = hole.par?.let { "Par $it" } ?: "Par —"
        val holePosition = "${snapshot.holeIndex + 1} of ${snapshot.holeCount}"
        val greenLabel = snapshot.greenYardageLabel()
        val teeLabel = snapshot.teeYardageLabel()

        val itemListBuilder = ItemList.Builder()
        itemListBuilder.addItem(
            Row.Builder()
                .setTitle(greenLabel)
                .addText("$parText · $holePosition")
                .addText(courseName)
                .setMetadata(
                    Metadata.Builder()
                        .setPlace(
                            Place.Builder(CarLocation.create(hole.green.latitude, hole.green.longitude))
                                .setMarker(PlaceMarker.Builder().setLabel("G").build())
                                .build(),
                        )
                        .build(),
                )
                .build(),
        )

        if (showsTeeOnMap && hole.tee != null) {
            val tee = hole.tee
            itemListBuilder.addItem(
                Row.Builder()
                    .setTitle(teeLabel)
                    .addText("Tee")
                    .addText(courseName)
                    .setMetadata(
                        Metadata.Builder()
                            .setPlace(
                                Place.Builder(CarLocation.create(tee.latitude, tee.longitude))
                                    .setMarker(PlaceMarker.Builder().setLabel("T").build())
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
        }

        val actionStripBuilder =
            ActionStrip.Builder()
                .addAction(
                    Action.Builder()
                        .setTitle(carContext.getString(R.string.android_auto_previous))
                        .setOnClickListener(onPrevious)
                        .setEnabled(snapshot.holeIndex > 0)
                        .build(),
                )
                .addAction(
                    Action.Builder()
                        .setTitle(carContext.getString(R.string.android_auto_next))
                        .setOnClickListener(onNext)
                        .setEnabled(snapshot.holeIndex < snapshot.holeCount - 1)
                        .build(),
                )

        if (hole.tee != null) {
            actionStripBuilder.addAction(
                Action.Builder()
                    .setTitle(
                        carContext.getString(
                            if (showsTeeOnMap) R.string.android_auto_hide_tee else R.string.android_auto_show_tee,
                        ),
                    )
                    .setOnClickListener(onToggleTee)
                    .build(),
            )
        }

        actionStripBuilder.addAction(
            Action.Builder()
                .setTitle(carContext.getString(R.string.android_auto_refresh))
                .setOnClickListener(onRefresh)
                .build(),
        )

        return PlaceListMapTemplate.Builder()
            .setTitle(poiPickerTitle(hole.number))
            .setHeaderAction(Action.APP_ICON)
            .setItemList(itemListBuilder.build())
            .setAnchor(
                Place.Builder(CarLocation.create(hole.green.latitude, hole.green.longitude))
                    .setMarker(PlaceMarker.Builder().setLabel("G").build())
                    .build(),
            )
            .setCurrentLocationEnabled(true)
            .setActionStrip(actionStripBuilder.build())
            .setOnContentRefreshListener { onRefresh() }
            .build()
    }
}
