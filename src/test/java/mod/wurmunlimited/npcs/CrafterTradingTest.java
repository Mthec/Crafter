package mod.wurmunlimited.npcs;

import com.wurmonline.server.Constants;
import com.wurmonline.server.behaviours.BehaviourDispatcher;
import com.wurmonline.server.creatures.CrafterTradeHandler;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.TradeHandler;
import com.wurmonline.server.economy.Economy;
import com.wurmonline.server.items.CrafterTrade;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.TradingWindow;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.villages.Village;
import com.wurmonline.server.villages.Villages;
import mod.wurmunlimited.CrafterObjectsFactory;
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.junit.jupiter.api.BeforeEach;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class CrafterTradingTest {
    protected CrafterObjectsFactory factory;
    protected CrafterTrade trade;
    protected Player player = null;
    protected Player owner = null;
    protected Creature crafter = null;
    protected Item tool = null;
    protected CrafterTradeHandler handler;
    protected CrafterType crafterType;

    @BeforeEach
    void setUp() throws Exception {
        Constants.dbHost = ".";
        factory = new CrafterObjectsFactory();
        BehaviourDispatcher.reset();
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("skillCap"), 99.99999f);
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("basePrice"), 1);
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("minimumPriceModifier"), 0.0000001f);
        ReflectionUtil.setPrivateField(null, CrafterMod.class.getDeclaredField("paymentOption"), CrafterMod.PaymentOption.for_owner);
        (ReflectionUtil.<ConcurrentHashMap<Integer, Village>>getPrivateField(null, Villages.class.getDeclaredField("villages"))).clear();
        List<Byte> restrictedMaterials = ReflectionUtil.getPrivateField(null, CrafterMod.class.getDeclaredField("restrictedMaterials"));
        restrictedMaterials.clear();
        owner = factory.createNewPlayer();
        player = factory.createNewPlayer();
        tool = factory.createNewItem();
        player.getInventory().insertItem(tool);
        crafterType = new CrafterType(SkillList.SMITHING_BLACKSMITHING);
    }

    protected void init() {
        if (crafter == null)
            crafter = factory.createNewCrafter(owner, crafterType, 50);
    }

    protected void makeNewCrafterTrade() {
        try {
            init();
            trade = new CrafterTrade(player, crafter);
            player.setTrade(trade);
            crafter.setTrade(trade);
        } catch (WorkBook.NoWorkBookOnWorker e) {
            throw new RuntimeException(e);
        }
    }

    protected void makeNewOwnerCrafterTrade() {
        try {
            init();
            trade = new CrafterTrade(owner, crafter);
            owner.setTrade(trade);
            crafter.setTrade(trade);
        } catch (WorkBook.NoWorkBookOnWorker e) {
            throw new RuntimeException(e);
        }
    }

    protected void makeHandler() {
        try {
            Field tradeHandler = Creature.class.getDeclaredField("tradeHandler");
            tradeHandler.setAccessible(true);
            handler = new CrafterTradeHandler(crafter, trade);
            tradeHandler.set(crafter, handler);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    protected void addItemsToTrade() {
        try {
            Method addItemsToTrade = TradeHandler.class.getDeclaredMethod("addItemsToTrade");
            addItemsToTrade.setAccessible(true);
            addItemsToTrade.invoke(handler);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    protected void setSatisfied(Creature creature) {
        trade.setSatisfied(creature, true, trade.getCurrentCounter());
    }

    protected Item getOptionFromWindow(String optionStartsWith, TradingWindow window) {
        for (Item item : window.getItems()) {
            if (item.getName().startsWith(optionStartsWith))
                return item;
        }
        return null;
    }

    protected void selectOption(String optionStartsWith) {
        TradingWindow crafterWindow = trade.getTradingWindow(1);
        Item option = getOptionFromWindow(optionStartsWith, crafterWindow);
        assert option != null;
        crafterWindow.removeItem(option);
        trade.getCreatureOneRequestWindow().addItem(option);
    }

    protected void deselectOption(String optionStartsWith) {
        TradingWindow crafterWindow = trade.getTradingWindow(3);
        Item option = getOptionFromWindow(optionStartsWith, crafterWindow);
        assert option != null;
        crafterWindow.removeItem(option);
        trade.getTradingWindow(1).addItem(option);
    }

    protected List<Item> getMoneyForItem(Item item) {
        return Arrays.asList(Economy.getEconomy().getCoinsFor(handler.getTraderBuyPriceForItem(item)));
    }

    protected void setNotBalanced() {
        try {
            ReflectionUtil.setPrivateField(handler, CrafterTradeHandler.class.getDeclaredField("balanced"), false);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    protected long afterTax(long price) {
        return (long)(price * 0.9f);
    }

    protected CrafterType getAllArmourType() {
        return new CrafterType(SkillList.SMITHING_BLACKSMITHING);
    }

    protected void setJobDone(WorkBook workBook) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        setJobDone(workBook, workBook.iterator().next());
    }

    protected void setJobDone(WorkBook workBook, Job job) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        ReflectionUtil.callPrivateMethod(workBook, WorkBook.class.getDeclaredMethod("setDone", Job.class, Creature.class), job, crafter);
    }
}
