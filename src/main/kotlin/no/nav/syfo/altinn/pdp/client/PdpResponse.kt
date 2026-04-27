package no.nav.syfo.altinn.pdp.client

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

fun PdpResponse.harTilgang(orgnrSet: Set<String>): Boolean {
    if (orgnrSet.isEmpty()) {
        return false
    }
    val beslutningPerOrgnr = decisionByOrgnr()
    if (response.size != orgnrSet.size || beslutningPerOrgnr.keys != orgnrSet) {
        return false
    }
    return orgnrSet.all { orgnr -> beslutningPerOrgnr[orgnr] == Decision.Permit }
}

fun PdpResponse.decisionByOrgnr(): Map<String, Decision> = response.mapNotNull { decisionResult ->
    decisionResult.orgnr()?.let { orgnr -> orgnr to decisionResult.decision }
}.toMap()

private fun DecisionResult.orgnr(): String? = category
    .orEmpty()
    .flatMap { it.attribute.orEmpty() }
    .firstOrNull { it.attributeId == ALTINN_ORGANIZATION_IDENTIFIER_ATTRIBUTE_ID }
    ?.value
