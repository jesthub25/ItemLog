package com.jestron.itemlog.command;

import com.jestron.itemlog.ItemLog;
import com.jestron.itemlog.config.Settings;
import com.jestron.itemlog.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * /itemlog                  → kendi logların
 * /itemlog <oyuncu>         → oyuncunun logları             (itemlog.others)
 * /itemlog <oyuncu> takas   → sen ↔ oyuncu                  (itemlog.takas)
 * /itemlog <a> takas <b>    → a ↔ b                         (itemlog.takas + itemlog.others)
 * /itemlog reload           → yeniden yükle                 (itemlog.reload)
 * /itemlogreload            → yeniden yükle                 (itemlog.reload)
 *
 * Oyuncu adı Mojang'a sorulmadan, önce çevrimiçi oyunculardan sonra kendi
 * veritabanımızdan (async) çözülür → ana thread asla bloklanmaz.
 */
public final class ItemLogCommand implements CommandExecutor, TabCompleter {

    private static final int MAX_SUGGESTIONS = 50;

    private final ItemLog plugin;

    public ItemLogCommand(ItemLog plugin) {
        this.plugin = plugin;
    }

    private static final class Target {
        final UUID uuid;
        final String name;

        Target(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("itemlogreload")) {
            reload(sender);
            return true;
        }

        if (args.length >= 1) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("reload") || sub.equals("yenile")) {
                reload(sender);
                return true;
            }
            if (sub.equals("yardim") || sub.equals("yardım") || sub.equals("help")) {
                help(sender);
                return true;
            }
        }

        if (!(sender instanceof Player)) {
            msg(sender, "&cBu komut oyun içinden kullanılmalıdır. Konsoldan sadece &f/itemlog reload&c.");
            return true;
        }
        final Player viewer = (Player) sender;

        if (!viewer.hasPermission("itemlog.use")) {
            msg(viewer, "&cBu komutu kullanma yetkiniz yok. &7(itemlog.use)");
            return true;
        }

        if (args.length == 0) {
            plugin.getGuiManager().openPlayerLog(viewer, viewer.getUniqueId(), viewer.getName());
            return true;
        }

        boolean takas = args.length >= 2 && args[1].equalsIgnoreCase("takas");
        if (args.length >= 2 && !takas) {
            help(viewer);
            return true;
        }

        if (takas) {
            if (!viewer.hasPermission("itemlog.takas")) {
                msg(viewer, "&cTakas logunu görme yetkiniz yok. &7(itemlog.takas)");
                return true;
            }
            if (args.length >= 3) {
                if (!viewer.hasPermission("itemlog.others")) {
                    msg(viewer, "&cBaşka oyuncular arası takası görme yetkiniz yok. &7(itemlog.others)");
                    return true;
                }
                final String nameB = args[2];
                resolve(viewer, args[0], a -> resolve(viewer, nameB, b -> {
                    if (a.uuid.equals(b.uuid)) {
                        msg(viewer, "&cİki farklı oyuncu belirtmelisiniz.");
                        return;
                    }
                    plugin.getGuiManager().openTrade(viewer, a.uuid, a.name, b.uuid, b.name);
                }));
                return true;
            }
            resolve(viewer, args[0], t -> {
                if (t.uuid.equals(viewer.getUniqueId())) {
                    msg(viewer, "&cKendinizle takas logu görüntüleyemezsiniz.");
                    return;
                }
                plugin.getGuiManager().openTrade(viewer, viewer.getUniqueId(), viewer.getName(), t.uuid, t.name);
            });
            return true;
        }

        if (args[0].equalsIgnoreCase(viewer.getName())) {
            plugin.getGuiManager().openPlayerLog(viewer, viewer.getUniqueId(), viewer.getName());
            return true;
        }
        if (!viewer.hasPermission("itemlog.others")) {
            msg(viewer, "&cBaşka oyuncuların loglarını görme yetkiniz yok. &7(itemlog.others)");
            return true;
        }
        resolve(viewer, args[0], t -> plugin.getGuiManager().openPlayerLog(viewer, t.uuid, t.name));
        return true;
    }

    /** İsim → UUID. Callback her zaman ana thread'de ve oyuncu hâlâ çevrimiçiyken çağrılır. */
    private void resolve(final Player viewer, final String name, final Consumer<Target> callback) {
        if (name.equalsIgnoreCase(viewer.getName())) {
            callback.accept(new Target(viewer.getUniqueId(), viewer.getName()));
            return;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            callback.accept(new Target(online.getUniqueId(), online.getName()));
            return;
        }
        if (name.length() > 64) {
            msg(viewer, "&cGeçersiz oyuncu adı.");
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final String[] found = plugin.getDatabase().findPlayer(name);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!viewer.isOnline()) return;
                if (found == null) {
                    msg(viewer, "&f" + name + " &cadlı oyuncuya ait hiç kayıt bulunamadı.");
                    return;
                }
                UUID uuid;
                try {
                    uuid = UUID.fromString(found[0]);
                } catch (IllegalArgumentException e) {
                    msg(viewer, "&cKayıttaki UUID bozuk: &f" + found[0]);
                    return;
                }
                callback.accept(new Target(uuid, found[1] == null ? name : found[1]));
            });
        });
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("itemlog.reload")) {
            msg(sender, "&cBu komutu kullanma yetkiniz yok. &7(itemlog.reload)");
            return;
        }
        plugin.reloadPlugin();
        Settings s = plugin.getConfigManager().get();
        msg(sender, "&aKonfigürasyon yeniden yüklendi.");
        sender.sendMessage(Text.color("&7Veritabanı: &f" + plugin.getDatabase().getTypeName()
                + " &8(değiştirmek için yeniden başlatma gerekir)"));
        sender.sendMessage(Text.color("&7Takip edilen materyal: &f" + s.getTrackedMaterials().size()));
        sender.sendMessage(Text.color("&7Bölge filtresi: &f"
                + (s.getZones().isEmpty() ? "yok (her yer)" : s.getZones().size() + " bölge")));
        sender.sendMessage(Text.color("&7Kayıt saklama: &f"
                + (s.getRetentionDays() <= 0 ? "süresiz" : s.getRetentionDays() + " gün")));
    }

    private void help(CommandSender sender) {
        msg(sender, "&fKomutlar:");
        sender.sendMessage(Text.color("&e/itemlog &7- kendi logların"));
        if (sender.hasPermission("itemlog.others"))
            sender.sendMessage(Text.color("&e/itemlog <oyuncu> &7- oyuncunun logları"));
        if (sender.hasPermission("itemlog.takas"))
            sender.sendMessage(Text.color("&e/itemlog <oyuncu> takas &7- sen ↔ oyuncu transferleri"));
        if (sender.hasPermission("itemlog.takas") && sender.hasPermission("itemlog.others"))
            sender.sendMessage(Text.color("&e/itemlog <a> takas <b> &7- a ↔ b transferleri"));
        if (sender.hasPermission("itemlog.reload"))
            sender.sendMessage(Text.color("&e/itemlog reload &7- konfigürasyonu yenile"));
    }

    private void msg(CommandSender sender, String text) {
        sender.sendMessage(plugin.getConfigManager().get().getPrefix() + Text.color(text));
    }

    // ── Tab ────────────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("itemlogreload") || !sender.hasPermission("itemlog.use")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            Set<String> out = new LinkedHashSet<>();
            String in = args[0].toLowerCase(Locale.ROOT);
            if (sender.hasPermission("itemlog.reload") && "reload".startsWith(in)) out.add("reload");
            if ("yardim".startsWith(in)) out.add("yardim");
            if (sender.hasPermission("itemlog.others") || sender.hasPermission("itemlog.takas")) {
                addNames(out, in);
            }
            return new ArrayList<>(out);
        }
        if (args.length == 2) {
            if (sender.hasPermission("itemlog.takas") && "takas".startsWith(args[1].toLowerCase(Locale.ROOT))
                    && !args[0].equalsIgnoreCase("reload")) {
                return Collections.singletonList("takas");
            }
            return Collections.emptyList();
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("takas")
                && sender.hasPermission("itemlog.takas") && sender.hasPermission("itemlog.others")) {
            Set<String> out = new LinkedHashSet<>();
            addNames(out, args[2].toLowerCase(Locale.ROOT));
            return new ArrayList<>(out);
        }
        return Collections.emptyList();
    }

    private void addNames(Set<String> out, String prefix) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (out.size() >= MAX_SUGGESTIONS) return;
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(p.getName());
        }
        for (String n : plugin.getNameCache()) {
            if (out.size() >= MAX_SUGGESTIONS) return;
            if (n.toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(n);
        }
    }
}
