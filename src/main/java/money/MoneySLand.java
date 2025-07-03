package money;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.level.Position;
import cn.nukkit.level.generator.Generator;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.scheduler.TaskHandler;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.ConfigSection;
import cn.nukkit.utils.TextFormat;
import me.onebone.economyapi.EconomyAPI;
import money.command.*;
import money.event.MoneySLandBuyEvent;
import money.event.MoneySLandOwnerChangeEvent;
import money.event.MoneySLandPriceCalculateEvent;
import money.generator.SLandGenerator;
import money.sland.SLand;
import money.sland.SLandPool;
import money.utils.SLandUtils;

import java.io.*;
import java.lang.reflect.Constructor;
import java.util.*;

/**
 * @author Him188 @ MoneySLand Project
 */
public final class MoneySLand extends PluginBase implements MoneySLandAPI {
    private static MoneySLand instance;

    {
        instance = this;
    }

    public static MoneySLand getInstance() {
        return instance;
    }


    private SLandPool lands;
    private SLandPool modifiedLands;
    private Config landConfig;


    private int id;

    private LinkedHashMap<String, Object> language;
    private MoneySLandEventListener eventListener;

    private TaskHandler savingTask;

    private static final Map<String, Class<? extends SLandCommand>> COMMAND_CLASSES = new HashMap<>();

    @Override
    public void onLoad() {
        //当地形生成器已注册时, 方法返回 false
        //服务器重启不会清空地形生成器
        reloadGeneratorDefaultSettings();
        for (String name : SLandGenerator.GENERATOR_NAMES) {
            Generator.addGenerator(SLandGenerator.class, name, Generator.TYPE_INFINITE);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onEnable() {
        getDataFolder().mkdir();
        new File(getDataFolder() + File.separator + "generator_settings").mkdir();

        lands = new SLandPool();
        modifiedLands = new SLandPool();

        initConfigSettings();

        try {
            initLanguageSettings(getConfig().getString("language", "chs"));
        } catch (IOException e) {
            e.printStackTrace();
            getLogger().critical("无法读取语言文件!! 请删除语言文件以恢复初始或修复其中的问题");
            getLogger().critical("Could not load language file!! Please delete language file or fix bugs in it");
        }

        landConfig = new Config(getDataFolder() + File.separator + "lands.dat", Config.YAML);
        landConfig.getSections().values().forEach((o) -> {
            try {
                lands.add(SLand.newLand((ConfigSection) o));
            } catch (IllegalArgumentException | NullPointerException e) {
                getLogger().warning(this.translateMessage("load.error",
                        "id", ((ConfigSection) o).getInt("id", -1)
                ));
                getLogger().debug("", e);
            }
        });
        getLogger().info(this.translateMessage("load.success",
                "count", getLandPool().size()
        ));

        COMMAND_CLASSES.forEach((name, cmdClass) -> {
            String command = getConfig().getString(name + "-command", null);
            if (command != null && !command.isEmpty()) { //for disable command
                this.getLogger().debug("Registering " + name + " command: " + command);
                try {
                    Constructor<? extends SLandCommand> constructor = cmdClass.getConstructor(String.class, MoneySLand.class);
                    constructor.setAccessible(true);
                    Server.getInstance().getCommandMap().register(command, constructor.newInstance(command, this));
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }
            }
        });

        if (eventListener == null) { //for reload
            eventListener = new MoneySLandEventListener(this);
            getServer().getPluginManager().registerEvents(eventListener, this);
        }

        savingTask = Server.getInstance().getScheduler().scheduleDelayedRepeatingTask(this, this::save, 20 * 60, 20 * 60);
    }

    private void reloadGeneratorDefaultSettings() {
        saveResource("generator_default.properties");
        SLandGenerator.setDefaultSettings(SLandUtils.loadProperties(getDataFolder() + File.separator + "generator_default.properties"));
    }

    public Map<String, Object> loadGeneratorSettings(String name) {
        File file;

        file = new File(getDataFolder() + File.separator + "generator_settings" + File.separator + name);
        if (!file.exists()) {
            file = new File(getDataFolder() + File.separator + "generator_settings" + File.separator + name + ".properties");
            if (!file.exists()) {
                file = new File(getDataFolder() + File.separator + "generator_settings" + File.separator + name + ".prop");
                if (!file.exists()) {
                    file = new File(getDataFolder() + File.separator + "generator_settings" + File.separator + name + ".property");
                    if (!file.exists()) {
                        return new HashMap<>();
                    }
                }
            }
        }

        return SLandUtils.loadProperties(file);
    }

    private void initConfigSettings() {
        saveDefaultConfig();
        reloadConfig();

        int size = getConfig().getAll().size();
        new Config(Config.YAML) {
            {
                load(getResource("config.yml"));
            }
        }.getAll().forEach((key, value) -> {
            if (!getConfig().exists(key)) {
                getConfig().set(key, value);
            }
        });
        if (getConfig().getAll().size() != size) {
            getConfig().save();
        }
    }

    private void initLanguageSettings(String language) throws IOException {
        saveResource("language/" + language + "/language.properties", "language.properties", false);

        Properties properties = new Properties();
        File file = new File(getDataFolder() + File.separator + "language.properties");
        properties.load(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        this.language = new LinkedHashMap<String, Object>() {
            {
                properties.forEach((key, value) -> put(key.toString(), value));
            }
        };

        int size = this.language.size();
        SLandUtils.loadProperties(getResource("language/" + language + "/language.properties")).forEach((key, value) -> {
            if (!this.language.containsKey(key)) {
                this.language.put(key, value);
            }
        });

        if (this.language.size() != size) {
            properties.putAll(this.language);

            properties.store(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"), "MoneySLand language config");
        }
    }

    private void save() {
        if (modifiedLands.size() == 0) {
            this.getLogger().debug("No land changes detected.");
            return;
        }
        this.getLogger().debug("Saving " + modifiedLands.size() + " lands...");
        for (SLand land : modifiedLands.values()) {
            landConfig.set(String.valueOf(land.getId()), land.save());
        }
        landConfig.save();
        modifiedLands.clear();
        this.getLogger().debug("Saving done...");
    }

    @Override
    public void onDisable() {
        save();

        if (savingTask != null) {
            savingTask.cancel();
            savingTask = null;
        }
    }

    public String translateMessage(String message) {
        if (language.get(message) == null) {
            return TextFormat.colorize(message);
        }

        return TextFormat.colorize(language.get(message).toString());
    }

    public String translateMessage(String message, Map<String, Object> args) {
        if (language.get(message) == null) {
            return message;
        }

        String msg = translateMessage(message);
        for (Map.Entry<String, Object> s : args.entrySet()) {
            String key = s.getKey();
            Object value = s.getValue();
            if (value instanceof Double || value instanceof Float) {
                msg = msg.replace("$" + key + "$", String.valueOf(Math.round(Double.parseDouble(value.toString()))));
            } else {
                msg = msg.replace("$" + key + "$", value.toString());
            }
        }
        return msg;
    }

    public String translateMessage(String message, Object... keys_values) {
        Map<String, Object> map = new HashMap<>();

        String key = null;
        for (Object o : keys_values) {
            if (key == null) {
                key = o.toString();
            } else {
                map.put(key, o);
                key = null;
            }
        }

        return translateMessage(message, map);
    }


    @Override
    public SLandPool getLandPool() {
        return lands;
    }

    @Override
    public SLandPool getModifiedLandPool() {
        return modifiedLands;
    }

    @Override
    public SLand getLand(Position position) {
        for (SLand land : lands.values()) {
            if (land.inRange(position) || land.getShopBlock().equals(position)) {
                return land;
            }
        }
        return null;
    }

    @Override
    public SLand[] getLands(String player) {
        List<SLand> list = new ArrayList<>();
        for (SLand land : lands.values()) {
            if (land.getOwner().equalsIgnoreCase(player)) {
                list.add(land);
            }
        }
        return list.toArray(new SLand[list.size()]);
    }

    @Override
    public boolean buyLand(SLand land, Player player) {
        //FAPixel SkyPVP家园系统
        //每位玩家最多购买一个家园
        if (this.getLands(player.getName()).length >= 1) {
            player.sendMessage(TextFormat.RED + "购买失败! 每位玩家最多拥有一个家园!");
            return false;
        }

        float price = calculatePrice(player, land);

        if (EconomyAPI.getInstance().myMoney(player) >= price) {

            MoneySLandBuyEvent event = new MoneySLandBuyEvent(land, player, price);
            Server.getInstance().getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return false;
            }

            MoneySLandOwnerChangeEvent ev = new MoneySLandOwnerChangeEvent(land, player, MoneySLandOwnerChangeEvent.Cause.BUY);
            Server.getInstance().getPluginManager().callEvent(ev);
            if (event.isCancelled()) {
                return false;
            }

            if (EconomyAPI.getInstance().reduceMoney(player, event.getPrice()) != EconomyAPI.RET_SUCCESS) {
                return false;
            }

            land.setOwner(player.getName());
            Server.getInstance().getLevelByName(land.getLevel()).setBlock(land.getShopBlock(), Block.get(Block.AIR));
            return true;
        } else {
            return false;
        }
    }

    @Override
    public float calculatePrice(Player player, SLand land) {
        float price = land.getBuyingPrice();

        MoneySLandPriceCalculateEvent event = new MoneySLandPriceCalculateEvent(land, player, price);
        Server.getInstance().getPluginManager().callEvent(event);
        return event.getPrice();
    }
}
