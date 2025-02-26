/*
 * Minecraft Forge
 * Copyright (c) 2016-2021.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.common.capabilities;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullLazy;
import net.minecraftforge.common.util.TablePrinter;
import net.minecraftforge.common.util.TextTable;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStackSimple;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static net.minecraftforge.common.util.TextTable.column;

@Mod(LazyCapabilitiesOnItemsTest.MOD_ID)
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, modid = LazyCapabilitiesOnItemsTest.MOD_ID)
public class LazyCapabilitiesOnItemsTest
{
    public static final String MOD_ID = "lazy_capabilities_on_items";

    private static final Logger LOGGER = LogManager.getLogger();
    private static final boolean ENABLED = true;
    private static final int SAMPLE_SIZE = 100000;

    private static final List<ItemStack> WARMUP_RESULTS = new ArrayList<>(SAMPLE_SIZE * 4);
    private static final List<ItemStack> NO_CAPS_DISABLED_RESULTS = new ArrayList<>(SAMPLE_SIZE);
    private static final List<ItemStack> WITH_CAPS_DISABLED_RESULTS = new ArrayList<>(SAMPLE_SIZE);
    private static final List<ItemStack> NO_CAPS_ENABLED_RESULTS = new ArrayList<>(SAMPLE_SIZE);
    private static final List<ItemStack> WITH_CAPS_ENABLED_RESULTS = new ArrayList<>(SAMPLE_SIZE);

    public LazyCapabilitiesOnItemsTest()
    {
        if (!ENABLED)
            return;

        final IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::onCommonSetup);
    }

    private void onCommonSetup(FMLCommonSetupEvent event)
    {
        try
        {
            final Field supportsFlagField = CapabilityProvider.class.getDeclaredField("SUPPORTS_LAZY_CAPABILITIES");
            supportsFlagField.setAccessible(true);
            supportsFlagField.set(null, false);

            final Stopwatch timer = Stopwatch.createUnstarted();
            final IEventBus bus = MinecraftForge.EVENT_BUS;

            final ResourceLocation testCapId = new ResourceLocation(MOD_ID, "test");
            final Consumer<AttachCapabilitiesEvent<ItemStack>> capAttachmentHandler = e -> {
                //Example capability we make everything a bucket :D
                e.addCapability(testCapId, new FluidHandlerItemStackSimple(e.getObject(), SAMPLE_SIZE));
            };

            //Warmup:
            for (int i = 0; i < (SAMPLE_SIZE); i++)
            {
                WARMUP_RESULTS.add(new ItemStack(Items.WATER_BUCKET));
            }

            ///First test: SAMPLE_SIZE itemstacks which do not have a capability attached.
            timer.start();
            for (int i = 0; i < SAMPLE_SIZE; i++)
            {
                NO_CAPS_DISABLED_RESULTS.add(new ItemStack(Items.WATER_BUCKET));
            }
            timer.stop();

            final long simpleNoCapsLazyDisabledElapsed = timer.elapsed(TimeUnit.MICROSECONDS);
            timer.reset();

            ///Second test: SAMPLE_SIZE itemstacks with a capability attached.
            bus.addGenericListener(ItemStack.class, capAttachmentHandler);
            //Warmup:
            for (int i = 0; i < (SAMPLE_SIZE); i++)
            {
                WARMUP_RESULTS.add(new ItemStack(Items.WATER_BUCKET));
            }

            timer.start();
            for (int i = 0; i < SAMPLE_SIZE; i++)
            {
                WITH_CAPS_DISABLED_RESULTS.add(new ItemStack(Items.WATER_BUCKET));
            }
            timer.stop();

            final long withCapsLazyDisabledElapsed = timer.elapsed(TimeUnit.MICROSECONDS);
            timer.reset();
            bus.unregister(capAttachmentHandler);

            ///Third test: SAMPLE_SIZE itemstacks which do not have a capability attached.
            supportsFlagField.set(null, true);
            //Warmup:
            for (int i = 0; i < (SAMPLE_SIZE); i++)
            {
                WARMUP_RESULTS.add(new ItemStack(Items.WATER_BUCKET));
            }

            timer.start();
            for (int i = 0; i < SAMPLE_SIZE; i++)
            {
                NO_CAPS_ENABLED_RESULTS.add(new ItemStack(Items.WATER_BUCKET));
            }
            timer.stop();

            final long simpleNoCapsLazyEnabledElapsed = timer.elapsed(TimeUnit.MICROSECONDS);
            timer.reset();

            ///Fourth test: SAMPLE_SIZE itemstacks with a capability attached.
            bus.addGenericListener(ItemStack.class, capAttachmentHandler);
            //Warmup:
            for (int i = 0; i < (SAMPLE_SIZE); i++)
            {
                WARMUP_RESULTS.add(new ItemStack(Items.WATER_BUCKET));
            }

            timer.start();
            for (int i = 0; i < SAMPLE_SIZE; i++)
            {
                WITH_CAPS_ENABLED_RESULTS.add(new ItemStack(Items.WATER_BUCKET));
            }
            timer.stop();

            final long withCapsLazyEnabledElapsed = timer.elapsed(TimeUnit.MICROSECONDS);
            timer.reset();
            bus.unregister(capAttachmentHandler);

            TextTable table = new TextTable(Lists.newArrayList(
              column("Test type", TextTable.Alignment.LEFT),
              column("Total time", TextTable.Alignment.CENTER)
            ));

            table.add("Lazy: Disabled / Caps: None", simpleNoCapsLazyDisabledElapsed + " ms.");
            table.add("Lazy: Disabled / Caps: One", withCapsLazyDisabledElapsed + " ms.");
            table.add("Lazy: Enabled  / Caps: None", simpleNoCapsLazyEnabledElapsed + " ms.");
            table.add("Lazy: Enabled  / Caps: One", withCapsLazyEnabledElapsed + " ms.");

            final String[] resultData = table.build("\n").split("\n");
            for (final String line : resultData)
            {
                LOGGER.warn(line);
            }
        }
        catch (NoSuchFieldException | IllegalAccessException e)
        {
            LOGGER.error("Failed to run capabilities on items test!");
            throw new IllegalStateException(e);
        }
    }
}
