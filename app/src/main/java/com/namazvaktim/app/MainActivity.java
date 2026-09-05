package com.namazvaktim.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.location.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity implements SensorEventListener {
    LinearLayout root, prayerBox;
    TextView locationText, dateText, countdownText, ayetArabic, ayetTurkish, ayetRef, categoryText;
    EditText cityInput;
    SharedPreferences prefs;
    JSONObject timings;
    final ExecutorService executor=Executors.newSingleThreadExecutor();
    final Handler handler=new Handler(Looper.getMainLooper());
    String currentScreen="main";
    int lastQuranPage=1;
    SensorManager sensorManager;
    Sensor rotationSensor;
    float[] gravity=new float[3];
    float[] geomagnetic=new float[3];
    boolean haveGravity=false, haveMagnetic=false;
    float qiblaBearing=0f;
    TextView qiblaDirectionText, qiblaArrow;

    static final String PREF_DONE="onboarding_done";
    static final String PREF_THEME="theme";
    static final String PREF_NOTIFY_MIN="notify_min";
    static final String PREF_NOTIFY_ON="notify_on";
    static final String PREF_AYET_NOTIFY="ayet_notify";
    static final String PREF_QURAN_PAGE="quran_page";

    final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateCountdown();
            autoThemeTick();
            handler.postDelayed(this, 1000);
        }
    };

    int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
    boolean night(){ return "night".equals(prefs.getString(PREF_THEME,"green")); }
    int bg(){return night()?Color.rgb(7,25,48):Color.rgb(250,248,242);} 
    int surface(){return night()?Color.rgb(13,42,72):Color.rgb(255,253,247);} 
    int primary(){return night()?Color.rgb(105,220,113):Color.rgb(27,94,32);} 
    int text(){return night()?Color.rgb(245,248,250):Color.rgb(45,45,45);} 
    int secondary(){return night()?Color.rgb(190,202,214):Color.rgb(95,95,95);} 
    int accent(){return night()?Color.rgb(255,193,7):Color.rgb(46,125,50);} 

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences("namaz",MODE_PRIVATE);
        restoreStoredTimings();
        applyBars();
        NotificationHelper.createChannels(this);
        requestNotificationPermission();
        if(prefs.getBoolean(PREF_DONE,false)) showMainScreen(); else showFirstLaunch();
    }
    void requestNotificationPermission(){
        if(Build.VERSION.SDK_INT >= 33 &&
           checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},30);
        }
    }
    void applyThemeToTree(View v, int bg, int fg){
        if(v==null)return;
        try{
            if(v instanceof TextView)((TextView)v).setTextColor(fg);
            if(v instanceof ViewGroup){
                ViewGroup g=(ViewGroup)v;
                for(int i=0;i<g.getChildCount();i++)applyThemeToTree(g.getChildAt(i),bg,fg);
            }
        }catch(Exception ignored){}
    }

void applyBars(){
        getWindow().setStatusBarColor(night()?Color.rgb(4,20,38):Color.rgb(27,94,32));
        getWindow().setNavigationBarColor(night()?Color.rgb(4,20,38):bg());
        int flags=getWindow().getDecorView().getSystemUiVisibility();
        if(!night()) flags|=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR; else flags&=~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        flags&=~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }
    TextView tv(String s,float size,int c){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(c);t.setPadding(dp(8),dp(7),dp(8),dp(7));return t;}
    Button btn(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextSize(15);b.setTextColor(text());GradientDrawable g=new GradientDrawable();g.setColor(surface());g.setCornerRadius(dp(12));g.setStroke(dp(1),accent());b.setBackground(g);return b;}
    LinearLayout page(){LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);p.setPadding(dp(16),dp(10),dp(16),dp(24));p.setBackgroundColor(Color.TRANSPARENT);return p;}
    TextView title(String s){TextView t=tv(s,26,primary());t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setGravity(Gravity.CENTER);return t;}
    void base(LinearLayout p,String name){
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(0,dp(4),0,dp(4));
        Button back=btn("‹");back.setTextSize(32);back.setPadding(0,0,0,0);back.setOnClickListener(v->showMainScreen());
        bar.addView(back,new LinearLayout.LayoutParams(dp(50),dp(55)));
        TextView t=title(name);bar.addView(t,new LinearLayout.LayoutParams(0,dp(55),1));
        p.addView(bar);
    }
    void setPage(LinearLayout p,String screen){
        currentScreen=screen;
        FrameLayout frame=new FrameLayout(this);
        ImageView wallpaper=new ImageView(this);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallpaper.setImageResource(night()?R.drawable.ayasofya_night:R.drawable.ayasofya_day);
        frame.addView(wallpaper,new FrameLayout.LayoutParams(-1,-1));

        View veil=new View(this);
        veil.setBackgroundColor(night()?Color.argb(150,0,8,20):Color.argb(135,255,255,255));
        frame.addView(veil,new FrameLayout.LayoutParams(-1,-1));

        ScrollView s=new ScrollView(this);
        s.setFillViewport(true);
        s.setBackgroundColor(Color.TRANSPARENT);
        s.addView(p);
        frame.addView(s,new FrameLayout.LayoutParams(-1,-1));
        setContentView(frame);
        p.setBackgroundColor(Color.TRANSPARENT);
        applyThemeToTree(p,bg(),text());
        applyBars();
    }

    void showFirstLaunch(){
        LinearLayout p=page();p.setGravity(Gravity.CENTER_HORIZONTAL);p.setPadding(dp(20),dp(36),dp(20),dp(30));
        TextView h=title("🕌 Namaz Vaktim");p.addView(h,new LinearLayout.LayoutParams(-1,dp(65)));
        TextView w=tv("Hoş geldiniz",23,text());w.setGravity(Gravity.CENTER);p.addView(w);
        TextView info=tv("İlk açılışta konumunuzu veya şehrinizi seçin. Seçiminiz kaydedilir ve sonraki açılışlarda bu sayfa tekrar gösterilmez.",16,text());info.setGravity(Gravity.CENTER);p.addView(info);
        Button loc=btn("📍 KONUMUMU KULLAN");loc.setOnClickListener(v->requestLocation(true));p.addView(loc,new LinearLayout.LayoutParams(-1,dp(56)));
        TextView or=tv("veya",14,secondary());or.setGravity(Gravity.CENTER);p.addView(or);
        cityInput=themedEdit(new EditText(this));cityInput.setHint("Şehir / ilçe (örn. Manisa)");cityInput.setSingleLine(true);p.addView(cityInput,new LinearLayout.LayoutParams(-1,dp(55)));
        Button city=btn("🏙️ ŞEHRİ KULLAN");city.setOnClickListener(v->useCityFromOnboarding());p.addView(city,new LinearLayout.LayoutParams(-1,dp(56)));
        setPage(p,"onboarding");
    }

    void showMainScreen(){
        LinearLayout p=page();
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView h=title("🕌 Namaz Vaktim");top.addView(h,new LinearLayout.LayoutParams(0,dp(58),1));
        Button menu=btn("☰");menu.setTextSize(25);menu.setOnClickListener(v->showMenu());top.addView(menu,new LinearLayout.LayoutParams(dp(58),dp(58)));p.addView(top);
        dateText=tv("",15,text());dateText.setGravity(Gravity.CENTER);p.addView(dateText);
        locationText=tv("Konum: yükleniyor...",16,text());p.addView(locationText);
        LinearLayout qrow=new LinearLayout(this); qrow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout qcard=new LinearLayout(this); qcard.setOrientation(LinearLayout.VERTICAL); qcard.setGravity(Gravity.CENTER);
        TextView qtitle=tv("KIBLE",16,primary()); qtitle.setGravity(Gravity.CENTER); qcard.addView(qtitle);
        qiblaArrow=tv("↑",42,primary()); qiblaArrow.setGravity(Gravity.CENTER); qcard.addView(qiblaArrow,new LinearLayout.LayoutParams(-1,dp(48)));
        qiblaDirectionText=tv("Kıble yönü hesaplanıyor...",14,text()); qiblaDirectionText.setGravity(Gravity.CENTER); qcard.addView(qiblaDirectionText);
        qcard.setOnClickListener(v->showQiblaDialog());
        qrow.addView(card(qcard),new LinearLayout.LayoutParams(dp(150),dp(105)));
        qrow.setGravity(Gravity.RIGHT); p.addView(qrow);
        countdownText=tv("⏳ Bir sonraki namaz hesaplanıyor...",20,primary());countdownText.setGravity(Gravity.CENTER);p.addView(card(countdownText));
        TextView ph=tv("Bugünün Namaz Vakitleri",22,primary());ph.setTypeface(Typeface.DEFAULT,Typeface.BOLD);p.addView(ph);
        prayerBox=new LinearLayout(this);prayerBox.setOrientation(LinearLayout.VERTICAL);p.addView(card(prayerBox));
        TextView kh=tv("⏰ Kerahat Vakitleri",20,primary());kh.setTypeface(Typeface.DEFAULT,Typeface.BOLD);p.addView(kh);
        LinearLayout kbox=new LinearLayout(this);kbox.setOrientation(LinearLayout.VERTICAL);p.addView(card(kbox));
        fillKerahetBox(kbox);
        TextView ah=tv("📖 Günün Ayeti",22,primary());ah.setTypeface(Typeface.DEFAULT,Typeface.BOLD);p.addView(ah);
        LinearLayout ab=new LinearLayout(this);ab.setOrientation(LinearLayout.VERTICAL);
        categoryText=tv("",14,secondary());ab.addView(categoryText);ayetArabic=tv("",23,text());ayetArabic.setGravity(Gravity.RIGHT);ayetArabic.setTextDirection(View.TEXT_DIRECTION_RTL);ab.addView(ayetArabic);ayetTurkish=tv("",17,text());ab.addView(ayetTurkish);ayetRef=tv("",14,secondary());ab.addView(ayetRef);
        Button share=btn("📤 Ayeti Paylaş");share.setOnClickListener(v->shareAyet());ab.addView(share);p.addView(card(ab));
        showAyet();
        String city=prefs.getString("city","");if(!city.isEmpty())locationText.setText("Konum: "+city);
        double lat=prefs.getFloat("lat",0),lon=prefs.getFloat("lon",0);if(lat!=0||lon!=0)loadPrayer(lat,lon,city);
        setPage(p,"main");handler.removeCallbacks(ticker);handler.post(ticker);
    }
    EditText themedEdit(EditText e){e.setTextColor(text());e.setHintTextColor(secondary());GradientDrawable g=new GradientDrawable();g.setColor(surface());g.setCornerRadius(dp(12));g.setStroke(dp(1),accent());e.setBackground(g);return e;}
    View card(View v){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(8),dp(8),dp(8),dp(8));box.setBackgroundColor(surface());box.addView(v);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(8));box.setLayoutParams(lp);return box;}

    void showMenu(){
        final String[] items={"🕌 Namaz Vakitleri","📿 Nasıl Kılınır?","📖 Ayetler","🤲 Dualar","⚙️ Ayarlar"};
        new AlertDialog.Builder(this).setTitle("☰ Menü").setItems(items,(d,w)->{
            if(w==0)showPrayerScreen(); else if(w==1)showHowTo(); else if(w==2)showAyetler(); else if(w==3)showDualar(); else showSettings();
        }).show();
    }

    void showPrayerScreen(){LinearLayout p=page();base(p,"Namaz Vakitleri");TextView c=tv("",20,primary());c.setGravity(Gravity.CENTER);p.addView(card(c));
        LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);p.addView(card(b));
        String city=prefs.getString("city","Konumum");TextView l=tv("📍 "+city,18,text());b.addView(l);String[] n={"Sabah","Güneş","Öğle","İkindi","Akşam","Yatsı"};String[] k={"Fajr","Sunrise","Dhuhr","Asr","Maghrib","Isha"};
        if(timings!=null)for(int i=0;i<n.length;i++)b.addView(tv(n[i]+" — "+timings.optString(k[i],"--:--"),18,text()));
        LinearLayout kb=new LinearLayout(this);kb.setOrientation(LinearLayout.VERTICAL);p.addView(card(kb));fillKerahetBox(kb);setPage(p,"prayer");
    }

    void showHowTo(){LinearLayout p=page();base(p,"Nasıl Kılınır?");String[] names={"Abdest nasıl alınır?","Sabah Namazı","Öğle Namazı","İkindi Namazı","Akşam Namazı","Yatsı Namazı"};for(String n:names){Button b=btn(n);b.setOnClickListener(v->showHowDetail(n));p.addView(b,new LinearLayout.LayoutParams(-1,dp(52)));}setPage(p,"how");}
    void showHowDetail(String name){LinearLayout p=page();base(p,name);TextView t=tv(howText(name),16,text());t.setLineSpacing(0,1.12f);p.addView(card(t));setPage(p,"howdetail");}
    String howText(String n){
        if(n.startsWith("Abdest")) return "ABDEST NASIL ALINIR?\n\n1. Niyet: Abdest almaya niyet edilir.\n2. Besmele: Besmele çekilir.\n3. Eller: Eller bileklere kadar yıkanır. Parmak araları temizlenir.\n4. Ağız ve burun: Ağız üç kez çalkalanır, buruna su verilip temizlenir.\n5. Yüz: Alın saç bitiminden çene altına ve iki kulak arasına kadar yıkanır.\n6. Kollar: Önce sağ, sonra sol kol dirsekle birlikte yıkanır.\n7. Baş ve kulaklar: Baş mesh edilir; kulaklar ve ense mesh edilir.\n8. Ayaklar: Önce sağ, sonra sol ayak topuklarla birlikte yıkanır; parmak araları temizlenir.\n\nAbdestin farzları ve bazı ayrıntılar mezheplere göre farklı değerlendirilebilir. Bu bölüm temel öğrenme rehberidir.";
        if(n.equals("Sabah Namazı")) return "SABAH NAMAZI\n\nToplam: 2 rekât sünnet + 2 rekât farz.\n\nSünnet: Niyet edilir, tekbir alınır. Kıyamda Sübhaneke, Eûzü-Besmele ve Fâtiha ile bir sûre okunur. Rükû ve iki secdeden sonra ikinci rekâta kalkılır. İkinci rekâtın sonunda oturulup Ettehiyyâtü, Salli-Barik ve Rabbena duaları okunur, sağa ve sola selam verilir.\n\nFarz: 2 rekât olarak kılınır. İkinci rekâtın sonunda son oturuş yapılır ve selam verilir.\n\nNot: Kıraat ve duaların ayrıntıları mezheplere göre değişebilir.";
        if(n.equals("Öğle Namazı")) return "ÖĞLE NAMAZI\n\nToplam: 4 rekât ilk sünnet + 4 rekât farz + 2 rekât son sünnet.\n\nİlk sünnet: Dört rekât kılınır. İkinci rekâtta ilk oturuş yapılır; üçüncü rekâta kalkınca kıraate yeniden başlanır. Dördüncü rekât sonunda son oturuş ve selam yapılır.\n\nFarz: Dört rekâttır. İkinci rekât sonunda ilk oturuş, dördüncü rekât sonunda son oturuş ve selam yapılır.\n\nSon sünnet: Sabah sünneti gibi 2 rekât kılınır.";
        if(n.equals("İkindi Namazı")) return "İKİNDİ NAMAZI\n\nToplam: 4 rekât sünnet + 4 rekât farz.\n\nSünnet: Dört rekât olarak kılınır. İkinci rekâtta oturuş yapılır; dördüncü rekât sonunda son oturuş ve selam verilir.\n\nFarz: Dört rekât olarak kılınır. İkinci rekâtta ilk oturuş, dördüncü rekâtta son oturuş yapılır.\n\nNot: Sünnetin ayrıntıları mezheplere göre farklılık gösterebilir.";
        if(n.equals("Akşam Namazı")) return "AKŞAM NAMAZI\n\nToplam: 3 rekât farz + 2 rekât son sünnet.\n\nFarz: Üç rekâttır. İkinci rekât sonunda ilk oturuş yapılır. Üçüncü rekâtta kıyamdan sonra rükû ve secdeler yapılır; son oturuşta dualar okunur ve selam verilir.\n\nSon sünnet: 2 rekât olarak kılınır. İkinci rekât sonunda son oturuş ve selam yapılır.";
        return "YATSI NAMAZI\n\nToplam: 4 rekât ilk sünnet + 4 rekât farz + 2 rekât son sünnet + 3 rekât vitir.\n\nİlk sünnet: 4 rekât. Farz: 4 rekât. Son sünnet: 2 rekât.\n\nVitir: 3 rekâttır. Hanefî uygulamasında üçüncü rekâtta Kunut dualarıyla ilgili özel uygulama bulunur. Diğer mezheplerde vitrin hükmü ve kılınışı farklı olabilir.\n\nBu bölüm temel öğrenme rehberidir; ibadetlerin ayrıntılı uygulamasında güvenilir bir ilmihal veya din görevlisinden destek alınması uygundur.";
    }

    void showAyetler(){LinearLayout p=page();base(p,"Ayetler");Button read=btn("📖 Kur'an'ı Oku — 604 Sayfa");read.setOnClickListener(v->showQuranReader(prefs.getInt(PREF_QURAN_PAGE,1)));p.addView(read);Button find=btn("🔎 Sure / Ayet Bul");find.setOnClickListener(v->showAyetFinder());p.addView(find);Button day=btn("🌟 Günün Ayeti");day.setOnClickListener(v->showDailyAyetDetail());p.addView(day);TextView info=tv("Kur'an okuyucuda sayfa numarası, yakınlaştırma, son sayfaya devam ve ayeti Mushaf'ta açma özellikleri bulunur.",14,secondary());p.addView(info);setPage(p,"ayetler");}

    void showDailyAyetDetail(){
        LinearLayout p=page();base(p,"Günün Ayeti");
        try{
            JSONObject o=new JSONObject(new String(readAll(getAssets().open("365_ayet.json")),"UTF-8"));
            JSONArray a=o.getJSONArray("items"); int d=Calendar.getInstance().get(Calendar.DAY_OF_YEAR); if(d>365)d=365;
            JSONObject x=a.getJSONObject(d-1);
            p.addView(card(tv("Kategori: "+x.optString("category"),14,secondary())));
            TextView ar=tv(x.optString("arabic"),26,text()); ar.setGravity(Gravity.RIGHT); ar.setTextDirection(View.TEXT_DIRECTION_RTL); p.addView(card(ar));
            p.addView(card(tv(x.optString("turkish_meaning"),17,text())));
            p.addView(tv(x.optString("surah")+" Suresi • "+x.optInt("ayah")+". Ayet",14,secondary()));
            Button share=btn("📤 Temalı Görseli Paylaş");
            share.setOnClickListener(v->shareAyetData(x.optString("category"), x.optString("arabic"), x.optString("turkish_meaning"), x.optString("surah")+" Suresi • "+x.optInt("ayah")+". Ayet"));
            p.addView(share);
        }catch(Exception e){p.addView(tv("Günün ayeti yüklenemedi.",16,text()));}
        setPage(p,"daily");
    }

    void showAyetFinder(){LinearLayout p=page();base(p,"Sure / Ayet Bul");TextView info=tv("Örnek: Bakara 255 veya 2:255",14,secondary());p.addView(info);EditText e=themedEdit(new EditText(this));e.setHint("Sure ve ayet numarası");e.setSingleLine(true);p.addView(e);Button b=btn("🔎 Bul");p.addView(b);LinearLayout result=new LinearLayout(this);result.setOrientation(LinearLayout.VERTICAL);p.addView(result);b.setOnClickListener(v->{int[] ref=parseRef(e.getText().toString());if(ref==null){Toast.makeText(this,"Örnek: Bakara 255 veya 2:255",Toast.LENGTH_SHORT).show();return;}fetchAyah(ref[0],ref[1],result);});setPage(p,"finder");}
    int[] parseRef(String s){s=s.trim();try{String[] parts=s.split(":");if(parts.length==2)return new int[]{Integer.parseInt(parts[0]),Integer.parseInt(parts[1])};String[] a=s.split("\\s+");if(a.length>=2){int ay=Integer.parseInt(a[a.length-1]);String name=s.substring(0,s.lastIndexOf(' ')).toLowerCase(new Locale("tr","TR"));String[] names={"fatiha","bakara","ali imran","nisa","maide","enam","araf","enfal","tevbe","yunus","hud","yusuf","rad","ibrahim","hicr","nahl","isra","kehf","meryem","taha","enbiya","hac","muminun","nur","furkan","şuara","neml","kasas","ankebut","rum","lokman","secde","ahzab","sebe","fatir","yasin","saffat","sad","zümer","mümin","fussilet","şura","zuhruf","duhan","casiye","ahkaf","muhammed","fetih","hucurat","kaf","zariyat","tur","necm","kamer","rahman","vakıa","hadid","mücadele","haşr","mümtehine","saff","cuma","münafikun","tegabun","talak","tahrim","mülk","kalem","hakka","mearic","nuh","cin","müzzemmil","müddessir","kıyamet","insan","mürselat","nebe","naziat","abese","tekvir","infitar","mutaffifin","inşikak","buruc","tarık","ala","gaşiye","fecr","beled","şems","leyl","duha","şerh","tin","alak","kadr","beyyine","zilzal","adiyat","karia","tekasür","asr","hümeze","fil","kureyş","maun","kevser","kafirun","nasr","tebbet","ihlas","felak","nas"};for(int i=0;i<names.length;i++)if(names[i].equals(name))return new int[]{i+1,ay};}}catch(Exception ignored){}return null;}

    void fetchAyah(int surah,int ayah,LinearLayout result){
        result.removeAllViews(); result.addView(tv("Yükleniyor...",16,secondary()));
        executor.execute(()->{
            try{
                String edition=findTurkishEdition();
                String url="https://api.alquran.cloud/v1/ayah/"+surah+":"+ayah+"/editions/quran-uthmani"+(edition.isEmpty()?"":","+edition);
                String a=get(url);
                JSONArray arr=new JSONObject(a).getJSONArray("data");
                JSONObject ar=arr.getJSONObject(0);
                JSONObject tr=arr.length()>1?arr.getJSONObject(1):null;
                String arabic=ar.optString("text","");
                String meal=tr==null?"Türkçe meal bulunamadı.":tr.optString("text","");
                int page=ar.optInt("page",1);
                String surahName=ar.optJSONObject("surah")!=null?ar.getJSONObject("surah").optString("englishName","Sure"):"Sure";
                runOnUiThread(()->{
                    result.removeAllViews();
                    result.addView(tv(surahName+" — "+ayah+". Ayet",19,primary()));
                    TextView aa=tv(arabic,26,text()); aa.setTextDirection(View.TEXT_DIRECTION_RTL); aa.setGravity(Gravity.RIGHT); result.addView(card(aa));
                    result.addView(card(tv("Türkçe meal\n"+meal,15,text())));
                    Button mush=btn("📖 Mushaf'ta Gör — Sayfa "+page); mush.setOnClickListener(v->showQuranReader(page)); result.addView(mush);
                });
            }catch(Exception e){
                runOnUiThread(()->{result.removeAllViews();result.addView(tv("Ayet alınamadı. İnternet bağlantınızı kontrol edin.",16,text()));});
            }
        });
    }

    String findTurkishEdition() throws Exception{
        String s=get("https://api.alquran.cloud/v1/edition?format=text&language=tr&type=translation");
        JSONArray a=new JSONObject(s).getJSONArray("data");
        for(int i=0;i<a.length();i++){String id=a.getJSONObject(i).optString("identifier","");if(!id.isEmpty())return id;}
        return "";
    }


    void showQuranReader(int page){lastQuranPage=Math.max(1,Math.min(604,page));prefs.edit().putInt(PREF_QURAN_PAGE,lastQuranPage).apply();
        LinearLayout p=page();base(p,"Kur'an'ı Oku");LinearLayout nav=new LinearLayout(this);Button prev=btn("◀");Button next=btn("▶");TextView pageNo=tv("Sayfa "+lastQuranPage+" / 604",17,primary());pageNo.setGravity(Gravity.CENTER);nav.addView(prev,new LinearLayout.LayoutParams(dp(55),dp(50)));nav.addView(pageNo,new LinearLayout.LayoutParams(0,dp(50),1));nav.addView(next,new LinearLayout.LayoutParams(dp(55),dp(50)));p.addView(nav);Button go=btn("🔢 Sayfaya Git");p.addView(go);Button mark=btn("🔖 Bu sayfayı yer imlerine kaydet");p.addView(mark);
         WebView w=new WebView(this);
         w.setBackgroundColor(bg());
         w.getSettings().setJavaScriptEnabled(true);
         w.getSettings().setSupportZoom(true);
         w.getSettings().setBuiltInZoomControls(true);
         w.getSettings().setDisplayZoomControls(false);
         w.getSettings().setLoadWithOverviewMode(true);
         w.getSettings().setUseWideViewPort(true);
         w.setInitialScale(0);
         w.getSettings().setTextZoom(100);
         w.setWebViewClient(new WebViewClient(){
             @Override public void onPageFinished(WebView view,String url){
                 super.onPageFinished(view,url);
                 String js="(function(){try{var els=[...document.querySelectorAll('button,a')];var e=els.find(x=>x.textContent.trim()==='Single');if(e)e.click();document.querySelectorAll('header,nav,footer').forEach(function(x){x.style.display='none';});document.body.style.background='"+String.format(Locale.US,"#%06X",0xFFFFFF & bg())+"';document.body.style.color='"+String.format(Locale.US,"#%06X",0xFFFFFF & text())+"';document.body.style.margin='0';document.documentElement.style.background='"+String.format(Locale.US,"#%06X",0xFFFFFF & bg())+"';document.querySelectorAll('*').forEach(function(x){x.style.color='"+String.format(Locale.US,"#%06X",0xFFFFFF & text())+"';});}catch(e){}})();";
                 view.evaluateJavascript(js,null);
             }
         });
         LinearLayout.LayoutParams wlp=new LinearLayout.LayoutParams(-1,Math.max(dp(640),getResources().getDisplayMetrics().heightPixels-dp(180)));
         p.addView(w,wlp);
        String url="https://alquran.cloud/mushaf/"+lastQuranPage;w.loadUrl(url);
        prev.setOnClickListener(v->{if(lastQuranPage>1)showQuranReader(lastQuranPage-1);});next.setOnClickListener(v->{if(lastQuranPage<604)showQuranReader(lastQuranPage+1);});go.setOnClickListener(v->pageDialog());mark.setOnClickListener(v->{prefs.edit().putInt(PREF_QURAN_PAGE,lastQuranPage).apply();Toast.makeText(this,"Sayfa kaydedildi.",Toast.LENGTH_SHORT).show();});setPage(p,"quran");
    }
    void pageDialog(){final EditText e=themedEdit(new EditText(this));e.setInputType(2);e.setHint("1 - 604");new AlertDialog.Builder(this).setTitle("Sayfaya Git").setView(e).setPositiveButton("Git",(d,w)->{try{int n=Integer.parseInt(e.getText().toString());if(n>=1&&n<=604)showQuranReader(n);else Toast.makeText(this,"1 ile 604 arasında bir sayı girin.",Toast.LENGTH_SHORT).show();}catch(Exception ex){}}).setNegativeButton("İptal",null).show();}

    void showDualar(){LinearLayout p=page();base(p,"Dualar");String[] cats={"🛏️ Yatmadan Önce Okunacak Dualar","🍽️ Yemek Duaları","🤲 Şifa Duaları","🕋 Kur'an'daki Dualar","🕌 Peygamber Duaları"};for(String c:cats){Button b=btn(c);b.setOnClickListener(v->showDuaCategory(c));p.addView(b,new LinearLayout.LayoutParams(-1,dp(54)));}setPage(p,"dualar");}
    void showDuaCategory(String cat){LinearLayout p=page();base(p,cat);String content=duaText(cat);TextView t=tv(content,16,text());t.setLineSpacing(0,1.15f);p.addView(card(t));Button share=btn("📤 Paylaş");share.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,content+"\n\nNamaz Vaktim");startActivity(Intent.createChooser(i,"Dua paylaş"));});p.addView(share);setPage(p,"dua");}
    String duaText(String c){if(c.startsWith("🛏"))return "Yatarken Okunabilecek Dua\n\nاللَّهُمَّ أَسْلَمْتُ نَفْسِي إِلَيْكَ، وَوَجَّهْتُ وَجْهِيَ إِلَيْكَ...\n\nAnlamı: Allah'ım! Kendimi Sana teslim ettim ve yüzümü Sana çevirdim...\n\nKaynak: Buhârî, Deavât; Müslim, Zikir ve Dua.\n\nAyrıca Âyetü'l-Kürsî, İhlâs, Felak ve Nâs sureleri de okunabilir.";if(c.startsWith("🍽"))return "Yemek Öncesi\n\nبِسْمِ اللَّهِ\n\nOkunuş: Bismillâh.\nAnlamı: Allah'ın adıyla.\n\nYemek Sonrası\n\nالْحَمْدُ لِلَّهِ\n\nOkunuş: Elhamdülillâh.\nAnlamı: Hamd Allah'a mahsustur.\n\nNot: Uzun ve kaynağı belirsiz 'yemek duası' metinleri yerine kısa ve güvenilir zikirler tercih edilmiştir.";if(c.startsWith("🤲"))return "Şifa Duası\n\nاللَّهُمَّ رَبَّ النَّاسِ، أَذْهِبِ الْبَأْسَ، اشْفِ أَنْتَ الشَّافِي، لَا شِفَاءَ إِلَّا شِفَاؤُكَ...\n\nAnlamı: Ey insanların Rabbi! Sıkıntıyı gider, şifa ver. Şifa veren Sensin; Senin şifandan başka şifa yoktur...\n\nKaynak: Buhârî, Merdâ; Müslim, Selâm.";if(c.startsWith("🕋"))return "Kur'an'daki Dualar\n\nرَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً وَفِي الْآخِرَةِ حَسَنَةً وَقِنَا عَذَابَ النَّارِ\nBakara 2/201\n\nرَبِّ زِدْنِي عِلْمًا\nTâhâ 20/114\n\nرَبِّ اشْرَحْ لِي صَدْرِي ۝ وَيَسِّرْ لِي أَمْرِي\nTâhâ 20/25-26\n\nرَبَّنَا ظَلَمْنَا أَنْفُسَنَا\nA'râf 7/23\n\nلَا إِلَٰهَ إِلَّا أَنْتَ سُبْحَانَكَ إِنِّي كُنْتُ مِنَ الظَّالِمِينَ\nEnbiyâ 21/87";return "Peygamber Duaları\n\nAllahümme Rabben-nâsi...\nHastalık ve şifa için rivayet edilen dua.\nKaynak: Buhârî, Merdâ; Müslim, Selâm.\n\nAllahümme innî es'elükel-hüdâ vet-tukâ vel-afâfe vel-gınâ.\nHidâyet, takvâ, iffet ve gönül zenginliği isteme duası.\nKaynak: Müslim, Zikir ve Dua.\n\nAllahümme innî es'elükel-cennete ve eûzü bike minen-nâr.\nCennet isteme ve ateşten korunma duası.\nKaynak: Ebû Dâvûd.";}

    void showSettings(){
        LinearLayout p=page();base(p,"Ayarlar");
        Button loc=btn("📍 Konum Değiştirme");loc.setOnClickListener(v->changeLocation());p.addView(loc);
        Button notif=btn("🔔 Bildirim Ayarı");notif.setOnClickListener(v->showNotificationSettings());p.addView(notif);
        Button theme=btn("🎨 Tema: Otomatik");theme.setOnClickListener(v->showThemeDialog());p.addView(theme);
        TextView auto=tv("Tema, seçili konumun namaz vakitlerine göre otomatik değişir.\n\n☀️ İmsak → Açık Tema\n🌙 Akşam → Gece Tema",16,text());
        auto.setGravity(Gravity.CENTER);p.addView(card(auto));
        TextView about=tv("Namaz Vaktim\nSürüm 1.6.0 • Otomatik Tema",13,secondary());about.setGravity(Gravity.CENTER);p.addView(about);
        setPage(p,"settings");
    }
    void changeLocation(){showFirstLaunch();prefs.edit().putBoolean(PREF_DONE,false).apply();}
    void showThemeDialog(){
        String state=night()?"🌙 Şu an: Gece Tema":"☀️ Şu an: Açık Tema";
        new AlertDialog.Builder(this)
            .setTitle("🎨 Otomatik Tema")
            .setMessage(state+"\n\nUygulama temayı namaz vakitlerine göre otomatik değiştirir.\n\n☀️ İmsak vaktinde → Açık Tema\n🌙 Akşam namazı vaktinde → Gece Tema")
            .setPositiveButton("Tamam",null)
            .show();
    }
    void showNotificationSettings(){
        int current=prefs.getInt(PREF_NOTIFY_MIN,3);
        LinearLayout p=new LinearLayout(this); p.setOrientation(LinearLayout.VERTICAL); p.setPadding(dp(8),dp(4),dp(8),0);
        CheckBox on=new CheckBox(this); on.setTextColor(text()); on.setButtonTintList(android.content.res.ColorStateList.valueOf(accent())); on.setText("Namaz bildirimleri"); on.setChecked(prefs.getBoolean(PREF_NOTIFY_ON,true)); p.addView(on);
        CheckBox ay=new CheckBox(this); ay.setTextColor(text()); ay.setButtonTintList(android.content.res.ColorStateList.valueOf(accent())); ay.setText("Günün Ayeti bildirimi"); ay.setChecked(prefs.getBoolean(PREF_AYET_NOTIFY,true)); p.addView(ay);
        TextView info=tv("Namaz vaktinden kaç dakika önce bildirim gelsin? 1–60 dakika arasında istediğiniz değeri yazabilirsiniz.",14,secondary()); p.addView(info);
        EditText min=themedEdit(new EditText(this)); min.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); min.setSingleLine(true); min.setHint("Örn. 7"); min.setText(String.valueOf(current)); p.addView(min);
        new AlertDialog.Builder(this).setTitle("🔔 Bildirim Ayarı").setView(p).setPositiveButton("Kaydet",(d,w)->{
            int value=current; try{value=Integer.parseInt(min.getText().toString().trim());}catch(Exception ignored){}
            value=Math.max(1,Math.min(60,value));
            prefs.edit().putBoolean(PREF_NOTIFY_ON,on.isChecked()).putBoolean(PREF_AYET_NOTIFY,ay.isChecked()).putInt(PREF_NOTIFY_MIN,value).apply();
            rescheduleNotifications();
        }).setNegativeButton("İptal",null).show();
    }
    void rescheduleNotifications(){if(timings!=null)NotificationHelper.schedulePrayerNotifications(this,timings);NotificationHelper.scheduleDailyAyet(this);}

    void fillKerahetBox(LinearLayout b){b.removeAllViews();if(timings==null){b.addView(tv("Vakitler yüklendiğinde hesaplanır.",15,secondary()));return;}try{String sr=timings.optString("Sunrise").substring(0,5);String ss=timings.optString("Maghrib").substring(0,5);String dh=timings.optString("Dhuhr").substring(0,5);b.addView(tv("🌅 Güneş doğarken: "+sr+" — "+addMin(sr,20),16,text()));b.addView(tv("☀️ Öğle öncesi: "+subMin(dh,10)+" — "+dh,16,text()));b.addView(tv("🌇 Güneş batarken: "+subMin(ss,20)+" — "+ss,16,text()));}catch(Exception e){b.addView(tv("Kerahat vakitleri hesaplanamadı.",15,secondary()));}}
    String addMin(String s,int m){return shift(s,m);}String subMin(String s,int m){return shift(s,-m);}String shift(String s,int delta){try{String[] p=s.split(":");Calendar c=Calendar.getInstance();c.set(Calendar.HOUR_OF_DAY,Integer.parseInt(p[0]));c.set(Calendar.MINUTE,Integer.parseInt(p[1]));c.add(Calendar.MINUTE,delta);return String.format(Locale.US,"%02d:%02d",c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE));}catch(Exception e){return s;}}

    void requestLocation(boolean onboarding){boolean f=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;boolean c=checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;if(!f&&!c){requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},onboarding?21:20);return;}LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);try{boolean gps=lm.isProviderEnabled(LocationManager.GPS_PROVIDER);String provider=gps?LocationManager.GPS_PROVIDER:LocationManager.NETWORK_PROVIDER;Location l=lm.getLastKnownLocation(provider);if(l!=null){handleLocation(l.getLatitude(),l.getLongitude(),onboarding);return;}lm.requestSingleUpdate(provider,new LocationListener(){public void onLocationChanged(Location x){handleLocation(x.getLatitude(),x.getLongitude(),onboarding);}public void onProviderEnabled(String s){}public void onProviderDisabled(String s){}public void onStatusChanged(String s,int a,Bundle b){}},Looper.getMainLooper());Toast.makeText(this,"Konumunuz alınıyor...",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"Konum alınamadı.",Toast.LENGTH_LONG).show();}}
    void handleLocation(double lat,double lon,boolean onboarding){
        executor.execute(()->{
            String city=resolveCity(lat,lon);
            if(city.isEmpty()) city="Konumum";
            final String selectedCity=city;
            runOnUiThread(()->{
                prefs.edit().putString("city",selectedCity)
                    .putFloat("lat",(float)lat)
                    .putFloat("lon",(float)lon)
                    .putBoolean(PREF_DONE,true).apply();
                showMainScreen();
            });
        });
    }
    String resolveCity(double lat,double lon){try{Geocoder g=new Geocoder(this,new Locale("tr","TR"));List<Address>a=g.getFromLocation(lat,lon,1);if(a!=null&&!a.isEmpty()){String x=a.get(0).getLocality();if(x==null||x.isEmpty())x=a.get(0).getAdminArea();return x==null?"":x;}}catch(Exception ignored){}return "";}
    void useCityFromOnboarding(){
        String s=cityInput.getText().toString().trim();
        if(s.isEmpty()) return;
        executor.execute(()->{
            try{
                Geocoder g=new Geocoder(this,new Locale("tr","TR"));
                List<Address>a=g.getFromLocationName(s,1);
                if(a==null||a.isEmpty()){
                    runOnUiThread(()->Toast.makeText(this,"Şehir bulunamadı.",Toast.LENGTH_SHORT).show());
                    return;
                }
                Address x=a.get(0);
                String foundCity=x.getLocality();
                if(foundCity==null||foundCity.isEmpty()) foundCity=s;
                final String selectedCity=foundCity;
                double lat=x.getLatitude(),lon=x.getLongitude();
                runOnUiThread(()->{
                    prefs.edit().putString("city",selectedCity)
                        .putFloat("lat",(float)lat)
                        .putFloat("lon",(float)lon)
                        .putBoolean(PREF_DONE,true).apply();
                    showMainScreen();
                });
            }catch(Exception e){
                runOnUiThread(()->Toast.makeText(this,"Şehir aranırken hata oluştu.",Toast.LENGTH_LONG).show());
            }
        });
    }
    @Override public void onRequestPermissionsResult(int r,String[]p,int[]g){super.onRequestPermissionsResult(r,p,g);if(r==21&&g.length>0){boolean ok=false;for(int x:g)if(x==PackageManager.PERMISSION_GRANTED)ok=true;if(ok)requestLocation(true);}}

    void loadPrayer(double lat,double lon,String label){executor.execute(()->{try{String date=new SimpleDateFormat("dd-MM-yyyy",Locale.US).format(new Date());String s=get("https://api.aladhan.com/v1/timings/"+date+"?latitude="+lat+"&longitude="+lon+"&method=13");JSONObject t=new JSONObject(s).getJSONObject("data").getJSONObject("timings");timings=t;
                prefs.edit().putString("timings_json", t.toString()).apply();
                boolean themeChanged=applyAutomaticThemeFromTimings(false);
                runOnUiThread(()->{
                    if(themeChanged){
                        handler.postDelayed(this::recreate,80);
                        return;
                    }if(locationText!=null)locationText.setText("Konum: "+label);if(prayerBox!=null){prayerBox.removeAllViews();String[] k={"Fajr","Sunrise","Dhuhr","Asr","Maghrib","Isha"};String[] n={"Sabah","Güneş","Öğle","İkindi","Akşam","Yatsı"};for(int i=0;i<k.length;i++)prayerBox.addView(tv(n[i]+" — "+t.optString(k[i],"--:--"),18,text()));}if(currentScreen.equals("main")){rescheduleNotifications();} });}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Namaz vakitleri alınamadı. İnternet bağlantınızı kontrol edin.",Toast.LENGTH_LONG).show());}});}
    void updateCountdown(){if(countdownText==null||timings==null)return;try{String[] k={"Fajr","Dhuhr","Asr","Maghrib","Isha"};String[] n={"Sabah","Öğle","İkindi","Akşam","Yatsı"};Calendar now=Calendar.getInstance();Calendar target=null;String name="";for(int i=0;i<k.length;i++){String r=timings.optString(k[i],"");if(r.length()<5)continue;String[] z=r.substring(0,5).split(":");Calendar x=(Calendar)now.clone();x.set(Calendar.HOUR_OF_DAY,Integer.parseInt(z[0]));x.set(Calendar.MINUTE,Integer.parseInt(z[1]));x.set(Calendar.SECOND,0);x.set(Calendar.MILLISECOND,0);if(x.after(now)){target=x;name=n[i];break;}}if(target==null){String r=timings.optString("Fajr","");String[]z=r.substring(0,5).split(":");target=(Calendar)now.clone();target.add(Calendar.DAY_OF_YEAR,1);target.set(Calendar.HOUR_OF_DAY,Integer.parseInt(z[0]));target.set(Calendar.MINUTE,Integer.parseInt(z[1]));target.set(Calendar.SECOND,0);name="Sabah";}long d=target.getTimeInMillis()-now.getTimeInMillis();long h=d/3600000,m=(d%3600000)/60000,s=(d/1000)%60;countdownText.setText("⏳ "+name+" namazına "+h+" saat "+m+" dakika "+s+" saniye kaldı");}catch(Exception e){}}

    void showAyet(){try{JSONObject o=new JSONObject(new String(readAll(getAssets().open("365_ayet.json")),"UTF-8"));JSONArray a=o.getJSONArray("items");int d=Calendar.getInstance().get(Calendar.DAY_OF_YEAR);if(d>365)d=365;JSONObject x=a.getJSONObject(d-1);categoryText.setText("Kategori: "+x.optString("category"));ayetArabic.setText(x.optString("arabic"));ayetTurkish.setText(x.optString("turkish_meaning"));ayetRef.setText(x.optString("surah")+" Suresi • "+x.optInt("ayah")+". Ayet");dateText.setText(new SimpleDateFormat("dd MMMM yyyy",new Locale("tr","TR")).format(new Date()));}catch(Exception e){}}
    void shareAyet(){
        shareAyetData(String.valueOf(categoryText.getText()), String.valueOf(ayetArabic.getText()), String.valueOf(ayetTurkish.getText()), String.valueOf(ayetRef.getText()));
    }

    void shareAyetData(String category, String arabic, String turkish, String ref){
        try{
            int w=1080,h=1350;
            Bitmap bmp=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            Canvas c=new Canvas(bmp);
            Paint bgp=new Paint(Paint.ANTI_ALIAS_FLAG); bgp.setColor(bg()); c.drawRect(0,0,w,h,bgp);
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(primary()); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(54); p.setTextAlign(Paint.Align.LEFT);
            c.drawText("Namaz Vaktim",60,90,p);
            p.setColor(accent()); p.setTextSize(28); c.drawText(category,60,135,p);
            p.setColor(text()); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(40); p.setTextAlign(Paint.Align.RIGHT);
            drawWrapped(c,p,arabic,w-60,220,64,w-120);
            p.setTypeface(Typeface.DEFAULT); p.setTextSize(34); p.setTextAlign(Paint.Align.LEFT);
            drawWrapped(c,p,turkish,60,850,50,w-120);
            p.setColor(secondary()); p.setTextSize(28);
            c.drawText(ref,60,1250,p);
            p.setColor(primary()); p.setTextSize(24); c.drawText("Namaz Vaktim",60,1295,p);
            File dir=new File(getCacheDir(),"shared"); if(!dir.exists())dir.mkdirs();
            File f=new File(dir,"ayet.png");
            FileOutputStream os=new FileOutputStream(f); bmp.compress(Bitmap.CompressFormat.PNG,100,os); os.close();
            Uri uri=androidx.core.content.FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f);
            Intent i=new Intent(Intent.ACTION_SEND); i.setType("image/png"); i.putExtra(Intent.EXTRA_STREAM,uri); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i,"Ayet görselini paylaş"));
        }catch(Exception e){ Toast.makeText(this,"Ayet görseli hazırlanamadı.",Toast.LENGTH_SHORT).show(); }
    }

    void drawWrapped(Canvas c,Paint p,String s,float x,float y,float line,float maxWidth){String[] words=s.split(" ");String lineText="";for(String word:words){String test=lineText.isEmpty()?word:lineText+" "+word;if(p.measureText(test)>maxWidth && !lineText.isEmpty()){c.drawText(lineText,x,y,p);y+=line;lineText=word;}else lineText=test;}if(!lineText.isEmpty())c.drawText(lineText,x,y,p);}

    String get(String u)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(12000);c.setRequestProperty("User-Agent","NamazVaktim/1.6");InputStream is=c.getInputStream();return new String(readAll(is),"UTF-8");}
    static byte[] readAll(InputStream i)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[]x=new byte[8192];int n;while((n=i.read(x))!=-1)b.write(x,0,n);return b.toByteArray();}

    @Override public void onBackPressed(){if(currentScreen.equals("main")){super.onBackPressed();}else showMainScreen();}
    void initQiblaSensor(){
        if(sensorManager!=null)return;
        sensorManager=(SensorManager)getSystemService(SENSOR_SERVICE);
        rotationSensor=sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }
    void startQiblaSensor(){
        initQiblaSensor();
        if(sensorManager==null)return;
        if(rotationSensor!=null){
            sensorManager.registerListener(this,rotationSensor,SensorManager.SENSOR_DELAY_GAME);
        }else{
            Sensor g=sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            Sensor m=sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            if(g!=null)sensorManager.registerListener(this,g,SensorManager.SENSOR_DELAY_GAME);
            if(m!=null)sensorManager.registerListener(this,m,SensorManager.SENSOR_DELAY_GAME);
        }
    }
    void stopQiblaSensor(){if(sensorManager!=null)sensorManager.unregisterListener(this);}
    double qiblaBearing(double lat,double lon){double lat1=Math.toRadians(lat),lat2=Math.toRadians(21.4225),dl=Math.toRadians(39.8262-lon);return (Math.toDegrees(Math.atan2(Math.sin(dl)*Math.cos(lat2),Math.cos(lat1)*Math.sin(lat2)-Math.sin(lat1)*Math.cos(lat2)*Math.cos(dl)))+360)%360;}
    void updateQibla(float az){double lat=prefs.getFloat("lat",0),lon=prefs.getFloat("lon",0);if(lat==0&&lon==0)return;float diff=(float)((qiblaBearing(lat,lon)-az+360)%360);if(qiblaDirectionText!=null)qiblaDirectionText.setText(String.format(Locale.US,"Kıble %.0f° • %s",qiblaBearing(lat,lon),directionName(diff)));if(qiblaArrow!=null)qiblaArrow.setRotation(diff);}
    String directionName(float d){if(d<22.5||d>=337.5)return "Önünde";if(d<67.5)return "Sağ ön";if(d<112.5)return "Sağ";if(d<157.5)return "Sağ arka";if(d<202.5)return "Arka";if(d<247.5)return "Sol arka";if(d<292.5)return "Sol";return "Sol ön";}
    void showQiblaDialog(){double lat=prefs.getFloat("lat",0),lon=prefs.getFloat("lon",0);if(lat==0&&lon==0){Toast.makeText(this,"Önce konumunuzu belirleyin.",Toast.LENGTH_SHORT).show();return;}new AlertDialog.Builder(this).setTitle("🧭 Kıble Yönü").setMessage(String.format(Locale.US,"Kâbe yönü: %.1f°\nTelefonunuzu yatay tutup pusula yönünü takip edin.",qiblaBearing(lat,lon))).setPositiveButton("Tamam",null).show();}
    @Override public void onSensorChanged(SensorEvent e){
        if(e.sensor.getType()==Sensor.TYPE_ROTATION_VECTOR){
            float[] r=new float[9]; SensorManager.getRotationMatrixFromVector(r,e.values);
            float[] o=new float[3]; SensorManager.getOrientation(r,o);
            float az=(float)Math.toDegrees(o[0]); if(az<0)az+=360; updateQibla(az); return;
        }
        if(e.sensor.getType()==Sensor.TYPE_ACCELEROMETER){ System.arraycopy(e.values,0,gravity,0,3); haveGravity=true; }
        if(e.sensor.getType()==Sensor.TYPE_MAGNETIC_FIELD){ System.arraycopy(e.values,0,geomagnetic,0,3); haveMagnetic=true; }
        if(haveGravity&&haveMagnetic){
            float[] r=new float[9], i=new float[9];
            if(SensorManager.getRotationMatrix(r,i,gravity,geomagnetic)){
                float[] o=new float[3]; SensorManager.getOrientation(r,o);
                float az=(float)Math.toDegrees(o[0]); if(az<0)az+=360; updateQibla(az);
            }
        }
    }
    @Override public void onAccuracyChanged(Sensor s,int a){}
    @Override protected void onResume(){super.onResume();applyAutomaticThemeFromTimings(true);startQiblaSensor();}
    @Override protected void onPause(){stopQiblaSensor();super.onPause();}

    @Override protected void onDestroy(){handler.removeCallbacks(ticker);executor.shutdownNow();super.onDestroy();}
}
