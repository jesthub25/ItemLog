# ItemLog

Yetkililer için item takip eklentisi. Takip edilen itemlerin **yere atılmasını, yerden alınmasını ve ölümde düşmesini** loglar; kimin kime ne verdiğini zincir halinde gösterir.

**Uyumluluk:** Spigot / Paper / Purpur **1.16.5 – 1.21.x** · Java 8+ · tek JAR

## Özellikler

- Atma → alma zinciri (item yerde birleşse bile bağlantı korunur)
- Ölüm düşüşü ve ölüden alma kaydı
- İki oyuncu arası takas geçmişi (`/itemlog a takas b`)
- GUI: sayfalama, tür filtresi, detay ekranı, bağlı kayda geçiş
- Detaydan konuma ışınlanma ve itemin birebir kopyasını alma
- Shulker içeriği görüntüleme
- Materyal joker karakteri (`NETHERITE_*`), dünya/koordinat bölge filtresi
- SQLite (varsayılan) veya MySQL/MariaDB
- Otomatik eski kayıt temizleme (`retention-days`)
- Tüm veritabanı işleri ana thread dışında; yazmalar tek sıralı kuyrukta

## Komutlar

| Komut | Açıklama | Yetki |
|---|---|---|
| `/itemlog` | Kendi logların | `itemlog.use` |
| `/itemlog <oyuncu>` | Oyuncunun logları | `itemlog.others` |
| `/itemlog <oyuncu> takas` | Sen ↔ oyuncu transferleri | `itemlog.takas` |
| `/itemlog <a> takas <b>` | a ↔ b transferleri | `itemlog.takas` + `itemlog.others` |
| `/itemlog reload`, `/itemlogreload` | Konfigürasyonu yenile | `itemlog.reload` |

Diğer yetkiler: `itemlog.teleport` (ışınlanma), `itemlog.give` (kopya alma), `itemlog.*` (hepsi). Hepsi varsayılan olarak OP.

## Kurulum

1. [Releases](https://github.com/jesthub25/ItemLog/releases) sayfasından `ItemLog-x.y.z.jar` indir.
2. `plugins/` klasörüne at, sunucuyu başlat.
3. `plugins/ItemLog/config.yml` dosyasını düzenle, `/itemlog reload`.

> `database` bölümündeki değişiklikler için sunucu yeniden başlatılmalıdır.

## Derleme

```bash
mvn clean package
# çıktı: target/ItemLog-<sürüm>.jar
```

Her `v*` etiketi GitHub Actions ile derlenip otomatik olarak release'e eklenir.

## Sürüm notları

### 1.1.0
- 1.16.5 – 1.21.x arası tüm Spigot tabanlı sunucularda çalışacak şekilde yeniden yazıldı (Adventure / InventoryView / Enchantment bağımlılığı kaldırıldı, Java 8 bytecode).
- **Düzeltme:** SQLite bağlantısı ilk sorgudan sonra kapanıyordu.
- **Düzeltme:** `getOrDefault` derleme hatası, `Location#getNearbyEntities` (Paper'a özel) kullanımı.
- **Düzeltme:** Sayfada tıklanan satır, araya yeni log girince yanlış kaydı açabiliyordu.
- **Düzeltme:** Son sayfada "ileri" tıklanınca boş sayfaya geçiliyordu.
- **Düzeltme:** Takas listesinden detaya girip geri dönünce normal listeye düşüyordu.
- **Düzeltme:** Çevrimdışı oyuncu adı ana thread'de Mojang'a soruluyordu (lag).
- **Düzeltme:** Reload sırasında async thread'ler yarım yüklenmiş config görebiliyordu.
- **Düzeltme:** Envanter doluyken kopya alınan item kayboluyordu.
- **Yeni:** item birleşme takibi, bağlı kayda geçiş, `/itemlog a takas b`, joker materyaller, olay türü aç/kapa, kayıt saklama süresi, tarih biçimi/saat dilimi ayarı, yenile butonu.
- Kapanışta bekleyen tüm loglar yazılır.
