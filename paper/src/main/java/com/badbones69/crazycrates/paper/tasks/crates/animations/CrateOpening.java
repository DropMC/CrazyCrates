package com.badbones69.crazycrates.paper.tasks.crates.animations;

import com.badbones69.crazycrates.paper.CrazyCrates;
import com.badbones69.crazycrates.paper.api.PrizeManager;
import com.badbones69.crazycrates.paper.api.objects.Crate;
import com.badbones69.crazycrates.paper.api.objects.Prize;
import com.badbones69.crazycrates.paper.api.objects.crates.CrateAnimation;
import com.badbones69.crazycrates.paper.api.objects.crates.CrateLocation;
import com.badbones69.crazycrates.paper.cache.CacheManager;
import com.badbones69.crazycrates.paper.support.holograms.types.BuiltinHologramSupport;
import com.badbones69.crazycrates.paper.support.models.ModelManager;
import com.badbones69.crazycrates.paper.tasks.crates.CrateManager;
import com.badbones69.crazycrates.paper.tasks.crates.animations.phases.CompactPhase;
import com.badbones69.crazycrates.paper.tasks.crates.animations.phases.CrackPhase;
import com.badbones69.crazycrates.paper.tasks.crates.animations.phases.KeyOpenerPhase;
import com.badbones69.crazycrates.paper.tasks.crates.animations.phases.PhysicalItemPhase;
import com.badbones69.crazycrates.paper.tasks.crates.animations.phases.RewardDisplayPhase;
import com.badbones69.crazycrates.paper.tasks.crates.animations.phases.SwirlPhase;
import com.ryderbelserion.fusion.paper.builders.folia.FoliaScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Lidded;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * One player opening a {@code Physical} crate: the key goes in, the crate bursts open, the prize floats above it. A port of
 * PhoenixCrates' opening session with simultaneous openings on, so only the player opening sees the animation and anyone
 * else may open the same crate meanwhile; the others keep seeing it idle.
 *
 * <p>Runs on the crate's region, a tick at a time. The prize is picked up front and given when the display phase starts,
 * or right away when the opening is cut short (the player quits, the plugin stops), so a key taken always pays out.</p>
 */
public class CrateOpening {

    /** The longest a model's animation may hold the opening up, whatever its length. */
    private static final int MAX_MODEL_TICKS = 200;

    private static final Map<UUID, CrateOpening> OPENINGS = new ConcurrentHashMap<>();

    private final CrazyCrates plugin = CrazyCrates.getPlugin();

    private final CrateManager crateManager = this.plugin.getPlatform().getCrateManager();

    private final CacheManager cacheManager = this.plugin.getPlatform().getCacheManager();

    private final NamespacedKey entityTag = new NamespacedKey(this.plugin, "animation");

    private final Player player;
    private final Crate crate;
    private final CrateAnimation animation;
    private final Block block;
    private final Location base;
    private final float yaw;
    private final Prize prize;
    private final String locationId;
    private final List<OpeningPhase> phases = new ArrayList<>();
    private final List<Entity> entities = new ArrayList<>();

    private ModelManager.ModelSession model;
    private ScheduledTask task;
    private int phaseIndex;
    private boolean closing;
    private int modelTicks; // left to run of the model's animation for this phase, or of its close.
    private boolean prizeGiven;
    private boolean lidOpen;
    private boolean crateHidden;
    private boolean finished;

    /**
     * @param player the player opening, whose key was already taken
     * @param crate the crate
     * @param crateLocation where it stands, with the yaw it faces
     * @param prize the prize already picked
     */
    public CrateOpening(@NotNull final Player player, @NotNull final Crate crate, @NotNull final CrateLocation crateLocation, @NotNull final Prize prize) {
        this.player = player;
        this.crate = crate;
        this.animation = crate.getAnimation();
        this.prize = prize;

        final Location location = crateLocation.getLocation();

        this.block = location.getBlock();
        this.yaw = location.getYaw();
        this.base = new Location(location.getWorld(), location.getBlockX() + 0.5, location.getBlockY(), location.getBlockZ() + 0.5);

        final ModelManager models = this.crateManager.getModels();

        if (models != null && crate.getModel().isEnabled()) {
            this.model = models.openFor(crateLocation.getID(), player);
        }

        this.locationId = crateLocation.getID();
    }

    /**
     * @param player a player
     * @return whether the player is watching a crate open
     */
    public static boolean isOpening(@NotNull final Player player) {
        return OPENINGS.containsKey(player.getUniqueId());
    }

    /**
     * Cuts a player's opening short, giving the prize if it was not given yet. For a player who quits.
     *
     * @param player the player
     */
    public static void abort(@NotNull final Player player) {
        final CrateOpening opening = OPENINGS.get(player.getUniqueId());

        if (opening != null) opening.abort();
    }

    /** Cuts every opening short, for the plugin stopping. */
    public static void abortAll() {
        for (final CrateOpening opening : List.copyOf(OPENINGS.values())) {
            try {
                opening.abort();
            } catch (final RuntimeException exception) { // one opening failing must not keep the others from paying out.
                opening.plugin.getComponentLogger().error("Could not end {}'s opening of {}.", opening.player.getName(), opening.crate.getFileName(), exception);
            }
        }
    }

    /** Starts the opening. Runs on the crate's region. */
    public void start() {
        OPENINGS.put(this.player.getUniqueId(), this);

        if (this.crateManager.getHolograms() instanceof BuiltinHologramSupport holograms) holograms.hideFrom(this.player, this.locationId);

        this.phases.add(switch (this.animation.getPreOpen()) {
            case KEY_OPENER -> new KeyOpenerPhase(this);
            case CRACK -> new CrackPhase(this);
            case NONE -> new OpeningPhase.Instant(this);
        });

        this.phases.add(switch (this.animation.getOpen()) {
            case SWIRL -> new SwirlPhase(this);
            case COMPACT -> new CompactPhase(this);
            case NONE -> new OpeningPhase.Instant(this);
        });

        this.phases.add(switch (this.animation.getDisplay()) {
            case DEFAULT -> new RewardDisplayPhase(this);
            case PHYSICAL_ITEM -> new PhysicalItemPhase(this);
        });

        startPhase(0);

        this.task = new FoliaScheduler(this.plugin, this.block.getLocation()) {
            @Override
            public void run() {
                try {
                    tick();
                } catch (final RuntimeException exception) { // a broken phase must not loop every tick with the key already spent.
                    final CrateOpening opening = CrateOpening.this;

                    opening.plugin.getComponentLogger().error("The opening of {} for {} failed, giving the prize now.", opening.crate.getFileName(), opening.player.getName(), exception);

                    try {
                        opening.abort();
                    } finally {
                        opening.cleanup(); // abort may fail halfway; the task must stop regardless.
                    }
                }
            }
        }.runAtFixedRate(1, 1);
    }

    private void tick() {
        if (this.finished) return;

        if (!this.player.isOnline()) {
            abort();

            return;
        }

        if (this.modelTicks > 0) this.modelTicks--;

        if (this.closing) {
            if (this.modelTicks == 0) cleanup();

            return;
        }

        final OpeningPhase phase = this.phases.get(this.phaseIndex);

        if (!phase.isDone()) {
            phase.tick();

            return;
        }

        if (this.modelTicks > 0) return; // the model's animation for this phase is still running.

        phase.finish();

        if (++this.phaseIndex < this.phases.size()) {
            startPhase(this.phaseIndex);
        } else {
            end();
        }
    }

    private void startPhase(final int index) {
        final OpeningPhase phase = this.phases.get(index);

        phase.start();

        this.modelTicks = playModel(this.crate.getModel().getOpenAnimation(index));
    }

    private void end() {
        givePrize();
        closeLid();
        showCrate();

        this.closing = true;
        this.modelTicks = playModel(this.crate.getModel().getCloseAnimation());

        if (this.modelTicks == 0) cleanup();
    }

    /**
     * @param animation the model animation to play
     * @return how many ticks to wait for it, 0 when there is no model or no such animation
     */
    private int playModel(@NotNull final String animation) {
        if (this.model == null) return 0;

        return Math.clamp(this.model.play(animation), 0, MAX_MODEL_TICKS);
    }

    private void abort() {
        if (this.finished) return;

        try {
            if (!this.closing && this.phaseIndex < this.phases.size()) {
                this.phases.get(this.phaseIndex).finish();
            }
        } finally {
            givePrize();
        }

        closeLid();
        showCrate();
        cleanup();
    }

    private void cleanup() {
        if (this.finished) return;

        this.finished = true;

        if (this.task != null) this.task.cancel();

        if (this.model != null) this.model.close();

        if (this.crateManager.getHolograms() instanceof BuiltinHologramSupport holograms) holograms.showTo(this.player, this.locationId);

        this.entities.forEach(Entity::remove);
        this.entities.clear();

        OPENINGS.remove(this.player.getUniqueId(), this);

        this.cacheManager.removeActiveCrate(this.player.getUniqueId());
    }

    // Helpers for the phases.

    public @NotNull Player getPlayer() {
        return this.player;
    }

    public @NotNull Crate getCrate() {
        return this.crate;
    }

    public @NotNull CrateAnimation getAnimation() {
        return this.animation;
    }

    public @NotNull Prize getPrize() {
        return this.prize;
    }

    /**
     * @return the middle of the crate's block floor, PhoenixCrates' animation location
     */
    public @NotNull Location base() {
        return this.base.clone();
    }

    /**
     * @return the middle of the crate's block
     */
    public @NotNull Location centre() {
        return this.base.clone().add(0.0, 0.5, 0.0);
    }

    /**
     * @return where the prize floats: the crate's display position, turned with the crate
     */
    public @NotNull Location displayLocation() {
        return base().add(rotate(this.animation.getDisplayPosition()));
    }

    /**
     * @return the yaw the crate faces
     */
    public float yaw() {
        return this.yaw;
    }

    /**
     * @return a unit vector pointing out of the crate's front
     */
    public @NotNull Vector facing() {
        return rotate(new Vector(0.0, 0.0, 1.0));
    }

    /**
     * Turns a vector given for a crate facing south to the way this crate faces.
     *
     * @param vector the vector, changed in place
     * @return the same vector
     */
    public @NotNull Vector rotate(@NotNull final Vector vector) {
        return vector.rotateAroundY(-Math.toRadians(this.yaw));
    }

    /**
     * @return what the crate breaks into: its block, or the block its placeable item names when the block is a barrier
     *         under a model
     */
    public @NotNull BlockData texture() {
        final Material type = this.block.getType();

        if (type != Material.BARRIER && type.isBlock() && !type.isAir()) return this.block.getBlockData();

        final ItemStack item = this.crate.getPlaceable().getItem(1);

        return (item != null && item.getType().isBlock() ? item.getType() : Material.CHEST).createBlockData();
    }

    /**
     * @return the item that stands for the crate itself, for the {@code COMPACT} phase
     */
    public @NotNull ItemStack crateItem() {
        final ItemStack item = this.crate.getPlaceable().getItem(1);

        return item != null ? item : new ItemStack(texture().getMaterial());
    }

    public void particle(@NotNull final Particle particle, @NotNull final Location location, final int count, final double spreadX, final double spreadY,
                         final double spreadZ, final double speed, @Nullable final Object data) {
        this.player.spawnParticle(particle, location, count, spreadX, spreadY, spreadZ, speed, data);
    }

    public void sound(@NotNull final Sound sound, final float volume, final float pitch) {
        this.player.playSound(this.base, sound, volume, pitch);
    }

    /**
     * Spawns an entity only the player opening the crate sees. It is never saved with the chunk, and goes when the opening
     * ends.
     */
    public <T extends Entity> @NotNull T spawn(@NotNull final Location location, @NotNull final Class<T> type, @NotNull final Consumer<T> setup) {
        final T entity = location.getWorld().spawn(location, type, spawned -> {
            spawned.setPersistent(false);
            spawned.setVisibleByDefault(false);
            spawned.getPersistentDataContainer().set(this.entityTag, PersistentDataType.BOOLEAN, true);

            setup.accept(spawned);
        });

        this.player.showEntity(this.plugin, entity);
        this.entities.add(entity);

        return entity;
    }

    public void remove(@Nullable final Entity entity) {
        if (entity == null) return;

        entity.remove();

        this.entities.remove(entity);
    }

    public void openLid() {
        if (this.lidOpen) return;

        this.lidOpen = true;

        if (this.block.getState(false) instanceof Lidded lidded) lidded.open();
    }

    public void closeLid() {
        if (!this.lidOpen) return;

        this.lidOpen = false;

        if (this.block.getState(false) instanceof Lidded lidded) lidded.close();
    }

    /** Makes the crate's block look like air to the player opening it, while something takes its place. */
    public void hideCrate() {
        if (this.crateHidden) return;

        this.crateHidden = true;

        this.player.sendBlockChange(this.block.getLocation(), Material.AIR.createBlockData());
    }

    public boolean isCrateHidden() {
        return this.crateHidden;
    }

    /** Puts a hidden crate back, the way PhoenixCrates does: in a burst of its own pieces. */
    public void showCrate() {
        if (!this.crateHidden) return;

        this.crateHidden = false;

        this.player.sendBlockChange(this.block.getLocation(), this.block.getBlockData());

        particle(Particle.BLOCK, centre(), 60, 0.5, 0.5, 0.5, 0.3, texture());
        sound(Sound.BLOCK_WOOD_PLACE, 1.0f, 1.0f);
    }

    /** Gives the prize, once. */
    public void givePrize() {
        if (this.prizeGiven) return;

        this.prizeGiven = true;

        PrizeManager.givePrize(this.player, base().add(0.0, 1.0, 0.0), this.crate, this.prize);
    }

    /**
     * @param entity an entity
     * @return whether it belongs to a crate opening, so nothing may pick it up or merge it
     */
    public static boolean isAnimationEntity(@NotNull final Entity entity) {
        return entity.getPersistentDataContainer().has(new NamespacedKey(CrazyCrates.getPlugin(), "animation"));
    }
}
