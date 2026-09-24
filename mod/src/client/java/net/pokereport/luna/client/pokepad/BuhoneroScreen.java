package net.pokereport.luna.client.pokepad;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.pokereport.luna.buhonero.BuhoneroNet;
import net.pokereport.luna.buhonero.ReglasMercadoNegro;
import org.lwjgl.glfw.GLFW;

/** Visor personal del Buhonero. El servidor confirma cada compra y cada plazo. */
public final class BuhoneroScreen extends Screen {
    private static final Identifier FONDO=Identifier.of("lunaeternal","textures/gui/pokepad/mercado_negro.png");
    private static final Identifier SKIN=Identifier.of("rctmod","textures/trainers/single/luna_buhonero.png");
    private static PositionedSoundInstance voz;
    private BuhoneroNet.Oferta oferta;
    private long recibido, pedido;
    private boolean confirmar, esperando;
    private float escala;
    private int izquierda,arriba;
    private ButtonWidget comprar;
    public BuhoneroScreen(BuhoneroNet.Oferta o) {super(Text.literal("Buhonero - Mercado Negro"));actualizar(o);}
    public static void registrar() {
        ClientPlayNetworking.registerGlobalReceiver(BuhoneroNet.Oferta.ID,(o,ctx) -> {
            var c=ctx.client();
            if(o.abrir()) {
                c.setScreen(new BuhoneroScreen(o));
                if(voz!=null)c.getSoundManager().stop(voz);
                voz=PositionedSoundInstance.master(SoundEvent.of(Identifier.of("lunaeternal","buhonero.saludo")),1f);
                c.getSoundManager().play(voz);
            } else if(c.currentScreen instanceof BuhoneroScreen s) s.actualizar(o);
        });
    }
    private void actualizar(BuhoneroNet.Oferta o) {
        oferta=o;recibido=System.nanoTime();esperando=false;confirmar=false;
    }
    private long ahora() {return oferta.ahora()+(System.nanoTime()-recibido)/1_000_000L;}
    private int x(int n){return izquierda+Math.round(n*escala);}
    private int y(int n){return arriba+Math.round(n*escala);}
    private int s(int n){return Math.round(n*escala);}
    @Override protected void init() {
        escala=Math.min(width/830f,height/506f);izquierda=(width-s(810))/2;arriba=(height-s(486))/2;
        comprar=boton(570,315,143,32,"Comprar",() -> {
            if(!confirmar){confirmar=true;return;}
            esperando=true;pedido=System.nanoTime();
            ClientPlayNetworking.send(new BuhoneroNet.Pedir(oferta.ciclo(),true));
        });
        boton(693,136,25,19,"×",this::close);
    }
    private ButtonWidget boton(int bx,int by,int bw,int bh,String label,Runnable action) {
        var boton=new ButtonWidget(x(bx),y(by),s(bw),s(bh),Text.literal(label),b -> action.run(),supplier -> supplier.get()) {
            @Override protected void renderWidget(DrawContext c,int mx,int my,float delta) {
                int borde=active?(isHovered()||isFocused()?0xFFF0D3A0:0xFF96784E):0xFF343D48;
                c.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),borde);
                c.fill(getX()+1,getY()+1,getX()+getWidth()-1,getY()+getHeight()-1,active?0xFF29232C:0xFF151B23);
                c.getMatrices().push();
                c.getMatrices().translate(getX()+getWidth()/2f,getY()+getHeight()/2f,0);
                c.getMatrices().scale(escala,escala,1);
                c.drawCenteredTextWithShadow(textRenderer,getMessage(),0,-4,active?0xFFFFE0AD:0xFF88929B);
                c.getMatrices().pop();
            }
        };
        return addDrawableChild(boton);
    }
    @Override
    public void renderBackground(DrawContext c, int mx, int my, float delta) {
        // En Minecraft 1.20.5+, renderBackground aplica el desenfoque del juego (applyBlur).
        // Se invoca explícitamente al inicio de render() con super.renderBackground() para
        // que quede DETRÁS, y se anula aquí para que super.render() no lo aplique encima de la UI.
    }
    @Override public void render(DrawContext c,int mx,int my,float delta) {
        super.renderBackground(c,mx,my,delta);
        c.drawTexture(FONDO,x(0),y(0),s(810),s(486),0f,0f,1620,971,1620,971);
        texto(c,"TRATOS EN LA SOMBRA",50,72,0xB69B77,0.9f);
        // Retrato de la skin original, sin modificarla ni difundir el sonido al mundo.
        c.fill(x(93),y(103),x(165),y(175),0xFF9B805C);
        c.fill(x(95),y(105),x(163),y(173),0xFF111720);
        c.drawTexture(SKIN,x(99),y(109),s(60),s(60),8f,8f,8,8,64,64);
        c.drawTexture(SKIN,x(99),y(109),s(60),s(60),40f,8f,8,8,64,64);
        texto(c,"BUHONERO",67,192,0xE9CC97,1.4f);
        texto(c,"Mercado Negro",68,217,0xB1BCCC,1f);
        linea(c,52,248,152,0xFF514657);
        texto(c,"TU BOLSILLO",55,265,0x969CAF,0.9f);
        texto(c,numero(oferta.saldo())+" Plata",55,286,0xEBD5B0,1.15f);
        texto(c,"Una compra cada 24 h",55,328,0xCED5DF,0.9f);
        texto(c,"Oferta solo para ti",55,347,0x7FB8BF,0.9f);
        if(oferta.mensaje().isBlank()) {
            texto(c,"«No hagas preguntas.",55,397,0xA7A1AE,0.86f);
            texto(c,"Cuida de él.»",55,412,0xA7A1AE,0.86f);
        } else parrafo(c,oferta.mensaje(),55,377,151,0xE3CCAC,0.87f);

        texto(c,"MERCANCÍA RESERVADA",275,139,0xD6C2A3,1.05f);
        linea(c,275,158,438,0xFF3A3F4B);
        c.fill(x(273),y(174),x(552),y(355),0xFF101923);
        linea(c,289,346,247,0xFF385059);
        texto(c,"ORIGEN DESCONOCIDO",298,361,0x8A9BA9,0.9f);
        // Coordenadas reales, como el visor de Cazas: no hereda escalado duplicado.
        Mascota3D.dibujarEspecie(c,Identifier.of("cobblemon",oferta.especie()),"buhonero:"+oferta.ciclo()+":"+oferta.especie(),"",
                x(279),y(173),s(267),s(174),0.06f,delta,true);
        String nombre=Text.translatable("cobblemon.species."+oferta.especie()+".name").getString();
        texto(c,nombre,570,181,0xF5E5C7,Math.min(1.3f,142f/Math.max(1,textRenderer.getWidth(nombre))));
        texto(c,"NIVEL "+oferta.nivel(),570,207,0x8BD1D3,1.1f);
        texto(c,"Kanto / Johto",570,229,0x9EA9B9,0.9f);
        linea(c,570,251,143,0xFF3A3F4B);
        texto(c,"PRECIO DEL TRATO",570,266,0xA4A2B0,0.85f);
        texto(c,numero(oferta.precio())+" Plata",570,285,0xEED49F,1.35f);
        long now=ahora();
        boolean caducada=now>=oferta.ciclo()+ReglasMercadoNegro.DIA;
        if(esperando && System.nanoTime()-pedido>15_000_000_000L) esperando=false;
        comprar.active=!esperando&&!oferta.comprada()&&!oferta.pendiente()&&!caducada&&now>=oferta.disponible()&&oferta.saldo()>=oferta.precio();
        comprar.setMessage(Text.literal(esperando?"Procesando…":oferta.pendiente()?"Entrega pendiente":oferta.comprada()?"Trato completado":
                caducada?"Actualizando…":now<oferta.disponible()?"En espera":oferta.saldo()<oferta.precio()?"Plata insuficiente":confirmar?"Confirmar · 10.000":"Comprar Pokémon"));
        String detalle=confirmar?"Se descontarán 10.000 Plata.":now<oferta.disponible()&&!oferta.comprada()?"Podrás comprar en "+tiempo(oferta.disponible()-now):"Pago único · Sin LunaCoins";
        texto(c,detalle,570,356,0xB2ABB9,0.76f);
        linea(c,275,379,438,0xFF3A3F4B);
        texto(c,"PRÓXIMA OFERTA",277,389,0x939EAE,0.8f);
        texto(c,tiempo(oferta.ciclo()+ReglasMercadoNegro.DIA-now),392,388,0x8BCBD0,1f);
        if((caducada||now>=oferta.disponible()&&oferta.disponible()>oferta.ahora()) && System.nanoTime()-pedido>3_000_000_000L && !esperando) {
            pedido=System.nanoTime();ClientPlayNetworking.send(new BuhoneroNet.Pedir(oferta.ciclo(),false));
        }
        super.render(c,mx,my,delta);
    }
    private void linea(DrawContext c,int bx,int by,int w,int color){c.fill(x(bx),y(by),x(bx+w),y(by)+Math.max(1,s(1)),color);}
    private void texto(DrawContext c,String t,int bx,int by,int color,float tamaño) {
        c.getMatrices().push();c.getMatrices().translate(x(bx),y(by),0);c.getMatrices().scale(escala*tamaño,escala*tamaño,1);
        c.drawTextWithShadow(textRenderer,t,0,0,0xFF000000|color);c.getMatrices().pop();
    }
    private void parrafo(DrawContext c,String t,int bx,int by,int w,int color,float tamaño) {
        int i=0;
        for(var line:textRenderer.wrapLines(Text.literal(t),(int)(w/tamaño))) {
            c.getMatrices().push();c.getMatrices().translate(x(bx),y(by+i*11),0);c.getMatrices().scale(escala*tamaño,escala*tamaño,1);
            c.drawTextWithShadow(textRenderer,line,0,0,0xFF000000|color);c.getMatrices().pop();if(++i>=5)break;
        }
    }
    private static String numero(long n){return String.format(java.util.Locale.forLanguageTag("es-CO"),"%,d",n);}
    private static String tiempo(long ms){long s=Math.max(0,(ms+999)/1000);return String.format("%02dh %02dm %02ds",s/3600,s/60%60,s%60);}
    @Override public boolean keyPressed(int key,int scan,int mods) {
        if(key==GLFW.GLFW_KEY_ESCAPE&&confirmar){confirmar=false;return true;}
        return super.keyPressed(key,scan,mods);
    }
    @Override public void removed(){if(client!=null&&voz!=null)client.getSoundManager().stop(voz);super.removed();}
    @Override public boolean shouldPause(){return false;}
}
