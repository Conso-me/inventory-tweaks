package net.invtweaks.logic;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import net.invtweaks.config.InvTweaksConfig;
import net.invtweaks.library.ContainerManager;
import net.invtweaks.library.ContainerManager.ContainerSection;
import net.invtweaks.library.Obfuscation;
import net.minecraft.client.Minecraft;
import net.minecraft.src.GuiContainer;
import net.minecraft.src.InvTweaks;
import net.minecraft.src.ItemStack;
import net.minecraft.src.Slot;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * 
 * @author Jimeo Wan
 *
 */
public class ShortcutsHandler extends Obfuscation {

    private ShortcutType defaultAction = ShortcutType.MOVE_ONE_STACK;
    private ShortcutType defaultDestination = null;
    
    // Context attributes
    private ContainerManager container;
    private ContainerSection fromSection;
    private int fromIndex;
    private InvTweaksConfig config;
    private ShortcutType shortcutType;
    private ContainerSection toSection;

    private enum ShortcutType {
        MOVE_ONE_STACK,
        MOVE_ONE_ITEM,
        MOVE_ALL_ITEMS,
        MOVE_UP,
        MOVE_DOWN,
        MOVE_TO_EMPTY_SLOT,
        DROP
    }
    
    /**
     * Allows to monitor the keys related to shortcuts
     */
    private Map<Integer, Boolean> shortcutKeysStatus;
    
    /**
     * Stores shortcuts mappings
     */
    private Map<ShortcutType, List<Integer>> shortcuts;
    
    public ShortcutsHandler(Minecraft mc, InvTweaksConfig config) {
        super(mc);
        this.config = config;
        reset();
    }
    
    public void reset() {
        
        shortcutKeysStatus = new HashMap<Integer, Boolean>();
        shortcuts = new HashMap<ShortcutType, List<Integer>>();
        
        Map<String, String> keys = config.getProperties(
                InvTweaksConfig.PROP_SHORTCUT_PREFIX);
        for (String key : keys.keySet()) {
            
            String value = keys.get(key);
            
            if (value.equals(InvTweaksConfig.VALUE_DEFAULT)) {
                // Customize default behaviour
                ShortcutType newDefault = propNameToShortcutType(key);
                if (newDefault == ShortcutType.MOVE_ALL_ITEMS
                        || newDefault == ShortcutType.MOVE_ONE_ITEM
                        || newDefault == ShortcutType.MOVE_ONE_STACK) {
                    defaultAction = newDefault;
                }
                else if (newDefault == ShortcutType.MOVE_DOWN
                        || newDefault == ShortcutType.MOVE_UP) {
                    defaultDestination = newDefault;
                }
            }
            else {
                // Register shortcut mappings
                String[] keyNames = keys.get(key).split("[ ]*,[ ]*");
                List<Integer> keyBindings = new LinkedList<Integer>();
                for (String keyName : keyNames) {
                    // Accept both KEY_### and ###, in case someone
                    // takes the LWJGL Javadoc at face value
                    keyBindings.add(Keyboard.getKeyIndex(
                            keyName.replace("KEY_", "")));
                }
                ShortcutType shortcutType = propNameToShortcutType(key);
                if (shortcutType != null) {
                    shortcuts.put(propNameToShortcutType(key), keyBindings);
                }
                
                // Register key status listener
                for (Integer keyCode : keyBindings) {
                    shortcutKeysStatus.put(keyCode, false);
                }
            }
            
        }
    }
    
    public void handleShortcut(GuiContainer guiScreen) {

        // IMPORTANT: This is called before the default action is executed.
        
        // Update keys statuses
        for (int keyCode : shortcutKeysStatus.keySet()) {
            if (Keyboard.isKeyDown(keyCode)) {
                if (!shortcutKeysStatus.get(keyCode)) {
                    shortcutKeysStatus.put(keyCode, true);
                }
            }
            else {
                shortcutKeysStatus.put(keyCode, false);
            }
        }
        
        // Initialization
        int ex = Mouse.getEventX(), ey = Mouse.getEventY();
        int x = (ex * guiScreen.width) / mc.displayWidth;
        int y = guiScreen.height - (ey * guiScreen.height) / mc.displayHeight - 1;
        boolean shortcutValid = false;
        
        // Check that the slot is not empty
        Slot slot = getSlotAtPosition((GuiContainer) guiScreen, x, y);
        
        if (slot != null) {
    
            // Choose shortcut type
            ShortcutType shortcutType = defaultAction;
            if (isActive(ShortcutType.MOVE_ALL_ITEMS)) {
                shortcutType = ShortcutType.MOVE_ALL_ITEMS;
                shortcutValid = true;
            }
            else if (isActive(ShortcutType.MOVE_ONE_ITEM)) {
                shortcutType = ShortcutType.MOVE_ONE_ITEM;
                shortcutValid = true;
            }
            
            // Choose target section
            try {
                ContainerManager container = new ContainerManager(mc);
                ContainerSection srcSection = container.getSlotSection(slot.slotNumber);
                ContainerSection destSection = null;
    
                // Set up available sections
                Vector<ContainerSection> availableSections = new Vector<ContainerSection>();
                if (container.isSectionAvailable(ContainerSection.CHEST)) {
                    availableSections.add(ContainerSection.CHEST);
                }
                else if (container.isSectionAvailable(ContainerSection.CRAFTING_IN)) {
                    availableSections.add(ContainerSection.CRAFTING_IN);
                }
                else if (container.isSectionAvailable(ContainerSection.FURNACE_IN)) {
                    availableSections.add(ContainerSection.FURNACE_IN);
                }
                availableSections.add(ContainerSection.INVENTORY_NOT_HOTBAR);
                availableSections.add(ContainerSection.INVENTORY_HOTBAR);
                
                // Check for destination modifiers
                int destinationModifier = 0; 
                if (isActive(ShortcutType.MOVE_UP)
                        || defaultDestination == ShortcutType.MOVE_UP) {
                    destinationModifier = -1;
                }
                else if (isActive(ShortcutType.MOVE_DOWN)
                        || defaultDestination == ShortcutType.MOVE_DOWN) {
                    destinationModifier = 1;
                }
                
                if (destinationModifier == 0) {
                    // Default behavior
                    switch (srcSection) {
                   
                    case INVENTORY_NOT_HOTBAR:
                        if (availableSections.get(0) != ContainerSection.INVENTORY_NOT_HOTBAR) {
                            destSection = availableSections.get(0);
                        }
                        else {
                            destSection = ContainerSection.INVENTORY_HOTBAR;
                        }
                        break;
                        
                    case INVENTORY_HOTBAR:
                        destSection = availableSections.get(0);
                        break;
                        
                    default:
                        destSection = ContainerSection.INVENTORY;
                    }
                }
                
                else {
                    // Specific destination
                    int srcSectionIndex = availableSections.indexOf(srcSection);
                    if (srcSectionIndex != -1) {
                        shortcutValid = true;
                        destSection = availableSections.get(
                                (availableSections.size() + srcSectionIndex + 
                                        destinationModifier) % availableSections.size());
                    }
                }
                
                if (shortcutValid) {
                    
                    initAction(slot.slotNumber, shortcutType, destSection);
                    
                    // Drop or move
                    if (isActive(ShortcutType.DROP)) { 
                        drop();
                    } else {
                        move(Mouse.isButtonDown(1));
                    }
                    
                    // Reset mouse status to prevent default action.
                    Mouse.destroy();
                    Mouse.create();
                    
                    // Fixes a tiny glitch (Steve looks for a short moment
                    // at [0, 0] because of the mouse reset).
                    Mouse.setCursorPosition(ex, ey);
                }
    
            } catch (Exception e) {
               InvTweaks.logInGameErrorStatic("Failed to trigger shortcut", e);
            }
        }
            
    }

    private void move(boolean separateStacks) throws Exception {
        
        // TODO: If separate stacks = false
        
        int toIndex = -1;
        ItemStack fromStack = container.getItemStack(fromSection, fromIndex);
        
        synchronized(this) {

            if (separateStacks) {
                toIndex = container.getFirstEmptyIndex(toSection); // TODO merge case
                // Switch to FURNACE_IN to FURNACE_FUEL if the slot is taken
                if (toIndex == -1 && toSection == ContainerSection.FURNACE_IN) {
                    toSection =  ContainerSection.FURNACE_FUEL;
                    toIndex = container.getFirstEmptyIndex(toSection);
                }
            }
            else {
                toIndex = getNextAvailableIndex();
            }
            
            if (toIndex != -1) {
                switch (shortcutType) {
                
                case MOVE_ONE_STACK:
                    container.move(fromSection, fromIndex, toSection, toIndex);
                    break;
    
                case MOVE_ONE_ITEM:
                    container.moveSome(fromSection, fromIndex, toSection, toIndex, 1);
                    break;
                    
                case MOVE_ALL_ITEMS:
                    for (Slot slot : container.getSectionSlots(fromSection)) {
                        if (slot.getHasStack() && fromStack.isItemEqual(slot.getStack())
                                && toIndex != -1) {
                            boolean moveResult = container.move(fromSection,
                                    container.getSlotIndex(slot.slotNumber),
                                    toSection, toIndex);
                            if (!moveResult) {
                                break;
                            }
                            if (separateStacks) {
                                toIndex = container.getFirstEmptyIndex(toSection);
                            }
                            else {
                                // TODO
                            }
                        }
                    }
                    
                }
            }
            
        }
    }
    
    private int getNextAvailableIndex() {
        container.getSectionSlots(toSection);
        return 0;
    }

    private void drop() throws Exception {
        synchronized(this) {
            // TODO Drop
        }
    }
    
    private boolean isActive(ShortcutType shortcutType) {
        for (Integer keyCode : shortcuts.get(shortcutType)) {
            if (shortcutKeysStatus.get(keyCode)) {
                return true;
            }
        }
        return false;
    }

    private void initAction(int fromSlot, ShortcutType shortcutType, ContainerSection destSection) throws Exception {
        
        // Set up context
        this.container = new ContainerManager(mc);
        this.fromSection = container.getSlotSection(fromSlot);
        this.fromIndex = container.getSlotIndex(fromSlot);
        this.shortcutType = shortcutType;
        this.toSection = destSection;
        
        // Put hold stack down
        if (getHoldStack() != null) {
            
            container.leftClick(fromSection, fromIndex);
            
            // Sometimes (ex: crafting output) we can't put back the item
            // in the slot, in that case choose a new one.
            if (getHoldStack() != null) {
                int firstEmptyIndex = container.getFirstEmptyIndex(ContainerSection.INVENTORY);
                if (firstEmptyIndex != -1) {
                   fromSection = ContainerSection.INVENTORY;
                   fromSlot = firstEmptyIndex;
                   container.leftClick(fromSection, fromSlot);
                   
                }
                else {
                    throw new Exception("Couldn't put hold item down");
                }
            }
        }
    }
    
    private Slot getSlotAtPosition(GuiContainer guiContainer, int i, int j) { 
        // Copied from GuiContainer
        for (int k = 0; k < guiContainer.inventorySlots.slots.size(); k++) {
            Slot slot = (Slot)guiContainer.inventorySlots.slots.get(k);
            if (InvTweaks.getIsMouseOverSlot(guiContainer, slot, i, j)) {
                return slot;
            }
        }
        return null;
    }

    private ShortcutType propNameToShortcutType(String property) {
        if (property.equals(InvTweaksConfig.PROP_SHORTCUT_ALL_ITEMS)) {
            return ShortcutType.MOVE_ALL_ITEMS;
        } else if (property.equals(InvTweaksConfig.PROP_SHORTCUT_DOWN)) {
            return ShortcutType.MOVE_DOWN;
        } else if (property.equals(InvTweaksConfig.PROP_SHORTCUT_DROP)) {
            return ShortcutType.DROP;
        } else if (property.equals(InvTweaksConfig.PROP_SHORTCUT_ONE_ITEM)) {
            return ShortcutType.MOVE_ONE_ITEM;
        } else if (property.equals(InvTweaksConfig.PROP_SHORTCUT_ONE_STACK)) {
            return ShortcutType.MOVE_ONE_STACK;
        } else if (property.equals(InvTweaksConfig.PROP_SHORTCUT_UP)) {
            return ShortcutType.MOVE_UP;
        } else {
            return null;
        }
    }
    
}