package net.minecraft.src;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.minecraft.client.Minecraft;

import org.lwjgl.input.Mouse;

public class InvTweaks extends InvTweaksObf {

    private static final Logger log = Logger.getLogger("InvTweaks");
    
    public static final String MINECRAFT_DIR = getMinecraftDir().getPath();
    public static final String CONFIG_FILE = MINECRAFT_DIR+"/InvTweaksConfig.txt";
    public static final String CONFIG_TREE_FILE = MINECRAFT_DIR+"/InvTweaksTree.txt";
    public static final String DEFAULT_CONFIG_FILE = "DefaultConfig.txt";
    public static final String DEFAULT_CONFIG_TREE_FILE = "DefaultTree.txt";
    public static final String INGAME_LOG_PREFIX = "InvTweaks: ";
    public static final Level DEFAULT_LOG_LEVEL = Level.WARNING;
    public static final Level DEBUG = Level.INFO;
    public static final int HOT_RELOAD_DELAY = 1000;
    public static final int INVENTORY_SIZE = 36;

	private static InvTweaks instance;
    private InvTweaksConfig config = null;
    private InvTweaksAlgorithm sortingAlgorithm = null;
    private long configLastModified = 0;
    private boolean configErrorsShown = false;
    private boolean onTickBusy = false;
	private int storedStackId = 0, storedStackDamage = -1, storedFocusedSlot = -1;
    
    public InvTweaks(Minecraft mc) {

    	super(mc);
    	
    	log.setLevel(DEFAULT_LOG_LEVEL);
    	
    	// Stor instance
    	instance = this;
    	
    	// Load config files
		tryLoading();
		
		// Load algorithm
    	sortingAlgorithm = new InvTweaksAlgorithm(mc, config);
    	
    	log.info("Mod initialItemStacked");
    	
    }
    
	public static InvTweaks getInstance() {
		return instance;
	}

    public final void onSortingKeyPressed()
    {
    	// Check config loading success & current GUI
    	if (config == null ||
    			!(getCurrentScreen() == null ||
    			getCurrentScreen() instanceof GuiContainer /* GuiContainer */)) {
    		return;
    	}
    	// Hot reload trigger
    	if (getConfigLastModified() != configLastModified)
    		tryLoading();
    	
    	// Config keywords error message
    	if (!configErrorsShown) {
    		showConfigErrors(config);
			configErrorsShown = true;
    	}

    	ItemStack selectedItem = getItemStack(
    			getMainInventory(),
    			getFocusedSlot());
    	
    	sortingAlgorithm.sortContainer(0); // TODO: Use window ID
		
    	// This needs to be remembered so that the
    	// autoreplace feature doesn't trigger
    	if (selectedItem != null && 
    			getMainInventory()[getFocusedSlot()] == null) {
    		storedStackId = 0;
    	}
    }

    /**
     * Autoreplace + middle click sorting
     */
	public void onTick() {
    	
    	if (config == null || onTickBusy == true)
    		return;
    	
    	if (Mouse.isButtonDown(2) && config.isMiddleClickEnabled())
    		onSortingKeyPressed();
    	
    	synchronized (this) {
    		
    	onTickBusy = true;
    	
    	ItemStack currentStack = getFocusedStack();
    	int currentStackId = (currentStack == null) ? 0 : getItemID(currentStack);
    	int currentStackDamage = (currentStack == null) ? 0 : getItemDamage(currentStack);
		int focusedSlot = getFocusedSlot();
		
    	// Auto-replace item stack
    	if (currentStackId != storedStackId
    			|| currentStackDamage != storedStackDamage) {
    		
	    	if (storedFocusedSlot != focusedSlot) { // Filter selection change
	    		storedFocusedSlot = focusedSlot;
	    	}
	    	else if ((currentStack == null ||
	    			getItemID(currentStack) == 281 && storedStackId == 282) // Handle eaten mushroom soup
	    			&&
	    			(getCurrentScreen() == null || // Filter open inventory or other window
	    			getCurrentScreen() instanceof GuiEditSign /* GuiEditSign */)) { 
		    		
	    			sortingAlgorithm.autoReplaceSlot(focusedSlot, 
	    					storedStackId, storedStackDamage);
	    		
	    		}
	    	}
	    	
	    	storedStackId = currentStackId;
	    	storedStackDamage = currentStackDamage;
    	}

    }
	
    public void logInGame(String message) {
    	addChatMessage(INGAME_LOG_PREFIX + message);
    }
    
    public InvTweaksConfig getConfig() {
		return config;
	}

	/**
     * Checks time of last edit for both configuration files.
     * @return
     */
    private long getConfigLastModified() {
    	return new File(CONFIG_FILE).lastModified() + 
    			new File(CONFIG_TREE_FILE).lastModified();
    }
    
    /**
     * Tries to load mod configuration from file, with error handling.
     * @param config
     */
    private boolean tryLoading() {

    	// Create missing files
    	
    	if (!new File(CONFIG_FILE).exists()
    			&& copyFile(DEFAULT_CONFIG_FILE, CONFIG_FILE)) {
    		logInGame(CONFIG_FILE+" missing, creating default one.");
		}
    	if (!new File(CONFIG_TREE_FILE).exists()
    			&& copyFile(DEFAULT_CONFIG_TREE_FILE, CONFIG_TREE_FILE)) {
    		logInGame(CONFIG_TREE_FILE+" missing, creating default one.");
		}
    	
    	// Load
    	
    	String error = null;
    	
		try {
	    	InvTweaksTree.loadTreeFromFile(CONFIG_TREE_FILE);
	    	if (config == null) {
	    		config = new InvTweaksConfig(CONFIG_FILE);
	    	}
			config.load();
			log.setLevel(config.getLogLevel());
			logInGame("Configuration reloaded");
			showConfigErrors(config);
		} catch (FileNotFoundException e) {
			error = "Config file not found";
		} catch (Exception e) {
			error = "Error while loading config: "+e.getMessage();
		}
			
		if (error != null) {
			logInGame(error);
			log.severe(error);
		    return false;
		}
		else {
			configLastModified = getConfigLastModified();
			return true;
		}
    }

    private void showConfigErrors(InvTweaksConfig config) {
    	Vector<String> invalid = config.getInvalidKeywords();
    	if (invalid.size() > 0) {
			String error = "Invalid keywords found: ";
			for (String keyword : config.getInvalidKeywords()) {
				error += keyword+" ";
			}
			logInGame(error);
    	}
    }
    
    private boolean copyFile(String resource, String destination) {

		String resourceContents = "";
		URL resourceUrl = InvTweaks.class.getResource(resource);
		
		// Extraction from minecraft.jar
		if (resourceUrl != null) {
			try  {
				Object o = resourceUrl.getContent();
				if (o instanceof InputStream) {
					InputStream content = (InputStream) o;
					while (content.available() > 0) {
						byte[] bytes = new byte[content.available()];
						content.read(bytes);
						resourceContents += new String(bytes);
					}
				}
			}
			catch (IOException e) {
				resourceUrl = null;
			}
		}
		
		// Extraction from mods folder
		if (resourceUrl == null) {
			
			File modFolder = new File(MINECRAFT_DIR+File.separatorChar+"mods");
			
			File[] zips = modFolder.listFiles();
			if (zips != null && zips.length > 0) {
				for (File zip : zips) {
					try {
						ZipFile invTweaksZip = new ZipFile(zip);
						ZipEntry zipResource = invTweaksZip.getEntry(resource);
						if (zipResource != null) {
							InputStream content = invTweaksZip.
									getInputStream(zipResource);
							while (content.available() > 0) {
								byte[] bytes = new byte[content.available()];
								content.read(bytes);
								resourceContents += new String(bytes);
							}
							break;
						}
					} catch (Exception e) {
						log.warning("Failed to extract "+resource+" from mod: "+e.getMessage());
					}
				}
			}
		}
		
		// Write to destination
		if (!resourceContents.isEmpty()) {
			try {
				FileWriter f = new FileWriter(destination);
				f.write(resourceContents);
				f.close();
				return true;
			}
			catch (IOException e) {
				logInGame("The mod won't work, because "+destination+" creation failed!");
				log.severe("Cannot create "+destination+" file: "+e.getMessage());
				return false;
			}
		}
		else {
			logInGame("The mod won't work, because "+resource+" could not be found!");
			log.severe("Cannot create "+destination+" file: "+resource+" not found");
			return false;
		}
   	}

}
