package mod.wurmunlimited.npcs;

import com.wurmonline.server.*;
import com.wurmonline.server.behaviours.*;
import com.wurmonline.server.creatures.*;
import com.wurmonline.server.creatures.ai.CreatureAI;
import com.wurmonline.server.economy.Economy;
import com.wurmonline.server.economy.MonetaryConstants;
import com.wurmonline.server.economy.Shop;
import com.wurmonline.server.items.*;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.questions.CrafterHireQuestion;
import com.wurmonline.server.questions.CreatureCreationQuestion;
import com.wurmonline.server.questions.Question;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.skills.Skills;
import com.wurmonline.shared.constants.IconConstants;
import com.wurmonline.shared.constants.ItemMaterials;
import javassist.*;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.*;
import org.gotti.wurmunlimited.modsupport.ItemTemplateBuilder;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;
import org.gotti.wurmunlimited.modsupport.creatures.ModCreatures;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.*;
import java.util.stream.Collectors;

public class CrafterMod implements WurmServerMod, PreInitable, Initable, Configurable, ItemTemplatesCreatedListener, ServerStartedListener, PlayerMessageListener {
    private static final Logger logger = Logger.getLogger(CrafterMod.class.getName());
    public static final CrafterFaceSetters faceSetters = new CrafterFaceSetters();
    private static final Random faceRandom = new Random();
    public static final int maxNameLength = 50;
    static final byte MAIL_TYPE_CRAFTER = 30;
    public static final int DRAGON_ARMOUR = -1;
    public static final int MOON_METAL = -2;
    private static String namePrefix;
    private static int contractTemplateId;
    private static int contractPrice = 10000;
    private static PaymentOption paymentOption = PaymentOption.for_owner;
    private static boolean canLearn = true;
    private static boolean updateTraders = false;
    private static boolean contractsOnTraders = true;
    private static byte gmManagePowerRequired = 10;
    private static float upkeepPercentage = 25.0f;
    private static float basePrice = 1.0f;
    private static int mailPrice = MonetaryConstants.COIN_COPPER;
    private final static Map<Integer, Float> skillPrices = new HashMap<>();
    // Do not set at 100.  Skill.setKnowledge will not set the skill level if so.
    private static float skillCap = 99.99999f;
    private static float startingSkill = 20;
    private static float crafterSkillGainRate = 1;
    private static boolean usePriceModifier = true;
    private static float minimumPriceModifier = 0.0000001f;
    private static boolean mailCommand = false;
    private static OutputOption output = OutputOption.none;
    private static int removeDonationsAt = Integer.MIN_VALUE;
    private static boolean singleSkill = false;
    private static boolean canChangeSkill = true;
    private static final List<Byte> restrictedMaterials = new ArrayList<>();
    private static final Map<Creature, Logger> crafterLoggers = new HashMap<>();
    private Properties properties;
    public static Path globalRestrictionsPath = Paths.get("mods", "crafter", "global_restrictions");

    private enum OutputOption {
        save,
        save_and_print,
        none
    }

    public enum PaymentOption {
        tax_and_upkeep,
        for_owner,
        all_tax
    }

    public static String getNamePrefix() {
        return namePrefix;
    }

    public static byte gmManagePowerRequired() {
        return gmManagePowerRequired;
    }

    public static int getContractTemplateId() {
        return contractTemplateId;
    }

    public static float getStartingSkillLevel() {
        return startingSkill;
    }

    public static float getSkillCap() {
        return skillCap;
    }

    public static boolean canLearn() {
        return canLearn;
    }

    public static float getBasePriceForSkill(int skill) {
        return basePrice * skillPrices.getOrDefault(skill, 1.0f);
    }

    public static boolean canUsePriceModifier() {
        return usePriceModifier;
    }

    public static float getMinimumPriceModifier() {
        return minimumPriceModifier;
    }

    public static int mailPrice() {
        return mailPrice;
    }

    public static float getUpkeepPercentage() {
        return upkeepPercentage / 100;
    }

    public static PaymentOption getPaymentOption() {
        return paymentOption;
    }

    public static boolean destroyDonationItem(float currentSkill, float itemQL) {
        if (removeDonationsAt == Integer.MIN_VALUE)
            return false;
        return currentSkill + removeDonationsAt <= itemQL || itemQL > 99.999999f;
    }

    public static boolean useSingleSkill() {
        return singleSkill;
    }

    public static boolean canChangeSkill() {
        return canChangeSkill;
    }

    public static boolean isGloballyRestrictedMaterial(byte material) {
        return restrictedMaterials.size() != 0 && !restrictedMaterials.contains(material);
    }

    public static boolean materialsRestrictedGlobally() {
        return restrictedMaterials.size() > 0;
    }

    private OutputOption parseOutputOption(String value) {
        OutputOption option = output;
        if (value != null && value.length() > 0) {
            if (value.equals("save"))
                option = OutputOption.save;
            else if (value.equals("print"))
                option = OutputOption.save_and_print;
        }
        return option;
    }

    private PaymentOption parsePaymentOption(String value) {
        PaymentOption option = paymentOption;
        if (value != null && value.length() > 0) {
            try {
                option = PaymentOption.valueOf(value);
            } catch (IllegalArgumentException e) {
                logger.warning("Invalid PaymentOption - " + value);
                e.printStackTrace();
            }
        }
        return option;
    }

    private boolean getOption(String option, boolean _default) {
        String val = properties.getProperty(option);
        if (val != null && val.length() > 0) {
            try {
                return Boolean.parseBoolean(val);
            } catch (NumberFormatException e) {
                logger.warning("Invalid value for " + option + " - Using default of " + _default);
            }
        }
        return _default;
    }

    private int getOption(String option, int _default) {
        String val = properties.getProperty(option);
        if (val != null && val.length() > 0) {
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException e) {
                logger.warning("Invalid value for " + option + " - Using default of " + _default);
            }
        }
        return _default;
    }

    private float getOption(String option, float _default) {
        String val = properties.getProperty(option);
        if (val != null && val.length() > 0) {
            try {
                float value = Float.parseFloat(val);
                if (value <= 0)
                    throw new NumberFormatException("Must be a positive number");
                return value;
            } catch (NumberFormatException e) {
                logger.warning("Invalid value for " + option + " - Using default of " + _default);
            }
        }
        return _default;
    }

    @Override
    public void configure(Properties properties) {
        this.properties = properties;
        namePrefix = (String)properties.getOrDefault("name_prefix", "");
        contractPrice = getOption("contract_price_in_irons", contractPrice);
        paymentOption = parsePaymentOption(properties.getProperty("payment"));
        output = parseOutputOption(properties.getProperty("output"));
        canLearn = getOption("can_learn", canLearn);
        updateTraders = getOption("update_traders", updateTraders);
        gmManagePowerRequired = (byte)getOption("gm_manage_power_required", gmManagePowerRequired);
        if (gmManagePowerRequired < 1) {
            logger.warning("gm_manage_power_required must be 1 or higher.  Setting default (10).");
            gmManagePowerRequired = 10;
        }
        contractsOnTraders = getOption("contracts_on_traders", contractsOnTraders);
        upkeepPercentage = getOption("upkeep_percentage", upkeepPercentage);
        basePrice = getOption("base_price", basePrice);
        mailPrice = getOption("mail_price", mailPrice);
        startingSkill = getOption("starting_skill", startingSkill);
        skillCap = Math.min(getOption("max_skill", skillCap), skillCap);
        if (startingSkill > skillCap) {
            logger.warning("starting_skill should not be higher than max_skill, capping value.");
            startingSkill = (int)skillCap;
        }
        crafterSkillGainRate = getOption("crafter_skill_gain_rate", crafterSkillGainRate);
        usePriceModifier = getOption("use_owner_price_modifier", usePriceModifier);
        minimumPriceModifier = getOption("minimum_price_modifier", minimumPriceModifier);
        mailCommand = getOption("mail_command", mailCommand);
        removeDonationsAt = getOption("remove_donations_at", removeDonationsAt);
        singleSkill = getOption("single_crafter_skill", singleSkill);
        canChangeSkill = getOption("change_skill_after_placement", canChangeSkill);

        skillPrices.put(SkillList.SMITHING_BLACKSMITHING, getOption("blacksmithing", 1.0f));
        skillPrices.put(SkillList.GROUP_SMITHING_WEAPONSMITHING, getOption("weaponsmithing", 1.0f));
        skillPrices.put(SkillList.SMITHING_GOLDSMITHING, getOption("jewelrysmithing", 1.0f));
        skillPrices.put(SkillList.SMITHING_ARMOUR_CHAIN, getOption("chainsmithing", 1.0f));
        skillPrices.put(SkillList.SMITHING_ARMOUR_PLATE, getOption("platesmithing", 1.0f));
        skillPrices.put(SkillList.CARPENTRY, getOption("carpentry", 1.0f));
        skillPrices.put(SkillList.CARPENTRY_FINE, getOption("fine_carpentry", 1.0f));
        skillPrices.put(SkillList.GROUP_FLETCHING, getOption("fletching", 1.0f));
        skillPrices.put(SkillList.GROUP_BOWYERY, getOption("bowyery", 1.0f));
        skillPrices.put(SkillList.LEATHERWORKING, getOption("leatherworking", 1.0f));
        skillPrices.put(SkillList.CLOTHTAILORING, getOption("clothtailoring", 1.0f));
        skillPrices.put(SkillList.STONECUTTING, getOption("stonecutting", 1.0f));
        skillPrices.put(SkillList.SMITHING_SHIELDS, getOption("shieldsmithing", 1.0f));
        skillPrices.put(DRAGON_ARMOUR, getOption("dragon_armour", 10.0f));
        skillPrices.put(MOON_METAL, getOption("moon_metal", 1.0f));

        try {
            byte[] materials = Files.readAllBytes(globalRestrictionsPath);
            if (materials.length > 0) {
                for (byte mat : materials) {
                    if (mat > 0 && mat <= ItemMaterials.MATERIAL_MAX) {
                        restrictedMaterials.add(mat);
                    }
                }
            }

        } catch (NoSuchFileException ignored) {
        } catch (IOException e) {
            logger.warning("Error reading \"global_restrictions\":");
            e.printStackTrace();
        }
    }

    public static List<Byte> getRestrictedMaterials() {
        return new ArrayList<>(restrictedMaterials);
    }

    public static void saveRestrictedMaterials(List<Byte> newRestrictions) {
        try {
            restrictedMaterials.clear();
            restrictedMaterials.addAll(newRestrictions);
            byte[] bytes = new byte[restrictedMaterials.size()];
            for (int i = 0; i < restrictedMaterials.size(); ++i)
                bytes[i] = restrictedMaterials.get(i);

            Files.write(globalRestrictionsPath, bytes, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        } catch (IOException e) {
            logger.warning("Error saving \"global_restrictions\", changes not saved:");
            e.printStackTrace();
        }
    }

    @Override
    public void onItemTemplatesCreated() {
        try {
            contractTemplateId = new ItemTemplateBuilder("writ.crafter")
                    .name("crafter contract", "crafter contracts", "A contract to hire a crafter, who will improve items of specified types for payment.")
                    .modelName("model.writ.merchant")
                    .imageNumber((short)IconConstants.ICON_TRADER_CONTRACT)
                    .weightGrams(0)
                    .dimensions(1, 10, 10)
                    .decayTime(Long.MAX_VALUE)
                    .material(ItemMaterials.MATERIAL_PAPER)
                    .itemTypes(new short[] {
                            ItemTypes.ITEM_TYPE_INDESTRUCTIBLE,
                            ItemTypes.ITEM_TYPE_NODROP,
                            ItemTypes.ITEM_TYPE_HASDATA,
                            ItemTypes.ITEM_TYPE_FULLPRICE,
                            ItemTypes.ITEM_TYPE_LOADED,
                            ItemTypes.ITEM_TYPE_NOT_MISSION,
                            ItemTypes.ITEM_TYPE_NOSELLBACK
                    })
                    .behaviourType(BehaviourList.itemBehaviour)
                    .value(contractPrice)
                    .difficulty(100.0F)
                    .build().getTemplateId();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void init() {
        HookManager manager = HookManager.getInstance();

        manager.registerHook("com.wurmonline.server.creatures.Creature",
                "getTradeHandler",
                "()Lcom/wurmonline/server/creatures/TradeHandler;",
                () -> this::getTradeHandler);

        // Listen for messages.
        manager.registerHook("com.wurmonline.server.creatures.CreatureCommunicator",
                "sendNormalServerMessage",
                "(Ljava/lang/String;)V",
                () -> this::logMessages);

        // Block forge opening if assigned to a crafter.
        manager.registerHook("com.wurmonline.server.behaviours.BehaviourDispatcher",
                "action",
                "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/creatures/Communicator;JJS)V",
                () -> this::behaviourDispatcher);

        manager.registerHook("com.wurmonline.server.economy.Economy",
                "getShop",
                "(Lcom/wurmonline/server/creatures/Creature;Z)Lcom/wurmonline/server/economy/Shop;",
                () -> this::getShop);

        manager.registerHook("com.wurmonline.server.items.TradingWindow",
                "stopLoggers",
                "()V",
                () -> this::stopLoggers);

        manager.registerHook("com.wurmonline.server.items.TradingWindow",
                "swapOwners",
                "()V",
                () -> this::swapOwners);

        manager.registerHook("com.wurmonline.server.creatures.Creature",
                "getFace",
                "()J",
                () -> this::getFace);

        manager.registerHook("com.wurmonline.server.creatures.Creature",
                "getBlood",
                "()B",
                () -> this::getBlood);

        manager.registerHook("com.wurmonline.server.creatures.Communicator",
                "sendNewCreature",
                "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;FFFJFBZZZBJBZZB)V",
                () -> this::sendNewCreature);

        manager.registerHook("com.wurmonline.server.creatures.Creature",
                "wearItems",
                "()V",
                () -> this::wearItems);

        manager.registerHook("com.wurmonline.server.skills.Skill",
                "alterSkill",
                "(DZFZD)V",
                () -> this::alterSkill);

        manager.registerHook("com.wurmonline.server.questions.QuestionParser",
                "parseCreatureCreationQuestion",
                "(Lcom/wurmonline/server/questions/CreatureCreationQuestion;)V",
                () -> this::creatureCreation);

        manager.registerHook("com.wurmonline.server.creatures.Communicator",
                "reallyHandle_CMD_NEW_FACE",
                "(Ljava/nio/ByteBuffer;)V",
                () -> this::setFace);

        manager.registerHook("com.wurmonline.server.items.Item",
                "willLeaveServer",
                "(ZZZ)Z",
                () -> this::willLeaveServer);

        ModCreatures.init();
        ModCreatures.addCreature(new CrafterTemplate());
    }

    Object wearItems(Object o, Method method, Object[] args) throws InvocationTargetException, IllegalAccessException {
        Creature creature = (Creature)o;
        if (CrafterTemplate.isCrafter(creature)) {
            WorkBook workBook = ((CrafterAIData)creature.getCreatureAIData()).getWorkBook();
            if (workBook != null) {
                List<Item> jobItems = new ArrayList<>();

                for (Item item : creature.getInventory().getItemsAsArray()) {
                    if (workBook.isJobItem(item)) {
                        jobItems.add(item);
                        creature.getInventory().getItems().remove(item);
                    }
                }

                try {
                    method.invoke(o, args);
                } finally {
                    for (Item item : jobItems) {
                        creature.getInventory().getItems().add(item);
                    }
                }
                return null;
            }
        }
        return method.invoke(o, args);
    }

    @Override
    public void preInit() {
        try {
            ClassPool pool = HookManager.getInstance().getClassPool();
            // Remove final from TradeHandler and TradingWindow.
            CtClass tradeHandler = pool.get("com.wurmonline.server.creatures.TradeHandler");
            tradeHandler.defrost();
            tradeHandler.setModifiers(Modifier.clear(tradeHandler.getModifiers(), Modifier.FINAL));
            // Add empty constructor.
            if (tradeHandler.getConstructors().length == 1)
                tradeHandler.addConstructor(CtNewConstructor.make(tradeHandler.getSimpleName() + "(){}", tradeHandler));

            CtClass tradingWindow = pool.get("com.wurmonline.server.items.TradingWindow");
            tradingWindow.defrost();
            tradingWindow.setModifiers(Modifier.clear(tradingWindow.getModifiers(), Modifier.FINAL));
            if (tradingWindow.getConstructors().length == 1)
                tradingWindow.addConstructor(CtNewConstructor.make(tradingWindow.getSimpleName() + "(){}", tradingWindow));

            // Add empty constructor.
            CtClass trade = pool.get("com.wurmonline.server.items.Trade");
            trade.defrost();
            if (trade.getConstructors().length == 1)
                trade.addConstructor(CtNewConstructor.make(trade.getSimpleName() + "(){}", trade));
            // Remove final from public fields.
            CtField creatureOne = trade.getDeclaredField("creatureOne");
            creatureOne.setModifiers(Modifier.clear(creatureOne.getModifiers(), Modifier.FINAL));
            CtField creatureTwo = trade.getDeclaredField("creatureTwo");
            creatureTwo.setModifiers(Modifier.clear(creatureTwo.getModifiers(), Modifier.FINAL));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ModActions.init();
    }

    @Override
    public void onServerStarted() {
        ModActions.registerAction(new AssignAction(contractTemplateId));
        ModActions.registerAction(new TradeAction());
        ModActions.registerAction(new CrafterContractAction(contractTemplateId));
        ModActions.registerAction(new ManageCrafterAction());
        new PlaceCrafterAction();
        PlaceNpcMenu.register();

        try {
            Class<?> ServiceHandler = Class.forName("mod.wurmunlimited.npcs.CrafterAI");
            CreatureTemplate template = CreatureTemplateFactory.getInstance().getTemplate(CrafterTemplate.getTemplateId());
            template.setCreatureAI((CreatureAI)ServiceHandler.getConstructor().newInstance());
            if (template.getCreatureAI() == null)
                throw new RuntimeException("CrafterAI not set properly.");
        } catch (NoSuchCreatureTemplateException | NoSuchMethodException | ClassNotFoundException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }

        if (updateTraders) {
            if (contractsOnTraders) {
                for (Shop shop : Economy.getTraders()) {
                    Creature creature = Creatures.getInstance().getCreatureOrNull(shop.getWurmId());
                    if (!shop.isPersonal() && creature != null && creature.isSalesman() && creature.getInventory().getItems().stream().noneMatch(i -> i.getTemplateId() == contractTemplateId)) {
                        try {
                            creature.getInventory().insertItem(Creature.createItem(contractTemplateId, (float) (10 + Server.rand.nextInt(80))));
                            shop.setMerchantData(shop.getNumberOfItems() + 1);
                        } catch (Exception e) {
                            logger.log(Level.INFO, "Failed to create trader inventory items for shop, creature: " + creature.getName(), e);
                        }
                    }
                }
            } else {
                for (Shop shop : Economy.getTraders()) {
                    Creature creature = Creatures.getInstance().getCreatureOrNull(shop.getWurmId());
                    if (!shop.isPersonal() && creature != null) {
                        creature.getInventory().getItems().stream().filter(i -> i.getTemplateId() == contractTemplateId).collect(Collectors.toList()).forEach(item -> {
                            Items.destroyItem(item.getWurmId());
                            shop.setMerchantData(shop.getNumberOfItems() - 1);
                        });
                    }
                }
            }
        }
    }

    static Logger getCrafterLogger(Creature crafter) {
        Logger log = crafterLoggers.get(crafter);
        if (log == null) {
            log = Logger.getLogger(crafter.getName() + "_" + crafter.getWurmId());
            if (output == OutputOption.none)
                log.setLevel(Level.OFF);
            else {
                log.setUseParentHandlers(false);
                if (output == OutputOption.save_and_print) {
                    Handler handler = new ConsoleHandler();
                    handler.setFilter(new CrafterLogFilter(false));
                    log.addHandler(handler);
                }
                try {
                    FileHandler fh = new FileHandler("crafter_" + log.getName() + ".log", 0, 1, true);
                    fh.setFilter(new CrafterLogFilter(true));
                    log.addHandler(fh);
                    fh.setFormatter(new SimpleFormatter());
                } catch (IOException e) {
                    logger.warning("Could not create log file for " + log.getName());
                }
            }
            crafterLoggers.put(crafter, log);
        }
        return log;
    }

    private Object logMessages(Object o, Method method, Object[] args) throws InvocationTargetException, IllegalAccessException, NoSuchFieldException {
        if (output != OutputOption.none) {
            Field creatureField = CreatureCommunicator.class.getDeclaredField("creature");
            creatureField.setAccessible(true);
            Creature creature = (Creature)creatureField.get(o);
            if (creature.getTemplate().getTemplateId() == CrafterTemplate.getTemplateId()) {
                getCrafterLogger(creature).info("Received - " + args[0]);
            }
        }
        return method.invoke(o, args);
    }

    Object stopLoggers(Object o, Method method, Object[] args) throws InvocationTargetException, IllegalAccessException {
        CrafterTradingWindow.stopLoggers();
        return method.invoke(o, args);
    }

    Object behaviourDispatcher(Object o, Method method, Object[] args) throws Throwable {
        Creature creature = (Creature)args[0];
        if ((Short)args[4] == Actions.OPEN) {
            try {
                Item maybeForge = Items.getItem((Long)args[3]);
                if (maybeForge.getTemplateId() == ItemList.forge && CrafterAI.assignedForges.containsValue(maybeForge)) {
                    if (creature.getPower() >= 2)
                        creature.getCommunicator().sendAlertServerMessage("This forge is assigned to a Crafter.  Do not change the contents of the forge unless you know what you are doing.");
                    else {
                        creature.getCommunicator().sendAlertServerMessage("The crafter blocks you from accessing the forge.");
                        return null;
                    }
                }
            } catch (NoSuchItemException ignored) {}
        }

        try {
            return method.invoke(o, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private Object getShop(Object o, Method method, Object[] args) throws IllegalAccessException, NoSuchFieldException, InvocationTargetException {
        Creature creature = (Creature)args[0];
        boolean destroying = (boolean)args[1];
        if (creature.getTemplate().getTemplateId() == CrafterTemplate.getTemplateId()) {
            Shop tm;
            ReentrantReadWriteLock SHOPS_RW_LOCK = ReflectionUtil.getPrivateField(null, Economy.class.getDeclaredField("SHOPS_RW_LOCK"));
            SHOPS_RW_LOCK.readLock().lock();

            try {
                Map<Long, Shop> shops = ReflectionUtil.getPrivateField(Economy.getEconomy(), Economy.class.getDeclaredField("shops"));
                tm = shops.get(creature.getWurmId());
            } finally {
                SHOPS_RW_LOCK.readLock().unlock();
            }

            if (!destroying && tm == null) {
                tm = Economy.getEconomy().createShop(creature.getWurmId());
            }

            return tm;
        }

        return method.invoke(o, args);
    }

    Object getTradeHandler(Object o, Method method, Object[] args) throws NoSuchFieldException, InvocationTargetException, IllegalAccessException, ClassNotFoundException, NoSuchMethodException, InstantiationException {
        Creature creature = (Creature)o;
        if (creature.getTemplate().getTemplateId() != CrafterTemplate.getTemplateId())
            return method.invoke(o, args);

        Field tradeHandler = Creature.class.getDeclaredField("tradeHandler");
        tradeHandler.setAccessible(true);
        TradeHandler handler = (TradeHandler)tradeHandler.get(o);

        if (handler == null) {
            Class<?> ServiceHandler = Class.forName("com.wurmonline.server.creatures.CrafterTradeHandler");
            //noinspection RedundantCast
            handler = (TradeHandler)ServiceHandler.getConstructor(Creature.class, CrafterTrade.class).newInstance(creature, (CrafterTrade)creature.getTrade());
            tradeHandler.set(o, handler);
        }
        return handler;
    }

    @Override
    public MessagePolicy onPlayerMessage(Communicator communicator, String message, String title) {
        Player player = communicator.getPlayer();

        if (player != null) {
            //noinspection SpellCheckingInspection
            if (mailCommand && message.replace(" ", "").equals("/crafterscollect")) {
                int collected = 0;
                for (WurmMail mail : WurmMail.getMailsFor(player.getWurmId()).toArray(new WurmMail[0])) {
                    if (mail.type == MAIL_TYPE_CRAFTER) {
                        Item item;
                        try {
                            item = Items.getItem(mail.getItemId());
                        } catch (NoSuchItemException e) {
                            logger.log(Level.INFO, " NO SUCH ITEM");
                            WurmMail.deleteMail(mail.itemId);
                            continue;
                        }

                        Item inventory = player.getInventory();
                        if (!inventory.testInsertItem(item) || (player.getPower() <= 0 && 1 + inventory.getNumItemsNotCoins() > 99)) {
                            player.getCommunicator().sendSafeServerMessage("You do not have enough space for any more items.");
                            break;
                        }
                        if (!player.canCarry(item.getFullWeight())) {
                            player.getCommunicator().sendSafeServerMessage("You cannot carry any more.");
                            break;
                        }

                        for (Item i : item.getAllItems(true)) {
                            i.setMailed(false);
                            i.setLastOwnerId(player.getWurmId());
                        }

                        WurmMail.removeMail(item.getWurmId());
                        inventory.insertItem(item, true);
                        item.setLastOwnerId(player.getWurmId());
                        item.setMailed(false);
                        logger.log(Level.INFO, player.getName() + " received " + item.getName() + " " + item.getWurmId());
                        collected += 1;
                    }
                }

                if (collected == 0)
                    player.getCommunicator().sendNormalServerMessage("There are no mail items to collect.");
                else
                    player.getCommunicator().sendNormalServerMessage(String.format("You receive " + collected + " item%s.", collected == 1 ? "" : "s"));

                return MessagePolicy.DISCARD;
            } else if (message.equals("/crafters")) {
                CrafterAI.sendCrafterStatusTo(player);
                return MessagePolicy.DISCARD;
            } else if (player.getPower() >= 2 && message.equals("/restrictmaterials")) {
                // Doesn't run if the question is called here directly.
                QuestionWrapper.materialRestrictionQuestion(player);
                return MessagePolicy.DISCARD;
            }
        }

        return MessagePolicy.PASS;
    }

    @Override
    public boolean onPlayerMessage(Communicator communicator, String message) {
        return false;
    }

    Object swapOwners(Object o, Method method, Object[] args) throws InvocationTargetException, IllegalAccessException {
        TradingWindow window = (TradingWindow)o;
        for (Item item : window.getItems()) {
            if (item.getTemplateId() == contractTemplateId) {
                long data = item.getData();
                if (data != -1) {
                    try {
                        Creature crafter = Server.getInstance().getCreature(data);
                        if (crafter.getTemplate().getTemplateId() == CrafterTemplate.getTemplateId()) {
                            Shop shop = Economy.getEconomy().getShop(crafter);
                            if (shop != null) {
                                Creature windowOwner = ReflectionUtil.getPrivateField(window, TradingWindow.class.getDeclaredField("windowowner"));
                                Creature watcher = ReflectionUtil.getPrivateField(window, TradingWindow.class.getDeclaredField("watcher"));

                                shop.setOwner(watcher.getWurmId());
                                watcher.getCommunicator().sendNormalServerMessage("You are now in control of " + crafter.getName() + ".");
                                windowOwner.getCommunicator().sendNormalServerMessage("You are no longer in control of " + crafter.getName() + ".");
                            }
                        }
                    } catch (NoSuchPlayerException | NoSuchCreatureException | NoSuchFieldException e) {
                        logger.warning("Error when trying to transfer crafter contract (for " + data + ") to another player.");
                        e.printStackTrace();
                    }
                }
            }
        }

        return method.invoke(o, args);
    }

    Object getFace(Object o, Method method, Object[] args) throws InvocationTargetException, IllegalAccessException {
        Creature creature = (Creature)o;
        if (CrafterTemplate.isCrafter(creature)) {
            Long newFace = CrafterDatabase.getFaceFor(creature);
            if (newFace == null) {
                faceRandom.setSeed(creature.getWurmId());
                newFace = faceRandom.nextLong();
            }
            return newFace;
        }
        return method.invoke(o, args);
    }

    // Required for not having weird character model.
    Object getBlood(Object o, Method method, Object[] args) throws InvocationTargetException, IllegalAccessException {
        Creature creature = (Creature)o;
        if (CrafterTemplate.isCrafter(creature)) {
            return (byte)-1;
        }
        return method.invoke(o, args);
    }

    Object sendNewCreature(Object o, Method method, Object[] args) throws InvocationTargetException, IllegalAccessException {
        // If id is Creature id.
        if (WurmId.getType((Long)args[0]) == 1) {
            if ((Byte)args[15] == (byte)-1) {
                // Blood - Should only apply to players, so re-purposing it for this should be okay.
                args[15] = (byte)0;
                // isCopy
                args[17] = true;
            }
        }
        return method.invoke(o, args);
    }

    Object alterSkill(Object o, Method method, Object[] args) throws InvocationTargetException, IllegalAccessException, NoSuchFieldException {
        if (crafterSkillGainRate != 1f) {
            Skill skill = (Skill)o;
            Creature creature = Server.getInstance().getCreatureOrNull(
                    ((Skills)ReflectionUtil.getPrivateField(skill, Skill.class.getDeclaredField("parent"))).getId());

            if (creature != null && CrafterTemplate.isCrafter(creature)) {
                double advanceMultiplicator = (double)args[0];
                float times = (float)args[2];
                advanceMultiplicator /= times * Servers.localServer.getSkillGainRate();
                advanceMultiplicator *= times * crafterSkillGainRate;
                args[0] = advanceMultiplicator;
            }
        }

        return method.invoke(o, args);
    }

    Object creatureCreation(Object o, Method method, Object[] args) throws InvocationTargetException, IllegalAccessException, NoSuchFieldException {
        CreatureCreationQuestion question = (CreatureCreationQuestion)args[0];
        Properties answers = ReflectionUtil.getPrivateField(question, Question.class.getDeclaredField("answer"));
        try {
            String templateIndexString = answers.getProperty("data1");
            String name = answers.getProperty("cname");
            if (name == null)
                answers.setProperty("name", "");
            else
                answers.setProperty("name", name);
            if (templateIndexString != null) {
                int templateIndex = Integer.parseInt(templateIndexString);
                List<CreatureTemplate> templates = ReflectionUtil.getPrivateField(question, CreatureCreationQuestion.class.getDeclaredField("cretemplates"));
                CreatureTemplate template = templates.get(templateIndex);

                if (template.getTemplateId() == CrafterTemplate.getTemplateId()) {
                    Creature responder = question.getResponder();
                    Item contract = Creature.createItem(contractTemplateId, (float)(10 + Server.rand.nextInt(80)));
                    responder.getInventory().insertItem(contract);
                    new CrafterHireQuestion(responder, contract.getWurmId()).sendQuestion();
                    return null;
                }
            }
        } catch (Exception e) {
            question.getResponder().getCommunicator().sendAlertServerMessage("An error occurred in the rifts of the void. The crafter was not created.");
            e.printStackTrace();
        }

        return method.invoke(o, args);
    }

    Object setFace(Object o, Method method, Object[] args) throws InvocationTargetException, IllegalAccessException {
        ByteBuffer buf = (ByteBuffer)args[0];
        buf.mark();
        long face = buf.getLong();
        long itemId = buf.getLong();

        if (itemId < 0) {
            Player player = ((Communicator)o).player;
            if (player != null) {
                Creature crafter = faceSetters.retrieveCrafterOrNull(player, itemId);

                if (crafter != null) {
                    if (CrafterTemplate.isCrafter(crafter)) {
                        if (CrafterDatabase.isDifferentFace(face, crafter)) {
                            try {
                                CrafterDatabase.setFaceFor(crafter, face);
                                CrafterDatabase.resetPlayerFace(player);
                                player.getCommunicator().sendNormalServerMessage("The crafter's face seems to shift about and takes a new form.");
                            } catch (SQLException e) {
                                logger.warning("Failed to set " + crafter.getName() + "'s face.");
                                e.printStackTrace();
                                player.getCommunicator().sendNormalServerMessage("The crafter's face seems to shift about, but then returns as it was.");
                            }
                        } else {
                            player.getCommunicator().sendNormalServerMessage("The crafter's face seems to shift about, but then returns as it was.");
                        }

                        return null;
                    }
                }
            } else {
                logger.warning("Why is player null?  This should never happen.");
            }
        }

        buf.reset();
        return method.invoke(o, args);
    }

    Object willLeaveServer(Object o, Method method, Object[] args) throws InvocationTargetException, IllegalAccessException {
        Item item = (Item)o;
        if (item.getTemplateId() == contractTemplateId) {
            boolean leaving = (boolean)args[0];

            if (item.getData() > 0) {
                if (leaving) {
                    item.setTransferred(true);
                }
                return false;
            }

            if (leaving) {
                item.setTransferred(false);
                return true;
            }
        }

        return method.invoke(o, args);
    }
}
