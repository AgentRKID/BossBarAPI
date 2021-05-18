package io.github.agentrkid.bossbar

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.wrappers.WrappedDataWatcher
import net.minecraft.server.v1_8_R3.MinecraftServer
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import java.lang.Exception
import java.lang.RuntimeException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

class BossBarAPI : JavaPlugin(), Listener {
    private var entityId: Int = Int.MIN_VALUE + 15000

    private val cachedBossBars: MutableMap<UUID, Int> = ConcurrentHashMap()
    private val lastTickTime: MutableMap<UUID, Int> = ConcurrentHashMap()

    override fun onEnable() {
        instance = this

        Bukkit.getScheduler().runTaskTimer(this, {
            for (uuid in cachedBossBars.keys) {
                val player = Bukkit.getPlayer(uuid)

                if (player == null || !player.isOnline) {
                    cachedBossBars.remove(uuid)
                    lastTickTime.remove(uuid)
                    continue
                }

                if (MinecraftServer.currentTick - lastTickTime.getOrDefault(uuid, 0) < 3) {
                    continue
                }

                updateBossBarPosition(player)
                lastTickTime[uuid] = MinecraftServer.currentTick
            }
        }, 0, 3)
        Bukkit.getPluginManager().registerEvents(this, this)
    }

    fun sendBossBar(player: Player, barMessage: String, health: Float) {
        // You can send empty boss
        // bars for it to remove
        if (barMessage.isEmpty()) {
            removeBossBar(player)
            return
        }

        // Health can only be 1.0 -> 0.0 (1.0 being 100)
        if (health < 0.0f || health > 1.0f) {
            throw RuntimeException("Health must be between 0 and 1")
        }

        var message = barMessage;

        // The bar character length
        // can't be longer then 64
        if (message.length > 64) {
            message = message.substring(0, 64)
        }

        message = ChatColor.translateAlternateColorCodes('&', message)

        // If they don't have a bar
        // create one else update the current
        if (!cachedBossBars.containsKey(player.uniqueId)) {
            createBossBar(player, message, health)
        } else {
            updateBossBar(player, message, health)
        }
    }

    fun removeBossBar(player: Player) {
        val entityId = cachedBossBars[player.uniqueId]

        if (entityId != null) {
            val destroyEntityPacket = PacketContainer(PacketType.Play.Server.ENTITY_DESTROY)

            // Add the entity of the players
            // current wither to an array by itself
            destroyEntityPacket.integerArrays.write(0, intArrayOf(entityId))

            sendPacket(player, destroyEntityPacket)

            cachedBossBars.remove(player.uniqueId)
            lastTickTime.remove(player.uniqueId)
        }
    }

    private fun createBossBar(bukkitPlayer: Player, barMessage: String, health: Float) {
        val player = (bukkitPlayer as CraftPlayer).handle

        val spawnPacket = PacketContainer(PacketType.Play.Server.SPAWN_ENTITY_LIVING)
        val entityId = getEntityId();

        spawnPacket.integers.write(0, entityId)
        spawnPacket.integers.write(1, 64)

        val pitch = Math.toRadians(player.pitch.toDouble())
        val yaw = Math.toRadians(player.yaw.toDouble())

        // Make the wither spawn where the
        // player is looking (Like hypixel)
        spawnPacket.integers.write(2, ((player.locX - sin(yaw) * cos(pitch) * 32.0) * 32.0).toInt())
        spawnPacket.integers.write(3, ((player.locY - sin(pitch) * 32.0) * 32.0).toInt())
        spawnPacket.integers.write(4, ((player.locZ + cos(yaw) * cos(pitch) * 32.0) * 32.0).toInt())

        val watcher = WrappedDataWatcher()
        watcher.setObject(0, 32.toByte())
        watcher.setObject(3, 1.toByte()) // Set invisible
        watcher.setObject(6, health * 300.0f) // Set health
        watcher.setObject(2, barMessage) // Set custom name
        spawnPacket.dataWatcherModifier.write(0, watcher)

        sendPacket(bukkitPlayer, spawnPacket)

        cachedBossBars[player.uniqueID] = entityId
    }

    private fun updateBossBar(player: Player, barMessage: String, health: Float) {
        val entityId = cachedBossBars[player.uniqueId]

        val metadataUpdatePacket = PacketContainer(PacketType.Play.Server.ENTITY_METADATA)
        metadataUpdatePacket.integers.write(0, entityId)

        val watcher = WrappedDataWatcher()
        watcher.setObject(6, health * 300.0f) // Update health
        watcher.setObject(2, barMessage) // Update custom name

        metadataUpdatePacket.watchableCollectionModifier.write(0, watcher.watchableObjects)

        sendPacket(player, metadataUpdatePacket)
    }

    private fun updateBossBarPosition(bukkitPlayer: Player) {
        val player = (bukkitPlayer as CraftPlayer).handle
        val entityId = cachedBossBars[player.uniqueID]

        if (entityId != null) {
            val teleportPacket = PacketContainer(PacketType.Play.Server.ENTITY_TELEPORT)

            val pitch = Math.toRadians(player.pitch.toDouble())
            val yaw = Math.toRadians(player.yaw.toDouble())

            teleportPacket.integers.write(0, entityId)

            // Put the wither directly in front of
            // the player 32 blocks away (Like hypixel)
            teleportPacket.integers.write(1, ((player.locX - sin(yaw) * cos(pitch) * 32.0) * 32.0).toInt())
            teleportPacket.integers.write(2, ((player.locY - sin(pitch) * 32.0) * 32.0).toInt())
            teleportPacket.integers.write(3, ((player.locZ + cos(yaw) * cos(pitch) * 32.0) * 32.0).toInt())

            sendPacket(bukkitPlayer, teleportPacket)
        }
    }

    private fun sendPacket(player: Player, packet: PacketContainer) {
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private fun getEntityId(): Int {
        // Reset the entity count
        if (entityId == Int.MIN_VALUE) {
            entityId += 15000
        }
        return entityId--
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player;

        cachedBossBars.remove(player.uniqueId)
        lastTickTime.remove(player.uniqueId)
    }

    companion object {
        lateinit var instance: BossBarAPI
    }
}