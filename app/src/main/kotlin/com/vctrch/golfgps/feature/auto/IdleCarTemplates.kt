package com.vctrch.golfgps.feature.auto

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.vctrch.golfgps.R

object IdleCarTemplates {
    fun build(carContext: CarContext): Template {
        val row =
            Row.Builder()
                .setTitle(carContext.getString(R.string.android_auto_open_on_phone))
                .addText(carContext.getString(R.string.android_auto_open_on_phone_detail))
                .build()
        val list =
            ItemList.Builder()
                .addItem(row)
                .build()
        val header =
            Header.Builder()
                .setTitle(carContext.getString(R.string.android_auto_idle_title))
                .setStartHeaderAction(Action.APP_ICON)
                .build()
        return ListTemplate.Builder()
            .setHeader(header)
            .setSingleList(list)
            .build()
    }
}
