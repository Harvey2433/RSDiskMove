package top.maple_bamboo.rs_disk_move;

import com.mojang.logging.LogUtils;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.extensions.IForgeMenuType;
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
import top.maple_bamboo.rs_disk_move.client.DiskMoveScreen;
import top.maple_bamboo.rs_disk_move.item.DiskMoveItem;
import top.maple_bamboo.rs_disk_move.menu.DiskMoveMenu;
import top.maple_bamboo.rs_disk_move.network.PacketHandler;

@Mod(RSDiskMove.MOD_ID)
public class RSDiskMove {
    public static final String MOD_ID = "rs_disk_move";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MOD_ID);

    public static final RegistryObject<Item> DISK_MOVE_ITEM = ITEMS.register("disk_mover", DiskMoveItem::new);

    public static final RegistryObject<MenuType<DiskMoveMenu>> DISK_MOVE_MENU = MENUS.register("disk_mover_menu",
            () -> IForgeMenuType.create((windowId, inv, data) -> new DiskMoveMenu(windowId, inv, inv.player)));

    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel NETWORK = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public RSDiskMove() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modEventBus);
        MENUS.register(modEventBus);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(PacketHandler::register);
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        MenuScreens.register(DISK_MOVE_MENU.get(), DiskMoveScreen::new);
    }
}