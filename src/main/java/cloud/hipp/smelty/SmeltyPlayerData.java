package cloud.hipp.smelty;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Uuids;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * World-level persistent data tracking per-player flags for Smelty.
 */
public class SmeltyPlayerData extends PersistentState {
	private final Set<UUID> receivedGuide;

	public SmeltyPlayerData() {
		this(new HashSet<>());
	}

	public SmeltyPlayerData(Set<UUID> receivedGuide) {
		this.receivedGuide = new HashSet<>(receivedGuide);
	}

	public boolean hasReceivedGuide(UUID playerId) {
		return receivedGuide.contains(playerId);
	}

	public void markGuideReceived(UUID playerId) {
		receivedGuide.add(playerId);
		markDirty();
	}

	private static final Codec<SmeltyPlayerData> CODEC = RecordCodecBuilder.create(instance ->
			instance.group(
					Uuids.CODEC.listOf().fieldOf("received_guide")
							.forGetter(data -> List.copyOf(data.receivedGuide))
			).apply(instance, list -> new SmeltyPlayerData(new HashSet<>(list)))
	);

	public static final PersistentStateType<SmeltyPlayerData> TYPE = new PersistentStateType<>(
			"smelty_player_data",
			SmeltyPlayerData::new,
			CODEC,
			null
	);

	public static SmeltyPlayerData get(ServerWorld world) {
		return world.getServer().getOverworld().getPersistentStateManager().getOrCreate(TYPE);
	}
}
