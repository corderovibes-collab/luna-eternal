package net.pokereport.luna.buhonero;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.storage.PokemonStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.pokereport.luna.LunaEternal;
import net.pokereport.luna.crianza.CrianzaService;
import net.pokereport.luna.economy.Currency;
import net.pokereport.luna.economy.EconomyException;
import net.pokereport.luna.pokedex.ClaveEspecie;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static net.pokereport.luna.buhonero.ReglasMercadoNegro.*;

/** Cada oferta pertenece a un jugador. El cliente nunca elige especie, nivel, saldo o precio. */
public final class BuhoneroService {
    private static final Set<UUID> OCUPADOS=ConcurrentHashMap.newKeySet();
    private static final Map<UUID,Long> CONSULTAS=new ConcurrentHashMap<>();
    private static volatile List<String> especies=List.of();
    private static ExecutorService io;
    private static volatile boolean detenido;
    private record Oferta(long ciclo,String especie,int nivel,long ultima) {}
    private record Entrega(long ciclo,UUID uuid,byte[] nbt) {}
    private record Preparado(UUID uuid,byte[] nbt) {}
    private BuhoneroService() {}

    public static void registrar() {
        BuhoneroNet.servidor();
        if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("rctmod")) BuhoneroNpc.servidor();
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            detenido=false;
            io=new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(64),r -> {
                var t=new Thread(r,"luna-buhonero-io"); t.setDaemon(true); return t;
            },new ThreadPoolExecutor.AbortPolicy());
            List<String> catalogo=new ArrayList<>();
            for(var sp:PokemonSpecies.getImplemented()) {
                if(!especiePermitida(sp.getNationalPokedexNumber())) continue;
                var labels=sp.getLabels();
                if(labels.stream().anyMatch(x -> Set.of("legendary","mythical","ultra_beast","paradox","restricted").contains(x))) continue;
                String id=ClaveEspecie.de(sp);
                if(!id.isBlank()) catalogo.add(id);
            }
            especies=List.copyOf(catalogo);
            LunaEternal.LOG.info("Buhonero listo: {} especies Gen I-II, 10000 Plata, nivel 1-20, oferta personal 24h",especies.size());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            detenido=true;
            if(io!=null) io.shutdownNow();
            OCUPADOS.clear(); CONSULTAS.clear();
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h,s) -> CONSULTAS.remove(h.player.getUuid()));
    }

    /** Invocado siempre desde el hilo principal. Limita peticiones antes de ir a SQL. */
    public static void atender(ServerPlayerEntity p,long esperado,boolean comprar,boolean abrir) {
        if(detenido || io==null || especies.isEmpty() || !BuhoneroNpc.cerca(p)) return;
        if(!ServerPlayNetworking.canSend(p,BuhoneroNet.Oferta.ID)) {
            if(abrir) p.sendMessage(Text.literal("§6Actualiza el juego desde el launcher para abrir el Mercado Negro."),false);
            return;
        }
        UUID uuid=p.getUuid(); long ahora=System.currentTimeMillis();
        if(ahora-CONSULTAS.getOrDefault(uuid,0L)<700 || !OCUPADOS.add(uuid)) return;
        CONSULTAS.put(uuid,ahora);
        try { io.execute(() -> operar(p,esperado,comprar,abrir)); }
        catch(RejectedExecutionException e) {
            OCUPADOS.remove(uuid);
            p.sendMessage(Text.literal("§7El Buhonero está ocupado. Inténtalo en un momento."),false);
        }
    }
    private static void operar(ServerPlayerEntity p,long esperado,boolean comprar,boolean abrir) {
        long id=0;
        try {
            id=LunaEternal.players().resolve(p.getUuid(),p.getGameProfile().getName());
            String mensaje="";
            boolean pendientes=entregarPendientes(p,id);
            Oferta oferta=oferta(id);
            if(comprar) {
                if(pendientes) mensaje="Libera espacio en el equipo o PC y vuelve a hablar conmigo.";
                else {
                    Oferta elegida=oferta;
                    Preparado listo=principal(p.getServer(),() -> {
                        if(!BuhoneroNpc.cerca(p)) throw new IllegalStateException("Acércate al Buhonero.");
                        var especie=ClaveEspecie.buscar(elegida.especie);
                        if(especie==null || !especiePermitida(especie.getNationalPokedexNumber()) || !nivelPermitido(elegida.nivel))
                            throw new IllegalStateException("Esta oferta ya no está disponible.");
                        Pokemon pokemon=PokemonProperties.Companion.parse(elegida.especie+" level="+elegida.nivel+" shiny=false").create();
                        try { return new Preparado(pokemon.getUuid(),CrianzaService.serializarPokemon(pokemon,p.getRegistryManager())); }
                        catch(Exception e) { throw new CompletionException(e); }
                    });
                    mensaje=comprar(id,esperado,listo);
                    pendientes=entregarPendientes(p,id);
                    if(pendientes) mensaje="Compra guardada. Libera espacio y vuelve para recibirlo sin pagar otra vez.";
                    oferta=oferta(id);
                }
            } else if(pendientes) mensaje="Tienes una entrega pendiente. Libera espacio y vuelve a hablar conmigo.";
            enviar(p,id,oferta,pendientes,abrir,mensaje);
        } catch(Exception e) {
            Throwable causa=e;
            while(causa.getCause()!=null) causa=causa.getCause();
            String mensaje=causa instanceof EconomyException ee && ee.kind==EconomyException.Kind.INSUFFICIENT_FUNDS
                    ? "No tienes suficiente Plata. Necesitas 10.000."
                    : causa instanceof Regla ? causa.getMessage() : "No pude completar la operación. Vuelve a hablar conmigo.";
            if(!(causa instanceof Regla) && !(causa instanceof EconomyException))
                LunaEternal.LOG.error("Buhonero: operación de {} pendiente de reintento",p.getUuid(),e);
            try {
                if(id!=0) enviar(p,id,oferta(id),hayPendiente(id),abrir,mensaje);
                else principal(p.getServer(),() -> {p.sendMessage(Text.literal(mensaje),false);return null;});
            } catch(Exception fallo) { LunaEternal.LOG.warn("Buhonero: no se pudo enviar estado: {}",fallo.toString()); }
        } finally { OCUPADOS.remove(p.getUuid()); }
    }
    private static Oferta leer(Connection c,long id) throws SQLException {
        try(var q=c.prepareStatement("SELECT cycle_ms,species,level,last_purchase_ms FROM black_market_offer WHERE player_id=? FOR UPDATE")) {
            q.setLong(1,id);
            try(var r=q.executeQuery()) {
                return r.next()?new Oferta(r.getLong(1),r.getString(2),r.getInt(3),r.getLong(4)):null;
            }
        }
    }
    private static String sortear(String anterior) {
        var posibles=especies.stream().filter(s -> !s.equals(anterior)).toList();
        if(posibles.isEmpty()) posibles=especies;
        return posibles.get(ThreadLocalRandom.current().nextInt(posibles.size()));
    }
    private static Oferta oferta(long id) throws SQLException {
        try(var c=LunaEternal.database().connection()) {
            c.setAutoCommit(false);
            try {
                long ahora=System.currentTimeMillis();
                try(var q=c.prepareStatement("INSERT IGNORE INTO black_market_offer(player_id,cycle_ms,species,level) VALUES(?,?,?,?)")) {
                    q.setLong(1,id);q.setLong(2,ahora);q.setString(3,sortear(""));q.setInt(4,ThreadLocalRandom.current().nextInt(1,21));q.executeUpdate();
                }
                Oferta o=leer(c,id);
                if(ahora>=o.ciclo+DIA) {
                    o=new Oferta(cicloActual(o.ciclo,ahora),sortear(o.especie),ThreadLocalRandom.current().nextInt(1,21),o.ultima);
                    try(var q=c.prepareStatement("UPDATE black_market_offer SET cycle_ms=?,species=?,level=? WHERE player_id=?")) {
                        q.setLong(1,o.ciclo);q.setString(2,o.especie);q.setInt(3,o.nivel);q.setLong(4,id);q.executeUpdate();
                    }
                }
                c.commit();return o;
            } catch(Exception e) { c.rollback();throw e; }
        }
    }
    private static String comprar(long id,long esperado,Preparado listo) throws Exception {
        try(var c=LunaEternal.database().connection()) {
            c.setAutoCommit(false);
            try {
                Oferta o=leer(c,id);long ahora=System.currentTimeMillis();
                if(o==null || o.ciclo!=esperado || ahora>=o.ciclo+DIA) throw new Regla("La oferta cambió. Revisa el nuevo Pokémon antes de comprar.");
                if(!puedeComprar(o.ciclo,o.ultima,ahora)) throw new Regla("Ya compraste. Deben pasar 24 horas desde tu última compra.");
                try(var q=c.prepareStatement("SELECT 1 FROM black_market_purchase WHERE player_id=? AND (cycle_ms=? OR delivered=FALSE) LIMIT 1")) {
                    q.setLong(1,id);q.setLong(2,o.ciclo);
                    try(var r=q.executeQuery()) { if(r.next()) throw new Regla("Esta compra ya está registrada. No se cobrará otra vez."); }
                }
                LunaEternal.economy().applyInTransaction(c,id,Currency.POKEDOLLAR,-PRECIO,
                        "Pokémon del Buhonero","black_market",o.ciclo,"buhonero:"+id+":"+o.ciclo);
                try(var q=c.prepareStatement("INSERT INTO black_market_purchase(player_id,cycle_ms,pokemon_uuid,pokemon_nbt,price,purchased_ms) VALUES(?,?,?,?,?,?)")) {
                    q.setLong(1,id);q.setLong(2,o.ciclo);q.setString(3,listo.uuid.toString());q.setBytes(4,listo.nbt);
                    q.setLong(5,PRECIO);q.setLong(6,ahora);q.executeUpdate();
                }
                try(var q=c.prepareStatement("UPDATE black_market_offer SET last_purchase_ms=? WHERE player_id=?")) {
                    q.setLong(1,ahora);q.setLong(2,id);q.executeUpdate();
                }
                c.commit();return "Trato hecho. El Pokémon ya es tuyo. Cuídalo bien.";
            } catch(Exception e) {c.rollback();throw e;}
        }
    }
    private static boolean hayPendiente(long id) throws SQLException {
        try(var c=LunaEternal.database().connection();var q=c.prepareStatement("SELECT 1 FROM black_market_purchase WHERE player_id=? AND delivered=FALSE LIMIT 1")) {
            q.setLong(1,id);try(var r=q.executeQuery()){return r.next();}
        }
    }
    private static boolean entregarPendientes(ServerPlayerEntity p,long id) throws Exception {
        List<Entrega> lista=new ArrayList<>();
        try(var c=LunaEternal.database().connection();var q=c.prepareStatement("SELECT cycle_ms,pokemon_uuid,pokemon_nbt FROM black_market_purchase WHERE player_id=? AND delivered=FALSE ORDER BY purchased_ms LIMIT 8")) {
            q.setLong(1,id);try(var r=q.executeQuery()){while(r.next()) lista.add(new Entrega(r.getLong(1),UUID.fromString(r.getString(2)),r.getBytes(3)));}
        }
        for(var e:lista) {
            var carpeta=p.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT).resolve("lunaeternal/market-receipts");
            if(ReciboEntrega.existe(carpeta,e.uuid)) {marcarEntregado(id,e.ciclo);continue;}
            CompletableFuture<Void> guardado=principal(p.getServer(),() -> {
                if(p.isRemoved() || !BuhoneroNpc.cerca(p)) return null;
                var storage=Cobblemon.INSTANCE.getStorage();var party=storage.getParty(p);var pc=storage.getPC(p);
                PokemonStore<?> destino;
                if(party.get(e.uuid)!=null) destino=party;
                else if(pc.get(e.uuid)!=null) destino=pc;
                else {
                    try {
                        Pokemon mon=CrianzaService.deserializarPokemon(e.nbt,p.getRegistryManager());
                        if(!mon.getUuid().equals(e.uuid)) throw new IllegalStateException("UUID del recibo no coincide");
                        if(party.add(mon)) destino=party;
                        else if(pc.add(mon)) destino=pc;
                        else return null;
                    } catch(Exception fallo) {throw new CompletionException(fallo);}
                }
                return GuardarPokemon.guardar(p,destino,e.uuid);
            });
            if(guardado==null) return true;
            guardado.get(30,TimeUnit.SECONDS);
            marcarEntregado(id,e.ciclo);
        }
        return false;
    }
    private static void marcarEntregado(long id,long ciclo) throws SQLException {
        try(var c=LunaEternal.database().connection();var q=c.prepareStatement("UPDATE black_market_purchase SET delivered=TRUE WHERE player_id=? AND cycle_ms=?")) {
            q.setLong(1,id);q.setLong(2,ciclo);q.executeUpdate();
        }
    }
    private static void enviar(ServerPlayerEntity p,long id,Oferta o,boolean pendiente,boolean abrir,String mensaje) throws Exception {
        long balance=LunaEternal.economy().balance(id,Currency.POKEDOLLAR);
        boolean comprada;
        try(var c=LunaEternal.database().connection();var q=c.prepareStatement("SELECT 1 FROM black_market_purchase WHERE player_id=? AND cycle_ms=?")) {
            q.setLong(1,id);q.setLong(2,o.ciclo);try(var r=q.executeQuery()){comprada=r.next();}
        }
        var estado=new BuhoneroNet.Oferta(o.ciclo,o.especie,o.nivel,PRECIO,balance,System.currentTimeMillis(),
                o.ultima==0?0:o.ultima+DIA,comprada,pendiente,abrir,mensaje);
        principal(p.getServer(),() -> {
            if(!p.isRemoved() && BuhoneroNpc.cerca(p) && ServerPlayNetworking.canSend(p,BuhoneroNet.Oferta.ID))
                ServerPlayNetworking.send(p,estado);
            return null;
        });
    }
    private static <T>T principal(MinecraftServer s,Supplier<T> accion) throws Exception {
        if(detenido || s==null) throw new InterruptedException("Servidor detenido");
        var f=new CompletableFuture<T>();
        s.execute(() -> {
            if(detenido || f.isCancelled()) {f.cancel(false);return;}
            try {f.complete(accion.get());} catch(Throwable e) {f.completeExceptionally(e);}
        });
        try {return f.get(15,TimeUnit.SECONDS);} catch(Exception e) {f.cancel(false);throw e;}
    }
    private static final class Regla extends Exception { Regla(String mensaje){super(mensaje);} }
}
