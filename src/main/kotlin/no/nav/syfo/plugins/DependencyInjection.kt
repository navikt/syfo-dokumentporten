package no.nav.syfo.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import no.nav.syfo.altinn.common.AltinnTokenProvider
import no.nav.syfo.ereg.EregService
import no.nav.syfo.ereg.client.EregClient
import no.nav.syfo.ereg.client.FakeEregClient
import no.nav.syfo.altinntilganger.AltinnTilgangerService
import no.nav.syfo.altinntilganger.client.AltinnTilgangerClient
import no.nav.syfo.altinntilganger.client.FakeAltinnTilgangerClient
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.Environment
import no.nav.syfo.application.LocalEnvironment
import no.nav.syfo.application.NaisEnvironment
import no.nav.syfo.application.database.Database
import no.nav.syfo.application.database.DatabaseConfig
import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.application.isLocalEnv
import no.nav.syfo.application.leaderelection.LeaderElection
import no.nav.syfo.document.service.ValidationService
import no.nav.syfo.document.db.DocumentDAO
import no.nav.syfo.altinn.dialogporten.client.DialogportenClient
import no.nav.syfo.altinn.dialogporten.client.FakeDialogportenClient
import no.nav.syfo.altinn.dialogporten.service.DialogportenService
import no.nav.syfo.altinn.dialogporten.task.SendDialogTask
import no.nav.syfo.altinn.pdp.client.FakePdpClient
import no.nav.syfo.altinn.pdp.client.PdpClient
import no.nav.syfo.altinn.pdp.service.PdpService
import no.nav.syfo.document.db.DialogDAO
import no.nav.syfo.document.db.DocumentContentDAO
import no.nav.syfo.texas.client.TexasHttpClient
import no.nav.syfo.util.httpClientDefault
import org.koin.core.scope.Scope
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureDependencies() {
    install(Koin) {
        slf4jLogger()

        modules(
            applicationStateModule(),
            environmentModule(isLocalEnv()),
            httpClient(),
            databaseModule(),
            servicesModule()
        )
    }
}

private fun applicationStateModule() = module { single { ApplicationState() } }

private fun environmentModule(isLocalEnv: Boolean) = module {
    single {
        if (isLocalEnv) LocalEnvironment()
        else NaisEnvironment()
    }
}

private fun httpClient() = module {
    single {
        httpClientDefault()
    }
}

private fun databaseModule() = module {
    single<DatabaseInterface> {
        Database(
            DatabaseConfig(
                jdbcUrl = env().database.jdbcUrl(),
                username = env().database.username,
                password = env().database.password,
            )
        )
    }
    single {
        DocumentDAO(get())
    }
    single { DialogDAO(get()) }
    single { DocumentContentDAO(get()) }
}

private fun servicesModule() = module {
    single { TexasHttpClient(client = get(), environment = env().texas) }
    single {
        if (isLocalEnv()) FakeEregClient() else EregClient(
            eregBaseUrl = env().clientProperties.eregBaseUrl,
        )
    }
    single {
        EregService(
            eregClient = get()
        )
    }
    single {
        if (isLocalEnv()) FakeAltinnTilgangerClient() else AltinnTilgangerClient(
            texasClient = get(),
            httpClient = get(),
            baseUrl = env().clientProperties.altinnTilgangerBaseUrl,
        )
    }
    single {
        AltinnTokenProvider(
            texasHttpClient = get(),
            altinnBaseUrl = env().clientProperties.altinn3BaseUrl,
            httpClient = get()
        )
    }
    single {
        if (isLocalEnv()) FakeDialogportenClient() else DialogportenClient(
            altinnTokenProvider = get(),
            httpClient = get(),
            baseUrl = env().clientProperties.altinn3BaseUrl,
        )
    }
    single {
        if (isLocalEnv()) FakePdpClient() else PdpClient(
            httpClient = get(),
            baseUrl = env().clientProperties.altinn3BaseUrl,
            subscriptionKey = env().clientProperties.pdpSubscriptionKey,
            altinnTokenProvider = get(),
        )
    }

    single { AltinnTilgangerService(get()) }
    single { PdpService(get()) }
    single { EregService(get()) }
    single { ValidationService(get(), get(), get()) }
    single { LeaderElection(get(), env().clientProperties.electorPath) }
    single { DialogportenService(get(), get(), env().publicIngressUrl, env().dialogportenIsApiOnly) }
    single { SendDialogTask(get(),get()) }
}

private fun Scope.env() = get<Environment>()
