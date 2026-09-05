# Namaz Vaktim 1.4.0

Namaz vakitleri, geri sayım, sabit kerahat aralıkları, günlük ayet, menü, namaz kılınışı rehberi, dualar, Kur'an okuyucu ve ayet arama özelliklerini içeren Android uygulaması.

## Özellikler
- İlk açılışta konum veya şehir seçimi; seçim kaydedilir.
- Namaz vakitleri ve bir sonraki namaza geri sayım.
- Sabit kerahat aralıkları: güneş doğuşundan sonra 20 dk, öğle öncesi 10 dk, güneş batışından 20 dk önce.
- Sağ üst menü: Namaz Vakitleri, Nasıl Kılınır?, Ayetler, Dualar, Ayarlar.
- Nasıl Kılınır?: abdest ve beş vakit namaz rehberi.
- Kur'an: 604 sayfa, sayfa seçimi, son sayfayı hatırlama, yakınlaştırma, Sure/Ayet Bul ve Mushaf'ta Gör.
- Bildirim zamanı 1-60 dakika arasında kullanıcı tarafından seçilebilir.
- Yeşil ve Gece olmak üzere iki tema.
- 365 günlük ayet arşivi.

## Derleme
GitHub Actions workflow'u `.github/workflows/android.yml` üzerinden `assembleDebug` çalıştırır ve APK'yı artifact olarak yayınlar.

## Ağ gereksinimi
Namaz vakitleri AlAdhan API üzerinden, Kur'an Mushaf sayfaları alquran.cloud üzerinden alınır. Konum/şehir çözümlemesi için Android Geocoder kullanılır.


## 1.6.0 Otomatik Tema

İmsak vaktinde Açık Tema, Akşam namazı vaktinde Gece Tema otomatik uygulanır. Ayasofya gündüz/gece arka planları uygulama sayfalarında kullanılır.
