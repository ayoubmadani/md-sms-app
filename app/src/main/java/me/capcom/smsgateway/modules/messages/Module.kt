package me.capcom.smsgateway.modules.messages

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val messagesModule = module {
    singleOf(::MessagesRepository)
    singleOf(::MessagesService)
    singleOf(::MessageStateService)
}

val MODULE_NAME = "messages"
