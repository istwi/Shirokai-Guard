import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public class PatchFlan implements Opcodes {
 static final String ROOT="io/github/flemmli97/flan/commands/CommandClaim";
 static final Set<String> BASIC=Set.of("AddClaimCommand","ClaimGroupCommand","ClaimInfoCommand","ClaimModeCommand","ClaimPermissionCommand","ConfirmCommand","DeleteClaimCommand","ExpandCommand","ListClaimCommand","NameClaimCommand","OpenMenuCommand","PersonalGroupCommand");
 static final Handle META=new Handle(H_INVOKESTATIC,"java/lang/invoke/LambdaMetafactory","metafactory","(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",false);
 static void predicate(InsnList out) {
  out.add(new InvokeDynamicInsnNode("test","()Ljava/util/function/Predicate;",META,Type.getMethodType("(Ljava/lang/Object;)Z"),new Handle(H_INVOKESTATIC,ROOT,"zorykai$isAdmin","(Ljava/lang/Object;)Z",false),Type.getMethodType("(Ljava/lang/Object;)Z")));
 }
 static AbstractInsnNode previousInstruction(AbstractInsnNode node) {
  AbstractInsnNode previous=node.getPrevious();
  while(previous!=null && previous.getOpcode()<0)previous=previous.getPrevious();
  return previous;
 }
 static byte[] patch(byte[] bytes,String name) {
  ClassNode c=new ClassNode();new ClassReader(bytes).accept(c,0);
  if(name.equals(ROOT+".class")) {
   MethodNode admin=new MethodNode(ACC_PUBLIC|ACC_STATIC|ACC_SYNTHETIC,"zorykai$isAdmin","(Ljava/lang/Object;)Z",null,null);
   admin.instructions.add(new VarInsnNode(ALOAD,0));admin.instructions.add(new TypeInsnNode(CHECKCAST,"net/minecraft/class_2168"));
   admin.instructions.add(new FieldInsnNode(GETSTATIC,"io/github/flemmli97/flan/config/ConfigHandler","CONFIG","Lio/github/flemmli97/flan/config/Config;"));
   admin.instructions.add(new FieldInsnNode(GETFIELD,"io/github/flemmli97/flan/config/Config","permissionLevel","I"));
   admin.instructions.add(new MethodInsnNode(INVOKEVIRTUAL,"net/minecraft/class_2168","method_9259","(I)Z",false));admin.instructions.add(new InsnNode(IRETURN));c.methods.add(admin);
   MethodNode and=new MethodNode(ACC_PUBLIC|ACC_STATIC|ACC_SYNTHETIC,"zorykai$adminOnly","(Ljava/util/function/Predicate;)Ljava/util/function/Predicate;",null,null);
   and.instructions.add(new VarInsnNode(ALOAD,0));predicate(and.instructions);
   and.instructions.add(new MethodInsnNode(INVOKEINTERFACE,"java/util/function/Predicate","and","(Ljava/util/function/Predicate;)Ljava/util/function/Predicate;",true));and.instructions.add(new InsnNode(ARETURN));c.methods.add(and);
   MethodNode literal=new MethodNode(ACC_PUBLIC|ACC_STATIC|ACC_SYNTHETIC,"zorykai$gateLiteral","(Lcom/mojang/brigadier/builder/LiteralArgumentBuilder;)Lcom/mojang/brigadier/builder/LiteralArgumentBuilder;",null,null);
   literal.instructions.add(new VarInsnNode(ALOAD,0));literal.instructions.add(new VarInsnNode(ALOAD,0));
   literal.instructions.add(new MethodInsnNode(INVOKEVIRTUAL,"com/mojang/brigadier/builder/LiteralArgumentBuilder","getRequirement","()Ljava/util/function/Predicate;",false));
   literal.instructions.add(new MethodInsnNode(INVOKESTATIC,ROOT,"zorykai$adminOnly","(Ljava/util/function/Predicate;)Ljava/util/function/Predicate;",false));
   literal.instructions.add(new MethodInsnNode(INVOKEVIRTUAL,"com/mojang/brigadier/builder/LiteralArgumentBuilder","requires","(Ljava/util/function/Predicate;)Lcom/mojang/brigadier/builder/ArgumentBuilder;",false));
   literal.instructions.add(new TypeInsnNode(CHECKCAST,"com/mojang/brigadier/builder/LiteralArgumentBuilder"));literal.instructions.add(new InsnNode(ARETURN));c.methods.add(literal);
  } else {
   boolean restricted=!BASIC.contains(name.substring(name.lastIndexOf('/')+1,name.length()-6));
   int gates=0;
   for(MethodNode m:c.methods) if(m.name.equals("register")) {
    for(AbstractInsnNode n:m.instructions.toArray()) if(n instanceof MethodInsnNode call) {
     if(restricted && call.name.equals("requires") && call.desc.equals("(Ljava/util/function/Predicate;)Lcom/mojang/brigadier/builder/ArgumentBuilder;")) {
      m.instructions.insertBefore(n,new MethodInsnNode(INVOKESTATIC,ROOT,"zorykai$adminOnly","(Ljava/util/function/Predicate;)Ljava/util/function/Predicate;",false));
     }
     if(call.owner.equals("net/minecraft/class_2170") && call.name.equals("method_9247") && (restricted || (previousInstruction(n) instanceof LdcInsnNode ldc && Set.of("adminDelete","listAdminClaims").contains(ldc.cst)))) {
      m.instructions.insert(n,new MethodInsnNode(INVOKESTATIC,ROOT,"zorykai$gateLiteral","(Lcom/mojang/brigadier/builder/LiteralArgumentBuilder;)Lcom/mojang/brigadier/builder/LiteralArgumentBuilder;",false));gates++;
     }
    }
   }
   if(!restricted) for(MethodNode m:c.methods) {
    boolean adminLambda=false;
    for(AbstractInsnNode n:m.instructions.toArray()) if(m.name.startsWith("lambda$register$") && n instanceof MethodInsnNode call && call.name.equals("perm") && call.desc.endsWith(";Z)Z") && previousInstruction(n).getOpcode()==ICONST_1)adminLambda=true;
    if(adminLambda) for(AbstractInsnNode n:m.instructions.toArray())if(n.getOpcode()==IRETURN) {
     InsnList guard=new InsnList();guard.add(new VarInsnNode(ALOAD,0));
     guard.add(new MethodInsnNode(INVOKESTATIC,ROOT,"zorykai$isAdmin","(Ljava/lang/Object;)Z",false));guard.add(new InsnNode(IAND));m.instructions.insertBefore(n,guard);
    }
   }
   if(restricted && gates==0)throw new IllegalStateException("No gates: "+name);
   System.out.println("Restricted "+name+" ("+gates+" literals)");
  }
  ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(writer);return writer.toByteArray();
 }
 public static void main(String[] args)throws Exception {
  try(ZipFile input=new ZipFile(args[0]);ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))) {
   var entries=input.entries();while(entries.hasMoreElements()) {
    var entry=entries.nextElement();byte[] bytes=input.getInputStream(entry).readAllBytes();String name=entry.getName();
    boolean modify=name.equals(ROOT+".class");
    if(name.startsWith("io/github/flemmli97/flan/commands/sub/") && name.endsWith("Command.class")) {
     String simple=name.substring(name.lastIndexOf('/')+1,name.length()-6);modify=!BASIC.contains(simple) || Set.of("AddClaimCommand","DeleteClaimCommand","ListClaimCommand").contains(simple);
    }
    if(modify)bytes=patch(bytes,name);
    ZipEntry next=new ZipEntry(name);next.setTime(0);out.putNextEntry(next);out.write(bytes);out.closeEntry();
   }
  }
 }
}
