package com.protomaps.basemap.layers;

import static com.protomaps.basemap.feature.Matcher.fromTag;
import static com.protomaps.basemap.feature.Matcher.getBoolean;
import static com.protomaps.basemap.feature.Matcher.getInteger;
import static com.protomaps.basemap.feature.Matcher.getString;
import static com.protomaps.basemap.feature.Matcher.rule;
import static com.protomaps.basemap.feature.Matcher.use;
import static com.protomaps.basemap.feature.Matcher.with;
import static com.protomaps.basemap.feature.Matcher.withPoint;
import static com.protomaps.basemap.feature.Matcher.withPolygon;
import static com.protomaps.basemap.feature.Matcher.without;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.expression.MultiExpression;
import com.onthegomap.planetiler.geo.GeoUtils;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.util.Parse;
import com.protomaps.basemap.feature.FeatureId;
import com.protomaps.basemap.names.OsmNames;
import java.util.List;
import java.util.Map;

@SuppressWarnings("java:S1192") // Duplicated string literals
public class Water implements ForwardingProfile.LayerPostProcessor {

  private static final double WORLD_AREA_FOR_70K_SQUARE_METERS =
    Math.pow(GeoUtils.metersToPixelAtEquator(0, Math.sqrt(70_000)) / 256d, 2);

  public static final String LAYER_NAME = "water";

  private static final MultiExpression.Index<Map<String, Object>> neIndex = MultiExpression.ofOrdered(List.of(
    rule(
      with("featurecla", "Ocean"),
      use("minZoom", fromTag("min_zoom")),
      use("kind", "ocean")
    ),
    rule(
      with("featurecla", "Playa"),
      use("minZoom", fromTag("min_zoom")),
      use("kind", "playa")
    ),
    rule(
      with("featurecla", "Reservoir"),
      use("minZoom", fromTag("min_zoom")),
      use("kind", "lake")
    ),
    rule(
      with("featurecla", "Lake"),
      use("minZoom", fromTag("min_zoom")),
      use("kind", "lake")
    ),
    rule(
      with("featurecla", "Alkaline Lake"),
      use("minZoom", fromTag("min_zoom")),
      use("kind", "lake")
    ),
    rule(
      without("""
          _source_layer
          ne_50m_ocean
          ne_50m_lakes
          ne_10m_ocean
          ne_10m_lakes
        """),
      use("kind", null)
    )
  )).index();

  private static final MultiExpression.Index<Map<String, Object>> osmIndex = MultiExpression.ofOrdered(List.of(
    rule(
      with("natural", "reef"),
      use("kind", "reef")
    ),
    rule(
      with("natural", "reef"),
      with("""
          reef
          coral
          rock
          sand
        """),
      use("kindDetail", fromTag("reef"))
    ),
    rule(
      with("waterway", "drain"),
      use("kind", "drain"),
      use("minZoom", 15)
    ),
    rule(
      with("waterway", "ditch"),
      use("kind", "ditch"),
      use("minZoom", 15)
    ),
    rule(
      with("waterway", "stream"),
      use("kind", "stream"),
      use("minZoom", 11)
    ),
    rule(
      with("waterway", "river"),
      use("kind", "river"),
      use("minZoom", 7)
    ),
    rule(
      with("waterway", "canal"),
      use("kind", "canal"),
      use("minZoom", 9)
    ),
    rule(
      with("waterway", "canal"),
      with("boat", "yes"),
      use("kind", "canal"),
      use("minZoom", 9)
    ),
    rule(
      with("amenity", "swimming_pool"),
      use("kind", "swimming_pool")
    ),
    rule(
      with("leisure", "swimming_pool"),
      use("kind", "swimming_pool")
    ),
    rule(
      with("landuse", "reservoir"),
      use("kind", "lake")
    ),
    rule(
      with("landuse", "basin"),
      use("kind", "basin")
    ),
    rule(
      with("""
          natural
          fjord
          strait
          bay
        """),
      use("kind", fromTag("natural")),
      use("keepPolygon", false)
    ),
    rule(
      with("natural", "water"),
      use("kind", "water")
    ),
    rule(
      with("natural", "water"),
      with("""
          water
          basin
          canal
          ditch
          drain
          lake
          river
          stream
        """),
      use("kindDetail", fromTag("water"))
    ),
    rule(
      with("natural", "water"),
      with("""
          water
          lagoon
          oxbow
          pond
          reservoir
          wastewater
        """),
      use("kindDetail", "lake")
    ),
    rule(
      with("amenity", "fountain"),
      use("kind", "fountain")
    ),
    rule(
      with("waterway", "dock"),
      use("kind", "dock")
    ),
    rule(
      with("waterway", "riverbank"),
      use("kind", "riverbank"),
      use("minZoom", 7)
    ),
    rule(
      with("covered", "yes"),
      use("kind", null)
    ),
    rule(
      with("place", "sea"),
      withPolygon(),
      use("kind", "sea"),
      use("keepPolygon", false)
    ),
    rule(
      with("place", "sea"),
      withPolygon(),
      with("""
          name:en
          North Sea
          Alboran Sea
        """),
      use("kind", null)
    ),
    rule(
      with("place", "sea"),
      withPolygon(),
      with("""
          name:en
          Caspian Sea
          Red Sea
          Persian Gulf
          Sea of Oman
          Gulf of Aden
          Gulf of Thailand
          Sea of Japan
        """),
      use("minZoom", 5)
    ),
    rule(
      with("place", "sea"),
      withPolygon(),
      with("""
          name:en
          Arabian Sea
          Bay of Bengal
          Black Sea
        """),
      use("minZoom", 3)
    ),
    rule(
      with("place", "sea"),
      with("""
            name:en
            North Sea
            Baltic Sea
            Black Sea
            Caspian Sea
        """),
      use("nameOverride", fromTag("name:en"))
    ),
    rule(
      with("place", "sea"),
      withPoint(),
      use("kind", "sea"),
      use("minZoom", 6)
    ),
    rule(
      with("place", "sea"),
      withPoint(),
      with("""
          name:en
          North Atlantic Ocean
          South Atlantic Ocean
          Caribbean Sea
          Gulf of Mexico
          Mediterranean Sea
          North Sea
          Philippine Sea
          Tasman Sea
          Fiji Sea
          South China Sea
          North Pacific Ocean
          South Pacific Ocean
          Scotia Sea
          Weddell Sea
          Indian Ocean
          Bering Sea
          Gulf of Alaska
          Gulf of Guinea
        """),
      use("minZoom", 3)
    ),
    rule(
      with("place", "ocean"),
      use("kind", "ocean"),
      use("minZoom", 0)
    )
  )).index();

  @Override
  public String name() {
    return LAYER_NAME;
  }

  @SuppressWarnings("java:S1172")
  public void processPreparedOsm(SourceFeature sf, FeatureCollector features) {
    features.polygon(LAYER_NAME)
      .setId(1)
      .setAttr("kind", "ocean")
      .setAttr("sort_rank", 200)
      .setPixelTolerance(Earth.PIXEL_TOLERANCE)
      .setMinZoom(6)
      .setBufferPixels(8);
  }

  public void processNe(SourceFeature sf, FeatureCollector features) {
    sf.setTag("_source_layer", sf.getSourceLayer());

    var matches = neIndex.getMatches(sf);
    if (matches.isEmpty()) {
      return;
    }

    String kind = getString(sf, matches, "kind", null);
    if (kind == null) {
      return;
    }

    String minZoomString = getString(sf, matches, "minZoom", null);

    if (sf.canBePolygon() && minZoomString != null) {
      int minZoom = (int) Math.round(Double.parseDouble(minZoomString));

      int themeMinZoom = sf.getSourceLayer().contains("_50m_") ? 0 : 4;
      int themeMaxZoom = sf.getSourceLayer().contains("_50m_") ? 3 : 5;

      int bufferPixels = 8;
      if (kind.equals("river") || kind.equals("lake")) {
        bufferPixels = 16;
      }

      features.polygon(LAYER_NAME)
        .setAttr("kind", kind)
        .setAttr("sort_rank", 200)
        .setPixelTolerance(Earth.PIXEL_TOLERANCE)
        .setZoomRange(Math.max(themeMinZoom, minZoom), themeMaxZoom)
        .setMinPixelSize(1.0)
        .setBufferPixels(bufferPixels);
    }
  }

  public void processOsm(SourceFeature sf, FeatureCollector features) {
    var matches = osmIndex.getMatches(sf);
    if (matches.isEmpty()) {
      return;
    }

    String kind = getString(sf, matches, "kind", null);
    if (kind == null) {
      return;
    }

    String nameOverride = getString(sf, matches, "nameOverride", null);
    if (nameOverride != null) {
      sf.setTag("name", nameOverride);
    }

    String kindDetail = getString(sf, matches, "kindDetail", null);
    boolean keepPolygon = getBoolean(sf, matches, "keepPolygon", true);

    int extraAttrMinzoom = 14;

    // polygons
    if (sf.canBePolygon() && keepPolygon) {
      // For riverbanks, we want to keep more detail at lower zoom levels
      double pixelTol = kind.equals("riverbank") ? Earth.PIXEL_TOLERANCE * 0.75 : Earth.PIXEL_TOLERANCE;
      double minPixelSize = kind.equals("riverbank") || kind.equals("river") ? 0.5 : 1.0;  // Smaller min size for riverbanks
      
      features.polygon(LAYER_NAME)
        .setAttr("kind", kind)
        .setAttr("kind_detail", kindDetail)
        .setAttr("sort_rank", 200)
        .setAttrWithMinzoom("bridge", sf.getString("bridge"), extraAttrMinzoom)
        .setAttrWithMinzoom("tunnel", sf.getString("tunnel"), extraAttrMinzoom)
        .setAttrWithMinzoom("layer", Parse.parseIntOrNull(sf.getString("layer")), extraAttrMinzoom)
        .setPixelTolerance(pixelTol)
        .setMinZoom(6)
        .setBufferPixels(8);
      
      // Only set minPixelSize if it's not a river
      if (!kind.equals("river")) {
        polygonFeature.setMinPixelSize(minPixelSize);
      }
    }

    // lines
    if (sf.canBeLine() && !sf.canBePolygon()) {
      int minZoom = getInteger(sf, matches, "minZoom", 12);
      
      // Set smaller pixel tolerance for rivers to keep more detail
      double lineTolerance = (kind.equals("river") || kind.equals("canal")) ? 0 : 0.5;
      
      // Adjust buffer pixels based on kind and minZoom - larger buffers for rivers at lower zooms
      int bufferPixels = 4;
      if ((kind.equals("river") || kind.equals("canal")) && minZoom <= 8) {
        bufferPixels = 12;
      } else if ((kind.equals("river") || kind.equals("canal"))) {
        bufferPixels = 8;
      }

      var feat = features.line(LAYER_NAME)
        .setId(FeatureId.create(sf))
        .setAttr("kind", kind)
        .setAttr("min_zoom", minZoom + 1)
        .setAttrWithMinzoom("layer", Parse.parseIntOrNull(sf.getString("layer")), extraAttrMinzoom)
        .setAttr("sort_rank", 200)
        .setSortKey(minZoom)
        .setMinPixelSize(0)
        .setPixelTolerance(lineTolerance)
        .setBufferPixels(bufferPixels)
        .setMinZoom(minZoom);

      OsmNames.setOsmNames(feat, sf, 0);
    }

    // points
    if (sf.isPoint()) {
      int minZoom = getInteger(sf, matches, "minZoom", 15);
      var feat = features.point(LAYER_NAME)
        .setId(FeatureId.create(sf))
        .setAttr("kind", kind)
        .setAttr("min_zoom", minZoom + 1)
        .setSortKey(minZoom)
        .setMinZoom(minZoom);

      OsmNames.setOsmNames(feat, sf, 0);
    }

    // points from polygons
    if (sf.hasTag("name") && sf.canBePolygon()) {
      int nameMinZoom = 15;
      Double wayArea = 0.0;

      try {
        wayArea = sf.area() / WORLD_AREA_FOR_70K_SQUARE_METERS;
      } catch (GeometryException e) {
        e.log("Exception in way area calculation");
      }

      for (int i = 6; i < 15; ++i) {
        if (wayArea > Math.pow(4.0, 15.0 - i)) {
          nameMinZoom = i;
          break;
        }
      }

      nameMinZoom = getInteger(sf, matches, "minZoom", nameMinZoom);

      var waterLabelPosition = features.pointOnSurface(LAYER_NAME)
        .setAttr("kind", kind)
        .setAttr("kind_detail", kindDetail)
        // While other layers don't need min_zoom, physical point labels do for more
        // predictable client-side label collisions
        // 512 px zooms versus 256 px logical zooms
        .setAttr("min_zoom", nameMinZoom + 1)
        .setMinZoom(nameMinZoom)
        .setAttr("sort_rank", 200)
        .setSortKey(nameMinZoom)
        .setBufferPixels(128);

      OsmNames.setOsmNames(waterLabelPosition, sf, 0);
    }
  }

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items) throws GeometryException {
    // Adjust line merging parameters to keep more detail for rivers at lower zoom levels
    double pixelTolerance = zoom < 9 ? 0.1 : Earth.PIXEL_TOLERANCE;
    double minArea = zoom < 9 ? Earth.MIN_AREA * 0.5 : Earth.MIN_AREA;
    
    // Use different buffer values based on zoom level
    double buffer = Earth.BUFFER;
    // For lower zooms (4-6), use a larger buffer to avoid gaps in rivers
    if (zoom <= 6) {
      buffer = Earth.BUFFER * 2;
    } else if (zoom <= 8) {
      buffer = Earth.BUFFER * 1.5;
    }
    
    // Modify line merging parameters for lower zooms to keep more detail
    double mergeDistance = zoom <= 6 ? 0.25 : 0.5;
    double simplifyTolerance = zoom <= 6 ? 1.0 : 2.0;
    
    items = FeatureMerge.mergeLineStrings(items, mergeDistance, pixelTolerance, simplifyTolerance);
    return FeatureMerge.mergeNearbyPolygons(items, minArea, minArea, mergeDistance, buffer);
  }
}
