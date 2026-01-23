package top.maple_bamboo.rs_disk_move;

import com.mojang.logging.LogUtils;
import com.refinedmods.refinedstorage.api.IRSAPI;
import com.refinedmods.refinedstorage.api.RSAPIInject;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import top.maple_bamboo.rs_disk_move.block.DiskMoverBlock;
import top.maple_bamboo.rs_disk_move.block.DiskMoverBlockEntity;
import top.maple_bamboo.rs_disk_move.client.DiskMoveScreen;
import top.maple_bamboo.rs_disk_move.client.SideConfigScreen;
import top.maple_bamboo.rs_disk_move.menu.DiskMoveMenu;
import top.maple_bamboo.rs_disk_move.menu.SideConfigMenu;
import top.maple_bamboo.rs_disk_move.network.PacketHandler;

@Mod(RSDiskMove.MOD_ID)
public class RSDiskMove {
    public static final String MOD_ID = "rs_disk_move";
    public static final Logger LOGGER = LogUtils.getLogger();

    @RSAPIInject
    public static IRSAPI RS_API;

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MOD_ID);

    public static final RegistryObject<Block> DISK_MOVER_BLOCK = BLOCKS.register("disk_mover",
            () -> new DiskMoverBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5.5f)
                    .lightLevel(state -> state.getValue(DiskMoverBlock.LIT) ? 15 : 0) // 修改：根据 LIT 状态设置亮度
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<Item> DISK_MOVER_ITEM = ITEMS.register("disk_mover",
            () -> new BlockItem(DISK_MOVER_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<DiskMoverBlockEntity>> DISK_MOVER_BE = BLOCK_ENTITIES.register("disk_mover_be",
            () -> BlockEntityType.Builder.of(DiskMoverBlockEntity::new, DISK_MOVER_BLOCK.get()).build(null));

    public static final RegistryObject<MenuType<DiskMoveMenu>> DISK_MOVER_MENU = MENUS.register("disk_mover_menu",
            () -> IForgeMenuType.create((windowId, inv, data) -> new DiskMoveMenu(windowId, inv, data.readBlockPos())));

    public static final RegistryObject<MenuType<SideConfigMenu>> SIDE_CONFIG_MENU = MENUS.register("side_config_menu",
            () -> IForgeMenuType.create((windowId, inv, data) -> new SideConfigMenu(windowId, inv, data.readBlockPos())));

    private static final String PROTOCOL_VERSION = "2";
    public static final SimpleChannel NETWORK = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public RSDiskMove() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        modEventBus.addListener(this::buildContents);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(PacketHandler::register);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        MenuScreens.register(DISK_MOVER_MENU.get(), DiskMoveScreen::new);
        MenuScreens.register(SIDE_CONFIG_MENU.get(), SideConfigScreen::new);
    }

    private void buildContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().location().equals(new ResourceLocation("refinedstorage", "general"))) {
            event.accept(DISK_MOVER_ITEM.get());
        }
    }
}