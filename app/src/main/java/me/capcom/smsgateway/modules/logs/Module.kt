package me.capcom.smsgateway.modules.logs

import me.capcom.smsgateway.modules.logs.repositories.LogsRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val logsModule = module {
    singleOf(::LogsRepository)
    singleOf(::LogsService)
}

val NAME = "logs"