package net.tracen.umapyoi.block.entity;

import cn.mcmod_mmf.mmlib.block.entity.SyncedBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;
import net.tracen.umapyoi.container.TrainingFacilityContainer;
import net.tracen.umapyoi.item.UmaSoulItem;
import net.tracen.umapyoi.registry.training.SupportContainer;
import net.tracen.umapyoi.registry.umadata.Growth;
import net.tracen.umapyoi.utils.UmaSoulUtils;

public class TrainingFacilityBlockEntity extends SyncedBlockEntity implements MenuProvider {

    public static final int MAX_PROCESS_TIME = 260;
    private final ItemStackHandler inventory;

    protected final ContainerData tileData;

    private int recipeTime;

    public TrainingFacilityBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.TRAINING_FACILITY.get(), pos, state);
        this.inventory = createHandler();
        this.tileData = createIntArray();
    }

    public static void workingTick(Level level, BlockPos pos, BlockState state,
            TrainingFacilityBlockEntity blockEntity) {
        if (level.isClientSide())
            return;

        boolean didInventoryChange = false;
        if (blockEntity.canWork()) {
            didInventoryChange = blockEntity.processRecipe();
        } else {
            blockEntity.recipeTime = 0;
        }

        if (didInventoryChange) {
            blockEntity.inventoryChanged();
        }
    }

    private boolean processRecipe() {
        if (level == null) {
            return false;
        }

        ++recipeTime;
        if (recipeTime < MAX_PROCESS_TIME) {
            return false;
        }

        recipeTime = 0;

        ItemStack resultStack = getResultItem();
        this.inventory.setStackInSlot(0, resultStack);

        for (int i = 1; i < 7; i++) {
            ItemStack supportItem = this.inventory.getStackInSlot(i);
            if (supportItem.getItem()instanceof SupportContainer supports) {
                if (supports.isConsumable(this.getLevel(), supportItem))
                    supportItem.shrink(1);
                else if(supportItem.hurt(1, this.getLevel().getRandom(), null)) {
                    this.getLevel().playSound(null, this.getBlockPos(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1F, 1F);
                    supportItem.shrink(1);
                    supportItem.setDamageValue(0);
                }
            }
        }
        
        this.getLevel().playSound(null, this.getBlockPos(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 1F, 1F);
        return true;
    }

    private ItemStack getResultItem() {
        if (this.level == null)
            return ItemStack.EMPTY;
        ItemStack result = this.inventory.getStackInSlot(0).copy();
        UmaSoulUtils.setGrowth(result, Growth.TRAINED);
        UmaSoulUtils.downPhysique(result);
        for (int i = 1; i < 7; i++) {
            ItemStack supportItem = this.inventory.getStackInSlot(i);
            if (supportItem.getItem()instanceof SupportContainer supports) {
                supports.getSupports(this.getLevel(), supportItem).forEach(support -> support.applySupport(result));
            }
        }
        return result;
    }

    private boolean canWork() {
        if (!this.hasInput())
            return false;
        return true;
    }

    private boolean hasInput() {
        ItemStack input = this.inventory.getStackInSlot(0);
        if (input.getItem() instanceof UmaSoulItem) {
            if (UmaSoulUtils.getGrowth(input) != Growth.RETIRED && UmaSoulUtils.getPhysique(input) > 0) {
                for (int i = 1; i < 7; i++) {
                    ItemStack supportItem = this.inventory.getStackInSlot(i);
                    if (supportItem.getItem()instanceof SupportContainer supports) {
                        if (!(supports.canSupport(level, supportItem).test(input)))
                            return false;
                    }

                }
                return true;
            }
        }

        return false;
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public NonNullList<ItemStack> getDroppableInventory() {
        NonNullList<ItemStack> drops = NonNullList.create();
        for (int i = 0; i < 7; ++i) {
            drops.add(inventory.getStackInSlot(i));
        }
        return drops;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
    }

    @Override
    public void load(CompoundTag compound) {
        super.load(compound);
        inventory.deserializeNBT(compound.getCompound("Inventory"));
        recipeTime = compound.getInt("RecipeTime");
    }

    @Override
    public void saveAdditional(CompoundTag compound) {
        super.saveAdditional(compound);
        compound.putInt("RecipeTime", recipeTime);
        compound.put("Inventory", inventory.serializeNBT());
    }

    private CompoundTag writeItems(CompoundTag compound) {
        super.saveAdditional(compound);
        compound.put("Inventory", inventory.serializeNBT());
        return compound;
    }

    @Override
    public CompoundTag getUpdateTag() {
        return writeItems(new CompoundTag());
    }

    private ItemStackHandler createHandler() {
        return new ItemStackHandler(7) {
            @Override
            protected void onContentsChanged(int slot) {
                inventoryChanged();
            }
        };
    }

    private ContainerData createIntArray() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                switch (index) {
                case 0:
                    return TrainingFacilityBlockEntity.this.recipeTime;
                default:
                    return 0;
                }
            }

            @Override
            public void set(int index, int value) {
                switch (index) {
                case 0:
                    TrainingFacilityBlockEntity.this.recipeTime = value;
                    break;
                }
            }

            @Override
            public int getCount() {
                return 1;
            }
        };
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory player, Player entity) {
        return new TrainingFacilityContainer(id, player, this, this.tileData);
    }

    @Override
    public Component getDisplayName() {
        return new TranslatableComponent("container.umapyoi.training_facility");
    }

}
