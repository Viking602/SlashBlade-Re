"""Migrate the blade item hooks while retaining original combo/break behavior."""
from pathlib import Path
import re
from migrate_names import end_parenthesis
root = Path(__file__).resolve().parents[1] / 'src/main/java/mods/flammpfeil/slashblade'
path = root / 'item/ItemSlashBlade.java'
text = path.read_text('utf-8')
def method(name, replacement):
    global text
    match = re.search(r'(?:    @(?:Override|OnlyIn\([^\n]*\)|Nullable)\s*)*    (?:static public|public|private|protected)[^;{\n]*\b' + name + r'\([^;{]*\)\s*\{', text)
    if not match: raise ValueError(f'Method not found: {name}')
    opening = text.index('{', match.start()); depth=1; end=opening+1
    while depth:
        if text[end]=='{': depth+=1
        elif text[end]=='}': depth-=1
        end+=1
    text = text[:match.start()] + replacement + text[end:]

text = text.replace('extends SwordItem', 'extends Item')
text = text.replace('ItemSlashBlade(Tier tier', 'ItemSlashBlade(ItemTierSlashBlade tier')
text = text.replace('super(tier, attackDamageIn, attackSpeedIn, builder);', 'super(builder.sword(tier.material(), attackDamageIn, attackSpeedIn));')
method('getAttributeModifiers', '')  # Modern ItemAttributeModifierEvent in SBItems supplies the same modifiers.
method('getRarity', '''    public Rarity getRarity(ItemStack stack) {
        return SBData.get(stack, BLADESTATE).map(ISlashBladeState::getRarity).orElse(Rarity.COMMON);
    }''')
method('getUseDuration', '''    @Override public int getUseDuration(ItemStack stack, LivingEntity user) { return 72000; }
    public int getUseDuration(ItemStack stack) { return 72000; }''')
method('use', '''    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        SBData.get(stack, BLADESTATE).ifPresent(state -> {
            SBData.get(player, INPUT_STATE).ifPresent(input -> input.getCommands().add(InputCommand.R_CLICK));
            try {
                ComboState combo = state.progressCombo(player);
                if (combo != ComboState.NONE) player.swing(hand);
            } finally { SBData.get(player, INPUT_STATE).ifPresent(input -> input.getCommands().remove(InputCommand.R_CLICK)); }
        });
        player.startUsingItem(hand);
        return InteractionResult.SUCCESS;
    }''')
text = text.replace('public boolean hurtEnemy(ItemStack stackF, LivingEntity target, LivingEntity attacker)', 'public void hurtEnemy(ItemStack stackF, LivingEntity target, LivingEntity attacker)')
start = text.index('    public void hurtEnemy('); end = text.index('    public boolean mineBlock', start)
text = text[:start] + text[start:end].replace('        return true;','') + text[end:]
text = text.replace('public void releaseUsing(', 'public boolean releaseUsing(')
start = text.index('    public boolean releaseUsing('); opening = text.index('{', start); depth=1; end=opening+1
while depth:
    if text[end]=='{': depth+=1
    elif text[end]=='}': depth-=1
    end+=1
text = text[:end-1] + '    return true;\n    ' + text[end-1:]
text = text.replace('public void inventoryTick(ItemStack stack, Level worldIn, Entity entityIn, int itemSlot, boolean isSelected)',
'''public void inventoryTick(ItemStack stack, ServerLevel worldIn, Entity entityIn, @Nullable EquipmentSlot slot) {
        tickInventory(stack, worldIn, entityIn, slot == EquipmentSlot.MAINHAND);
    }

    public void tickInventory(ItemStack stack, Level worldIn, Entity entityIn, boolean isSelected)''')
text = text.replace('        super.inventoryTick(stack, worldIn, entityIn, itemSlot, isSelected);', '')
method('getShareTag', '''    public CompoundTag getShareTag(ItemStack stack) { return mods.flammpfeil.slashblade.compat.SBItemData.tag(stack); }''')
method('readShareTag', '')  # Typed components synchronize the complete state without the obsolete packet mixin.
text = text.replace('this.getMaxDamage()', '100')
text = text.replace('public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<T> onBroken)',
    'public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, @Nullable T entity, Consumer<Item> onBroken)')
method('isBarVisible', '''    @Override public boolean isBarVisible(ItemStack stack) { return isDamaged(stack); }''')
text = text.replace('    @Override\n    public String getDescriptionId(ItemStack stack)', '    public String getDescriptionId(ItemStack stack)')
text = text.replace('super.getDescriptionId(stack)', 'super.getDescriptionId()')
method('getClientRM', '')
method('isValidRepairItem', '''    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) { return repair.is(ItemTags.STONE_TOOL_MATERIALS) || repair.is(ItemTags.create(Identifier.fromNamespaceAndPath("slashblade", "proudsouls"))); }''')
method('appendHoverText', '''    @Override public void appendHoverText(ItemStack stack, Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        SBData.get(stack, BLADESTATE).ifPresent(state -> {
            if (state.getKillCount() > 0) tooltip.accept(Component.translatable("slashblade.tooltip.killcount", state.getKillCount()));
            if (state.getRefine() > 0) tooltip.accept(Component.translatable("slashblade.tooltip.refine", state.getRefine()).withStyle((ChatFormatting)refineColor.get(state.getRefine())));
        });
        super.appendHoverText(stack, context, display, tooltip, flag);
    }
    @Override public Component getName(ItemStack stack) { return Component.translatable(getDescriptionId(stack)); }''')
text = text.replace('public boolean onEntitySwing(ItemStack stack, LivingEntity entity)', 'public boolean onEntitySwing(ItemStack stack, LivingEntity entity, InteractionHand hand)')
method('initializeClient', '')  # Registered SpecialModelRenderer replaces TEISR; common item has no client linkage.
text = re.sub(r'import (?:net\.minecraft\.client\.[^;]+|net\.neoforged\.neoforge\.client\.[^;]+|net\.neoforged\.fml\.DistExecutor|net\.neoforged\.neoforge\.common\.ForgeMod|net\.minecraft\.world\.item\.(?:SwordItem|Tier)|net\.minecraft\.world\.InteractionResultHolder|mods\.flammpfeil\.slashblade\.client\.renderer\.SlashBladeTEISR);\n', '', text)
path.write_text(text, 'utf-8')

# The activated soul's durability now lives in its item components.
path = root / 'item/ItemSoulActivated.java'; text = path.read_text('utf-8')
text = text.replace('import net.minecraft.client.renderer.EffectInstance;', '').replace('import net.minecraft.world.InteractionResultHolder;', '')
text = text.replace('public void inventoryTick(ItemStack itemStack, Level level, Entity entity, int slot, boolean selected)',
    'public void inventoryTick(ItemStack itemStack, net.minecraft.server.level.ServerLevel level, Entity entity, @org.jetbrains.annotations.Nullable EquipmentSlot slot)')
text = text.replace('if(entity != null && entity instanceof Player && ((Player)entity).getInventory().isHotbarSlot(slot)){',
    'if(entity instanceof Player && (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND || isInHotbar((Player)entity, itemStack))){')
text = text.replace('    //reverse', '''    private static boolean isInHotbar(Player player, ItemStack stack) {
        for (int index = 0; index < 9; index++) if (player.getInventory().getItem(index) == stack) return true;
        return false;
    }
    //reverse''')
path.write_text(text, 'utf-8')
print('Ported blade and soul interaction, tick, tooltip and damage hooks')
