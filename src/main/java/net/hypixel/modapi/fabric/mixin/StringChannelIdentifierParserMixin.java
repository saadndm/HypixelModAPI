package net.hypixel.modapi.fabric.mixin;

import net.ornithemc.osl.core.api.util.NamespacedIdentifier;
import net.ornithemc.osl.core.api.util.NamespacedIdentifiers;
import net.ornithemc.osl.networking.api.StringChannelIdentifierParser;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = StringChannelIdentifierParser.class, remap = false)
public class StringChannelIdentifierParserMixin {
	@Inject(method = "fromString", at = @At("HEAD"), cancellable = true, remap = false)
	private static void parseHypixelIdentifier(String value, CallbackInfoReturnable<NamespacedIdentifier> cir) {
		if (value.startsWith("hypixel:") || value.startsWith("hyevent:")) {
			cir.setReturnValue(NamespacedIdentifiers.parse(value));
		}
	}

	@Inject(method = "toString", at = @At("HEAD"), cancellable = true, remap = false)
	private static void formatHypixelIdentifier(NamespacedIdentifier id, CallbackInfoReturnable<String> cir) {
		if (id.namespace().equals("hypixel") || id.namespace().equals("hyevent")) {
			cir.setReturnValue(id.toString());
		}
	}
}
