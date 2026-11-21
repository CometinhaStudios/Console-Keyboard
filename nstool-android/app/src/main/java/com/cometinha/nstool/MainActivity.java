package com.cometinha.nstool;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.widget.*;
import androidx.documentfile.provider.DocumentFile;
import java.io.*;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
 static final int PICK_NSP=1, PICK_PROD_KEYS=2, PICK_TITLE_KEYS=3, PICK_DEST=4;
 TextView status,output;
 Button gameBtn,prodKeysBtn,titleKeysBtn,destBtn,scanBtn,extractBtn;
 Uri gameUri,prodKeysUri,titleKeysUri,destUri;
 File gameFile;

 public void onCreate(Bundle b){
  super.onCreate(b);
  LinearLayout root=new LinearLayout(this);
  root.setOrientation(1);
  root.setPadding(24,24,24,24);
  TextView title=new TextView(this);
  title.setText("NSTool Android • ARM64 Beta 4");
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
   status.setText("Arquivo NSP/NCA selecionado.");
  } else if(request==PICK_PROD_KEYS){
   prodKeysUri=u;
   status.setText("prod.keys selecionado. Será passado ao NSTool pela opção -k.");
  } else if(request==PICK_TITLE_KEYS){
   titleKeysUri=u;
   status.setText("title.keys selecionado. Será instalado em HOME/.switch/title.keys.");
  } else if(request==PICK_DEST){
   destUri=u;
   int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
   getContentResolver().takePersistableUriPermission(u,flags);
   status.setText("Pasta de destino selecionada.");
  }
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

 String read(File f){
  try{
   byte[] buffer=new byte[(int)Math.min(f.length(),2000000)];
   try(FileInputStream in=new FileInputStream(f)){in.read(buffer);}
   return new String(buffer);
  }catch(Exception e){return e.toString();}
 }
}
