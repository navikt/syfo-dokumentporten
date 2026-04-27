package no.nav.syfo.altinn.pdp.client

import kotlinx.serialization.json.Json

internal const val ALTINN_RESOURCE_ATTRIBUTE_ID = "urn:altinn:resource"
internal const val ALTINN_ORGANIZATION_IDENTIFIER_ATTRIBUTE_ID = "urn:altinn:organization:identifier-no"

data class PdpRequest(val request: XacmlJsonRequestExternal) {
    data class XacmlJsonRequestExternal(
        val returnPolicyIdList: Boolean,
        val combinedDecision: Boolean = false,
        val accessSubject: List<XacmlJsonCategoryExternal>,
        val action: List<XacmlJsonCategoryExternal>,
        val resource: List<XacmlJsonCategoryExternal>,
        val multiRequests: XacmlJsonMultiRequestsExternal? = null,
    )

    data class XacmlJsonCategoryExternal(val id: String? = null, val attribute: List<XacmlJsonAttributeExternal>)

    data class XacmlJsonMultiRequestsExternal(val requestReference: List<XacmlJsonRequestReferenceExternal>)

    data class XacmlJsonRequestReferenceExternal(val referenceId: List<String>)

    data class XacmlJsonAttributeExternal(
        val attributeId: String,
        val value: String,
        val dataType: String? = null,
        val includeInResult: Boolean = true,
    )

    override fun toString(): String = Json.encodeToString(this)
}

sealed class Bruker(val id: String, val attributeId: String)

class Person(id: String) : Bruker(id, "urn:altinn:person:identifier-no")

class System(id: String) : Bruker(id, "urn:altinn:systemuser:uuid")

fun lagPdpRequest(bruker: Bruker, orgnrSet: Set<String>, ressurs: String): PdpRequest {
    val subjectId = "subject1"
    val actionId = "action1"
    val resourceCategories = orgnrSet.mapIndexed { index, orgnr ->
        PdpRequest.XacmlJsonCategoryExternal(
            id = "resource${index + 1}",
            attribute = listOf(
                PdpRequest.XacmlJsonAttributeExternal(
                    attributeId = ALTINN_RESOURCE_ATTRIBUTE_ID,
                    value = ressurs,
                ),
                PdpRequest.XacmlJsonAttributeExternal(
                    attributeId = ALTINN_ORGANIZATION_IDENTIFIER_ATTRIBUTE_ID,
                    value = orgnr,
                ),
            ),
        )
    }

    return PdpRequest(
        request = PdpRequest.XacmlJsonRequestExternal(
            returnPolicyIdList = true,
            combinedDecision = false,
            accessSubject = listOf(
                PdpRequest.XacmlJsonCategoryExternal(
                    id = subjectId,
                    attribute = listOf(
                        PdpRequest.XacmlJsonAttributeExternal(
                            attributeId = bruker.attributeId,
                            value = bruker.id,
                        ),
                    ),
                ),
            ),
            action = listOf(
                PdpRequest.XacmlJsonCategoryExternal(
                    id = actionId,
                    attribute = listOf(
                        PdpRequest.XacmlJsonAttributeExternal(
                            attributeId = "urn:oasis:names:tc:xacml:1.0:action:action-id",
                            value = "access",
                            dataType = "http://www.w3.org/2001/XMLSchema#string",
                        ),
                    ),
                ),
            ),
            resource = resourceCategories,
            multiRequests = PdpRequest.XacmlJsonMultiRequestsExternal(
                requestReference = resourceCategories.mapNotNull { resourceCategory ->
                    resourceCategory.id?.let { resourceId ->
                        PdpRequest.XacmlJsonRequestReferenceExternal(
                            referenceId = listOf(subjectId, actionId, resourceId),
                        )
                    }
                },
            ),
        ),
    )
}
