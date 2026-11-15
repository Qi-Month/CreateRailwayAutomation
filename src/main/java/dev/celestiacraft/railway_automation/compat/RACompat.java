package dev.celestiacraft.railway_automation.compat;

import dev.celestiacraft.railway_automation.compat.railways.RailwaysCompat;
import net.minecraftforge.fml.ModList;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RACompat {
	private static final Map<String, Boolean> LOADED = new ConcurrentHashMap<>();

	public static boolean isModLoaded(String modId) {
		// 按 modid 检测并缓存，避免在渲染等热路径反复查询模块列表
		return LOADED.computeIfAbsent(modId, id -> ModList.get().isLoaded(id));
	}

	public static boolean isRailwaysLoaded() {
		// SnR 是当前唯一的运行期可选兼容，其余 mod 判定统一走 isModLoaded
		return isModLoaded(RailwaysCompat.MOD_ID);
	}
}
