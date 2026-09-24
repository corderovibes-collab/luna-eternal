package net.pokereport.luna.buhonero;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.PokemonStore;
import com.cobblemon.mod.common.api.storage.adapter.flatfile.FileStoreAdapter;
import com.cobblemon.mod.common.api.storage.factory.FileBackedPokemonStoreFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.pokereport.luna.mixin.FileStoreAccessor;
import net.pokereport.luna.mixin.StoreManagerAccessor;
import java.util.concurrent.CompletableFuture;

/** Serializa en el hilo principal; escribe en la cola original de Cobblemon.
 * El recibo solo se confirma después de que la escritura termine sin error. */
public final class GuardarPokemon {
    private GuardarPokemon() {}
    @SuppressWarnings({"rawtypes","unchecked"})
    public static CompletableFuture<Void> guardar(ServerPlayerEntity p, PokemonStore store, java.util.UUID pokemon) {
        var recibos=p.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("lunaeternal/market-receipts");
        for(var f:((StoreManagerAccessor)Cobblemon.INSTANCE.getStorage()).luna$factories()) {
            if(f instanceof FileBackedPokemonStoreFactory<?> file && file.isCached(store)) {
                var acceso=(FileStoreAccessor)file;
                FileStoreAdapter adapter=acceso.luna$adapter();
                var data=adapter.serialize(store,p.getRegistryManager());
                return CompletableFuture.runAsync(() -> {
                    adapter.save(store.getClass(),store.getUuid(),data);
                    // Misma tarea y cola: ningún guardado posterior puede adelantarse al recibo.
                    try {ReciboEntrega.escribir(recibos,pokemon);}
                    catch(java.io.IOException e) {throw new java.util.concurrent.CompletionException(e);}
                },
                        acceso.luna$executor());
            }
        }
        throw new IllegalStateException("No hay almacenamiento persistente compatible para entregar el Pokémon");
    }
}
