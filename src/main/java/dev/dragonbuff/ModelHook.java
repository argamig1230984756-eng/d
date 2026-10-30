package dev.dragonbuff;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Optional BetterModel integration, done with reflection so DragonBuff still
 * compiles and runs when BetterModel is NOT installed (models are then skipped).
 */
public class ModelHook {

    private static final String[] MODEL_FILES = {"void_dragon.bbmodel", "void_wraith.bbmodel"};

    private final JavaPlugin plugin;
    private final Set<UUID> attached = new HashSet<>();
    private boolean warnedFail = false;

    public ModelHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("BetterModel");
    }

    /** Copies our .bbmodel files into plugins/BetterModel/models (never overwrites). */
    public void installModelFiles() {
        Plugin bm = Bukkit.getPluginManager().getPlugin("BetterModel");
        if (bm == null) return;
        File dir = new File(bm.getDataFolder(), "models");
        if (!dir.exists() && !dir.mkdirs()) return;

        boolean copied = false;
        for (String name : MODEL_FILES) {
            File out = new File(dir, name);
            if (out.exists()) continue;
            try (InputStream in = plugin.getResource("models/" + name)) {
                if (in == null) continue;
                Files.copy(in, out.toPath(), StandardCopyOption.REPLACE_EXISTING);
                copied = true;
                plugin.getLogger().info("Installed model " + name + " into BetterModel/models");
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not copy " + name + ": " + ex.getMessage());
            }
        }
        if (copied) {
            plugin.getLogger().warning("New models were installed. RESTART the server once so BetterModel "
                    + "loads them and builds its resource pack.");
        }
    }

    /** Attach a BetterModel model to an entity. Returns true if a tracker was created. */
    public boolean attach(Entity entity, String modelName, String animation) {
        if (!available() || entity == null) return false;
        if (!attached.add(entity.getUniqueId())) return true; // already done this session
        try {
            Class<?> bm = Class.forName("kr.toxicity.model.api.BetterModel");
            Object renderer = bm.getMethod("modelOrNull", String.class).invoke(null, modelName);
            if (renderer == null) {
                attached.remove(entity.getUniqueId());
                warnOnce("BetterModel has no model named '" + modelName + "'. Restart the server after the "
                        + "models are copied, then check the console for BetterModel errors.");
                return false;
            }

            Class<?> adapterCls = Class.forName("kr.toxicity.model.api.bukkit.platform.BukkitAdapter");
            Object adapted = null;
            for (Method m : adapterCls.getMethods()) {
                if (m.getName().equals("adapt") && m.getParameterCount() == 1
                        && Modifier.isStatic(m.getModifiers())
                        && m.getParameterTypes()[0].isInstance(entity)) {
                    adapted = m.invoke(null, entity);
                    break;
                }
            }
            if (adapted == null) throw new IllegalStateException("BukkitAdapter.adapt(Entity) not found");

            Object tracker = null;
            for (String mname : new String[]{"getOrCreate", "create"}) {
                for (Method m : renderer.getClass().getMethods()) {
                    if (m.getName().equals(mname) && m.getParameterCount() == 1
                            && m.getParameterTypes()[0].isInstance(adapted)) {
                        tracker = m.invoke(renderer, adapted);
                        break;
                    }
                }
                if (tracker != null) break;
            }
            if (tracker == null) throw new IllegalStateException("could not create tracker");

            playAnimation(tracker, animation);
            return true;
        } catch (Throwable t) {
            attached.remove(entity.getUniqueId());
            warnOnce("Could not attach model '" + modelName + "': " + t);
            return false;
        }
    }

    private void playAnimation(Object tracker, String animation) {
        if (animation == null || animation.isEmpty()) return;
        try {
            for (Method m : tracker.getClass().getMethods()) {
                if (!m.getName().equals("animate") || !m.getParameterTypes()[0].equals(String.class)) continue;
                if (m.getParameterCount() == 1) {
                    m.invoke(tracker, animation);
                    return;
                }
                if (m.getParameterCount() == 2) {
                    Class<?> modCls = m.getParameterTypes()[1];
                    try {
                        Field f = modCls.getField("DEFAULT");
                        m.invoke(tracker, animation, f.get(null));
                        return;
                    } catch (NoSuchFieldException ignored) {
                        // try next overload
                    }
                }
            }
        } catch (Throwable t) {
            warnOnce("Model attached, but animation '" + animation + "' failed: " + t);
        }
    }

    public void forget(UUID id) {
        attached.remove(id);
    }

    public void retainOnly(Set<UUID> alive) {
        attached.retainAll(alive);
    }

    private void warnOnce(String msg) {
        if (warnedFail) return;
        warnedFail = true;
        plugin.getLogger().warning(msg);
    }
}
