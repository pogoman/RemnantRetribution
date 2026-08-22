package remret;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.GateEntityPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.missions.GateCMD;
import com.fs.starfarer.api.util.Misc.Token;

/**
 * Rule command backing the Gate Control Fragment: a fragment cut from a dead
 * Nexus's command core repairs one dormant Gate, skipping the Academy
 * questline one gate at a time.
 *
 * Used from data/campaign/rules.csv both as a condition ("hasFragment", gating
 * the dialog option) and as the action ("repair").
 *
 * Repair mirrors what the questline grants, scoped to a single gate: the
 * global gates-active and player-can-use flags are set (both are also gated on
 * a Janus Device in cargo, so the first repair yields one - the fragment's
 * control assembly serving as the Janus-equivalent), but ONLY this gate gets
 * the per-gate scanned flag, and $canScanGates stays unset - so other gates
 * remain dormant until the player spends another fragment (or does the
 * questline for real).
 */
public class RemRetGateCMD extends BaseCommandPlugin {

	public static final String ITEM_ID = "remret_gate_fragment";
	public static final String JANUS_ID = "janus";

	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null || params.isEmpty()) return false;
		String command = params.get(0).getString(memoryMap);

		if ("hasFragment".equals(command)) {
			if (!RemRetConfig.gateFragments()) return false;
			CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
			return cargo.getQuantity(CargoItemType.SPECIAL, new SpecialItemData(ITEM_ID, null)) > 0;
		}

		if ("repair".equals(command)) {
			repair(dialog);
			return true;
		}

		return false;
	}

	protected void repair(InteractionDialogAPI dialog) {
		SectorEntityToken gate = dialog.getInteractionTarget();
		CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
		TextPanelAPI text = dialog.getTextPanel();

		// the fragment is consumed by the attunement
		cargo.removeItems(CargoItemType.SPECIAL, new SpecialItemData(ITEM_ID, null), 1);
		AddRemoveCommodity.addItemLossText(new SpecialItemData(ITEM_ID, null), 1, text);

		// both areGatesActive() and canUseGates() require a Janus Device in the
		// player's cargo on top of the memory flags - the first repair salvages
		// the fragment's intact control assembly as a working Janus-equivalent
		if (cargo.getQuantity(CargoItemType.SPECIAL, new SpecialItemData(JANUS_ID, null)) <= 0) {
			cargo.addSpecial(new SpecialItemData(JANUS_ID, null), 1);
			AddRemoveCommodity.addItemGainText(new SpecialItemData(JANUS_ID, null), 1, text);
		}

		MemoryAPI global = Global.getSector().getMemoryWithoutUpdate();
		global.set(GateEntityPlugin.GATES_ACTIVE, true);
		global.set(GateEntityPlugin.PLAYER_CAN_USE_GATES, true);

		// per-gate unlock: only this gate is attuned
		gate.getMemoryWithoutUpdate().set(GateEntityPlugin.GATE_SCANNED, true);
		GateEntityPlugin.addGateScanned();
		GateCMD.notifyScanned(gate);

		// touching Gate-network machinery draws the attention that normally
		// only late academy-story players attract: eligible for the abyssal
		// space oddity invite (rules.csv hook fires on next friendly dock)
		if (RemRetConfig.abyssUnlock()) {
			global.set("$remret_abyssInvite", true);
		}

		// attuning the gate's fracture machinery teaches the same trick the
		// academy storyline does: Transverse Jump (ability id fracture_jump)
		if (RemRetConfig.gateTeachesTransverseJump()
				&& !Global.getSector().getCharacterData().getAbilities()
						.contains(com.fs.starfarer.api.impl.campaign.ids.Abilities.TRANSVERSE_JUMP)) {
			Global.getSector().getCharacterData().addAbility(
					com.fs.starfarer.api.impl.campaign.ids.Abilities.TRANSVERSE_JUMP);
			text.addPara("Working through the gate's fracture calibration, your navigator "
					+ "grasps the underlying principle: a nascent gravity well can be opened "
					+ "anywhere. Transverse Jump ability gained.",
					com.fs.starfarer.api.util.Misc.getPositiveHighlightColor());
			RemRetDebug.log("Transverse Jump granted via gate repair.");
		}

		RemRetDebug.log("Gate repaired with control fragment: "
				+ (gate.getContainingLocation() == null ? gate.getName()
						: gate.getName() + " (" + gate.getContainingLocation().getName() + ")") + ".");
	}
}
