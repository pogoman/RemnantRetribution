package remret;

import lunalib.lunaSettings.LunaSettings;

/**
 * Thin wrapper around LunaLib's settings API. This class references LunaLib
 * types directly, so it must ONLY be loaded when LunaLib is present - callers
 * gate every use behind {@link RemRetConfig#lunaAvailable()}, which keeps the
 * classloader from ever touching this class in a LunaLib-less install.
 */
class LunaConfigBridge {

	static Integer getInt(String key) {
		return LunaSettings.getInt(RemRetConfig.MOD_ID, key);
	}

	static Float getFloat(String key) {
		return LunaSettings.getFloat(RemRetConfig.MOD_ID, key);
	}

	static Boolean getBoolean(String key) {
		return LunaSettings.getBoolean(RemRetConfig.MOD_ID, key);
	}
}
