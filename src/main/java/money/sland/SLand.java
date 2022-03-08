package money.sland;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.ConfigSection;

import money.MoneySLand;
import money.event.MoneySLandInviteeChangeEvent;
import money.generator.SLandGenerator;
import money.utils.ActionType;
import money.utils.Range;
import money.utils.SLandUtils;

import java.util.*;

/**
 * @author Him188 @ MoneySLand Project
 */
public final class SLand { // TODO: 2017/7/2 javadoc
    public static SLand newLand(int id, Range x, Range z, String owner, Collection<String> invitees, long time, boolean free, String level, Vector3 shopBlock) {
        return new SLand(id, x, z, owner, invitees, time, free, level, shopBlock);
    }

    public static SLand newLand(int id, Range x, Range z, Player owner, Collection<String> invitees, long time, boolean free, String level, Vector3 shopBlock) {
        return new SLand(id, x, z, owner.getName(), invitees, time, free, level, shopBlock);
    }

    public static SLand newLand(int id, Range x, Range z, String owner, Collection<String> invitees, long time, boolean free, Level level, Vector3 shopBlock) {
        return new SLand(id, x, z, owner, invitees, time, free, level.getFolderName(), shopBlock);
    }

    public static SLand newLand(int id, Range x, Range z, Player owner, Collection<String> invitees, long time, boolean free, Level level, Vector3 shopBlock) {
        return new SLand(id, x, z, owner.getName(), invitees, time, free, level.getName(), shopBlock);
    }

    public static SLand newLand(ConfigSection data) throws IllegalArgumentException, NullPointerException {
        return new SLand(
                data.getInt("id", -1),
                Range.fromString(data.getString("x", null)),
                Range.fromString(data.getString("z", null)),
                data.getString("owner", null),
                data.containsKey("invitees") ? data.getStringList("invitees") : null,
                data.getLong("time", -1),
                data.getBoolean("free"),
                data.getString("level", null),
                SLandUtils.parseVector3(data.getString("shopBlock", null))
        );
    }

    public static SLand newInitialLand(int id, Range x, Range z, String level, Vector3 shopBLock) {
        return new SLand(id, x, z, "", new ArrayList<>(), System.currentTimeMillis(), false, level, shopBLock);
    }

    public static SLand newInitialLand(int id, Range x, Range z, Level level, Vector3 shopBLock) {
        return new SLand(id, x, z, "", new ArrayList<>(), System.currentTimeMillis(), false, level.getFolderName(), shopBLock);
    }


    private final Range x;
    private final Range z;
    private final int id;

    private final String level;
    private Level levelInstance;
    private String owner;
    private final LinkedHashSet<String> invitees;
    private final long time;

    private final Vector3 shopBlock;


    private float sellingPrice = -1;
    private float buyingPrice = -1;

    private SLand(int id, Range x, Range z, String owner, Collection<String> invitees, long time, boolean free, String level, Vector3 shopBlock) {
        Objects.requireNonNull(x);
        Objects.requireNonNull(z);
        Objects.requireNonNull(owner);
        Objects.requireNonNull(invitees);
        Objects.requireNonNull(level);
        Objects.requireNonNull(shopBlock);

        if (id < 0) {
            throw new IllegalArgumentException("sland id is invalid");
        }

        if (time < 0) {
            throw new IllegalArgumentException("sland time is invalid");
        }

        this.x = x;
        this.z = z;
        this.id = id;
        this.owner = owner;
        this.invitees = new LinkedHashSet<>(invitees);
        this.time = time;
        this.level = level;
        this.shopBlock = shopBlock.floor();
    }

    public int getId() {
        return id;
    }


    public Vector3 getShopBlock() {
        return shopBlock.clone();
    }

    public ConfigSection save() {
        return new ConfigSection() {
            {
                put("id", id);
                put("level", level);
                put("owner", owner);
                put("invitees", new ArrayList<>(invitees));
                put("time", time);
                put("x", x.toString());
                put("z", z.toString());
                put("shopBlock", shopBlock.toString());
            }
        };
    }

    /**
     * Gets the level folder name
     *
     * @return the level folder name
     */
    public String getLevel() {
        return level;
    }

    /**
     * Gets the level instance
     *
     * @return the level instance
     * @see Server#getLevelByName(String)
     */
    public Level getLevelInstance() {
        return this.levelInstance == null ? this.levelInstance = Server.getInstance().getLevelByName(this.level) : this.levelInstance;
    }

    /**
     * Returns if the {@code position} is included in ths land
     *
     * @param position position
     * @return TRUE on the {@code position} is included in ths land, otherwise FALSE
     */
    public boolean inRange(Position position) {
        return position.getLevel().getFolderName().equalsIgnoreCase(level)
                && x.inRangeIncludingFrame(position.getFloorX(), false)
                && z.inRangeIncludingFrame(position.getFloorZ(), false);
    }

    /**
     * Calculate the square of this land
     *
     * @return square in square blocks
     */
    public int getSquare() {
        return Math.abs(x.getRealLength() * z.getRealLength());
    }

    /**
     * Gets the time when generating the land (in milliseconds ({@link System#currentTimeMillis()}))
     *
     * @return the time when generating the land in milliseconds
     */
    public long getTime() {
        return time;
    }

    /**
     * Gets the x range
     *
     * @return the x range
     */
    public Range getX() {
        return x;
    }

    /**
     * Gets the z range
     *
     * @return the z range
     */
    public Range getZ() {
        return z;
    }

    public boolean isFrame(Vector3 position) {
        return ((this.getX().min == position.getFloorX() || this.getX().max == position.getFloorX()) && this.getZ().inRangeIncludingFrame(position.getFloorZ(), false))
                || ((this.getZ().min == position.getFloorZ() || this.getZ().max == position.getFloorZ()) && this.getX().inRangeIncludingFrame(position.getFloorX(), false));
    }

    /**
     * Gets the owner's name
     *
     * @return the owner's name
     */
    public String getOwner() {
        return owner;
    }

    /**
     * Sets the owner's name
     *
     * @param owner the owner's name
     */
    public void setOwner(String owner) {
        if (!Objects.equals(this.owner, owner)) {
            this.owner = owner == null || owner.isEmpty() ? null : owner;
            MoneySLand.getInstance().getModifiedLandPool().add(this);
        }
    }

    public void setOwner(Player owner) {
        if (owner == null)
            setOwner("");
        else
            setOwner(owner.getName());
    }

    /**
     * Checks if this land has owner
     *
     * @return if this land has owner
     */
    public boolean isOwned() {
        return getOwner() != null && !getOwner().isEmpty();
    }

    public Set<String> getInvitees() {
        return Collections.unmodifiableSet(invitees);
    }

    /**
     * Adds an invitee
     *
     * @param player player's name
     * @return TRUE on success or the player is already invited. FALSE on {@link MoneySLandInviteeChangeEvent} is cancelled
     */
    public boolean addInvitee(String player) {
        if (this.invitees.contains(player)) {
            return true;
        }

        MoneySLandInviteeChangeEvent event = new MoneySLandInviteeChangeEvent(this, player, MoneySLandInviteeChangeEvent.Type.ADD);
        Server.getInstance().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        this.invitees.add(player);
        MoneySLand.getInstance().getModifiedLandPool().add(this);
        return true;
    }

    /**
     * Removes an invitee
     *
     * @param player player's name
     * @return TRUE on success or <code>player</code> is not invited. FALSE on {@link MoneySLandInviteeChangeEvent} is cancelled
     */
    public boolean removeInvitee(String player) {
        if (!this.invitees.contains(player)) {
            return true;
        }

        MoneySLandInviteeChangeEvent event = new MoneySLandInviteeChangeEvent(this, player, MoneySLandInviteeChangeEvent.Type.REMOVE);
        Server.getInstance().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        this.invitees.remove(player);
        MoneySLand.getInstance().getModifiedLandPool().add(this);
        return true;
    }

    /**
     * Returns if {@code player} is invited from this land
     *
     * @param player player's name
     * @return TRUE on {@code player} is invited from this land, otherwise FALSE
     */
    public boolean isInvited(String player) {
        return this.invitees.contains(player);
    }

    /**
     * Returns if the {@code player} can modify this land
     *
     * @param player player
     * @return TRUE on {@code player} can modify this land, otherwise FALSE
     */
    public boolean testPermission(Player player, ActionType type) {
        return /* Owner */ (getOwner() != null && getOwner().equalsIgnoreCase(player.getName()))
                || /* invitee */ this.isInvited(player.getName())
                || /* action permission */ type.testPermission(player, this);
    }

    private static final Block AIR = new BlockAir();

    private final byte[] regeneratorLock = new byte[0];

    /**
     * Clears this {@link SLand} (Replace all blocks with {@link BlockAir})
     */
    public void clear() {
        synchronized (regeneratorLock) {
            this.getX().forEach(x -> this.getZ().forEach(z -> {
                for (int y = 0; y < 0xff; y++) {
                    this.getLevelInstance().setBlock(new Vector3(x, y, z), AIR, false, false);
                }
            }));
        }
    }

    /**
     * Regenerates and repopulates this {@link SLand}.
     * This method should be called after calling {@link SLand#clear()}
     */
    public void regenerate(boolean putShopBlock) {
        synchronized (regeneratorLock) {

            final SLandGenerator generator = (SLandGenerator) this.getLevelInstance().getGenerator();
            int groundHeight = Integer.parseInt(generator.getSettings().getOrDefault("groundHeight", SLandGenerator.DEFAULT_SETTINGS.get("groundHeight")).toString());
            this.getX().forEach(x -> this.getZ().forEach(z -> {
                int chunkX = x >> 4;
                int chunkZ = z >> 4;
                FullChunk chunk = this.getLevelInstance().getChunk(chunkX, chunkZ);
                for (int y = 0; y < groundHeight; y++) {
                    generator.generate(chunk, chunkX, chunkZ, chunkX * 16, chunkZ * 16, x - chunkX * 16, y, z - chunkZ * 16);
                }
            }));

            if (putShopBlock) {
                this.getX().forEach(x -> this.getZ().forEach(z -> {
                    int chunkX = x >> 4;
                    int chunkZ = z >> 4;
                    FullChunk chunk = this.getLevelInstance().getChunk(chunkX, chunkZ);
                    for (int y = 0; y < groundHeight; y++) {
                        generator.populate(chunk, chunkX, chunkZ, chunkX * 16, chunkZ * 16, x - chunkX * 16, y, z - chunkZ * 16, false);
                    }
                }));
            }
        }
    }

    public SLandGenerator getGenerator() {
        return (SLandGenerator) this.getLevelInstance().getGenerator();
    }

    public float getBuyingPrice() {
        if (this.buyingPrice == -1) { //SLand被加载时不一定世界也被加载, Generator 不一定能获取到.
            float price = Float.parseFloat(this.getGenerator().getSettings().getOrDefault("price", SLandGenerator.DEFAULT_SETTINGS.get("price")).toString());
            return this.buyingPrice = this.getSquare() * price;
        }
        return this.buyingPrice;
    }

    public float getSellingPrice() {
        if (this.sellingPrice == -1) {
            float price = Float.parseFloat(this.getGenerator().getSettings().getOrDefault("sellingPrice", SLandGenerator.DEFAULT_SETTINGS.get("sellingPrice")).toString());
            return this.sellingPrice = this.getSquare() * price;
        }
        return this.sellingPrice;
    }

}
