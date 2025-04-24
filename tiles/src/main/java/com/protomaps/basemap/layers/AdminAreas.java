package com.protomaps.basemap.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmReader;
import com.onthegomap.planetiler.reader.osm.OsmRelationInfo;
import com.onthegomap.planetiler.util.Parse;
import com.protomaps.basemap.feature.FeatureId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

public class AdminAreas implements ForwardingProfile.OsmRelationPreprocessor,
    ForwardingProfile.LayerPostProcessor {

  public static final String LAYER_NAME = "admin_areas";
  
  // Map of country names to ISO codes for countries with known issues
  private static final Map<String, String> COUNTRY_NAME_TO_ISO = new HashMap<>();
  
  // Map of ISO alpha-3 to ISO alpha-2 codes
  private static final Map<String, String> ISO3_TO_ISO2 = new HashMap<>();
  
  static {
    // Initialize name-to-ISO map for countries with known issues
    COUNTRY_NAME_TO_ISO.put("France", "FR");
    COUNTRY_NAME_TO_ISO.put("Germany", "DE");
    COUNTRY_NAME_TO_ISO.put("Italy", "IT");
    COUNTRY_NAME_TO_ISO.put("Spain", "ES");
    COUNTRY_NAME_TO_ISO.put("United Kingdom", "GB");
    COUNTRY_NAME_TO_ISO.put("United States of America", "US");
    COUNTRY_NAME_TO_ISO.put("United States", "US");
    COUNTRY_NAME_TO_ISO.put("Canada", "CA");
    COUNTRY_NAME_TO_ISO.put("Brazil", "BR");
    COUNTRY_NAME_TO_ISO.put("Russia", "RU");
    COUNTRY_NAME_TO_ISO.put("Australia", "AU");
    COUNTRY_NAME_TO_ISO.put("China", "CN");
    COUNTRY_NAME_TO_ISO.put("Japan", "JP");
    COUNTRY_NAME_TO_ISO.put("India", "IN");
    COUNTRY_NAME_TO_ISO.put("Mexico", "MX");
    COUNTRY_NAME_TO_ISO.put("South Africa", "ZA");
    
    // Initialize ISO3 to ISO2 mapping
    ISO3_TO_ISO2.put("FRA", "FR");
    ISO3_TO_ISO2.put("DEU", "DE");
    ISO3_TO_ISO2.put("ITA", "IT");
    ISO3_TO_ISO2.put("ESP", "ES");
    ISO3_TO_ISO2.put("GBR", "GB");
    ISO3_TO_ISO2.put("USA", "US");
    ISO3_TO_ISO2.put("CAN", "CA");
    ISO3_TO_ISO2.put("BRA", "BR");
    ISO3_TO_ISO2.put("RUS", "RU");
    ISO3_TO_ISO2.put("AUS", "AU");
    ISO3_TO_ISO2.put("CHN", "CN");
    ISO3_TO_ISO2.put("JPN", "JP");
    ISO3_TO_ISO2.put("IND", "IN");
    ISO3_TO_ISO2.put("MEX", "MX");
    ISO3_TO_ISO2.put("ZAF", "ZA");
  }

  @Override
  public String name() {
    return LAYER_NAME;
  }

  public void processNe(SourceFeature sf, FeatureCollector features) {
    var sourceLayer = sf.getSourceLayer();
    
    // Process Natural Earth administrative boundaries
    if (sf.canBePolygon() && (
        sourceLayer.equals("ne_50m_admin_0_countries") || 
        sourceLayer.equals("ne_10m_admin_0_countries") ||
        sourceLayer.equals("ne_10m_admin_1_states_provinces"))) {
      
      int minZoom = sourceLayer.equals("ne_50m_admin_0_countries") ? 0 : 4;
      int maxZoom = sourceLayer.equals("ne_50m_admin_0_countries") ? 3 : 5;
      
      String kind = "country";
      int adminLevel = 2;
      
      if (sourceLayer.equals("ne_10m_admin_1_states_provinces")) {
        kind = "region";
        adminLevel = 4;
      }
      
      String name = sf.getString("name");
      
      var polygon = features.polygon(this.name())
        .setId(FeatureId.create(sf))
        .setAttr("kind", kind)
        .setAttr("kind_detail", adminLevel)
        .setAttr("name", name)
        .setAttr("sort_rank", 200 - adminLevel) // Higher admin levels (smaller areas) drawn on top
        .setZoomRange(minZoom, maxZoom)
        .setMinPixelSize(1.0)
        .setPixelTolerance(Earth.PIXEL_TOLERANCE)
        .setBufferPixels(8);
        
      // Try to find the ISO code using multiple strategies
      
      // Strategy 1: Try common field names for ISO alpha-2 codes
      String isoCode = null;
      String[] possibleIsoFields = {
        "iso_a2", "ISO_A2", "ISO_A2_EH", "ISO3166-1", "ADM0_A2", "adm0_a2", "SOV_A2"
      };
      
      for (String field : possibleIsoFields) {
        String value = sf.getString(field);
        if (value != null && !value.isEmpty() && !value.equals("-99") && !value.equals("-")) {
          // Only use if it looks like a valid ISO alpha-2 code (2 characters)
          if (value.length() == 2) {
            isoCode = value;
            polygon.setAttr("iso_code", isoCode);
            break;
          }
        }
      }
      
      // Strategy 2: Try to map from ISO alpha-3 if alpha-2 wasn't found
      if (isoCode == null || isoCode.isEmpty() || isoCode.equals("-99") || isoCode.equals("-")) {
        // Try common field names for ISO alpha-3 codes
        String[] alpha3Fields = {"ADM0_A3", "BRK_A3", "ISO_A3"};
        
        for (String field : alpha3Fields) {
          String alpha3 = sf.getString(field);
          if (alpha3 != null && !alpha3.isEmpty() && !alpha3.equals("-99") && !alpha3.equals("-")) {
            // If we have a mapping for this ISO3 code, use it
            String mappedCode = ISO3_TO_ISO2.get(alpha3);
            if (mappedCode != null) {
              polygon.setAttr("iso_code", mappedCode);
              isoCode = mappedCode;
              break;
            }
            
            // Store the alpha-3 code as a fallback
            polygon.setAttr("iso_code3", alpha3);
          }
        }
      }
      
      // Strategy 3: Fallback to country name lookup for countries with known issues
      if ((isoCode == null || isoCode.isEmpty() || isoCode.equals("-99") || isoCode.equals("-")) && name != null) {
        String mappedCode = COUNTRY_NAME_TO_ISO.get(name);
        if (mappedCode != null) {
          polygon.setAttr("iso_code", mappedCode);
        }
      }
    }
  }

  public void processOsm(SourceFeature sf, FeatureCollector features) {
    // Only process multipolygons and relations that can be polygons
    if (sf.canBePolygon()) {
      // Get related admin boundary info
      List<OsmReader.RelationMember<AdminRecord>> recs = sf.relationInfo(AdminRecord.class);
      
      // Only continue if we have admin boundary info
      if (!recs.isEmpty()) {
        OptionalInt minAdminLevel = recs.stream().mapToInt(r -> r.relation().adminLevel).min();
        OptionalInt disputed = recs.stream().mapToInt(r -> r.relation().disputed).max();

        // Skip if no admin level found
        if (!minAdminLevel.isPresent()) {
          return;
        }

        String kind = "";
        int themeMinZoom = 6;

        // Determine kind and min zoom level based on admin level
        switch (minAdminLevel.getAsInt()) {
          case 2:
            kind = "country";
            themeMinZoom = 6;
            break;
          case 3:
            kind = "region"; // used in Colombia, Brazil, Kenya (historical)
            themeMinZoom = 6;
            break;
          case 4:
            kind = "region";
            themeMinZoom = 6;
            break;
          case 5:
            kind = "county"; // used in Colombia, Brazil
            themeMinZoom = 8;
            break;
          case 6:
            kind = "county";
            themeMinZoom = 8;
            break;
          case 8:
            kind = "locality";
            themeMinZoom = 10;
            break;
          default:
            kind = "locality";
            themeMinZoom = 10;
            break;
        }

        if (!kind.isEmpty()) {
          // Create polygon feature
          var polygon = features.polygon(this.name())
            .setId(FeatureId.create(sf))
            .setAttr("kind", kind)
            .setAttr("kind_detail", minAdminLevel.getAsInt())
            .setAttr("sort_rank", 200 - minAdminLevel.getAsInt()) // Higher admin levels drawn on top
            .setMinZoom(themeMinZoom)
            .setMinPixelSize(0.0) // Don't filter small polygons
            .setPixelTolerance(Earth.PIXEL_TOLERANCE)
            .setBufferPixels(8);

          // Add name if available
          String name = sf.getString("name");
          if (name != null && !name.isEmpty()) {
            polygon.setAttr("name", name);
          }
          
          // Add ISO country code if available - try multiple possible tag formats
          String isoCode = sf.getString("ISO3166-1:alpha2");
          if (isoCode == null || isoCode.isEmpty()) {
            isoCode = sf.getString("ISO3166-1");
          }
          if (isoCode == null || isoCode.isEmpty()) {
            isoCode = sf.getString("country_code_iso3166_1_alpha_2");
          }
          if (isoCode == null || isoCode.isEmpty()) {
            isoCode = sf.getString("iso3166-1:alpha2");
          }
          
          if (isoCode != null && !isoCode.isEmpty() && isoCode.length() == 2) {
            polygon.setAttr("iso_code", isoCode);
          } 
          // Fallback to country name lookup for known countries
          else if (name != null && minAdminLevel.getAsInt() == 2) {
            String mappedCode = COUNTRY_NAME_TO_ISO.get(name);
            if (mappedCode != null) {
              polygon.setAttr("iso_code", mappedCode);
            }
          }

          // Mark disputed areas
          if (disputed.isPresent() && disputed.getAsInt() == 1) {
            polygon.setAttr("disputed", true);
          }
        }
      }
    }
  }

  @Override
  public List<OsmRelationInfo> preprocessOsmRelation(OsmElement.Relation relation) {
    if (relation.hasTag("type", "boundary") &&
        relation.hasTag("boundary", "administrative")) {
      Integer adminLevel = Parse.parseIntOrNull(relation.getString("admin_level"));
      Integer disputed = relation.hasTag("boundary", "disputed") ? 1 : 0;

      if (adminLevel == null || adminLevel > 8)
        return null;
      return List.of(new AdminRecord(relation.id(), adminLevel, disputed));
    } else if (relation.hasTag("type", "boundary") && 
               relation.hasTag("boundary", "disputed")) {
      // Handle explicit disputed boundaries
      Integer adminLevel = Parse.parseIntOrNull(relation.getString("admin_level"));
      if (adminLevel == null) {
        // Default to country level for disputed areas without explicit admin_level
        adminLevel = 2;
      }
      if (adminLevel > 8) {
        return null;
      }
      return List.of(new AdminRecord(relation.id(), adminLevel, 1));
    }
    return null;
  }

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items) throws GeometryException {    
    double tolerance = 0.4;
    if (zoom < 6) {
      tolerance = 0.2;
    }
    return FeatureMerge.mergeNearbyPolygons(items, 0, 0, tolerance, 12);
  }

  private record AdminRecord(long id, int adminLevel, int disputed) implements OsmRelationInfo {}
} 