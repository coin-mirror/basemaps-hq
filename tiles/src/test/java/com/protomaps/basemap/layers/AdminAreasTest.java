package com.protomaps.basemap.layers;

import static com.onthegomap.planetiler.TestUtils.newPolygon;

import com.onthegomap.planetiler.reader.SimpleFeature;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdminAreasTest extends LayerTest {

  @Test
  void testNeCountry() {
    var feature = SimpleFeature.create(
      newPolygon(0, 0, 0, 1, 1, 1, 1, 0, 0, 0),
      new HashMap<>(Map.of(
        "name", "Test Country",
        "iso_a2", "TC"
      )),
      "ne",
      "ne_50m_admin_0_countries",
      123
    );

    var collector = featureCollectorFactory.get(feature);
    profile.processFeature(feature, collector);

    assertFeatures(2,
      List.of(Map.of(
        "kind", "country",
        "kind_detail", 2,
        "name", "Test Country",
        "iso_code", "TC",
        "_minzoom", 0,
        "_maxzoom", 3
      )),
      collector
    );
  }

  @Test
  void testNeRegion() {
    var feature = SimpleFeature.create(
      newPolygon(0, 0, 0, 1, 1, 1, 1, 0, 0, 0),
      new HashMap<>(Map.of(
        "name", "Test Region"
      )),
      "ne",
      "ne_10m_admin_1_states_provinces",
      123
    );

    var collector = featureCollectorFactory.get(feature);
    profile.processFeature(feature, collector);

    assertFeatures(5,
      List.of(Map.of(
        "kind", "region",
        "kind_detail", 4,
        "name", "Test Region",
        "_minzoom", 4,
        "_maxzoom", 5
      )),
      collector
    );
  }

  @Test
  void testOsmCountry() {
    var infos = profile.preprocessOsmRelation(
      new OsmElement.Relation(1, Map.of(
        "type", "boundary",
        "boundary", "administrative",
        "admin_level", "2",
        "name", "Test Country"
      ), List.of(new OsmElement.Relation.Member(OsmElement.Type.WAY, 123, "")))
    );

    var feature = SimpleFeature.createFakeOsmFeature(
      newPolygon(0, 0, 0, 1, 1, 1, 1, 0, 0, 0),
      new HashMap<>(Map.of(
        "name", "Test Country",
        "ISO3166-1:alpha2", "TC"
      )),
      "osm",
      null,
      123,
      infos.stream().map(r -> new OsmReader.RelationMember<>("", r)).toList()
    );

    var collector = featureCollectorFactory.get(feature);
    profile.processFeature(feature, collector);

    assertFeatures(8,
      List.of(Map.of(
        "kind", "country",
        "kind_detail", 2,
        "name", "Test Country",
        "iso_code", "TC",
        "_minzoom", 6
      )),
      collector
    );
  }

  @Test
  void testOsmCountryAlternateIsoTag() {
    var infos = profile.preprocessOsmRelation(
      new OsmElement.Relation(1, Map.of(
        "type", "boundary",
        "boundary", "administrative",
        "admin_level", "2",
        "name", "Test Country"
      ), List.of(new OsmElement.Relation.Member(OsmElement.Type.WAY, 123, "")))
    );

    var feature = SimpleFeature.createFakeOsmFeature(
      newPolygon(0, 0, 0, 1, 1, 1, 1, 0, 0, 0),
      new HashMap<>(Map.of(
        "name", "Test Country",
        "ISO3166-1", "TC"
      )),
      "osm",
      null,
      123,
      infos.stream().map(r -> new OsmReader.RelationMember<>("", r)).toList()
    );

    var collector = featureCollectorFactory.get(feature);
    profile.processFeature(feature, collector);

    assertFeatures(8,
      List.of(Map.of(
        "kind", "country",
        "kind_detail", 2,
        "name", "Test Country",
        "iso_code", "TC",
        "_minzoom", 6
      )),
      collector
    );
  }

  @Test
  void testOsmRegion() {
    var infos = profile.preprocessOsmRelation(
      new OsmElement.Relation(1, Map.of(
        "type", "boundary",
        "boundary", "administrative",
        "admin_level", "4",
        "name", "Test Region"
      ), List.of(new OsmElement.Relation.Member(OsmElement.Type.WAY, 123, "")))
    );

    var feature = SimpleFeature.createFakeOsmFeature(
      newPolygon(0, 0, 0, 1, 1, 1, 1, 0, 0, 0),
      new HashMap<>(Map.of(
        "name", "Test Region" 
      )),
      "osm",
      null,
      123,
      infos.stream().map(r -> new OsmReader.RelationMember<>("", r)).toList()
    );

    var collector = featureCollectorFactory.get(feature);
    profile.processFeature(feature, collector);

    assertFeatures(8,
      List.of(Map.of(
        "kind", "region",
        "kind_detail", 4,
        "name", "Test Region",
        "_minzoom", 6
      )),
      collector
    );
  }

  @Test
  void testOsmCounty() {
    var infos = profile.preprocessOsmRelation(
      new OsmElement.Relation(1, Map.of(
        "type", "boundary",
        "boundary", "administrative",
        "admin_level", "6",
        "name", "Test County"
      ), List.of(new OsmElement.Relation.Member(OsmElement.Type.WAY, 123, "")))
    );

    var feature = SimpleFeature.createFakeOsmFeature(
      newPolygon(0, 0, 0, 1, 1, 1, 1, 0, 0, 0),
      new HashMap<>(Map.of(
        "name", "Test County"
      )),
      "osm",
      null,
      123,
      infos.stream().map(r -> new OsmReader.RelationMember<>("", r)).toList()
    );

    var collector = featureCollectorFactory.get(feature);
    profile.processFeature(feature, collector);

    assertFeatures(10,
      List.of(Map.of(
        "kind", "county",
        "kind_detail", 6,
        "name", "Test County",
        "_minzoom", 8
      )),
      collector
    );
  }

  @Test
  void testDisputedTerritory() {
    var infos = profile.preprocessOsmRelation(
      new OsmElement.Relation(1, Map.of(
        "type", "boundary",
        "boundary", "disputed",
        "admin_level", "2",
        "name", "Disputed Territory"
      ), List.of(new OsmElement.Relation.Member(OsmElement.Type.WAY, 123, "")))
    );

    var feature = SimpleFeature.createFakeOsmFeature(
      newPolygon(0, 0, 0, 1, 1, 1, 1, 0, 0, 0),
      new HashMap<>(Map.of(
        "name", "Disputed Territory"
      )),
      "osm",
      null,
      123,
      infos.stream().map(r -> new OsmReader.RelationMember<>("", r)).toList()
    );

    var collector = featureCollectorFactory.get(feature);
    profile.processFeature(feature, collector);

    assertFeatures(8,
      List.of(Map.of(
        "kind", "country",
        "kind_detail", 2,
        "name", "Disputed Territory",
        "disputed", true,
        "_minzoom", 6
      )),
      collector
    );
  }
} 