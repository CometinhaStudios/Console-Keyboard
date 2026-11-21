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
 static final int PICK_NSP=1,PICK_KEYS=2,PICK_DEST=3;
 TextView status,output; Button gameBtn,keysBtn,destBtn,scanBtn,extractBtn;
 Uri gameUri,keysUri,destUri; File gameFile,keysFile;
 public void onCreate(Bundle b){super.onCreate(b);
  LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(24,24,24,24);
  TextView title=new TextView(this);title.setText("NSTool Android • ARM64 Beta 3");title.setTextSize(23);root.addView(title);
  TextView note=new TextView(this);note.setText("Leitor e extrator NSP/NCA. Escolha a pasta onde salvar os arquivos extraídos.");root.addView(note);
  gameBtn=btn(root,"1. Selecionar arquivo NSP");keysBtn=btn(root,"2. Selecionar chaves (opcional)");destBtn=btn(root,"3. Escolher pasta de destino");scanBtn=btn(root,"Analisar estrutura e metadados");extractBtn=btn(root,"Extrair para pasta escolhida");
  status=new TextView(this);status.setText("Aguardando NSP.");root.addView(status);
  ScrollView sc=new ScrollView(this);output=new TextView(this);output.setTextIsSelectable(true);sc.addView(output);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
  gameBtn.setOnClickListener(v->pick(PICK_NSP,"application/octet-stream"));keysBtn.setOnClickListener(v->pick(PICK_KEYS,"*/*"));
  destBtn.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags( Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK_DEST);});
  scanBtn.setOnClickListener(v->runTool(false));extractBtn.setOnClickListener(v->runTool(true));
 }
 Button btn(LinearLayout l,String s){Button b=new Button(this);b.setText(s);l.addView(b);return b;}
 void pick(int n,String t){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType(t);i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,n);}
 protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(c!=RESULT_OK||d==null)return;Uri u=d.getData();if(r==PICK_NSP){gameUri=u;status.setText("NSP selecionado.");}if(r==PICK_KEYS){keysUri=u;status.setText("Chaves selecionadas.");}if(r==PICK_DEST){destUri=u;getContentResolver().takePersistableUriPermission(u,d.getFlags()& (Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));status.setText("Pasta de destino selecionada.");}}
 File copy(Uri u,String n)throws Exception{File f=new File(getFilesDir(),n);try(InputStream i=getContentResolver().openInputStream(u);OutputStream o=new FileOutputStream(f)){byte[]b=new byte[65536];int x;while((x=i.read(b))>0)o.write(b,0,x);}return f;}
 void copyTree(File src,DocumentFile dst)throws Exception{File[] a=src.listFiles();if(a==null)return;for(File f:a){if(f.isDirectory()){DocumentFile d=dst.createDirectory(f.getName());if(d!=null)copyTree(f,d);}else{DocumentFile d=dst.createFile("application/octet-stream",f.getName());if(d!=null)try(InputStream i=new FileInputStream(f);OutputStream o=getContentResolver().openOutputStream(d.getUri())){byte[]b=new byte[65536];int n;while((n=i.read(b))>0)o.write(b,0,n);}}}}
 void deleteTree(File f){if(f.isDirectory()){File[]a=f.listFiles();if(a!=null)for(File x:a)deleteTree(x);}f.delete();}
 void runTool(boolean extract){if(gameUri==null){status.setText("Selecione um NSP.");return;}if(extract&&destUri==null){status.setText("Escolha a pasta de destino.");return;}
  scanBtn.setEnabled(false);extractBtn.setEnabled(false);status.setText("Processando...");
  Executors.newSingleThreadExecutor().execute(()->{try{
   if(gameFile==null||!gameFile.exists())gameFile=copy(gameUri,"selected-game.nsp");
   if(keysUri!=null&&(keysFile==null||!keysFile.exists()))keysFile=copy(keysUri,"keys.dat");
   File bin=new File(getFilesDir(),"nstool");if(!bin.exists()){try(InputStream i=getAssets().open("nstool");OutputStream o=new FileOutputStream(bin)){byte[]b=new byte[65536];int n;while((n=i.read(b))>0)o.write(b,0,n);}bin.setExecutable(true);}
   File work=new File(getCacheDir(),extract?"extract-work":"scan-work");if(work.exists())deleteTree(work);work.mkdirs();File log=new File(work,"nstool.log");
   java.util.ArrayList<String> args=new java.util.ArrayList<>();
   args.add(bin.getAbsolutePath());
   if(keysFile!=null&&keysFile.exists()){args.add("-k");args.add(keysFile.getAbsolutePath());}
   if(extract){args.add("-x");args.add(new File(work,"contents").getAbsolutePath());}
   else{args.add("-v");args.add("--showlayout");args.add("--fstree");}
   args.add(gameFile.getAbsolutePath());
   ProcessBuilder pb=new ProcessBuilder(args);
   pb.redirectErrorStream(true);pb.redirectOutput(log);int code=pb.start().waitFor();
   if(extract&&code==0){DocumentFile root=DocumentFile.fromTreeUri(this,destUri);DocumentFile folder=root.createDirectory("NSTool-Extracted");if(folder==null)folder=root;copyTree(new File(work,"contents"),folder);}
   String msg=read(log);runOnUiThread(()->{status.setText("Código NSTool: "+code+(extract&&code==0?" • extração copiada para a pasta escolhida":""));output.setText(msg);scanBtn.setEnabled(true);extractBtn.setEnabled(true);});
  }catch(Exception e){runOnUiThread(()->{status.setText("Falha: "+e.getMessage());scanBtn.setEnabled(true);extractBtn.setEnabled(true);});}});
 }
 String read(File f){try{byte[]b=new byte[(int)Math.min(f.length(),2000000)];try(FileInputStream i=new FileInputStream(f)){i.read(b);}return new String(b);}catch(Exception e){return e.toString();}}
}