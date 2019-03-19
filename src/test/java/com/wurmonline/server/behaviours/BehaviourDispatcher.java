package com.wurmonline.server.behaviours;

import com.wurmonline.server.Items;
import com.wurmonline.server.NoSuchItemException;
import com.wurmonline.server.creatures.Communicator;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;

import java.util.ArrayList;
import java.util.List;

public class BehaviourDispatcher {

    private static List<Dispatch> dispatches = new ArrayList<>();

    private static class Dispatch {
        private final Item subject;
        private final Item target;
        private final short action;

        Dispatch(long subject, long target, short action) throws NoSuchItemException {
            if (subject != -10)
                this.subject = Items.getItem(subject);
            else
                this.subject = null;

            this.target = Items.getItem(target);
            this.action = action;
        }
    }

    public static void reset() {
        dispatches.clear();
    }

    public static boolean wasDispatched(Item item, short action) {
        for (Dispatch dispatch : dispatches) {
            if (dispatch.target == item && dispatch.action == action)
                return true;
        }
        return false;
    }

    public static boolean nothingDispatched() {
        return dispatches.isEmpty();
    }

    public static Item getLastDispatchSubject() {
        return dispatches.get(dispatches.size() - 1).subject;
    }

    public static Item getLastDispatchTarget() {
        return dispatches.get(0).target;
    }

    public static short getLastDispatchAction() {
        return dispatches.get(0).action;
    }

    public static void action(Creature creature, Communicator comm, long subject, long target, short action) throws NoSuchItemException {
        dispatches.add(new Dispatch(subject, target, action));
    }

}
