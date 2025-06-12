package org.jetbrains.kotlinx.jupyter.api.outputs

import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.kotlinx.jupyter.api.MimeTypedResultEx
import org.jetbrains.kotlinx.jupyter.api.MimeTypes
import kotlin.reflect.KProperty

fun interface MetadataModifier {
    fun JsonObjectBuilder.modifyMetadata()
}

fun standardMetadataModifiers(
    isolatedHtml: Boolean = false,
    expandedJson: Boolean = false,
): List<MetadataModifier> =
    buildList {
        if (isolatedHtml) add(IsolatedHtmlMarker)
        if (expandedJson) add(ExpandedJsonMarker)
    }

object IsolatedHtmlMarker : MetadataModifier {
    private val marker =
        buildJsonObject {
            put("isolated", true)
        }

    override fun JsonObjectBuilder.modifyMetadata() {
        put(MimeTypes.HTML, marker)
    }
}

var MimeTypedResultEx.isIsolatedHtml by hasModifier(IsolatedHtmlMarker)

object ExpandedJsonMarker : MetadataModifier {
    private val marker =
        buildJsonObject {
            put("expanded", true)
        }

    override fun JsonObjectBuilder.modifyMetadata() {
        put(MimeTypes.JSON, marker)
    }
}

var MimeTypedResultEx.isExpandedJson by hasModifier(ExpandedJsonMarker)

fun hasModifier(modifier: MetadataModifier) = MetadataModifierIsSet(modifier)

class MetadataModifierIsSet(
    private val modifier: MetadataModifier,
) {
    operator fun getValue(
        result: MimeTypedResultEx,
        property: KProperty<*>,
    ): Boolean = result.hasMetadataModifiers { it === modifier }

    operator fun setValue(
        result: MimeTypedResultEx,
        property: KProperty<*>,
        value: Boolean,
    ) {
        if (value) {
            result.addMetadataModifier(modifier)
        } else {
            result.removeMetadataModifier(modifier)
        }
    }
}
