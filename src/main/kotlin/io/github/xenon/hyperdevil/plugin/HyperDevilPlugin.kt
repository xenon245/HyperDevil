package io.github.xenon.hyperdevil.plugin

import io.github.monun.kommand.kommand
import io.github.monun.tap.event.EntityEventManager
import io.github.monun.tap.fake.*
import io.github.monun.tap.math.normalizeAndLength
import io.github.monun.tap.util.isDamageable
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.TextColor
import org.bukkit.*
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.BoundingBox
import java.util.function.Predicate
import kotlin.random.Random.Default.nextFloat

class HyperDevilPlugin : JavaPlugin() {
    lateinit var fakeEntityServer: FakeEntityServer
    lateinit var fakeProjectileManager: FakeProjectileManager
    lateinit var entityEventManager: EntityEventManager

    var toggled = false

    var devil = Bukkit.getPlayerExact("devil0812")!!

    override fun onEnable() {
        fakeProjectileManager = FakeProjectileManager()
        fakeEntityServer = FakeEntityServer.create(this)
        entityEventManager = EntityEventManager(this)

        setupKomamnds(this, server)
    }

    private fun setupKomamnds(plugin: JavaPlugin, server: Server) {
        kommand {
            register("hyperdevil") {
                executes {
                    feedback(text().color(TextColor.color(194, 0, 0)).content("HyperDevil").build())
                    server.scheduler.runTaskTimer(plugin, HyperDevilScheduler(), 0L, 1L)
                    server.pluginManager.registerEvents(HyperDevilListener(), plugin)
                    Bukkit.getOnlinePlayers().forEach {
                        fakeEntityServer.addPlayer(it)
                    }
                }
            }
        }
    }

    inner class HyperDevilListener : Listener {
        @EventHandler
        fun onJoin(event: PlayerJoinEvent) {
            fakeEntityServer.addPlayer(event.player)
            event.player.isVisualFire = false
            event.player.fireTicks = 0
        }
        @EventHandler
        fun onQuit(event: PlayerQuitEvent) {
            fakeEntityServer.removePlayer(event.player)
        }
        @EventHandler
        fun onInteract(event: PlayerInteractEvent) {
            if (event.player != devil) return
            if (event.item?.type == Material.BLAZE_ROD) {
                if(event.action != Action.RIGHT_CLICK_AIR && event.action == Action.RIGHT_CLICK_BLOCK) return
                val fakeEntity = fakeEntityServer.spawnEntity(event.player.location, ArmorStand::class.java).apply {
                    updateMetadata<ArmorStand> {
                        isInvisible = true
                        isMarker = true
                    }
                    updateEquipment {
                        helmet = ItemStack(Material.MAGMA_BLOCK)
                    }
                }
                val projectile = HyperMagmaBlock(event.player, fakeEntity)
                projectile.velocity = event.player.location.direction.multiply(1.5)
                fakeProjectileManager.launch(event.player.location, projectile)
            }
        }
        @EventHandler
        fun onPlayerSneak(event: PlayerToggleSneakEvent) {
            if (event.player != devil) return
            toggled = event.player.itemInHand.type == Material.FLINT_AND_STEEL && event.isSneaking
        }
    }

    inner class HyperMagmaBlock(val player: Player, val fakeEntity: FakeEntity) : FakeProjectile(100, 256.0) {
        override fun onPreUpdate() {
            velocity = velocity.multiply(0.99).also { it.y -= 0.04 }
        }

        override fun onMove(movement: Movement) {
            fakeEntity.moveTo(movement.to)
        }

        override fun onTrail(trail: Trail) {
            trail.velocity?.let {
                val from = trail.from
                val to = trail.to
                val world = from.world
                val length = velocity.normalizeAndLength()
                val filter = Predicate<Entity> { entity ->
                    when(entity) {
                        player -> false
                        is Player -> entity.gameMode.isDamageable
                        is LivingEntity -> true
                        else -> false
                    }
                }

                world.rayTrace(
                    from,
                    velocity,
                    length,
                    FluidCollisionMode.NEVER,
                    true,
                    1.0,
                    filter
                )?.let { result ->
                    remove()

                    val hitPosition = result.hitPosition
                    val hitLocation = hitPosition.toLocation(world)
                    hitLocation.createExplosion(15.0F)
                }
            }

            val to = trail.to

            val vector = to.direction.multiply(-1)
            val dx = vector.x
            val dy = vector.y
            val dz = vector.z
            val wiggle = 0.4

            for (i in 0 until 20) {
                to.world.spawnParticle(
                    Particle.FLAME,
                    to.clone().add(vector),
                    0,
                    dx + nextFloat() * wiggle - wiggle / 2.0,
                    dy + nextFloat() * wiggle - wiggle / 2.0,
                    dz + nextFloat() * wiggle - wiggle / 2.0,
                    0.5, null, true
                )
            }

            to.world.playSound(to, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.MASTER, 0.25F, 0.1F)
        }

        override fun onPostUpdate() {
            velocity = velocity.multiply(1.0 + 0.1)
        }

        override fun onRemove() {
            fakeEntity.remove()
        }
    }

    private fun playDevilBreath() {
        val player = devil
        val breathLoc = player.eyeLocation.clone().apply {
            y += 0.5
        }
        val vector = player.eyeLocation.direction.multiply(0.15)
        val dx = vector.x
        val dy = vector.y
        val dz = vector.z
        val wiggle = 0.4
        for (i in 0 until 10) {
            player.world.spawnParticle(Particle.FLAME,
                breathLoc.clone().add(vector),
                0,
                dx + nextFloat() * wiggle - wiggle / 3.0,
                dy + nextFloat() * wiggle - wiggle / 3.0,
                dz + nextFloat() * wiggle - wiggle / 3.0,
                0.5, null, true)
        }

        val filter = Predicate<LivingEntity> { entity ->
            when(entity) {
                player -> false
                is Player -> entity.gameMode.isDamageable
                else -> true
            }
        }

        player.eyeLocation.clone().add(vector).getNearbyLivingEntities(dx.times(4.0), dy.times(4.0), dz.times(4.0), filter).forEach { entity ->
            Bukkit.getScheduler().runTaskTimer(this@HyperDevilPlugin, BurnEntityScheduler(entity), 0L, 1L)
        }
    }

    inner class HyperDevilScheduler : Runnable {
        override fun run() {
            fakeEntityServer.update()
            fakeProjectileManager.update()
            if (toggled) playDevilBreath()
            if (devil.itemInHand.type != Material.FLINT_AND_STEEL) toggled = false
        }
    }

    inner class BurnEntityScheduler(val entity: LivingEntity): Runnable {
        private var ticks = 0
        override fun run() {
            ticks++
            when (ticks) {
                2 -> {
                    entity.isVisualFire = true
                    entity.fireTicks = 100
                }
                100 -> {
                    entity.isVisualFire = false
                    entity.fireTicks = -1
                }
            }
        }
    }
}