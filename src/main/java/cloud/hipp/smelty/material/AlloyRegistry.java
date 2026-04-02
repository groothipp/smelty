package cloud.hipp.smelty.material;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.PersistentStateType;

import java.util.HashMap;
import java.util.Map;

public class AlloyRegistry extends PersistentState {
	private final Map<String, String> compositionToName;
	private final Map<String, String> nameToComposition;

	public AlloyRegistry() {
		this(new HashMap<>());
	}

	public AlloyRegistry(Map<String, String> compositionToName) {
		this.compositionToName = new HashMap<>(compositionToName);
		this.nameToComposition = new HashMap<>();
		for (var entry : compositionToName.entrySet()) {
			nameToComposition.put(entry.getValue().toLowerCase(), entry.getKey());
		}
	}

	public void setAlloyName(String normalizedKey, String name) {
		String oldName = compositionToName.get(normalizedKey);
		if (oldName != null) {
			nameToComposition.remove(oldName.toLowerCase());
		}
		String oldKey = nameToComposition.get(name.toLowerCase());
		if (oldKey != null) {
			compositionToName.remove(oldKey);
		}
		compositionToName.put(normalizedKey, name);
		nameToComposition.put(name.toLowerCase(), normalizedKey);
		markDirty();
	}

	public String getAlloyName(String normalizedKey) {
		return compositionToName.getOrDefault(normalizedKey, null);
	}

	public String getAlloyName(AlloyComposition composition) {
		return getAlloyName(composition.getNormalizedKey());
	}

	public Map<String, String> getCompositionToName() {
		return compositionToName;
	}

	public static final Codec<AlloyRegistry> CODEC = RecordCodecBuilder.create(instance ->
			instance.group(
					Codec.unboundedMap(Codec.STRING, Codec.STRING)
							.fieldOf("named_alloys")
							.forGetter(AlloyRegistry::getCompositionToName)
			).apply(instance, AlloyRegistry::new)
	);

	public static final PersistentStateType<AlloyRegistry> TYPE = new PersistentStateType<>(
			"smelty_alloy_registry",
			AlloyRegistry::new,
			CODEC,
			null
	);

	public static AlloyRegistry get(ServerWorld serverWorld) {
		PersistentStateManager manager = serverWorld.getServer().getOverworld().getPersistentStateManager();
		return manager.getOrCreate(TYPE);
	}
}
