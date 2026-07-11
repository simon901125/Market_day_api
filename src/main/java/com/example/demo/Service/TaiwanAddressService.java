package com.example.demo.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

@Service
public class TaiwanAddressService {

    private static final Map<String, Set<String>> CITY_DISTRICTS = createCityDistricts();

    public boolean isValidCity(String city) {
        String normalizedCity = normalize(city);
        return normalizedCity != null && CITY_DISTRICTS.containsKey(normalizedCity);
    }

    public boolean isValidDistrict(String city, String district) {
        String normalizedCity = normalize(city);
        String normalizedDistrict = normalize(district);
        if (normalizedCity == null || normalizedDistrict == null) {
            return false;
        }

        Set<String> districts = CITY_DISTRICTS.get(normalizedCity);
        return districts != null && districts.contains(normalizedDistrict);
    }

    public Set<String> cities() {
        return CITY_DISTRICTS.keySet();
    }

    public Set<String> districts(String city) {
        Set<String> districts = CITY_DISTRICTS.get(normalize(city));
        return districts == null ? Set.of() : districts;
    }

    private static Map<String, Set<String>> createCityDistricts() {
        Map<String, Set<String>> cityDistricts = new LinkedHashMap<>();

        cityDistricts.put("基隆市", orderedSet("仁愛區", "信義區", "中正區", "中山區", "安樂區", "暖暖區", "七堵區"));
        cityDistricts.put("台北市", orderedSet("中正區", "大同區", "中山區", "松山區", "大安區", "萬華區", "信義區", "士林區", "北投區", "內湖區", "南港區", "文山區"));
        cityDistricts.put("新北市", orderedSet("板橋區", "三重區", "中和區", "永和區", "新莊區", "新店區", "樹林區", "鶯歌區", "三峽區", "淡水區", "汐止區", "瑞芳區", "土城區", "蘆洲區", "五股區", "泰山區", "林口區", "深坑區", "石碇區", "坪林區", "三芝區", "石門區", "八里區", "平溪區", "雙溪區", "貢寮區", "金山區", "萬里區", "烏來區"));
        cityDistricts.put("桃園市", orderedSet("桃園區", "中壢區", "平鎮區", "八德區", "楊梅區", "蘆竹區", "大溪區", "龍潭區", "龜山區", "大園區", "觀音區", "新屋區", "復興區"));
        cityDistricts.put("新竹市", orderedSet("東區", "北區", "香山區"));
        cityDistricts.put("新竹縣", orderedSet("竹北市", "竹東鎮", "新埔鎮", "關西鎮", "湖口鄉", "新豐鄉", "芎林鄉", "橫山鄉", "北埔鄉", "寶山鄉", "峨眉鄉", "尖石鄉", "五峰鄉"));
        cityDistricts.put("苗栗縣", orderedSet("苗栗市", "苑裡鎮", "通霄鎮", "竹南鎮", "頭份市", "後龍鎮", "卓蘭鎮", "大湖鄉", "公館鄉", "銅鑼鄉", "南庄鄉", "頭屋鄉", "三義鄉", "西湖鄉", "造橋鄉", "三灣鄉", "獅潭鄉", "泰安鄉"));
        cityDistricts.put("台中市", orderedSet("中區", "東區", "南區", "西區", "北區", "西屯區", "南屯區", "北屯區", "豐原區", "東勢區", "大甲區", "清水區", "沙鹿區", "梧棲區", "后里區", "神岡區", "潭子區", "大雅區", "新社區", "石岡區", "外埔區", "大安區", "烏日區", "大肚區", "龍井區", "霧峰區", "太平區", "大里區", "和平區"));
        cityDistricts.put("彰化縣", orderedSet("彰化市", "員林市", "和美鎮", "鹿港鎮", "溪湖鎮", "二林鎮", "田中鎮", "北斗鎮", "花壇鄉", "芬園鄉", "大村鄉", "永靖鄉", "伸港鄉", "線西鄉", "福興鄉", "秀水鄉", "埔心鄉", "埔鹽鄉", "大城鄉", "芳苑鄉", "竹塘鄉", "社頭鄉", "二水鄉", "田尾鄉", "埤頭鄉", "溪州鄉"));
        cityDistricts.put("南投縣", orderedSet("南投市", "埔里鎮", "草屯鎮", "竹山鎮", "集集鎮", "名間鄉", "鹿谷鄉", "中寮鄉", "魚池鄉", "國姓鄉", "水里鄉", "信義鄉", "仁愛鄉"));
        cityDistricts.put("雲林縣", orderedSet("斗六市", "斗南鎮", "虎尾鎮", "西螺鎮", "土庫鎮", "北港鎮", "古坑鄉", "大埤鄉", "莿桐鄉", "林內鄉", "二崙鄉", "崙背鄉", "麥寮鄉", "東勢鄉", "褒忠鄉", "台西鄉", "元長鄉", "四湖鄉", "口湖鄉", "水林鄉"));
        cityDistricts.put("嘉義市", orderedSet("東區", "西區"));
        cityDistricts.put("嘉義縣", orderedSet("太保市", "朴子市", "布袋鎮", "大林鎮", "民雄鄉", "溪口鄉", "新港鄉", "六腳鄉", "東石鄉", "義竹鄉", "鹿草鄉", "水上鄉", "中埔鄉", "竹崎鄉", "梅山鄉", "番路鄉", "大埔鄉", "阿里山鄉"));
        cityDistricts.put("台南市", orderedSet("中西區", "東區", "南區", "北區", "安平區", "安南區", "永康區", "歸仁區", "新化區", "左鎮區", "玉井區", "楠西區", "南化區", "仁德區", "關廟區", "龍崎區", "官田區", "麻豆區", "佳里區", "西港區", "七股區", "將軍區", "學甲區", "北門區", "新營區", "後壁區", "白河區", "東山區", "六甲區", "下營區", "柳營區", "鹽水區", "善化區", "大內區", "山上區", "新市區", "安定區"));
        cityDistricts.put("高雄市", orderedSet("新興區", "前金區", "苓雅區", "鹽埕區", "鼓山區", "旗津區", "前鎮區", "三民區", "楠梓區", "小港區", "左營區", "仁武區", "大社區", "岡山區", "路竹區", "阿蓮區", "田寮區", "燕巢區", "橋頭區", "梓官區", "彌陀區", "永安區", "湖內區", "鳳山區", "大寮區", "林園區", "鳥松區", "大樹區", "旗山區", "美濃區", "六龜區", "內門區", "杉林區", "甲仙區", "桃源區", "那瑪夏區", "茂林區", "茄萣區"));
        cityDistricts.put("屏東縣", orderedSet("屏東市", "潮州鎮", "東港鎮", "恆春鎮", "萬丹鄉", "長治鄉", "麟洛鄉", "九如鄉", "里港鄉", "鹽埔鄉", "高樹鄉", "萬巒鄉", "內埔鄉", "竹田鄉", "新埤鄉", "枋寮鄉", "新園鄉", "崁頂鄉", "林邊鄉", "南州鄉", "佳冬鄉", "琉球鄉", "車城鄉", "滿州鄉", "枋山鄉", "三地門鄉", "霧台鄉", "瑪家鄉", "泰武鄉", "來義鄉", "春日鄉", "獅子鄉", "牡丹鄉"));
        cityDistricts.put("宜蘭縣", orderedSet("宜蘭市", "羅東鎮", "蘇澳鎮", "頭城鎮", "礁溪鄉", "壯圍鄉", "員山鄉", "冬山鄉", "五結鄉", "三星鄉", "大同鄉", "南澳鄉"));
        cityDistricts.put("花蓮縣", orderedSet("花蓮市", "鳳林鎮", "玉里鎮", "新城鄉", "吉安鄉", "壽豐鄉", "光復鄉", "豐濱鄉", "瑞穗鄉", "富里鄉", "秀林鄉", "萬榮鄉", "卓溪鄉"));
        cityDistricts.put("台東縣", orderedSet("台東市", "成功鎮", "關山鎮", "卑南鄉", "鹿野鄉", "池上鄉", "東河鄉", "長濱鄉", "太麻里鄉", "大武鄉", "綠島鄉", "海端鄉", "延平鄉", "金峰鄉", "達仁鄉", "蘭嶼鄉"));
        cityDistricts.put("澎湖縣", orderedSet("馬公市", "湖西鄉", "白沙鄉", "西嶼鄉", "望安鄉", "七美鄉"));
        cityDistricts.put("金門縣", orderedSet("金城鎮", "金湖鎮", "金沙鎮", "金寧鄉", "烈嶼鄉", "烏坵鄉"));
        cityDistricts.put("連江縣", orderedSet("南竿鄉", "北竿鄉", "莒光鄉", "東引鄉"));

        return Collections.unmodifiableMap(cityDistricts);
    }

    private static Set<String> orderedSet(String... values) {
        return Collections.unmodifiableSet(new java.util.LinkedHashSet<>(java.util.Arrays.asList(values)));
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().replace('臺', '台');
    }
}
