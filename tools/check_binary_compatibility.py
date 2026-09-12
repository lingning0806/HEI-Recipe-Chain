"""Check baseline public/protected members remain in the replacement jar (no game launch)."""
import struct,sys,zipfile

def abi(data):
 i=8
 def u2():
  nonlocal i
  n=struct.unpack_from('>H',data,i)[0];i+=2;return n
 def u4():
  nonlocal i
  n=struct.unpack_from('>I',data,i)[0];i+=4;return n
 pool={};size=u2();n=1
 while n<size:
  t=data[i];i+=1
  if t==1:
   length=u2();pool[n]=data[i:i+length].decode('utf-8',errors='replace');i+=length
  elif t in (3,4):i+=4
  elif t in (5,6):i+=8;n+=1
  elif t in (7,8,16,19,20):i+=2
  elif t in (9,10,11,12,17,18):i+=4
  elif t==15:i+=3
  else:raise ValueError(t)
  n+=1
 i+=6;interfaces=u2();i+=2*interfaces
 members=set()
 for kind in ('field','method'):
  count=u2()
  for _ in range(count):
   access=u2();name=pool[u2()];desc=pool[u2()]
   if access&5:members.add((kind,name,desc,access&8))
   for _ in range(u2()):
    u2();length=u4();i+=length
 return members

a,b=map(zipfile.ZipFile,sys.argv[1:3]);errors=[];count=0
for name in a.namelist():
 if not name.endswith('.class'):continue
 count+=1
 if name not in b.namelist():errors.append((name,'missing class'));continue
 removed=abi(a.read(name))-abi(b.read(name))
 if removed:errors.append((name,str(removed)))
for error in errors:print(error)
print('Checked',count,'baseline classes; incompatible removals:',len(errors))
sys.exit(bool(errors))
