package app.revanced.extension.twitter.patches.customise.appIcon;

import java.util.ArrayList;
import java.util.List;

public class IconListBuilder {

    public List<RowItem> buildRows() {
        List<RowItem> list = new ArrayList<>();

        list.add(RowItem.header("piko_app_icon_thanks"));
        list.add(RowItem.header("piko_app_icon_category_legacy"));
        list.add(RowItem.icon("piko_app_icon_name_default","ic_launcher_twitter"));
        list.add(RowItem.icon("piko_app_icon_name_legacy_icon_1","ic_app_icon_legacy_icon_1","81"));
        list.add(RowItem.icon("piko_app_icon_name_legacy_icon_2","ic_app_icon_legacy_icon_2","82"));
        list.add(RowItem.icon("piko_app_icon_name_legacy_icon_3","ic_app_icon_legacy_icon_3","83"));
        list.add(RowItem.icon("piko_app_icon_name_legacy_icon_4","ic_app_icon_legacy_icon_4","84"));
        list.add(RowItem.icon("piko_app_icon_name_legacy_blue","ic_app_icon_legacy_blue","80"));
        list.add(RowItem.icon("piko_app_icon_name_twitter_blue","ic_app_icon_blue","79"));

        list.add(RowItem.header("piko_app_icon_category_space"));
        list.add(RowItem.icon("piko_app_icon_name_earth","ic_app_icon_10","40"));
        list.add(RowItem.icon("piko_app_icon_name_earth","ic_app_icon_11","41"));
        list.add(RowItem.icon("piko_app_icon_name_mars","ic_app_icon_12","42"));
        list.add(RowItem.exclusiveIcon("piko_app_icon_name_sun","ic_app_icon_sun","86"));
        list.add(RowItem.icon("piko_app_icon_name_stars","ic_app_icon_13","43"));
        list.add(RowItem.exclusiveIcon("piko_app_icon_name_milky_way","ic_app_icon_milkyway","85"));

        list.add(RowItem.header("piko_app_icon_category_seasonal_event"));
        list.add(RowItem.icon("piko_app_icon_name_autumn_2021","ic_seasonal_autumn_2021","0"));
        list.add(RowItem.icon("piko_app_icon_name_autumn_2022","ic_seasonal_autumn_2022","1"));
        list.add(RowItem.icon("piko_app_icon_name_autumn_southern","ic_seasonal_autumn_southern","48"));
        list.add(RowItem.icon("piko_app_icon_name_south_spring_2021","ic_seasonal_south_spring_2021","2"));
        list.add(RowItem.icon("piko_app_icon_name_south_spring_2022","ic_seasonal_south_spring_2022","3"));
        list.add(RowItem.icon("piko_app_icon_name_winter","ic_seasonal_winter","61"));
        list.add(RowItem.icon("piko_app_icon_name_winter_1","ic_seasonal_winter_1","4"));
        list.add(RowItem.icon("piko_app_icon_name_winter_2","ic_seasonal_winter_2","5"));
        list.add(RowItem.icon("piko_app_icon_name_summer","ic_seasonal_summer","60"));
        list.add(RowItem.icon("piko_app_icon_name_summer_1","ic_seasonal_summer_1","6"));
        list.add(RowItem.icon("piko_app_icon_name_summer_2","ic_seasonal_summer_2","7"));

        list.add(RowItem.header("piko_app_icon_category_sports"));
        list.add(RowItem.icon("piko_app_icon_name_beijing_olympics_1","ic_seasonal_beijing_olympics_1","8"));
        list.add(RowItem.icon("piko_app_icon_name_beijing_olympics_2","ic_seasonal_beijing_olympics_2","9"));
        list.add(RowItem.icon("piko_app_icon_name_daytona","ic_seasonal_daytona","10"));
        list.add(RowItem.icon("piko_app_icon_name_formulaone","ic_seasonal_formulaone","11"));
        list.add(RowItem.icon("piko_app_icon_name_kentucky_derby","ic_seasonal_kentucky_derby","12"));
        list.add(RowItem.icon("piko_app_icon_name_mlb","ic_seasonal_mlb","13"));
        list.add(RowItem.icon("piko_app_icon_name_nba","ic_seasonal_nba","14"));
        list.add(RowItem.icon("piko_app_icon_name_nba_2","ic_seasonal_nba_2","15"));
        list.add(RowItem.icon("piko_app_icon_name_nba_finals","ic_seasonal_nba_finals","57"));
        list.add(RowItem.icon("piko_app_icon_name_masters","ic_seasonal_masters","17"));
        list.add(RowItem.icon("piko_app_icon_name_commonwealth","ic_seasonal_commonwealth","51"));
        list.add(RowItem.icon("piko_app_icon_name_stanley_cup","ic_seasonal_stanley_cup","59"));

        list.add(RowItem.header("piko_app_icon_category_cultural_and_celebrations"));
        list.add(RowItem.icon("piko_app_icon_name_mothers_day","ic_seasonal_mothers_day","18"));
        list.add(RowItem.icon("piko_app_icon_name_womansday","ic_seasonal_womansday","19"));
        list.add(RowItem.icon("piko_app_icon_name_earth_hour","ic_seasonal_earth_hour","52"));
        list.add(RowItem.icon("piko_app_icon_name_halloween_2021","ic_seasonal_halloween_2021","20"));
        list.add(RowItem.icon("piko_app_icon_name_halloween_2022","ic_seasonal_halloween_2022","21"));
        list.add(RowItem.icon("piko_app_icon_name_halloween_2024_1","ic_app_icon_15","45"));
        list.add(RowItem.icon("piko_app_icon_name_halloween_2024_2","ic_app_icon_16","46"));
        list.add(RowItem.icon("piko_app_icon_name_halloween_2024_3","ic_app_icon_17","47"));
        list.add(RowItem.icon("piko_app_icon_name_holi","ic_seasonal_holi","22"));
        list.add(RowItem.icon("piko_app_icon_name_stpatricks_day","ic_seasonal_stpatricks_day","23"));
        list.add(RowItem.icon("piko_app_icon_name_easter","ic_seasonal_easter","24"));
        list.add(RowItem.icon("piko_app_icon_name_anzac","ic_seasonal_anzac","25"));
        list.add(RowItem.icon("piko_app_icon_name_ramadan","ic_seasonal_ramadan","26"));
        list.add(RowItem.icon("piko_app_icon_name_black_history","ic_seasonal_black_history","27"));
        list.add(RowItem.icon("piko_app_icon_name_independence_day","ic_seasonal_independence_day","54"));
        list.add(RowItem.icon("piko_app_icon_name_canada_day","ic_seasonal_canada_day","49"));
        list.add(RowItem.icon("piko_app_icon_name_canada_indigenous","ic_seasonal_canada_indigenous","50"));
        list.add(RowItem.icon("piko_app_icon_name_euro","ic_seasonal_euro","53"));
        list.add(RowItem.icon("piko_app_icon_name_eurovisionfinal","ic_seasonal_eurovisionfinal","28"));
        list.add(RowItem.icon("piko_app_icon_name_lunar_new_year_1","ic_seasonal_lunar_new_year_1","29"));
        list.add(RowItem.icon("piko_app_icon_name_lunar_new_year_2","ic_seasonal_lunar_new_year_2","30"));
        list.add(RowItem.icon("piko_app_icon_name_japanese_new_year_2024_1","ic_app_icon_28","77"));
        list.add(RowItem.icon("piko_app_icon_name_japanese_new_year_2024_2","ic_app_icon_29","78"));
        list.add(RowItem.icon("piko_app_icon_name_may_the_fourth","ic_seasonal_may_the_fourth","31"));
        list.add(RowItem.icon("piko_app_icon_name_barbie","ic_seasonal_barbie","32"));
        list.add(RowItem.icon("piko_app_icon_name_thanksgiving_1","ic_seasonal_thanksgiving_1","33"));
        list.add(RowItem.icon("piko_app_icon_name_thanksgiving_2","ic_seasonal_thanksgiving_2","34"));
        list.add(RowItem.icon("piko_app_icon_name_thanksgiving_2024_1","ic_app_icon_18","67"));
        list.add(RowItem.icon("piko_app_icon_name_thanksgiving_2024_2","ic_app_icon_19","68"));
        list.add(RowItem.icon("piko_app_icon_name_thanksgiving_2024_3","ic_app_icon_20","69"));
        list.add(RowItem.icon("piko_app_icon_name_thanksgiving_2024_4","ic_app_icon_21","70"));
        list.add(RowItem.icon("piko_app_icon_name_thanksgiving_2024_5","ic_app_icon_22","71"));
        list.add(RowItem.icon("piko_app_icon_name_thanksgiving_2024_6","ic_app_icon_23","72"));
        list.add(RowItem.icon("piko_app_icon_name_juneteenth","ic_seasonal_juneteenth","71"));
        list.add(RowItem.icon("piko_app_icon_name_naidoc","ic_seasonal_naidoc","56"));
        list.add(RowItem.icon("piko_app_icon_name_christmas_2024_1","ic_app_icon_24","73"));
        list.add(RowItem.icon("piko_app_icon_name_christmas_2024_2","ic_app_icon_25","74"));
        list.add(RowItem.icon("piko_app_icon_name_christmas_2024_3","ic_app_icon_26","75"));
        list.add(RowItem.icon("piko_app_icon_name_christmas_2024_4","ic_app_icon_27","76"));

        list.add(RowItem.header("piko_app_icon_category_movies_n_series"));
        list.add(RowItem.exclusiveIcon("piko_app_icon_name_upsidedown","ic_app_icon_upsidedown","87"));
        list.add(RowItem.exclusiveIcon("piko_app_icon_name_games_chamber","ic_app_icon_games_chamber","88"));

        list.add(RowItem.header("piko_app_icon_category_pride"));
        list.add(RowItem.icon("piko_app_icon_name_pridesouthern","ic_seasonal_pridesouthern","35"));
        list.add(RowItem.icon("piko_app_icon_name_newzealand_pride_1","ic_seasonal_newzealand_pride_1","36"));
        list.add(RowItem.icon("piko_app_icon_name_newzealand_pride_2","ic_seasonal_newzealand_pride_2","37"));
        list.add(RowItem.icon("piko_app_icon_name_pride_month","ic_seasonal_pride_month","58"));

        list.add(RowItem.header("piko_app_icon_category_misc"));
        list.add(RowItem.icon("piko_app_icon_name_broken","ic_app_icon_7","38"));
        list.add(RowItem.icon("piko_app_icon_name_fancy","ic_app_icon_8","39"));
        list.add(RowItem.icon("piko_app_icon_name_cyber","ic_app_icon_14","44"));
        list.add(RowItem.icon("piko_app_icon_name_soft_pastel_pink","ic_app_icon_2","62"));
        list.add(RowItem.icon("piko_app_icon_name_electric_indigo","ic_app_icon_3","63"));
        list.add(RowItem.icon("piko_app_icon_name_neon_pink","ic_app_icon_4","64"));
        list.add(RowItem.icon("piko_app_icon_name_tropical_teal","ic_app_icon_5","65"));
        list.add(RowItem.icon("piko_app_icon_name_vivid_orange","ic_app_icon_6","66"));

        return list;
    }
}
