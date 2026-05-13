package com.autoleap

import com.autoleap.commands.autoLeapCommand
import com.autoleap.features.AutoLeap
import com.autoleap.features.BigTimer
import com.odtheking.odin.config.ModuleConfig
import com.odtheking.odin.features.ModuleManager
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback

object AutoLeapAddon : ClientModInitializer {
    override fun onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            autoLeapCommand.register(dispatcher)
        }

        ModuleManager.registerModules(ModuleConfig("trji.json"), AutoLeap, BigTimer)

        FontPackRegistrar.register()
    }
}
