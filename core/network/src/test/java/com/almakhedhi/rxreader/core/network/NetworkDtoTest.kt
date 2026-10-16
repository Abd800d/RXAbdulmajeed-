package com.almakhedhi.rxreader.core.network

import com.almakhedhi.rxreader.core.network.commons.CommonsResponse
import com.almakhedhi.rxreader.core.network.dailymed.DmMediaResponse
import com.almakhedhi.rxreader.core.network.dailymed.DmSplListResponse
import com.almakhedhi.rxreader.core.network.gemini.GContent
import com.almakhedhi.rxreader.core.network.gemini.GGenConfig
import com.almakhedhi.rxreader.core.network.gemini.GGenerateRequest
import com.almakhedhi.rxreader.core.network.gemini.GGenerateResponse
import com.almakhedhi.rxreader.core.network.gemini.GPart
import com.almakhedhi.rxreader.core.network.openfda.FdaLabelResponse
import com.almakhedhi.rxreader.core.network.rxnorm.RxApproxResponse
import com.almakhedhi.rxreader.core.network.rxnorm.RxPropertiesResponse
import com.almakhedhi.rxreader.core.network.rxnorm.RxRelatedResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** DTO shapes pinned against the documented API examples (no network). */
class NetworkDtoTest {

    private val json = NetworkJson.json

    @Test
    fun `rxnorm approx accepts string and numeric scores`() {
        val raw = """{"approximateGroup":{"inputTerm":"panadol","candidate":[""" +
            """{"rxcui":"161","score":"75","rank":"1"},""" +
            """{"rxcui":"209387","score":60,"rank":2}]}}"""
        val parsed = json.decodeFromString<RxApproxResponse>(raw)
        val candidates = parsed.approximateGroup?.candidate ?: emptyList()
        assertEquals(2, candidates.size)
        assertEquals(75.0, candidates[0].scoreValue(), 0.001)
        assertEquals(60.0, candidates[1].scoreValue(), 0.001)
    }

    @Test
    fun `rxnorm properties and related parse`() {
        val props = json.decodeFromString<RxPropertiesResponse>(
            """{"properties":{"rxcui":"161","name":"Acetaminophen","tty":"IN"}}""",
        )
        assertEquals("Acetaminophen", props.properties?.name)
        val related = json.decodeFromString<RxRelatedResponse>(
            """{"relatedGroup":{"rxcui":"161","conceptGroup":[""" +
                """{"tty":"SCD","conceptProperties":[""" +
                """{"rxcui":"104375","name":"Acetaminophen 325 MG Oral Tablet"}]}]}}""",
        )
        val group = related.relatedGroup?.conceptGroup?.firstOrNull()
        assertEquals("SCD", group?.tty)
        assertEquals("104375", group?.conceptProperties?.firstOrNull()?.rxcui)
    }

    @Test
    fun `openfda label parses sections and openfda fields`() {
        val raw = """{"results":[{"openfda":{"brand_name":["Panadol"],""" +
            """"generic_name":["Acetaminophen"],"route":["ORAL"],""" +
            """"dosage_form":["TABLET"]},"indications_and_usage":["Pain reliever"],""" +
            """"packaging":["30 TABLET in 1 BOTTLE"]}]}"""
        val label = json.decodeFromString<FdaLabelResponse>(raw).results.first()
        assertEquals("Panadol", label.openfda?.brand_name?.first())
        assertEquals("Pain reliever", label.first(label.indications_and_usage))
        assertEquals("30 TABLET in 1 BOTTLE", label.first(label.packaging))
    }

    @Test
    fun `openfda empty object yields no results`() {
        val parsed = json.decodeFromString<FdaLabelResponse>("{}")
        assertTrue(parsed.results.isEmpty())
    }

    @Test
    fun `dailymed spl list parses and builds label url`() {
        val raw = """{"metadata":{"total_elements":"1"},"data":[""" +
            """{"setid":"abc-123","title":"PANADOL TABLET","published_date":"Nov 01, 2013","spl_version":"25"}]}"""
        val spl = json.decodeFromString<DmSplListResponse>(raw).data.first()
        assertEquals("abc-123", spl.setid)
        assertEquals(
            "https://dailymed.nlm.nih.gov/dailymed/drugInfo.cfm?setid=abc-123",
            spl.labelUrl(),
        )
    }

    @Test
    fun `dailymed media parses image files`() {
        val raw = """{"metadata":{},"data":{"spl_version":"1","setid":"1efe378e",""" +
            """"title":"EDECRIN TABLET","published_date":"Mar 12, 2012","media":[""" +
            """{"mime_type":"image/jpeg","name":"edecrin-01.jpg",""" +
            """"url":"https://dailymed.nlm.nih.gov/dailymed/image.cfm?setid=1efe378e&name=edecrin-01.jpg"}]}}"""
        val media = json.decodeFromString<DmMediaResponse>(raw).data?.media ?: emptyList()
        assertEquals(1, media.size)
        assertEquals("image/jpeg", media[0].mime_type)
        assertTrue(media[0].url?.startsWith("https://dailymed.nlm.nih.gov/") == true)
    }

    @Test
    fun `commons search parses imageinfo and strips license html`() {
        val raw = """{"query":{"pages":{"12345":{"title":"File:Panadol.jpg","imageinfo":[""" +
            """{"url":"https://upload.wikimedia.org/wikipedia/commons/1/2/Panadol.jpg",""" +
            """"thumburl":"https://upload.wikimedia.org/wikipedia/commons/thumb/1/2/Panadol.jpg/800px-Panadol.jpg",""" +
            """"width":800,"height":600,"extmetadata":{""" +
            """"LicenseShortName":{"value":"CC BY-SA 4.0"},""" +
            """"Artist":{"value":"<a href=\"https://x\">Some One</a>"}}}]}}}}"""
        val image = json.decodeFromString<CommonsResponse>(raw)
            .query?.pages?.values?.firstOrNull()?.imageinfo?.firstOrNull()
        assertEquals(800, image?.width)
        assertEquals("CC BY-SA 4.0", image?.license())
        assertEquals("Some One", image?.artist())
    }

    @Test
    fun `gemini generateContent response parses first text`() {
        val raw = """{"candidates":[{"content":{"role":"model","parts":[""" +
            """{"text":"  {\"a\":1}  "}]}}],"usageMetadata":{"a":1}}"""
        val parsed = json.decodeFromString<GGenerateResponse>(raw)
        assertEquals("{\"a\":1}", parsed.firstText())
        val request = GGenerateRequest(
            system_instruction = GContent(parts = listOf(GPart(text = "sys"))),
            contents = listOf(GContent(role = "user", parts = listOf(GPart(text = "hi")))),
            generationConfig = GGenConfig(response_mime_type = "application/json"),
        )
        val encoded = json.encodeToString(request)
        assertTrue(encoded.contains("system_instruction"))
        assertTrue(encoded.contains("response_mime_type"))
    }
}
