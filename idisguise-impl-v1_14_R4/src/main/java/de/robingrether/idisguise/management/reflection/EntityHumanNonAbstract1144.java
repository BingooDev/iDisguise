package de.robingrether.idisguise.management.reflection;

import com.mojang.authlib.GameProfile;

import net.minecraft.server.v1_14_R1.EntityHuman;
import net.minecraft.server.v1_14_R1.World;

public class EntityHumanNonAbstract1144 extends EntityHuman {
	
	public EntityHumanNonAbstract1144(World world, GameProfile gameProfile) {
		super(world, gameProfile);
		getDataWatcher().set(bt, (byte)0x7f);
	}
	
	public boolean isSpectator() { return false; }
	
	public boolean u() { return false; }

	@Override
	public boolean isCreative() {
		// TODO Auto-generated method stub
		return false;
	}
	
}