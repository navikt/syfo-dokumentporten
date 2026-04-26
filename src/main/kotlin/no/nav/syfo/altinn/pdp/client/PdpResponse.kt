package no.nav.syfo.altinn.pdp.client

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.syfo.util.logger

val logger = logger("PDPResponse")

data class PdpResponse(val response: List<DecisionResult>)

data class DecisionResult(
    val decision: Decision,
    val status: PdpStatus? = null,
    val obligations: List<PdpObligation>? = null,
    val associateAdvice: List<PdpAdvice>? = null,
    val category: List<PdpCategory>? = null,
    val policyIdentifierList: PolicyIdentifierList? = null,
)

enum class Decision {
    Permit,
    Indeterminate,
    NotApplicable,
    Deny,
}

data class PdpStatus(
    val statusMessage: String? = null,
    val statusDetails: List<String>? = null,
    val statusCode: PdpStatusCode? = null,
)

data class PdpStatusCode(val value: String? = null, val statusCode: String? = null)

data class PdpObligation(val id: String? = null, val attributeAssignment: List<AttributeAssignment>? = null)

data class PdpAdvice(val id: String? = null, val attributeAssignment: List<AttributeAssignment>? = null)

data class AttributeAssignment(
    val attributeId: String? = null,
    val value: String? = null,
    val category: String? = null,
    val dataType: String? = null,
    val issuer: String? = null,
)

data class PdpCategory(
    val categoryId: String? = null,
    val id: String? = null,
    val content: String? = null,
    val attribute: List<PdpAttribute>? = null,
)

data class PdpAttribute(
    val attributeId: String? = null,
    val value: String? = null,
    val issuer: String? = null,
    val dataType: String? = null,
    val includeInResult: Boolean? = null,
)

data class PolicyIdentifierList(
    val policyIdReference: List<PolicyReference>? = null,
    val policySetIdReference: List<PolicyReference>? = null,
)

data class PolicyReference(val id: String? = null, val version: String? = null)

// fun PdpResponse.resultat() = response.first().decision

fun PdpResponse.harTilgang(): Boolean {
    val objectMapper = ObjectMapper()
    logger.info("PDP response unfiltered response=${objectMapper.writeValueAsString(response)}")
    val failed = response.filterNot { it.decision == Decision.Permit }
    if (failed.isNotEmpty()) {
//        logger.info("PDP response har ikke tilgang: response=$response")
        return false
    }
    return true
}
