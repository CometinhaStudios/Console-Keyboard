package com.cometinha.nstool;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.widget.*;
import java.io.*;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
  static final int PICK_NSP=1, PICK_KEYS=2;
  TextView status, output;
  Button gameBtn, keysBtn, scanBtn, extractBtn;
  Uri gameUri, keysUri;
  File gameFile, keysFile;
  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    LinearLayout root=new LinearLayout(this); root.setOrientation(1); root.setPadding(24,24,24,24);
    TextView title=new TextView(this); title.setText("NSTool Android  •  ARM64"); title.setTextSize(23); root.addView(title);
    TextView note=new TextView(this); note.setText("Leitor de NSP / NCA. O arquivo selecionado será copiado para o armazenamento privado do app antes da análise; reserve espaço livre suficiente."); root.addView(note);
    gameBtn=new Button(this); gameBtn.setText("1. Selecionar arquivo NSP"); root.addView(gameBtn);
    keysBtn=new Button(this); keysBtn.setText("2. Selecionar arquivo de chaves (opcional)"); root.addView(keysBtn);
    scanBtn=new Button(this); scanBtn.setText("Analisar estrutura e metadados"); root.addView(scanBtn);
    extractBtn=new Button(this); extractBtn.setText("Extrair sistemas de arquivos"); root.addView(extractBtn);
    status=new TextView(this); status.setText("Aguardando arquivo."); root.addView(status);
    ScrollView scroll=new ScrollView(this); output=new TextView(this); output.setTextIsSelectable(true); scroll.addView(output); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    setContentView(root);
    gameBtn.setOnClickListener(v->pick(PICK_NSP,"application/octet-stream"));
    keysBtn.setOnClickListener(v->pick(PICK_KEYS,"*/*"));
    scanBtn.setOnClickListener(v->runTool(false));
    extractBtn.setOnClickListener(v->runTool(true));
  }
  void pick(int code,String type){ Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType(type); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i,code); }
  @Override protected void onActivityResult(int r,int c,Intent d){ super.onActivityResult(r,c,d); if(c==RESULT_OK&&d!=null){ if(r==PICK_NSP){gameUri=d.getData(); status.setText("NSP selecionado.");} else if(r==PICK_KEYS){keysUri=d.getData(); status.setText("Arquivo de chaves selecionado.");} } }
  File copy(Uri uri,String name)throws Exception { File f=new File(getFilesDir(),name); try(InputStream in=getContentResolver().openInputStream(uri); OutputStream out=new FileOutputStream(f)){byte[] b=new byte[1024*1024]; int n; while((n=in.read(b))>0)out.write(b,0,n);} return f; }
  void runTool(boolean extract){
    if(gameUri==null){status.setText("Selecione um NSP primeiro.");return;}
    scanBtn.setEnabled(false); extractBtn.setEnabled(false); status.setText("Preparando análise...");
    Executors.newSingleThreadExecutor().execute(()->{
      try{
        if(gameFile==null||!gameFile.exists()) gameFile=copy(gameUri,"selected-game.nsp");
        if(keysUri!=null&&(keysFile==null||!keysFile.exists())) keysFile=copy(keysUri,"keys.dat");
        File bin=new File(getFilesDir(),"nstool"); if(!bin.exists()){try(InputStream in=getAssets().open("nstool")){try(OutputStream o=new FileOutputStream(bin)){byte[] b=new byte[65536];int n;while((n=in.read(b))>0)o.write(b,0,n);}} bin.setExecutable(true);}
        File outDir=new File(getExternalFilesDir(null),extract?"extracted":"reports"); outDir.mkdirs();
        File report=new File(outDir,extract?"extract.log":"stray-analysis.txt");
        ProcessBuilder pb;
        if(extract) pb=new ProcessBuilder(bin.getAbsolutePath(),"-x",new File(outDir,"contents").getAbsolutePath(),gameFile.getAbsolutePath());
        else pb=new ProcessBuilder(bin.getAbsolutePath(),"-v","--showlayout","--fstree",gameFile.getAbsolutePath());
        pb.redirectErrorStream(true); pb.redirectOutput(report);
        Process p=pb.start(); int code=p.waitFor();
        runOnUiThread(()->{status.setText("NSTool terminou com código "+code+". Saída: "+report.getAbsolutePath()); output.setText(read(report)); scanBtn.setEnabled(true);extractBtn.setEnabled(true);});
      }catch(Exception e){runOnUiThread(()->{status.setText("Falha: "+e.getMessage());scanBtn.setEnabled(true);extractBtn.setEnabled(true);});}
    });
  }
  String read(File f){try{byte[] b=new byte[(int)Math.min(f.length(),2_000_000)];try(FileInputStream in=new FileInputStream(f)){in.read(b);}return new String(b);}catch(Exception e){return e.toString();}}
}
