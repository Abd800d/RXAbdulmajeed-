package com.almakhedhi.rxreader.core.files.columns

/**
 * Canonical drug fields with Arabic/English headers and header synonyms
 * for import auto-mapping. Field ids double as JSON keys.
 */
object DrugColumns {

    data class Field(
        val id: String,
        val ar: String,
        val en: String,
        val synonyms: Set<String> = emptySet(),
    )

    val ALL: List<Field> = listOf(
        Field("tradeEn", "الاسم التجاري (إنجليزي)", "Trade Name", setOf("الاسم التجاري", "brand", "trade name", "trade", "tradename", "product")),
        Field("tradeAr", "الاسم التجاري (عربي)", "Arabic Name", setOf("الاسم العربي", "arabic name", "arabic trade")),
        Field("scientific", "الاسم العلمي", "Scientific Name", setOf("scientific", "inn", "generic", "generic name", "المادة العلمية")),
        Field("actives", "المواد الفعالة", "Active Ingredients", setOf("actives", "ingredients", "composition", "التركيب")),
        Field("strength", "التركيز", "Strength", setOf("dose strength", "concentration", "القوة")),
        Field("dosageForm", "الشكل الصيدلاني", "Dosage Form", setOf("form", "dosage form", "الشكل", "pharmaceutical form")),
        Field("route", "طريقة الإعطاء", "Route", setOf("administration", "route of administration", "طريق الإعطاء")),
        Field("manufacturer", "الشركة المصنعة", "Manufacturer", setOf("company", "manufacturer", "الشركة", "maker", "producer")),
        Field("country", "بلد المنشأ", "Country", setOf("origin", "country of origin", "المنشأ", "made in")),
        Field("agent", "الوكيل", "Agent", setOf("distributor", "dealer", "الموزع", "yemen agent")),
        Field("regNo", "رقم التسجيل", "Registration No", setOf("reg", "reg no", "registration", "registration number", "التسجيل")),
        Field("barcode", "الباركود", "Barcode", setOf("ean", "upc", "gtin", "bar code")),
        Field("packSize", "حجم العبوة", "Pack Size", setOf("pack", "package", "package size", "العبوة", "packaging")),
        Field("price", "السعر (ر.ي)", "Price (YER)", setOf("price", "cost", "السعر", "yer", "rial")),
        Field("priceDate", "تاريخ السعر", "Price Date", setOf("price date", "تاريخ التسعيرة")),
        Field("dispensing", "تصنيف الصرف", "Dispensing", setOf("dispensing", "rx status", "prescription status", "الصرف", "otc")),
        Field("therapeutic", "الفئة العلاجية", "Therapeutic Class", setOf("category", "class", "therapeutic", "therapeutic class", "الفئة", "group")),
        Field("atc", "ATC", "ATC Code", setOf("atc", "atc code")),
        Field("indications", "دواعي الاستعمال", "Indications", setOf("indications", "indication", "uses", "use", "دواعي الاستخدام")),
        Field("dosageAdults", "جرعة البالغين", "Adult Dose", setOf("adult dose", "adult dosage", "dosage adults")),
        Field("dosageChildren", "جرعة الأطفال", "Child Dose", setOf("child dose", "pediatric dose", "children dose", "pediatric dosage")),
        Field("contraindications", "موانع الاستعمال", "Contraindications", setOf("contraindications", "contra", "موانع")),
        Field("sideEffects", "الآثار الجانبية", "Side Effects", setOf("side effects", "adverse effects", "adverse", "الأعراض الجانبية")),
        Field("interactions", "التداخلات", "Interactions", setOf("interactions", "drug interactions", "التفاعلات")),
        Field("pregnancy", "الحمل والرضاعة", "Pregnancy", setOf("pregnancy", "pregnancy and lactation", "lactation", "الحمل")),
        Field("warnings", "التحذيرات", "Warnings", setOf("warnings", "warning", "precautions", "تحذير")),
        Field("storage", "التخزين", "Storage", setOf("storage", "store", "الحفظ")),
        Field("rxNormId", "RxNorm", "RxNorm ID", setOf("rxnorm", "rxcui", "rxnorm id")),
        Field("dailymedId", "DailyMed", "DailyMed ID", setOf("dailymed", "setid", "dailymed id")),
        Field("reviewed", "تمت المراجعة", "Reviewed", setOf("reviewed", "verified", "checked", "مراجعة")),
    )

    val byId: Map<String, Field> = ALL.associateBy { it.id }

    /** At least one of these must map for a file to be importable. */
    val NAME_FIELDS = setOf("tradeEn", "tradeAr", "scientific")

    private val synonymIndex: Map<String, String> by lazy {
        buildMap {
            ALL.forEach { field ->
                put(normalizeHeader(field.id), field.id)
                put(normalizeHeader(field.ar), field.id)
                put(normalizeHeader(field.en), field.id)
                field.synonyms.forEach { put(normalizeHeader(it), field.id) }
            }
        }
    }

    data class MappingResult(
        /** File column index -> field id (absent = ignored column). */
        val map: Map<Int, String>,
        val unmappedHeaders: List<String>,
        val conflicts: List<String>,
    )

    /** Maps file headers to fields; first column wins on duplicates. */
    fun autoMap(headers: List<String>): MappingResult {
        val map = mutableMapOf<Int, String>()
        val unmapped = mutableListOf<String>()
        val conflicts = mutableListOf<String>()
        val used = mutableSetOf<String>()
        headers.forEachIndexed { index, header ->
            val fieldId = synonymIndex[normalizeHeader(header)]
            when {
                fieldId == null -> unmapped.add(header)
                !used.add(fieldId) -> conflicts.add(header)
                else -> map[index] = fieldId
            }
        }
        return MappingResult(map, unmapped, conflicts)
    }

    fun normalizeHeader(header: String): String =
        header.trim().lowercase()
            .replace(Regex("[_\\-/()\\[\\].]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Arabic/English form names -> canonical code (PLAN dosage vocabulary). */
    fun canonicalForm(raw: String): String {
        val key = raw.trim().lowercase()
        return when (key) {
            "tablet", "tablets", "tab", "tabs", "أقراص", "قرص", "حبوب", "حبة" -> "tablet"
            "capsule", "capsules", "cap", "caps", "كبسولات", "كبسولة" -> "capsule"
            "syrup", "syr", "شراب" -> "syrup"
            "suspension", "susp", "معلق", "مُعلَّق" -> "suspension"
            "injection", "injections", "inj", "ampoule", "amp", "vial", "حقن", "حقنة", "أمبول", "إبرة" -> "injection"
            "drops", "drop", "gtt", "قطرات", "قطرة" -> "drops"
            "cream", "creams", "كريم" -> "cream"
            "ointment", "oint", "مرهم" -> "ointment"
            "suppository", "suppositories", "supp", "تحاميل", "تحميلة" -> "suppository"
            "inhaler", "بخاخ", "بخاخة" -> "inhaler"
            "sachet", "sachets", "أكياس", "كيس", "بودرة", "powder" -> "sachet"
            else -> "other"
        }
    }

    /** Dispensing class -> rx/otc/controlled ("" when unrecognized). */
    fun canonicalDispensing(raw: String): String {
        val key = raw.trim().lowercase()
        return when {
            key.isEmpty() -> ""
            key in setOf("rx", "prescription", "بوصفة", "بوصفة طبية", "بوصفه", "pom") -> "rx"
            key in setOf("otc", "بدون وصفة", "بدون وصفه", "over the counter") -> "otc"
            key in setOf("controlled", "مراقب", "مخدر", "جدول", "مقيد") -> "controlled"
            else -> ""
        }
    }

    /** Accepts "1250", "1,250", "1250.5", "١٢٥٠"; null when invalid. */
    fun parsePrice(raw: String): Double? {
        val folded = buildString {
            for (ch in raw.trim()) {
                append(
                    when (ch) {
                        in '٠'..'٩' -> '0' + (ch - '٠')
                        in '۰'..'۹' -> '0' + (ch - '۰')
                        else -> ch
                    },
                )
            }
        }.replace(",", "").replace(" ", "")
        if (folded.isEmpty()) return null
        return folded.toDoubleOrNull()?.takeIf { it >= 0 }
    }

    fun parseBool(raw: String): Boolean {
        val key = raw.trim().lowercase()
        return key in setOf("1", "true", "yes", "y", "نعم", "تم", "تمت", "صح", "✓", "true", "t")
    }
}
