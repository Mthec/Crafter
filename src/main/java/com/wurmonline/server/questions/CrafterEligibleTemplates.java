package com.wurmonline.server.questions;

import com.google.common.base.Joiner;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.items.ItemTemplate;
import com.wurmonline.server.items.ItemTemplateFactory;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class CrafterEligibleTemplates {
    private static Map<Integer, ItemTemplate> eligibleTemplates;
    private static String defaultTemplatesNames;
    private static int[] defaultOrder;
    private static boolean loaded = false;

    private final int[] order;
    private final String eligibleTemplatesNames;


    CrafterEligibleTemplates(Set<Integer> toRemove, String filter) {
        if (toRemove.isEmpty() && filter.length() == 0) {
            order = defaultOrder;
            eligibleTemplatesNames = defaultTemplatesNames;
        } else {
            Stream<ItemTemplate> templates = eligibleTemplates.values().stream();
            CrafterItemTemplateFilter test = new CrafterItemTemplateFilter(filter);
            templates = templates.filter(template -> !toRemove.contains(template.getTemplateId()) && test.matches(template.getName()));

            order = templates.sorted(ItemTemplate::compareTo).mapToInt(ItemTemplate::getTemplateId).toArray();
            eligibleTemplatesNames = Joiner.on(",").join(nameIterator(order));
        }
    }

    static void init() {
        if (!loaded) {
            eligibleTemplates = Arrays.stream(ItemTemplateFactory.getInstance().getTemplates())
                                    .filter(i ->
                                            !i.isInventory() && !i.isBodyPart() &&
                                            i.getTemplateId() != ItemList.altarHoly && i.getTemplateId() != ItemList.altarUnholy &&
                                            !i.isGuardTower() && !i.isServerPortal && !i.isNoImprove() && i.isRepairable()
                                    ).collect(Collectors.toMap(ItemTemplate::getTemplateId, i -> i));

            defaultOrder = eligibleTemplates.values().stream().sorted(ItemTemplate::compareTo).mapToInt(ItemTemplate::getTemplateId).toArray();
            defaultTemplatesNames = Joiner.on(",").join(nameIterator(defaultOrder));
            loaded = true;
        }
    }

    String getOptions() {
        return eligibleTemplatesNames;
    }

    ItemTemplate getTemplate(int templateIndex) throws ArrayIndexOutOfBoundsException {
        return eligibleTemplates.get(order[templateIndex]);
    }

    int getIndexOf(int templateId) {
        for (int i = 0; i < order.length; i++) {
            if (order[i] == templateId)
                return i;
        }
        return -1;
    }

    private static Iterator<String> nameIterator(int[] order) {
        return new Iterator<String>() {
            private int idx;

            @Override
            public boolean hasNext() {
                return idx < order.length;
            }

            @Override
            public String next() {
                ItemTemplate template = eligibleTemplates.get(order[idx++]);
                return template.getName();
            }
        };
    }
}
