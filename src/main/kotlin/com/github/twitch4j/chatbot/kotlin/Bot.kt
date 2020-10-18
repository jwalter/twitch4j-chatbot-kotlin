package com.github.twitch4j.chatbot.kotlin

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential
import com.github.philippheuer.events4j.simple.SimpleEventHandler
import com.github.twitch4j.TwitchClient
import com.github.twitch4j.TwitchClientBuilder
import com.github.twitch4j.chat.events.channel.ChannelJoinEvent
import com.github.twitch4j.chatbot.kotlin.features.ChannelNotificationOnDonation
import com.github.twitch4j.chatbot.kotlin.features.ChannelNotificationOnFollow
import com.github.twitch4j.chatbot.kotlin.features.ChannelNotificationOnSubscription
import com.github.twitch4j.chatbot.kotlin.features.WriteChannelChatToConsole
import kotlin.system.exitProcess

object Bot {

    /** Holds the configuration */
    private val configuration: Configuration =
        loadConfiguration()

    /** Holds the client */
    private val twitchClient: TwitchClient = createClient()

    /** Register all features */
    fun registerFeatures() {
        val eventHandler = twitchClient.eventManager.getEventHandler(SimpleEventHandler::class.java)
        eventHandler.onEvent(ChannelJoinEvent::class.java) {
            println("Joined channel: ${it.channel.name} [${it.channel.id}]: ${it.user?.name}")
        }
        WriteChannelChatToConsole(eventHandler)
        ChannelNotificationOnFollow(eventHandler)
        ChannelNotificationOnSubscription(eventHandler)
        ChannelNotificationOnDonation(eventHandler)
    }

    /** Start the bot, connecting it to every channel specified in the configuration */
    fun start() {
        val credential = OAuth2Credential(
            "twitch",
            configuration.credentials["irc"]
        )
        // Connect to all channels
        for (channel in configuration.channels) {
            twitchClient.chat.joinChannel(channel)
            val channelId = twitchClient.chat.channelNameToChannelId[channel]
            twitchClient.pubSub.listenForFollowingEvents(credential, channelId);
            //twitchClient.pubSub.listenForSubscriptionEvents(credential, channelId);
        }
    }

    /** Load the configuration from the config.yaml file */
    private fun loadConfiguration(): Configuration {
        val config: Configuration
        val classloader = Thread.currentThread().contextClassLoader
        val inputStream = classloader.getResourceAsStream("config.yaml")
        val mapper = ObjectMapper(YAMLFactory())

        try {
            config = mapper.readValue(inputStream, Configuration::class.java)
        } catch (ex: Exception) {
            println("Unable to load configuration... Exiting")
            exitProcess(1)
        }

        return config
    }

    /** Create the client */
    private fun createClient(): TwitchClient {
        var clientBuilder = TwitchClientBuilder.builder()
        val client: TwitchClient

        //region Chat related configuration
        val credential = OAuth2Credential(
            "twitch",
            configuration.credentials["irc"]
        )

        clientBuilder = clientBuilder
            .withChatAccount(credential)
            .withEnableChat(true)
        //endregion

        //region Api related configuration
        clientBuilder = clientBuilder
            .withClientId(configuration.api["twitch_client_id"])
            .withClientSecret(configuration.api["twitch_client_secret"])
            .withEnableHelix(true)
            /*
                 * GraphQL has a limited support
                 * Don't expect a bunch of features enabling it
                 */
            .withEnableGraphQL(true)
            /*
                 * Kraken is going to be deprecated
                 * see : https://dev.twitch.tv/docs/v5/#which-api-version-can-you-use
                 * It is only here so you can call methods that are not (yet)
                 * implemented in Helix
                 */
            .withEnableKraken(true)
            .withEnablePubSub(true)
        //endregion
        // Build the client out of the configured builder
        client = clientBuilder.build()

        return client
    }

}
