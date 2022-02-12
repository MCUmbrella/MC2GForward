package vip.floatationdevice.mc2gforward;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class I18nUtil
{
    private static FileConfiguration l;
    private static String lang = "en_US";

    public static String setLanguage(String language)
    {
        if (language == null || language.equals("")) return lang;
        lang = language;
        File langFile = new File(MC2GForward.instance.getDataFolder(), "lang_" + lang + ".yml");
        if (!langFile.exists()) MC2GForward.instance.saveResource("lang_" + lang + ".yml", false);
        l = YamlConfiguration.loadConfiguration(langFile);
        return lang;
    }

    public static String translate(String key)
    {
        String msg = l.getString(key);
        if (msg == null) return "[NO TRANSLATION: " + key + "]";
        return msg;
    }

    public static String getLanguage()
    {
        return lang;
    }
}
