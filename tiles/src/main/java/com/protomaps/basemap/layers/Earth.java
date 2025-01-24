package com.protomaps.basemap.layers;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.protomaps.basemap.feature.FeatureId;
import com.protomaps.basemap.names.OsmNames;
import java.util.List;
import org.locationtech.jts.geom.Point;

public class Earth implements ForwardingProfile.LayerPostProcesser {
  @Override
  public String name() {
    return "earth";
  }

  public void processPreparedOsm(SourceFeature ignoredSf, FeatureCollector features) {
    features.polygon(this.name())
        .setZoomRange(3, 9).setBufferPixels(12).setAttr("kind", "earth");
    features.polygon(this.name())
        .setZoomRange(10, 15).setBufferPixels(8).setAttr("kind", "earth");
  }

  public void processNe(SourceFeature sf, FeatureCollector features) {
    var sourceLayer = sf.getSourceLayer();
    if (sourceLayer.equals("ne_10m_land")) {
      features.polygon(this.name()).setZoomRange(0, 2).setBufferPixels(8).setAttr("kind", "earth");
    }
    // Daylight landcover uses ESA WorldCover which only goes to a latitude of
    // roughly 80 deg S.
    // Parts of Antarctica therefore get no landcover = glacier from Daylight.
    // To fix this, we add glaciated areas from Natural Earth in Antarctica.
    if (sourceLayer.equals("ne_10m_glaciated_areas")) {
      try {
        Point centroid = (Point) sf.centroid();
        // Web Mercator Y = 0.7 is roughly 60 deg South, i.e., Antarctica.
        if (centroid.getY() > 0.7) {
          features.polygon("landcover")
              .setAttr("kind", "glacier")
              .setZoomRange(0, 7)
              .setMinPixelSize(0.0);
        }
      } catch (GeometryException e) {
        System.out.println("Error: " + e);
      }
    }
  }

  // For now the cliffs are very bad in quality, so we don't include them
  // public void processOsm(SourceFeature sf, FeatureCollector features) {
  // if (sf.canBeLine() && !sf.canBePolygon() && sf.hasTag("natural", "cliff")) {
  // int minZoom = 12;
  // var feat = features.line(this.name())
  // .setId(FeatureId.create(sf))
  // .setAttr("min_zoom", minZoom + 1)
  // .setAttr("kind", "cliff")
  // .setZoomRange(minZoom, 15);

  // OsmNames.setOsmNames(feat, sf, 0);
  // }
  // }

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items) throws GeometryException {
    return FeatureMerge.mergeOverlappingPolygons(items, 1);
  }
}
