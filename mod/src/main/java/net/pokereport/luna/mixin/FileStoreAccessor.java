package net.pokereport.luna.mixin;
import com.cobblemon.mod.common.api.storage.adapter.flatfile.FileStoreAdapter;
import com.cobblemon.mod.common.api.storage.factory.FileBackedPokemonStoreFactory;
import java.util.concurrent.ExecutorService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=FileBackedPokemonStoreFactory.class, remap=false)
public interface FileStoreAccessor {
    @Accessor("adapter") FileStoreAdapter<?> luna$adapter();
    @Accessor("saveExecutor") ExecutorService luna$executor();
}
