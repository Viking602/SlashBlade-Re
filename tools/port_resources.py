"""One-time, validated conversion of every upstream data file and item model to 26.1.2."""
from pathlib import Path
import copy
import json
import re
import zipfile

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'

class Snbt:
    def __init__(self,text): self.text=text; self.pos=0
    def skip(self):
        while self.pos<len(self.text) and self.text[self.pos].isspace(): self.pos+=1
    def token(self,key=False):
        self.skip()
        if self.text[self.pos] in "\"'":
            quote=self.text[self.pos]; self.pos+=1; value=''
            while self.pos<len(self.text):
                c=self.text[self.pos]; self.pos+=1
                if c==quote: return value
                if c=='\\':
                    c=self.text[self.pos]; self.pos+=1
                    value+= {'n':'\n','r':'\r','t':'\t'}.get(c,c)
                else: value+=c
            raise ValueError('Unterminated quoted SNBT')
        start=self.pos
        while self.pos<len(self.text) and self.text[self.pos] not in (':,{}[]' if key else ',{}[]') and not self.text[self.pos].isspace(): self.pos+=1
        if start==self.pos: raise ValueError(f'Unexpected SNBT at {self.pos}: {self.text[self.pos:self.pos+40]}')
        return self.text[start:self.pos]
    def value(self):
        self.skip(); c=self.text[self.pos]
        if c=='{':
            self.pos+=1; result={}; self.skip()
            while self.text[self.pos]!='}':
                key=self.token(True); self.skip()
                assert self.text[self.pos]==':'; self.pos+=1
                result[key]=self.value(); self.skip()
                if self.text[self.pos]==',': self.pos+=1; self.skip()
                else: break
            assert self.text[self.pos]=='}'; self.pos+=1; return result
        if c=='[':
            self.pos+=1; self.skip(); result=[]
            if self.text[self.pos:self.pos+2].upper() in ('B;','I;','L;'): self.pos+=2
            while self.text[self.pos]!=']':
                result.append(self.value()); self.skip()
                if self.text[self.pos]==',': self.pos+=1; self.skip()
                else: break
            assert self.text[self.pos]==']'; self.pos+=1; return result
        if c in "\"'": return self.token()
        value=self.token()
        if value.lower() in ('true','false'): return 1 if value.lower()=='true' else 0
        if re.fullmatch(r'[+-]?\d+[bBsSlL]?',value): return int(value.rstrip('bBsSlL'))
        if re.fullmatch(r'[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?[fFdD]?',value): return float(value.rstrip('fFdD'))
        return value
    @classmethod
    def parse(cls,value):
        if not isinstance(value,str): return copy.deepcopy(value)
        parser=cls(value); result=parser.value(); parser.skip()
        if parser.pos!=len(value): raise ValueError(f'Trailing SNBT {value[parser.pos:]}')
        return result

CATALOG={}
def blade_state(state):
    state=copy.deepcopy(state)
    for name in ('ShareTag','BladeUniqueId','Owner'): state.pop(name,None)
    if state.get('TargetEntity')==0: state['TargetEntity']=-1
    if 'ComboRootAir' not in state: state['ComboRootAir']='standby_inair'
    if state.get('translationKey'):
        CATALOG.setdefault(state['translationKey'], copy.deepcopy(state))
    return state

def stack(data):
    if isinstance(data,str): return {'id':data}
    data=copy.deepcopy(data)
    item=data.get('id',data.get('item','minecraft:air'))
    count=int(data.get('count',data.get('Count',1)))
    raw=Snbt.parse(data.get('nbt',data.get('tag',{})))
    if 'SlashBladeIcon' in raw:
        extra={k:v for k,v in raw.items() if k!='SlashBladeIcon'}
        result=stack(raw['SlashBladeIcon'])
        if extra: result.setdefault('components',{}).setdefault('minecraft:custom_data',{}).update(extra)
        return result
    components=copy.deepcopy(data.get('components',{}))
    caps=data.get('ForgeCaps',raw.pop('ForgeCaps',{})).get('slashblade:bladestate',{})
    if caps:
        state=blade_state(caps.get('State',caps))
        components['slashblade:blade_state']=state
        components['minecraft:rarity']=['common','uncommon','rare','epic'][int(state.get('rarityType',0))%4]
    raw.pop('ShareTag',None)
    damage=raw.pop('Damage',0)
    if damage and item!='slashblade:slashblade': components['minecraft:damage']=int(damage)
    enchants=raw.pop('Enchantments',[])
    if enchants: components['minecraft:enchantments']={e['id']:int(e['lvl']) for e in enchants}
    stored=raw.pop('StoredEnchantments',[])
    if stored: components['minecraft:stored_enchantments']={e['id']:int(e['lvl']) for e in stored}
    display=raw.pop('display',{})
    if 'Name' in display:
        try: components['minecraft:custom_name']=json.loads(display.pop('Name'))
        except json.JSONDecodeError: components['minecraft:custom_name']={'text':display.pop('Name')}
    if 'Lore' in display: components['minecraft:lore']=[json.loads(x) for x in display.pop('Lore')]
    if display: raw['display']=display
    block=raw.pop('BlockEntityTag',{})
    if 'Items' in block:
        components['minecraft:container']=[{'slot':int(i['Slot']),'item':stack(i)} for i in block.pop('Items')]
    if block: raw['BlockEntityTag']=block
    if raw: components['minecraft:custom_data']=convert_nested(raw)
    result={'id':item}
    if count!=1: result['count']=count
    if components: result['components']=components
    return result

def convert_nested(value):
    if isinstance(value,list): return [convert_nested(x) for x in value]
    if not isinstance(value,dict): return value
    result={}
    for key,child in value.items():
        if key=='result' and isinstance(child,dict) and ('id' in child or 'item' in child): result[key]=stack(child)
        elif key=='overwriteTag' and isinstance(child,dict):
            # Convert an NBT overlay to a modern component overlay without inventing a default blade state.
            converted=stack({'id':'slashblade:slashblade','tag':child})
            result[key]={'components':converted.get('components',{})}
        else: result[key]=convert_nested(child)
    return result

def ingredient(data):
    if isinstance(data,str): return data
    if isinstance(data,list): return [ingredient(x) for x in data]
    if data.get('type')=='forge:nbt':
        raw=Snbt.parse(data.get('nbt',{})); shown=stack({'item':data['item'],'nbt':raw})
        components=shown.get('components',{})
        state={}
        if 'ShareTag' in raw:
            state=copy.deepcopy(raw['ShareTag'])
            if 'isBroken' in state: state['isBroken']=int(str(state['isBroken']).lower()=='true')
        if 'ForgeCaps' in raw: state.update(raw['ForgeCaps'].get('slashblade:bladestate',{}).get('State',{}))
        custom=components.get('minecraft:custom_data',{}) if 'SlashBladeIcon' not in raw else {}
        result={'type':'slashblade:blade_ingredient','item':data['item'],'display':shown}
        if state: result['blade']=state
        if custom: result['custom']=custom
        return result
    if 'item' in data: return data['item']
    if 'tag' in data: return '#'+data['tag']
    return data

def item_predicate(data):
    data=copy.deepcopy(data)
    if 'item' in data: data['items']=data.pop('item')
    if 'tag' in data: data['items']='#'+data.pop('tag')
    if 'nbt' in data:
        raw=Snbt.parse(data.pop('nbt')); predicates=data.setdefault('predicates',{})
        state=raw.pop('ShareTag',{})
        if 'isBroken' in state: state['isBroken']=int(str(state['isBroken']).lower()=='true')
        if 'ForgeCaps' in raw: state.update(raw.pop('ForgeCaps').get('slashblade:bladestate',{}).get('State',{}))
        if state: predicates['slashblade:blade']=state
        if raw: predicates['minecraft:custom_data']=convert_nested(raw)
    return data

def advancement(value):
    if isinstance(value,list): return [advancement(x) for x in value]
    if not isinstance(value,dict): return value
    result={}
    for key,child in value.items():
        if key=='icon': result[key]=stack(child)
        elif key=='items' and isinstance(child,list): result[key]=[item_predicate(x) if isinstance(x,dict) else x for x in child]
        else: result[key]=advancement(child)
    return result

def recipe(data):
    data=copy.deepcopy(data)
    if 'key' in data: data['key']={key:ingredient(value) for key,value in data['key'].items()}
    for field in ('ingredient','template','base','addition'):
        if field in data: data[field]=ingredient(data[field])
    if 'ingredients' in data: data['ingredients']=[ingredient(x) for x in data['ingredients']]
    if 'result' in data: data['result']=stack(data['result'])
    return data

def loot(value):
    if isinstance(value,list): return [loot(x) for x in value]
    if not isinstance(value,dict): return value
    value=copy.deepcopy(value)
    if value.get('function') in ('minecraft:set_nbt','set_nbt'):
        raw=Snbt.parse(value.pop('tag'))
        value['function']='minecraft:set_components'
        value['components']=stack({'id':'slashblade:slashblade','nbt':raw}).get('components',{})
    return {key:loot(child) for key,child in value.items()}

def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def main():
    namespace=RES/'data/slashblade'
    pending=[]
    counts={}
    for old,new,converter in [('recipes','recipe',recipe),('advancements','advancement',advancement),('loot_tables','loot_table',loot)]:
        folder=namespace/old
        if not folder.exists(): raise RuntimeError(f'Already migrated or missing {folder}')
        for path in folder.rglob('*.json'):
            pending.append((path,namespace/new/path.relative_to(folder),converter(json.loads(path.read_text('utf-8')))))
        counts[new]=sum(1 for x in pending if x[1].is_relative_to(namespace/new))
    for old,new,pathConverter in [('items','item',lambda d:d)]:
        folder=namespace/'tags'/old
        for path in folder.rglob('*.json'): pending.append((path,namespace/'tags'/new/path.relative_to(folder),pathConverter(json.loads(path.read_text('utf-8')))))
    # Nothing is mutated until all SNBT and JSON descriptions have been parsed successfully.
    for old,new,data in pending:
        write(new,data); old.unlink()
    write(namespace/'blade_catalog.json',list(CATALOG.values()))
    models=RES/'assets/slashblade/models/item'
    for path in models.glob('*.json'):
        data=json.loads(path.read_text('utf-8'))
        if data.get('loader')=='forge:obj':
            data['loader']='neoforge:obj'; data['flip_v']=data.pop('custom',{}).get('flip-v',False)
        if path.stem=='slashblade':
            data.pop('parent',None); data.pop('overrides',None); data['elements']=[]
        write(path,data)
    names=['slashblade','proudsoul','proudsoul_ingot','proudsoul_tiny','proudsoul_sphere','proudsoul_crystal','proudsoul_trapezohedron','proudsoul_activated','proudsoul_awakened','bladestand_1','bladestand_2','bladestand_v','bladestand_s','bladestand_1w','bladestand_2w']
    for name in names:
        model={'type':'minecraft:model','model':f'slashblade:item/{name}'}
        if name=='slashblade':
            contexts={'gui':'gui','ground':'ground','fixed':'fixed','firstperson_righthand':'first_person_right_hand','firstperson_lefthand':'first_person_left_hand','thirdperson_righthand':'third_person_right_hand','thirdperson_lefthand':'third_person_left_hand','head':'head'}
            def special(context): return {'type':'minecraft:special','base':'slashblade:item/slashblade','model':{'type':'slashblade:blade','context':context}}
            model={'type':'minecraft:select','property':'minecraft:display_context','cases':[{'when':key,'model':special(value)} for key,value in contexts.items()],'fallback':special('none')}
        write(RES/f'assets/slashblade/items/{name}.json',{'model':model,'hand_animation_on_swap':False})
    game=ROOT.parents[1]
    with zipfile.ZipFile(game/'project02.jar') as jar: version=json.loads(jar.read('version.json'))
    formats=version['pack_version']
    low=min(formats.values(),key=lambda x:x['major'] if isinstance(x,dict) else x)
    high=max(formats.values(),key=lambda x:x['major'] if isinstance(x,dict) else x)
    def format_value(value): return [value['major'],value['minor']] if isinstance(value,dict) else value
    write(RES/'pack.mcmeta',{'pack':{'description':'SlashBlade 26.1.2 resources','min_format':format_value(low),'max_format':format_value(high)}})
    write(RES/'slashblade.mixins.json',{'package':'mods.flammpfeil.slashblade.mixin','mixins':['MixinBlockBehaviour'],'required':True,'minVersion':'0.8','compatibilityLevel':'JAVA_25'})
    toml=ROOT/'src/main/templates/META-INF/neoforge.mods.toml'
    toml.write_text(toml.read_text('utf-8')+'\n[[mixins]]\nconfig="slashblade.mixins.json"\n',encoding='utf-8')
    counts['catalog_blades']=len(CATALOG); counts['client_items']=len(names)
    write(ROOT/'tools/resource-migration-counts.json',counts)
    print(json.dumps(counts))

if __name__=='__main__': main()
