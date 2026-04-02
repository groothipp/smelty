package cloud.hipp.smelty.material;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side cache of alloy names synced from the server.
 */
public class ClientAlloyRegistry {
	private static final Map<String, String> compositionToName = new HashMap<>();

	public static void update(Map<String, String> data) {
		compositionToName.clear();
		compositionToName.putAll(data);
	}

	public static String getAlloyName(String normalizedKey) {
		return compositionToName.get(normalizedKey);
	}

	public static String getAlloyName(AlloyComposition composition) {
		return getAlloyName(composition.getNormalizedKey());
	}
}
