package com.almakhedhi.rxreader.core.data.model

import com.almakhedhi.rxreader.core.database.entity.DrugEntity

/** Editable drug form state (repository fills system/search columns). */
data class DrugDraft(
    val id: Long = 0,
    val tradeEn: String? = null,
    val tradeAr: String? = null,
    val scientific: String? = null,
    val actives: String? = null,
    val strength: String? = null,
    val dosageForm: String? = null,
    val route: String? = null,
    val manufacturer: String? = null,
    val country: String? = null,
    val agent: String? = null,
    val regNo: String? = null,
    val barcode: String? = null,
    val packSize: String? = null,
    val price: Double? = null,
    val priceDate: String? = null,
    val dispensing: String? = null,
    val therapeutic: String? = null,
    val atc: String? = null,
    val indications: String? = null,
    val dosageAdults: String? = null,
    val dosageChildren: String? = null,
    val contraindications: String? = null,
    val sideEffects: String? = null,
    val interactions: String? = null,
    val pregnancy: String? = null,
    val warnings: String? = null,
    val storage: String? = null,
    val rxNormId: String? = null,
    val dailymedId: String? = null,
    val reviewed: Boolean = false,
)

fun DrugEntity.toDraft(): DrugDraft =
    DrugDraft(
        id = id,
        tradeEn = tradeEn,
        tradeAr = tradeAr,
        scientific = scientific,
        actives = actives,
        strength = strength,
        dosageForm = dosageForm,
        route = route,
        manufacturer = manufacturer,
        country = country,
        agent = agent,
        regNo = regNo,
        barcode = barcode,
        packSize = packSize,
        price = price,
        priceDate = priceDate,
        dispensing = dispensing,
        therapeutic = therapeutic,
        atc = atc,
        indications = indications,
        dosageAdults = dosageAdults,
        dosageChildren = dosageChildren,
        contraindications = contraindications,
        sideEffects = sideEffects,
        interactions = interactions,
        pregnancy = pregnancy,
        warnings = warnings,
        storage = storage,
        rxNormId = rxNormId,
        dailymedId = dailymedId,
        reviewed = reviewed,
    )

enum class MatchVia { EXACT, ALIAS, FTS, FUZZY }

/** One candidate from the match pipeline with its score and stage. */
data class DrugMatch(
    val drug: DrugEntity,
    val score: Double,
    val via: MatchVia,
)
