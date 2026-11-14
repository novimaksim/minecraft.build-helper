package ru.avganbaev.sevenstarsbuilder;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.level.Level;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

@Mod(SevenStarsBuilder.MODID)
public final class SevenStarsBuilder {
    public static final String MODID="sevenstars_builder";
    private static final Logger LOG=LogUtils.getLogger();
    private static final String CAT="category.sevenstars_builder";
    // Призрак-клавиши активны только когда призрак включён и не открыт экран.
    private static final IKeyConflictContext GHOST=new IKeyConflictContext(){
        @Override public boolean isActive(){return preview&&Minecraft.getInstance().screen==null;}
        @Override public boolean conflicts(IKeyConflictContext other){return this==other;}
    };
    private static KeyMapping key(String name,int code){return new KeyMapping("key.sevenstars_builder."+name,GHOST,InputConstants.Type.KEYSYM.getOrCreate(code),CAT);}
    private static KeyMapping keyAlways(String name,int code){return new KeyMapping("key.sevenstars_builder."+name,KeyConflictContext.IN_GAME,InputConstants.Type.KEYSYM.getOrCreate(code),CAT);}
    private static final KeyMapping NEXT=keyAlways("next",GLFW.GLFW_KEY_RIGHT_BRACKET);
    private static final KeyMapping ROTATE=key("rotate",GLFW.GLFW_KEY_R);
    private static final KeyMapping CONFIRM=key("confirm",GLFW.GLFW_KEY_ENTER);
    private static final KeyMapping CANCEL=keyAlways("cancel",GLFW.GLFW_KEY_BACKSPACE);
    private static final KeyMapping LOCK=key("lock",GLFW.GLFW_KEY_G);
    private static final KeyMapping FORWARD=key("forward",GLFW.GLFW_KEY_UP);
    private static final KeyMapping BACK=key("back",GLFW.GLFW_KEY_DOWN);
    private static final KeyMapping LEFT=key("left",GLFW.GLFW_KEY_LEFT);
    private static final KeyMapping RIGHT=key("right",GLFW.GLFW_KEY_RIGHT);
    private static final KeyMapping UP=key("up",GLFW.GLFW_KEY_PAGE_UP);
    private static final KeyMapping DOWN=key("down",GLFW.GLFW_KEY_PAGE_DOWN);
    private static final KeyMapping[] KEYS={NEXT,ROTATE,CONFIRM,CANCEL,LOCK,FORWARD,BACK,LEFT,RIGHT,UP,DOWN};
    private static final List<BuildDefinition> BUILDS=new ArrayList<>();
    private static final Deque<String> QUEUE=new ArrayDeque<>();
    private static int selected,rotation,ticks,total;
    private static boolean preview;
    private static BlockPos lockedOrigin;
    private static Object previousLevel;
    private static BlockPos pos1,pos2;
    private static final Map<String,Bounds> BOUNDS=new HashMap<>();
    private record Bounds(int minX,int minY,int minZ,int maxX,int maxY,int maxZ){}

    public SevenStarsBuilder(){
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::keys);
        MinecraftForge.EVENT_BUS.register(this);
        loadSaved();
    }
    private void keys(RegisterKeyMappingsEvent e){for(KeyMapping k:KEYS)e.register(k);}
    private static Path dir(){return FMLPaths.CONFIGDIR.get().resolve("sevenstars_builds");}
    private static void msg(String text,boolean chat){
        LocalPlayer p=Minecraft.getInstance().player;
        if(p!=null)p.displayClientMessage(Component.literal("[7Stars] "+text),!chat);
    }
    private static BuildDefinition build(){return BUILDS.isEmpty()?null:BUILDS.get(selected%BUILDS.size());}
    private static void loadSaved(){
        try{
            Files.createDirectories(dir());
            try(Stream<Path> files=Files.list(dir())){
                for(Path f:files.filter(p->p.toString().endsWith(".txt")).sorted().toList()){
                    try{BUILDS.add(ScriptParser.parse(Files.readString(f,StandardCharsets.UTF_8)));}
                    catch(ScriptParser.ParseException|IOException ex){LOG.warn("[7Stars] {}: {}",f,ex.getMessage());}
                }
            }
        }catch(IOException ex){LOG.warn("[7Stars] Cannot load builds",ex);}
    }
    private static void resetPlacement(){lockedOrigin=null;rotation=0;BOUNDS.clear();}
    private static void hidePreview(){preview=false;lockedOrigin=null;}
    private static void paste(){
        if(!QUEUE.isEmpty()){msg("Сначала останови очередь: Backspace",true);return;}
        try{
            String text=Minecraft.getInstance().keyboardHandler.getClipboard();
            BuildDefinition b=ScriptParser.parse(text);
            int index=-1;
            for(int i=0;i<BUILDS.size();i++)if(BUILDS.get(i).name().equalsIgnoreCase(b.name())){index=i;break;}
            if(index<0){index=BUILDS.size();BUILDS.add(b);}else BUILDS.set(index,b);
            selected=index;resetPlacement();preview=true;
            String file=b.name().replaceAll("[^\\p{L}\\p{N}_-]+","_")+".txt";
            try{Files.createDirectories(dir());Files.writeString(dir().resolve(file),text,StandardCharsets.UTF_8);}
            catch(IOException ex){msg("Загружено, но файл не сохранён: "+ex.getMessage(),true);}
            msg("Загружено: "+b.name()+". Призрак включён. G — фиксация, Backspace — скрыть",true);
        }catch(ScriptParser.ParseException ex){msg("Ошибка скрипта: "+ex.getMessage(),true);}
    }
    private static BlockPos target(){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null)return null;
        HitResult hit=mc.player.pick(80.0D,1.0F,false);
        if(hit.getType()!=HitResult.Type.BLOCK)return null;
        BlockHitResult h=(BlockHitResult)hit;
        return h.getBlockPos().relative(h.getDirection());
    }
    private static BlockPos origin(){return lockedOrigin!=null?lockedOrigin:target();}
    private static Bounds bounds(){
        String key=selected+":"+rotation;
        return BOUNDS.computeIfAbsent(key,k->{
            int ax=Integer.MAX_VALUE,ay=ax,az=ax,bx=Integer.MIN_VALUE,by=bx,bz=bx;
            for(BuildDefinition.Op o:build().rotatedOps(rotation)){
                ax=Math.min(ax,o.x1());ay=Math.min(ay,o.y1());az=Math.min(az,o.z1());
                bx=Math.max(bx,o.x2());by=Math.max(by,o.y2());bz=Math.max(bz,o.z2());
            }
            return new Bounds(ax,ay,az,bx,by,bz);
        });
    }
    private static void rotate(boolean reverse){
        if(build()==null||!preview)return;
        Bounds old=bounds();
        rotation=(rotation+(reverse?3:1))&3;
        if(lockedOrigin!=null){
            Bounds now=bounds();
            int dx=(int)Math.round((old.minX()+old.maxX()-now.minX()-now.maxX())/2.0);
            int dz=(int)Math.round((old.minZ()+old.maxZ()-now.minZ()-now.maxZ())/2.0);
            lockedOrigin=lockedOrigin.offset(dx,0,dz);
        }
    }
    private static void move(Direction direction,int amount){
        if(!preview)return;
        if(lockedOrigin==null){msg("Сначала зафиксируй призрак: G",false);return;}
        lockedOrigin=lockedOrigin.relative(direction,amount);
    }
    private static void drain(){for(KeyMapping k:KEYS)while(k.consumeClick()){} }
    private static int consume(KeyMapping key){int n=0;while(key.consumeClick())n++;return n;}
    private static void controls(LocalPlayer p){
        boolean shift=p.isShiftKeyDown();int step=shift?5:1;
        if(consume(CANCEL)>0){
            if(!QUEUE.isEmpty()){QUEUE.clear();hidePreview();msg("Очередь остановлена. Призрак скрыт.",true);}
            else if(preview){hidePreview();msg("Призрак скрыт.",true);}
        }
        if(!QUEUE.isEmpty()){drain();return;}
        if(consume(NEXT)>0){
            if(BUILDS.isEmpty())msg("Скопируй скрипт и напиши /sb paste",true);
            else{
                selected=(selected+1)%BUILDS.size();
                lockedOrigin=null;BOUNDS.clear();
                msg("Выбрано: "+build().name()+(preview?"":". Призрак скрыт — /sb paste или /sb use"),true);
            }
        }
        if(!preview){drain();return;}
        if(consume(LOCK)>0){
            if(build()==null)msg("Нет постройки. /sb paste — вставить скрипт",true);
            else if(lockedOrigin!=null){lockedOrigin=null;msg("Призрак следует за прицелом",false);}
            else{BlockPos pos=target();if(pos!=null){lockedOrigin=pos.immutable();msg("Положение зафиксировано. Стрелки — сдвиг",false);}else msg("Наведи прицел на блок",false);}
        }
        if(consume(ROTATE)>0)rotate(shift);
        Direction forward=p.getDirection();
        int n;
        if((n=consume(FORWARD))>0)move(forward,step*n);
        if((n=consume(BACK))>0)move(forward.getOpposite(),step*n);
        if((n=consume(LEFT))>0)move(forward.getCounterClockWise(),step*n);
        if((n=consume(RIGHT))>0)move(forward.getClockWise(),step*n);
        if((n=consume(UP))>0)move(Direction.UP,step*n);
        if((n=consume(DOWN))>0)move(Direction.DOWN,step*n);
        if(consume(CONFIRM)>0){
            if(build()==null)return;
            if(lockedOrigin==null){msg("Сначала зафиксируй постройку клавишей G",true);return;}
            Bounds b=bounds();
            int bottom=lockedOrigin.getY()+b.minY(),top=lockedOrigin.getY()+b.maxY();
            if(bottom<Minecraft.getInstance().level.getMinBuildHeight()||top>=Minecraft.getInstance().level.getMaxBuildHeight()){
                msg("Постройка выходит за пределы высоты мира. Измени высоту PgUp/PgDn.",true);return;
            }
            QUEUE.addAll(build().commands(lockedOrigin,rotation));total=QUEUE.size();hidePreview();
            msg("Отправляю "+total+" команд. Backspace — остановить",true);
        }
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        Minecraft mc=Minecraft.getInstance();
        if(previousLevel!=mc.level){QUEUE.clear();hidePreview();previousLevel=mc.level;}
        if(mc.player==null){drain();return;}
        if(mc.screen==null)controls(mc.player);else drain();
        if(!QUEUE.isEmpty()){
            mc.player.commandUnsigned(QUEUE.removeFirst());
            if(QUEUE.isEmpty())msg("Все команды отправлены. Проверь результат на сервере.",true);
            else if(++ticks%20==0)msg("Осталось команд: "+QUEUE.size()+" / "+total,false);
        }else if(mc.screen==null&&preview&&build()!=null&&++ticks%10==0){
            BlockPos o=origin();
            if(o==null){msg("Наведи прицел на блок · G — фиксация",false);return;}
            msg((lockedOrigin==null?"ПРИЦЕЛ":"ЗАФИКСИРОВАНО")+" · X "+o.getX()+" Y "+o.getY()+" Z "+o.getZ()+
                " · "+rotation*90+"° · G / стрелки / PgUp PgDn / R / Enter",false);
        }
    }
    @SubscribeEvent public void render(RenderLevelStageEvent e){
        if(!preview||build()==null||Minecraft.getInstance().level==null||e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        BlockPos o=origin();
        if(o!=null)GhostRenderer.render(e.getPoseStack(),e.getCamera().getPosition(),o,build().cells(rotation));
    }
    private static LiteralArgumentBuilder<CommandSourceStack> pos(String name,int which){
        return Commands.literal(name)
            .executes(c->{setPos(which,null);return 1;})
            .then(Commands.argument("x",IntegerArgumentType.integer())
                .then(Commands.argument("y",IntegerArgumentType.integer())
                    .then(Commands.argument("z",IntegerArgumentType.integer())
                        .executes(c->{
                            setPos(which,new BlockPos(IntegerArgumentType.getInteger(c,"x"),IntegerArgumentType.getInteger(c,"y"),IntegerArgumentType.getInteger(c,"z")));
                            return 1;
                        }))));
    }
    private static void setPos(int which,BlockPos p){
        if(p==null){
            Minecraft mc=Minecraft.getInstance();
            if(mc.player==null)return;
            HitResult hit=mc.player.pick(80.0D,1.0F,false);
            if(hit.getType()!=HitResult.Type.BLOCK){msg("Наведи прицел на блок",true);return;}
            p=((BlockHitResult)hit).getBlockPos();
        }
        if(which==1)pos1=p.immutable();else pos2=p.immutable();
        msg("Точка "+which+": "+p.getX()+" "+p.getY()+" "+p.getZ(),true);
    }
    private static void exportZone(String name){
        if(pos1==null||pos2==null){msg("Задай две точки: /sb pos1 и /sb pos2",true);return;}
        Level level=Minecraft.getInstance().level;
        if(level==null)return;
        try{
            List<String> lines=WorldExporter.export(level,pos1,pos2,name);
            String text=String.join("\n",lines)+"\n";
            ScriptParser.parse(text);
            String file=name.replaceAll("[^\\p{L}\\p{N}_-]+","_")+".txt";
            Files.createDirectories(dir());
            Files.writeString(dir().resolve(file),text,StandardCharsets.UTF_8);
            int ops=lines.size()-2;
            msg("Сохранено: "+file+" ("+ops+" команд). Пришли мне этот файл из config/sevenstars_builds",true);
            if(ops>20000)msg("Внимание: больше 20000 команд, мод не сможет загрузить такой скрипт",true);
        }catch(ScriptParser.ParseException|IOException|IllegalStateException ex){
            msg("Не удалось сохранить: "+ex.getMessage(),true);
        }
    }
    @SubscribeEvent public void commands(RegisterClientCommandsEvent e){
        e.getDispatcher().register(Commands.literal("sb")
            .then(Commands.literal("paste").executes(c->{paste();return 1;}))
            .then(Commands.literal("list").executes(c->{
                if(BUILDS.isEmpty())msg("Построек нет. /sb paste",true);
                for(int i=0;i<BUILDS.size();i++)msg((i==selected?"> ":"")+BUILDS.get(i).name(),true);
                return 1;
            }))
            .then(pos("pos1",1)).then(pos("pos2",2))
            .then(Commands.literal("export").then(Commands.argument("name",StringArgumentType.greedyString()).executes(c->{exportZone(StringArgumentType.getString(c,"name"));return 1;})))
            .then(Commands.literal("hide").executes(c->{hidePreview();msg("Призрак скрыт.",true);return 1;}))
            .then(Commands.literal("use").then(Commands.argument("name",StringArgumentType.greedyString()).executes(c->{
                if(!QUEUE.isEmpty()){msg("Сначала останови очередь",true);return 0;}
                String q=StringArgumentType.getString(c,"name").toLowerCase(Locale.ROOT);
                for(int i=0;i<BUILDS.size();i++)if(BUILDS.get(i).name().toLowerCase(Locale.ROOT).contains(q)){
                    selected=i;resetPlacement();preview=true;
                    msg("Выбрано: "+build().name()+". Призрак включён.",true);return 1;
                }
                msg("Постройка не найдена",true);return 0;
            }))));
    }
}
