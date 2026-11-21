package com.cometinha.nstool;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.widget.*;
import androidx.documentfile.provider.DocumentFile;
import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
 static final int PICK_NSP=1, PICK_PROD_KEYS=2, PICK_TITLE_KEYS=3, PICK_DEST=4, PICK_TIK=5;
 TextView status,output;
 Button gameBtn,prodKeysBtn,titleKeysBtn,destBtn,scanBtn,extractBtn,tikBtn,ncaBtn;
 CheckBox onlyExe;
 Uri gameUri,prodKeysUri,titleKeysUri,destUri,tikUri;
 File gameFile;

 public void onCreate(Bundle b){
  super.onCreate(b);
  LinearLayout root=new LinearLayout(this);
  root.setOrientation(1);
  root.setPadding(24,24,24,24);
  TextView title=new TextView(this);
  title.setText("NSTool Android • ARM64 Beta 10");
  title.setTextSize(23);
  root.addView(title);
  TextView note=new TextView(this);
  note.setText("Leitor e extrator NSP/NCA. Selecione separadamente prod.keys e title.keys quando disponíveis.");
  root.addView(note);

  gameBtn=btn(root,"1. Selecionar arquivo NSP/NCA");
  prodKeysBtn=btn(root,"2. Selecionar prod.keys (opcional)");
  titleKeysBtn=btn(root,"3. Selecionar title.keys (opcional)");
  destBtn=btn(root,"4. Escolher pasta de destino");
  scanBtn=btn(root,"Analisar estrutura e metadados");
  extractBtn=btn(root,"Extrair para pasta escolhida");
  tikBtn=btn(root,"5. Selecionar .tik (para abrir NCA)");
  onlyExe=new CheckBox(this);
  onlyExe.setText("Só ExeFS (NÃO marque para o Peanuts: ele precisa do RomFS)");
  onlyExe.setChecked(false);
  root.addView(onlyExe);
  ncaBtn=btn(root,"Extrair conteúdo do NCA (Program)");

  status=new TextView(this);
  status.setText("Aguardando arquivo.");
  root.addView(status);
  ScrollView sc=new ScrollView(this);
  output=new TextView(this);
  output.setTextIsSelectable(true);
  sc.addView(output);
  root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
  setContentView(root);

  gameBtn.setOnClickListener(v->pick(PICK_NSP,"application/octet-stream"));
  prodKeysBtn.setOnClickListener(v->pick(PICK_PROD_KEYS,"*/*"));
  titleKeysBtn.setOnClickListener(v->pick(PICK_TITLE_KEYS,"*/*"));
  destBtn.setOnClickListener(v->{
   Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
   i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
   startActivityForResult(i,PICK_DEST);
  });
  tikBtn.setOnClickListener(v->pick(PICK_TIK,"*/*"));
  ncaBtn.setOnClickListener(v->runNca());
  scanBtn.setOnClickListener(v->runTool(false));
  extractBtn.setOnClickListener(v->runTool(true));
 }

 Button btn(LinearLayout l,String s){
  Button b=new Button(this);
  b.setText(s);
  l.addView(b);
  return b;
 }

 void pick(int request,String type){
  Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
  i.setType(type);
  i.addCategory(Intent.CATEGORY_OPENABLE);
  startActivityForResult(i,request);
 }

 protected void onActivityResult(int request,int result,Intent data){
  super.onActivityResult(request,result,data);
  if(result!=RESULT_OK||data==null)return;
  Uri u=data.getData();
  if(u==null)return;
  if(request==PICK_NSP){
   gameUri=u;
   gameFile=null;
   status.setText("Arquivo NSP/NCA selecionado: "+describe(u));
  } else if(request==PICK_PROD_KEYS){
   prodKeysUri=u;
   status.setText("prod.keys selecionado. Será passado ao NSTool pela opção -k.");
  } else if(request==PICK_TITLE_KEYS){
   titleKeysUri=u;
   status.setText("title.keys selecionado. Será instalado em HOME/.switch/title.keys.");
  } else if(request==PICK_TIK){
   tikUri=u;
   status.setText(".tik selecionado: "+describe(u)+"\n(o .tik certo tem 704 bytes; o .cert tem ~1792)");
  } else if(request==PICK_DEST){
   destUri=u;
   int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
   getContentResolver().takePersistableUriPermission(u,flags);
   status.setText("Pasta de destino selecionada.");
  }
 }

 String describe(Uri u){
  String name="?";long size=-1;
  try(android.database.Cursor c=getContentResolver().query(u,null,null,null,null)){
   if(c!=null&&c.moveToFirst()){
    int i=c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
    int j=c.getColumnIndex(android.provider.OpenableColumns.SIZE);
    if(i>=0)name=c.getString(i);
    if(j>=0&&!c.isNull(j))size=c.getLong(j);
   }
  }catch(Exception e){}
  return name+" ("+size+" bytes)";
 }

 File copy(Uri source,File target)throws Exception{
  File parent=target.getParentFile();
  if(parent!=null&&!parent.exists()&&!parent.mkdirs())throw new IOException("Não foi possível criar "+parent);
  try(InputStream in=getContentResolver().openInputStream(source)){
   if(in==null)throw new IOException("Não foi possível abrir o arquivo selecionado.");
   try(OutputStream out=new FileOutputStream(target)){
    byte[] buffer=new byte[65536];
    int n;
    while((n=in.read(buffer))>0)out.write(buffer,0,n);
   }
  }
  if(!target.isFile()||target.length()==0)throw new IOException("O arquivo selecionado está vazio.");
  return target;
 }

 void copyTree(File src,DocumentFile dst)throws Exception{
  File[] files=src.listFiles();
  if(files==null)return;
  for(File f:files){
   if(f.isDirectory()){
    DocumentFile child=dst.findFile(f.getName());
    if(child==null)child=dst.createDirectory(f.getName());
    if(child!=null)copyTree(f,child);
   }else{
    DocumentFile child=dst.findFile(f.getName());
    if(child==null)child=dst.createFile("application/octet-stream",f.getName());
    if(child!=null){
     try(InputStream in=new FileInputStream(f);
         OutputStream out=getContentResolver().openOutputStream(child.getUri(),"wt")){
      if(out==null)throw new IOException("Não foi possível gravar "+f.getName());
      byte[] buffer=new byte[65536];
      int n;
      while((n=in.read(buffer))>0)out.write(buffer,0,n);
     }
    }
   }
  }
 }

 void deleteTree(File f){
  if(f.isDirectory()){
   File[] files=f.listFiles();
   if(files!=null)for(File child:files)deleteTree(child);
  }
  f.delete();
 }

 void runTool(boolean extract){
  if(gameUri==null){status.setText("Selecione um NSP/NCA.");return;}
  if(extract&&destUri==null){status.setText("Escolha a pasta de destino.");return;}
  scanBtn.setEnabled(false);
  extractBtn.setEnabled(false);
  status.setText("Processando...");
  Executors.newSingleThreadExecutor().execute(()->{
   try{
    if(gameFile==null||!gameFile.exists())gameFile=copy(gameUri,new File(getFilesDir(),"selected-game.bin"));

    File home=new File(getFilesDir(),"nstool-home");
    File switchDir=new File(home,".switch");
    if(!switchDir.exists()&&!switchDir.mkdirs())throw new IOException("Não foi possível criar a pasta .switch.");

    File prodKeysFile=null;
    if(prodKeysUri!=null){
     prodKeysFile=copy(prodKeysUri,new File(getFilesDir(),"prod.keys"));
    }

    if(titleKeysUri!=null){
     copy(titleKeysUri,new File(switchDir,"title.keys"));
    }

    File bin=new File(getFilesDir(),"nstool");
    if(!bin.exists()){
     try(InputStream in=getAssets().open("nstool");OutputStream out=new FileOutputStream(bin)){
      byte[] buffer=new byte[65536];
      int n;
      while((n=in.read(buffer))>0)out.write(buffer,0,n);
     }
     bin.setExecutable(true);
    }

    File work=new File(getCacheDir(),extract?"extract-work":"scan-work");
    if(work.exists())deleteTree(work);
    if(!work.mkdirs()&&!work.isDirectory())throw new IOException("Não foi possível criar a pasta de trabalho.");
    File log=new File(work,"nstool.log");

    java.util.ArrayList<String> args=new java.util.ArrayList<>();
    args.add(bin.getAbsolutePath());
    if(prodKeysFile!=null){
     args.add("-k");
     args.add(prodKeysFile.getAbsolutePath());
    }
    if(extract){
     args.add("-x");
     args.add(new File(work,"contents").getAbsolutePath());
    }else{
     args.add("-v");
     args.add("--showlayout");
     args.add("--fstree");
    }
    args.add(gameFile.getAbsolutePath());

    ProcessBuilder pb=new ProcessBuilder(args);
    pb.environment().put("HOME",home.getAbsolutePath());
    pb.environment().put("USERPROFILE",home.getAbsolutePath());
    String nativeLibDir=getApplicationInfo().nativeLibraryDir;
    String inheritedLibPath=pb.environment().get("LD_LIBRARY_PATH");
    pb.environment().put("LD_LIBRARY_PATH",nativeLibDir+(inheritedLibPath==null||inheritedLibPath.isEmpty()?"":":"+inheritedLibPath));
    pb.redirectErrorStream(true);
    pb.redirectOutput(log);
    int code=pb.start().waitFor();

    if(extract&&code==0){
     DocumentFile root=DocumentFile.fromTreeUri(this,destUri);
     if(root==null)throw new IOException("A pasta de destino não está mais acessível. Selecione-a novamente.");
     DocumentFile folder=root.findFile("NSTool-Extracted");
     if(folder==null)folder=root.createDirectory("NSTool-Extracted");
     if(folder==null)throw new IOException("Não foi possível criar NSTool-Extracted na pasta escolhida.");
     copyTree(new File(work,"contents"),folder);
    }

    String message=read(log);
    final boolean hasProd=prodKeysFile!=null;
    final boolean hasTitle=titleKeysUri!=null;
    runOnUiThread(()->{
     String state="Código NSTool: "+code+(extract&&code==0?" • arquivos copiados para a pasta escolhida":"");
     if(code==0&&extract){
      state+="\nChaves: "+(hasProd?"prod.keys carregado":"prod.keys não selecionado")+"; "+(hasTitle?"title.keys instalado em HOME/.switch":"title.keys não selecionado");
      state+="\nNota: extrair um NSP remove os arquivos NCA do contêiner. Para abrir o conteúdo interno de um NCA, o arquivo precisa ser processado separadamente com as chaves correspondentes.";
     }
     status.setText(state);
     output.setText(message);
     scanBtn.setEnabled(true);
     extractBtn.setEnabled(true);
    });
   }catch(Exception e){
    runOnUiThread(()->{
     status.setText("Falha: "+e.getMessage());
     scanBtn.setEnabled(true);
     extractBtn.setEnabled(true);
    });
   }
  });
 }


 // ===== Beta 5: extração do conteúdo de um NCA =====
 static byte[] hex(String h){
  int n=h.length()/2;byte[] r=new byte[n];
  for(int i=0;i<n;i++)r[i]=(byte)Integer.parseInt(h.substring(2*i,2*i+2),16);
  return r;
 }
 static String hexs(byte[] b,int off,int len){
  StringBuilder sb=new StringBuilder();
  for(int i=off;i<off+len;i++)sb.append(String.format("%02x",b[i]&0xff));
  return sb.toString();
 }
 static java.util.HashMap<String,String> parseKeys(File f)throws IOException{
  java.util.HashMap<String,String> m=new java.util.HashMap<>();
  try(BufferedReader r=new BufferedReader(new FileReader(f))){
   String line;
   while((line=r.readLine())!=null){
    int i=line.indexOf('=');
    if(i<0)continue;
    m.put(line.substring(0,i).trim().toLowerCase(),line.substring(i+1).trim().toLowerCase());
   }
  }
  return m;
 }

 // Lê o .tik: acha o RightsID varrendo o arquivo (não depende do tipo de assinatura).
 // Retorna {rightsId, titleKeyCifrada, relatório, titleKeyDecifrada1, titleKeyDecifrada2, ...}
 String[] readTicket(File tik,File prod)throws Exception{
  byte[] t=new byte[(int)tik.length()];
  try(FileInputStream in=new FileInputStream(tik)){
   int o=0,n;
   while(o<t.length&&(n=in.read(t,o,t.length-o))>0)o+=n;
  }
  StringBuilder info=new StringBuilder();
  info.append("tik: ").append(t.length).append(" bytes, cabeçalho ").append(hexs(t,0,4)).append("\n");
  // RightsID = TitleID (8 bytes, não nulo) + 7 bytes zero + 1 byte de geração (1..0x30)
  int ro=-1;
  for(int o=0x100;o+16<=t.length&&ro<0;o++){
   boolean nz=false;
   for(int i=0;i<8;i++)if(t[o+i]!=0){nz=true;break;}
   if(!nz)continue;
   boolean z=true;
   for(int i=8;i<15;i++)if(t[o+i]!=0){z=false;break;}
   int g=t[o+15]&0xff;
   if(z&&g>=1&&g<=0x30)ro=o;
  }
  if(ro<0)throw new IOException("Não achei o RightsID dentro do .tik ("+t.length+" bytes). Header: "+hexs(t,0,16));
  int d=ro-0x160;
  if(d<0x40)throw new IOException("Layout do .tik inesperado (RightsID no offset "+ro+").");
  String rightsId=hexs(t,ro,16);
  int gen=t[ro+15]&0xff;
  int kb=d+0x40;
  boolean tailZero=true;
  for(int i=16;i<0x100&&kb+i<t.length;i++)if(t[kb+i]!=0){tailZero=false;break;}
  info.append("RightsID=").append(rightsId).append(" (offset 0x").append(Integer.toHexString(ro)).append(")\n");
  info.append("byte do ticket em d+0x145=").append(t[d+0x145]&0xff).append(", tipo d+0x141=").append(t[d+0x141]&0xff).append(", resto do bloco zerado=").append(tailZero).append("\n");
  if(!tailZero)throw new IOException("Title key parece personalizada (RSA): bloco de chave não é AES comum.\n"+info);
  String enc=hexs(t,kb,16);
  java.util.HashMap<String,String> keys=parseKeys(prod);
  java.util.LinkedHashSet<Integer> revs=new java.util.LinkedHashSet<>();
  revs.add(Math.max(gen-1,0));       // geração 18 -> titlekek_11
  revs.add(gen);                      // caso o byte já seja o índice
  revs.add(t[d+0x145]&0xff);          // campo do ticket
  java.util.ArrayList<String> out=new java.util.ArrayList<>();
  out.add(rightsId);out.add(enc);out.add("");
  for(int rev:revs){
   String name=String.format("titlekek_%02x",rev);
   String kek=keys.get(name);
   if(kek==null){info.append(name).append(": ausente no prod.keys\n");continue;}
   javax.crypto.Cipher c=javax.crypto.Cipher.getInstance("AES/ECB/NoPadding");
   c.init(javax.crypto.Cipher.DECRYPT_MODE,new javax.crypto.spec.SecretKeySpec(hex(kek),"AES"));
   out.add(hexs(c.doFinal(hex(enc)),0,16));
   info.append(name).append(": ok\n");
  }
  if(out.size()<=3)throw new IOException("Nenhum titlekek compatível no prod.keys.\n"+info);
  out.set(2,info.toString());
  return out.toArray(new String[0]);
 }

 int exec(java.util.List<String> args,File home,File log)throws Exception{
  ProcessBuilder pb=new ProcessBuilder(args);
  pb.environment().put("HOME",home.getAbsolutePath());
  pb.environment().put("USERPROFILE",home.getAbsolutePath());
  String nativeLibDir=getApplicationInfo().nativeLibraryDir;
  String inherited=pb.environment().get("LD_LIBRARY_PATH");
  pb.environment().put("LD_LIBRARY_PATH",nativeLibDir+(inherited==null||inherited.isEmpty()?"":":"+inherited));
  pb.redirectErrorStream(true);
  pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log));
  return pb.start().waitFor();
 }

 static void append(File f,String s){
  try(FileWriter w=new FileWriter(f,true)){w.write(s);}catch(Exception e){}
 }

 void ui(String st,String out,boolean enable){
  runOnUiThread(()->{
   if(st!=null)status.setText(st);
   if(out!=null)output.setText(out);
   scanBtn.setEnabled(enable);extractBtn.setEnabled(enable);ncaBtn.setEnabled(enable);
  });
 }

 File findFile(File dir,String name){
  File[] fs=dir.listFiles();
  if(fs==null)return null;
  for(File f:fs){
   if(f.isDirectory()){File r=findFile(f,name);if(r!=null)return r;}
   else if(f.getName().equals(name))return f;
  }
  return null;
 }

 void runNca(){
  if(gameUri==null){status.setText("Selecione o NCA Program (o de ~4,59 GB) no botão 1.");return;}
  if(prodKeysUri==null){status.setText("Selecione o prod.keys (botão 2).");return;}
  if(tikUri==null&&titleKeysUri==null){status.setText("Selecione o .tik do jogo (botão 5) ou um title.keys.");return;}
  if(destUri==null){status.setText("Escolha a pasta de destino (botão 4).");return;}
  final boolean exeOnly=onlyExe.isChecked();
  scanBtn.setEnabled(false);extractBtn.setEnabled(false);ncaBtn.setEnabled(false);
  status.setText("Copiando NCA para o armazenamento do app (pode demorar vários minutos)...");
  Executors.newSingleThreadExecutor().execute(()->{
   File work=new File(getCacheDir(),"nca-work");
   File log=new File(work,"nstool-nca.log");
   try{
    if(work.exists())deleteTree(work);
    if(!work.mkdirs())throw new IOException("Não foi possível criar a pasta de trabalho.");
    if(gameFile==null||!gameFile.exists())gameFile=copy(gameUri,new File(getFilesDir(),"selected-game.bin"));
    long need=gameFile.length();
    long free=getCacheDir().getUsableSpace();
    if(free<need*9/10)throw new IOException(String.format("Pouco espaço livre: %.1f GB livres, preciso de pelo menos ~%.1f GB. Libere espaço (apague o NSP/ZIPs temporários) e tente de novo.",free/1e9,need*0.9/1e9));

    runOnUiThread(()->status.setText("Preparando chaves..."));
    File prod=copy(prodKeysUri,new File(getFilesDir(),"prod.keys"));
    File home=new File(getFilesDir(),"nstool-home");
    File switchDir=new File(home,".switch");
    if(!switchDir.exists()&&!switchDir.mkdirs())throw new IOException("Não foi possível criar .switch.");
    File titleKeys=new File(switchDir,"title.keys");

    File bin=new File(getFilesDir(),"nstool");
    if(!bin.exists()){
     try(InputStream in=getAssets().open("nstool");OutputStream out=new FileOutputStream(bin)){
      byte[] buffer=new byte[65536];int n;
      while((n=in.read(buffer))>0)out.write(buffer,0,n);
     }
     bin.setExecutable(true);
    }

    java.util.ArrayList<String> titleKeyVariants=new java.util.ArrayList<>();
    String tkInfo="";
    if(tikUri!=null){
     File tik=copy(tikUri,new File(getFilesDir(),"selected.tik"));
     String[] r=readTicket(tik,prod);
     tkInfo=r[2];
     titleKeyVariants.add(r[0]+"="+r[1]+"\n"); // chave como está no ticket (FUNCIONOU no teste real com o Stray)
     for(int i=3;i<r.length;i++)titleKeyVariants.add(r[0]+"="+r[i]+"\n"); // chaves decifradas (fallback)
    }else{
     titleKeyVariants.add(null); // usa o title.keys escolhido pelo usuário
    }
    final String tkInfoF=tkInfo;
    append(log,"=== Beta 5 / extração de NCA ===\n"+tkInfo+"\n");

    // detecta as opções do NSTool
    File helpLog=new File(work,"help.log");
    exec(java.util.Arrays.asList(bin.getAbsolutePath(),"--help"),home,helpLog);
    String help=read(helpLog);
    boolean perPart=help.contains("--part0dir");
    append(log,"perPart="+perPart+"\n");

    // layout do NCA (ajuda no diagnóstico)
    java.util.ArrayList<String> a0=new java.util.ArrayList<>();
    a0.add(bin.getAbsolutePath());a0.add("-k");a0.add(prod.getAbsolutePath());
    a0.add("-v");a0.add("--showlayout");a0.add(gameFile.getAbsolutePath());
    if(titleKeyVariants.get(0)!=null)Files.write(titleKeys.toPath(),titleKeyVariants.get(0).getBytes("UTF-8"));
    else copy(titleKeysUri,titleKeys);
    exec(a0,home,log);

    File out=new File(work,"out");
    File exeDir=null;boolean found=false;
    for(int attempt=0;attempt<titleKeyVariants.size()&&!found;attempt++){
     String v=titleKeyVariants.get(attempt);
     if(v!=null)Files.write(titleKeys.toPath(),v.getBytes("UTF-8"));
     append(log,"\n--- tentativa "+(attempt+1)+(attempt==0||tikUri==null?" (title key do ticket)":" (title key decifrada #"+attempt+")")+" ---\n");
     if(out.exists())deleteTree(out);
     out.mkdirs();
     final int at=attempt;
     if(perPart){
      for(int i=0;i<4;i++){
       final int pi=i;
       runOnUiThread(()->status.setText("Extraindo partição "+pi+" (tentativa "+(at+1)+")..."));
       File pd=new File(out,"part"+i);
       java.util.ArrayList<String> a=new java.util.ArrayList<>();
       a.add(bin.getAbsolutePath());a.add("-k");a.add(prod.getAbsolutePath());
       a.add("--part"+i+"dir");a.add(pd.getAbsolutePath());
       a.add(gameFile.getAbsolutePath());
       int code=exec(a,home,log);
       append(log,"part"+i+" código "+code+"\n");
       if(new File(pd,"main.npdm").exists()){
        found=true;exeDir=pd;
        if(exeOnly)break;
       }else if(exeOnly&&pd.exists()){
        deleteTree(pd);
       }
      }
     }else{
      runOnUiThread(()->status.setText("Extraindo NCA (tentativa "+(at+1)+")..."));
      java.util.ArrayList<String> a=new java.util.ArrayList<>();
      a.add(bin.getAbsolutePath());a.add("-k");a.add(prod.getAbsolutePath());
      a.add("-x");a.add(out.getAbsolutePath());a.add(gameFile.getAbsolutePath());
      int code=exec(a,home,log);
      append(log,"-x código "+code+"\n");
      File npdm=findFile(out,"main.npdm");
      if(npdm!=null){found=true;exeDir=npdm.getParentFile();}
     }
    }

    DocumentFile root=DocumentFile.fromTreeUri(this,destUri);
    if(root==null)throw new IOException("A pasta de destino não está mais acessível. Selecione-a novamente.");
    DocumentFile folder=root.findFile("NSTool-NCA");
    if(folder==null)folder=root.createDirectory("NSTool-NCA");
    if(folder==null)throw new IOException("Não foi possível criar NSTool-NCA no destino.");

    if(found){
     runOnUiThread(()->status.setText("Copiando arquivos para a pasta escolhida..."));
     // Layout que o Peanuts Native espera: <pasta>/exefs e <pasta>/romfs
     DocumentFile ef=folder.findFile("exefs");
     if(ef==null)ef=folder.createDirectory("exefs");
     if(ef==null)throw new IOException("Não foi possível criar exefs no destino.");
     copyTree(exeDir,ef);
     if(!exeOnly){
      File romDir=null;
      File[] sib=exeDir.getParentFile().listFiles();
      if(sib!=null)for(File c:sib){
       if(!c.isDirectory()||c.equals(exeDir))continue;
       if(new File(c,"NintendoLogo.png").exists())continue; // partição do logo
       romDir=c;break;
      }
      if(romDir==null)throw new IOException("ExeFS copiado, mas não achei o RomFS extraído (partição de dados).");
      runOnUiThread(()->status.setText("Copiando RomFS (vários GB, pode demorar bastante)..."));
      DocumentFile rf=folder.findFile("romfs");
      if(rf==null)rf=folder.createDirectory("romfs");
      if(rf==null)throw new IOException("Não foi possível criar romfs no destino.");
      copyTree(romDir,rf);
     }
    }
    // sempre grava o log no destino para diagnóstico
    DocumentFile lg=folder.findFile("nstool-nca.log");
    if(lg==null)lg=folder.createFile("text/plain","nstool-nca.log");
    if(lg!=null)try(InputStream in=new FileInputStream(log);OutputStream o=getContentResolver().openOutputStream(lg.getUri(),"wt")){
     byte[] buffer=new byte[65536];int n;
     while((n=in.read(buffer))>0)o.write(buffer,0,n);
    }

    final boolean ok=found;
    String msg=read(log);
    if(ok)deleteTree(out);
    ui(ok?"SUCESSO: ExeFS encontrado (main + main.npdm). Arquivos em NSTool-NCA/ na pasta escolhida.\n"+tkInfoF
        :"FALHOU: main.npdm não apareceu. Chave errada, prod.keys incompleto ou NCA incorreto. Veja o log abaixo (também salvo em NSTool-NCA/nstool-nca.log).\n"+tkInfoF,msg,true);
   }catch(Exception e){
    StringBuilder m=new StringBuilder(read(log));
    m.append("\n").append(e.toString());
    ui("Falha: "+e.getMessage(),m.toString(),true);
   }
  });
 }

 String read(File f){
  try{
   if(!f.exists())return "";
   byte[] buffer=new byte[(int)Math.min(f.length(),2000000)];
   try(FileInputStream in=new FileInputStream(f)){in.read(buffer);}
   return new String(buffer);
  }catch(Exception e){return e.toString();}
 }
}